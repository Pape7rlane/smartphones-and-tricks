import importlib.machinery
import importlib.util
import inspect
import os
import queue
import sys
import threading
import traceback
from datetime import datetime
from time import perf_counter as _perf_counter

_module = None
_lock = threading.Lock()
_log_file = None
_MAX_LOG_FILE_BYTES = 2 * 1024 * 1024


class _LogSink:
    """Log writer that can never lose or stall the on-sdcard log.

    Two very different sinks are involved:

    * the on-sdcard log file, which is the authoritative record that the
      in-app log view and our debugging rely on;
    * the host stream (logcat on Chaquopy), whose writes can block for a very
      long time — long enough that draining it through one thread silently
      stopped the log while startup kept going.

    The log file is therefore written synchronously on the calling thread: a
    local file write is fast and cannot stall on logcat. Only the host-stream
    copy is handed to a background thread, and if that thread falls behind we
    drop lines rather than ever block the caller.
    """

    def __init__(self, log_file, maxsize=10000):
        self.log_file = log_file
        self._log_lock = threading.Lock()
        self.queue = queue.Queue(maxsize=maxsize)
        self.dropped = 0
        self._drop_lock = threading.Lock()
        threading.Thread(
            target=self._run, name="ppsr-host-writer", daemon=True
        ).start()

    def write_log(self, text):
        """Write to the on-sdcard log synchronously. Never raises, never blocks."""
        with self._log_lock:
            try:
                self.log_file.write(text)
                if "\n" in text:
                    self.log_file.flush()
                    if self.log_file.tell() >= _MAX_LOG_FILE_BYTES:
                        self.log_file.seek(0)
                        self.log_file.truncate(0)
                        self.log_file.write("[log rotated at 2 MB]\n")
                        self.log_file.flush()
            except BaseException:
                pass

    def emit_host(self, stream, text):
        try:
            self.queue.put_nowait((stream, text))
        except queue.Full:
            with self._drop_lock:
                self.dropped += 1

    def _run(self):
        while True:
            stream, text = self.queue.get()
            try:
                if stream is not None:
                    stream.write(text)
                    if "\n" in text:
                        stream.flush()
            except BaseException:
                pass


class _TeeStream:
    def __init__(self, original, log_file, sink):
        self.original = original
        self.log_file = log_file
        self.sink = sink
        self.encoding = "utf-8"

    def write(self, value):
        if not value:
            return 0
        # Log file first, synchronously: it must survive even if the host
        # stream is wedged. The host copy is best-effort and asynchronous.
        self.sink.write_log(value)
        self.sink.emit_host(self.original, value)
        return len(value)

    def flush(self):
        # The log file is already flushed on every newline by write_log; the
        # host copy is drained asynchronously and needs no explicit flush.
        return None

    def isatty(self):
        return False

    def __getattr__(self, name):
        if self.original is None:
            raise AttributeError(name)
        return getattr(self.original, name)


_log_sink = None


def _configure_logging(server_dir):
    global _log_file, _log_sink
    log_dir = os.path.join(server_dir, "logs")
    os.makedirs(log_dir, exist_ok=True)
    log_path = os.path.join(log_dir, "server.log")
    open(log_path, "w", encoding="utf-8").close()
    _log_file = open(log_path, "a", encoding="utf-8", buffering=1)
    _log_sink = _LogSink(_log_file)
    sys.stdout = _TeeStream(sys.stdout, _log_file, _log_sink)
    sys.stderr = _TeeStream(sys.stderr, _log_file, _log_sink)
    _arm_faulthandler(_log_file)
    _start_heartbeat()
    print(f"[{datetime.now().astimezone().isoformat(timespec='seconds')}] "
          "ppSR 4.6.53 live log started")


def _start_heartbeat(interval=5):
    """Write a heartbeat line straight to the log while startup runs.

    When nothing else is being logged this tells us whether the process is
    still working (heartbeats keep coming) or has died (they stop). It stops
    by itself once the servers are up.
    """
    def beat():
        import time as _time
        n = 0
        while _module is None:
            _time.sleep(interval)
            if _module is not None or _log_sink is None:
                return
            n += 1
            _log_sink.write_log(
                f"[{datetime.now().astimezone().isoformat(timespec='seconds')}] "
                f"heartbeat {n}: startup still in progress\n")
    threading.Thread(target=beat, name="ppsr-heartbeat", daemon=True).start()


def _arm_faulthandler(log_file):
    """Dump a Python stack trace into the log if startup ever stalls.

    faulthandler writes straight to the log file descriptor from its own
    thread, so the dump still appears even when the main thread is wedged
    inside an import — the exact failure mode that produced no traceback.
    """
    try:
        import faulthandler
        faulthandler.enable(file=log_file, all_threads=True)
        faulthandler.dump_traceback_later(20, repeat=True, file=log_file)
        _stage("faulthandler armed: stack dump every 20 s if startup stalls")
    except BaseException as exc:
        _stage(f"faulthandler not armed: {exc!r}")


def _disarm_faulthandler():
    try:
        import faulthandler
        faulthandler.cancel_dump_traceback_later()
    except BaseException:
        pass


def _stage(message):
    print(f"[{datetime.now().astimezone().isoformat(timespec='seconds')}] {message}", flush=True)


_import_timing_installed = False
_import_state = {"name": None, "started": 0.0}


def _install_import_timing(threshold=0.2):
    """Log any module import slower than `threshold` seconds.

    A watchdog thread also reports the import currently in progress, so a
    startup that hangs (rather than raising) still shows up in the live log.
    """
    global _import_timing_installed
    if _import_timing_installed:
        return
    _import_timing_installed = True
    import builtins
    import time as _time
    real_import = builtins.__import__

    def timed_import(name, *args, **kwargs):
        previous = _import_state["name"]
        started = _time.perf_counter()
        _import_state["name"] = name
        _import_state["started"] = started
        try:
            module = real_import(name, *args, **kwargs)
        finally:
            _import_state["name"] = previous
        elapsed = _time.perf_counter() - started
        if elapsed >= threshold:
            _stage(f"import {name}: {elapsed * 1000:.0f} ms")
        return module

    builtins.__import__ = timed_import

    def watchdog():
        while True:
            _time.sleep(5)
            name = _import_state["name"]
            if name is None:
                continue
            running = _time.perf_counter() - _import_state["started"]
            if running >= 5:
                _stage(f"watchdog: still importing {name!r} after {running:.0f} s")

    threading.Thread(target=watchdog, name="ppsr-import-watchdog", daemon=True).start()


# Modules owned by Chaquopy itself: never serve these from the extracted copies.
_EXTRACTED_SKIP = {"java", "chaquopy", "chaquopy_java", "app_bridge"}


def _asset_finder_roots():
    """Directories Chaquopy extracted the APK Python assets into.

    Chaquopy loads modules straight out of the APK through its JNI-backed
    AssetFinder. When `extractPackages` is enabled the same files also exist
    under files/chaquopy/AssetFinder, and importing them from there uses the
    normal (much faster) filesystem importer instead.
    """
    marker = os.path.join("chaquopy", "AssetFinder")
    return [entry for entry in sys.path if marker in entry and os.path.isdir(entry)]


def _pure_python_names(root):
    """Top-level import names under `root` that contain no native code.

    Anything backed by a .so is left to Chaquopy so its DT_NEEDED libraries
    (e.g. libssl for _ssl) are preloaded as usual.
    """
    names = set()
    try:
        entries = os.listdir(root)
    except OSError:
        return names
    for name in entries:
        if name in _EXTRACTED_SKIP or name.startswith("_") or not name.isidentifier():
            continue
        path = os.path.join(root, name)
        if os.path.isfile(path):
            if path.endswith((".pyc", ".py")):
                names.add(name)
        elif os.path.isdir(path):
            for _, _, files in os.walk(path):
                if any(f.endswith(".so") for f in files):
                    break
            else:
                names.add(name)
    return names


_extracted_importer_installed = False
_extracted_finder = None


def _prefer_extracted_modules(server_dir=None):
    """Import extracted modules from the filesystem instead of from the APK.

    This is the single biggest startup win on Chaquopy: it replaces the
    per-import JNI AssetFinder with the native filesystem importer. If the
    extracted copies aren't available (extractPackages not set, extraction
    failed, ...) this is a no-op and imports fall back to normal.

    Create a file named `.no-extract-import` in the server directory to turn
    this off at runtime, for A/B comparison without rebuilding the APK.
    """
    global _extracted_importer_installed, _extracted_finder
    if _extracted_importer_installed:
        return False
    _extracted_importer_installed = True
    try:
        if server_dir and os.path.exists(os.path.join(server_dir, ".no-extract-import")):
            _stage("extracted import: disabled by .no-extract-import")
            return False

        import importlib.machinery as machinery

        roots = _asset_finder_roots()
        if not roots:
            _stage("extracted import: no extracted asset roots, using APK assets")
            return False

        names = set()
        for root in roots:
            names |= _pure_python_names(root)
        if not names:
            _stage("extracted import: nothing extracted, using APK assets")
            return False
        _stage(f"extracted roots: {[os.path.basename(r) for r in roots]}")
        _stage(f"extracted names: {sorted(names)}")

        hook = machinery.FileFinder.path_hook(
            (machinery.ExtensionFileLoader, machinery.EXTENSION_SUFFIXES),
            (machinery.SourcelessFileLoader, machinery.BYTECODE_SUFFIXES),
            (machinery.SourceFileLoader, machinery.SOURCE_SUFFIXES),
        )
        finder_cache = {}

        def finder_for(directory):
            if directory in finder_cache:
                return finder_cache[directory]
            try:
                finder = hook(directory)
            except ImportError:
                finder = None
            finder_cache[directory] = finder
            return finder

        class _ExtractedFinder:
            def find_spec(self, fullname, path=None, target=None):
                if fullname.partition(".")[0] not in names:
                    return None
                for directory in (path if path else roots):
                    finder = finder_for(directory)
                    if finder is None:
                        continue
                    spec = finder.find_spec(fullname, target)
                    if spec is not None:
                        return spec
                return None

        _extracted_finder = _ExtractedFinder()
        sys.meta_path.insert(0, _extracted_finder)
        _stage(f"extracted import enabled for {len(names)} packages")
        return True
    except BaseException as exc:
        _stage(f"extracted import disabled: {exc!r}")
        return False


def _disable_extracted_modules():
    global _extracted_finder
    if _extracted_finder is not None and _extracted_finder in sys.meta_path:
        sys.meta_path.remove(_extracted_finder)
    _extracted_finder = None


def _freeze_gc_for_startup():
    """Move the already-loaded modules into a permanent GC generation.

    Decoding freesr-data.json allocates thousands of objects; without this the
    collector rescans every module, class and function on each generational
    pass. Frozen objects are never collected, which is what we want for
    module-level state that lives for the whole process.
    """
    try:
        import gc
        gc.collect()
        gc.freeze()
        _stage("gc: froze the module generation for startup")
    except BaseException as exc:
        _stage(f"gc freeze skipped ({exc!r})")


def _preinstall_google_namespace():
    """Register `google` as a plain package before anything imports it.

    Chaquopy's `google/__init__.py` (from the protobuf wheel) calls
    `pkg_resources.declare_namespace('google')`, which drags in the whole
    deprecated setuptools pkg_resources machinery — ~4 s on device, and it is
    pure overhead: nothing here needs namespace-package merging. Installing a
    `google` module whose __path__ points straight at the extracted directory
    makes `import google.protobuf` skip that __init__ entirely (and use the
    filesystem importer rather than Chaquopy's AssetFinder).

    Redirecting google's submodules also redirects the native `google._upb`
    extension, which normally goes through Chaquopy's loader, so this is
    validated by actually importing protobuf and the upb extension and is
    reverted if either fails — a miss here costs nothing but the 4 s.
    """
    if "google" in sys.modules:
        return False
    paths = []
    for root in _asset_finder_roots():
        candidate = os.path.join(root, "google")
        if os.path.isdir(candidate):
            paths.append(candidate)
    if not paths:
        return False

    import types
    saved = {name: mod for name, mod in sys.modules.items()
             if name == "google" or name.startswith("google.")}
    module = types.ModuleType("google")
    module.__path__ = paths
    module.__package__ = "google"
    module.__doc__ = "google namespace preinstalled by ppSR (skips pkg_resources)"
    sys.modules["google"] = module
    try:
        import google.protobuf  # noqa: F401
        import google._upb._message  # noqa: F401  native: must load
    except BaseException as exc:
        for name in [n for n in sys.modules
                     if n == "google" or n.startswith("google.")]:
            sys.modules.pop(name, None)
        sys.modules.update(saved)
        _stage(f"google namespace preinstall reverted ({exc!r}); "
               "pkg_resources will load normally")
        return False
    _stage("google namespace preinstalled (skips pkg_resources): "
           f"{[os.path.dirname(p) for p in paths]}")
    return True


def _load_server_module(server_dir):
    """Load ppsr.pyc, falling back to the APK assets if the extracted import
    path breaks anything, so the server still starts (just more slowly)."""
    path = os.path.join(server_dir, "ppsr.pyc")

    def _import():
        loader = importlib.machinery.SourcelessFileLoader("ppsr_embedded", path)
        spec = importlib.util.spec_from_loader(loader.name, loader)
        module = importlib.util.module_from_spec(spec)
        loader.exec_module(module)
        return module

    before = set(sys.modules)
    try:
        return _import()
    except BaseException:
        if _extracted_finder is None:
            raise
        traceback.print_exc()
        _stage("extracted import broke the server; retrying from the APK assets")
        for name in [n for n in sys.modules if n not in before]:
            sys.modules.pop(name, None)
        _disable_extracted_modules()
        return _import()


_runtime_shims_installed = False


def _install_runtime_shims():
    """Headless runtime shims, ported from the reference ppSR Android loader.

    Everything here is local: nothing touches the network, and nothing is
    downloaded. It only makes the server behave under a GUI host.
    """
    global _runtime_shims_installed
    if _runtime_shims_installed:
        return
    _runtime_shims_installed = True

    # 1. The server runs without a console, so a blocking input() would hang or
    #    raise EOFError. Log the prompt and return an empty line instead.
    try:
        import builtins
        if not getattr(builtins.input, "_ppsr_shim", False):
            def _input(prompt=""):
                if prompt:
                    print(f"[input] {prompt}")
                return ""
            _input._ppsr_shim = True
            builtins.input = _input
            _stage("runtime shims: input() made non-blocking")
    except BaseException as exc:
        _stage(f"runtime shims: input() patch skipped ({exc!r})")

    # 2. The server calls werkzeug.serving.make_server with an `options` keyword
    #    that it doesn't accept; drop it and re-enable address reuse so a restart
    #    can rebind port 21000 immediately.
    try:
        import socket
        import werkzeug.serving as _serving
        if not getattr(_serving.make_server, "_ppsr_shim", False):
            _original_make_server = _serving.make_server

            def _make_server(*args, **kwargs):
                kwargs.pop("options", None)
                server = _original_make_server(*args, **kwargs)
                try:
                    if getattr(server, "socket", None) is not None:
                        server.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                except OSError:
                    pass
                return server

            _make_server._ppsr_shim = True
            _serving.make_server = _make_server
            _stage("runtime shims: werkzeug make_server patched")
    except BaseException as exc:
        _stage(f"runtime shims: werkzeug patch skipped ({exc!r})")

    # 3. Silence marshmallow/dataclasses-json deprecation noise in the log.
    try:
        import warnings
        for pattern in (".*default.*deprecated.*", ".*metadata.*deprecated.*"):
            warnings.filterwarnings("ignore", message=pattern)
    except BaseException:
        pass


def _make_memo(fn, name):
    """Wrap a pure function of a hashable first argument with a memo.

    The overwhelmingly common call shape is fn(obj) with no extra arguments —
    dataclasses_json asks the same question about the same type for every
    element it decodes — so that path is a single dict lookup. Calls with
    extra positional or keyword arguments use a tuple key, falling back to
    object identity when an argument is unhashable (undefined.py passes the
    globalns dict). No lock is needed: writes are atomic under the GIL and a
    lost race only recomputes the same value.
    """
    cache = {}
    missing = object()

    def memoized(obj, *args, **kwargs):
        if not args and not kwargs:
            try:
                value = cache.get(obj, missing)
            except TypeError:
                return fn(obj)  # unhashable obj: don't try to cache it
            if value is not missing:
                return value
            value = fn(obj)
            cache[obj] = value
            return value
        try:
            hash(obj)
            kobj = obj
        except TypeError:
            # obj itself may be unhashable (dataclasses_json calls some
            # helpers with a list), so it has to be keyed by identity too —
            # otherwise the fallback key still contains the unhashable object
            # and cache.get() raises TypeError.
            kobj = ("id", id(obj))
        try:
            k = (kobj, args, tuple(sorted(kwargs.items())))
            hash(k)  # an unhashable arg only fails when the key is hashed
        except TypeError:
            k = (kobj, tuple(map(id, args)),
                 tuple(sorted((a, id(b)) for a, b in kwargs.items())))
        value = cache.get(k, missing)
        if value is not missing:
            return value
        value = fn(obj, *args, **kwargs)
        cache[k] = value
        return value

    memoized._ppsr_memoized = True
    memoized.__name__ = name
    return memoized


def _cache_dataclasses_json_hot_paths():
    """Memoize dataclasses_json's per-object and per-type resolution.

    dataclasses_json._decode_dataclass runs once for every decoded object, and
    every field of every object runs through the typing helpers below. All of
    them are pure functions of a type object (or a class), and the results are
    stable, so memoizing turns the repeated work into a dict lookup.

    Two groups matter for decoding the bundled 720 KB freesr-data.json:

      * per class:  core.py:178 get_type_hints(cls) re-resolves every
        annotation (importing their modules); core.py:159
        _user_overrides_or_exts(cls) rebuilds a defaultdict over fields(cls)
        twice. Both are O(fields) on every object.

      * per field/type: _is_supported_generic / _is_optional / _is_collection /
        _is_mapping / _is_counter / _is_tuple / _is_generic_dataclass /
        _is_new_type / _issubclass_safe / _get_type_origin / _get_type_args /
        _get_type_cons. These are called for every element of every collection
        and are dominated by issubclass() calls against typing constructs,
        which raise and swallow TypeError each time. That is the single
        biggest cost when decoding deeply nested data.

    core and mm import these helpers by value, so each memo is installed in
    the defining module and every module that copied it.
    """
    patched = []
    try:
        import dataclasses_json.core as _core
        import dataclasses_json.utils as _utils
    except BaseException as exc:
        _stage(f"dataclasses_json hot-path cache skipped ({exc!r})")
        return
    try:
        import dataclasses_json.mm as _mm
    except BaseException:
        _mm = None
    try:
        import dataclasses_json.undefined as _undefined
    except BaseException:
        _undefined = None

    def memoize(name, definition, *copies):
        real = getattr(definition, name, None)
        if real is None or getattr(real, "_ppsr_memoized", False):
            return
        memo = _make_memo(real, name)
        setattr(definition, name, memo)
        for module in copies:
            if module is not None and getattr(module, name, None) is not None:
                setattr(module, name, memo)
        patched.append(name)

    # Per-class resolution.
    memoize("get_type_hints", _core, _undefined)
    memoize("_user_overrides_or_exts", _core, _mm)

    # Per-field / per-type predicates (defined in utils, copied into core/mm).
    for name in ("_is_optional", "_is_collection", "_is_mapping", "_is_counter",
                 "_is_tuple", "_is_generic_dataclass", "_is_new_type",
                 "_issubclass_safe", "_get_type_origin", "_get_type_args",
                 "_get_type_cons"):
        memoize(name, _utils, _core, _mm)

    # _is_supported_generic is defined in core and copied into mm.
    memoize("_is_supported_generic", _core, _mm)

    # fields(cls) and is_dataclass(x) are pure functions of the class/type, but
    # dataclasses_json calls them several times per decoded object.
    memoize("fields", _core)
    memoize("is_dataclass", _core)

    if patched:
        _stage(f"dataclasses_json: memoized {len(patched)} helpers "
               f"({', '.join(patched)})")
    else:
        _stage("dataclasses_json: hot-path cache already installed")


def _install_fast_decoder():
    """Replace dataclasses_json._decode_dataclass with a precomputed version.

    The stock _decode_dataclass rebuilds everything that only depends on the
    class — fields(cls), the letter-case name map, the per-field type, the
    override decoder and the branch it will take — for every single decoded
    object. For the bundled freesr-data.json that is thousands of objects, so
    the same work is thrown away thousands of times.

    This version computes a per-class plan once (memoized in `plans`) and then
    the per-object loop only does the value-dependent work. Behaviour is kept
    identical, including the None warnings, the NewType unwrapping (the
    optional check uses the *raw* type, matching the original) and the
    undefined-parameter handling.

    Must run after _cache_dataclasses_json_hot_paths so the captured helpers
    are the memoized ones. core, api and mm all imported _decode_dataclass by
    value, so all three are repointed.
    """
    try:
        import dataclasses_json.core as _core
    except BaseException as exc:
        _stage(f"fast decoder skipped ({exc!r})")
        return
    from dataclasses import MISSING, fields as _dc_fields, is_dataclass as _dc_is_dataclass
    import warnings as _warnings

    _isinstance_safe = _core._isinstance_safe
    _user_overrides_or_exts = _core._user_overrides_or_exts
    _decode_letter_case_overrides = _core._decode_letter_case_overrides
    _handle_undefined = _core._handle_undefined_parameters_safe
    _is_new_type = _core._is_new_type
    _is_optional = _core._is_optional
    _is_supported_generic = _core._is_supported_generic
    _decode_generic = _core._decode_generic
    _support_extended_types = _core._support_extended_types
    _get_type_hints = _core.get_type_hints

    plans = {}

    def _plan(cls):
        plan = plans.get(cls)
        if plan is not None:
            return plan
        overrides = _user_overrides_or_exts(cls)
        flds = _dc_fields(cls)
        decode_names = _decode_letter_case_overrides(
            [f.name for f in flds], overrides)
        types = _get_type_hints(cls)
        init_plan = []   # (name, unwrapped_type, decoder, kind, raw_is_optional)
        defaults = []    # (name, kind, value)  kind: 0 none, 1 default, 2 factory
        for f in flds:
            name = f.name
            if f.default is not MISSING:
                defaults.append((name, 1, f.default))
            elif f.default_factory is not MISSING:
                defaults.append((name, 2, f.default_factory))
            else:
                defaults.append((name, 0, None))
            if not f.init:
                continue
            raw_type = types[name]
            field_type = raw_type
            while _is_new_type(field_type):
                field_type = field_type.__supertype__
            decoder = None
            override = overrides.get(name)
            if override is not None and override.decoder is not None:
                decoder = override.decoder
            if decoder is not None:
                kind = 0
            elif _dc_is_dataclass(field_type):
                kind = 1
            elif _is_supported_generic(field_type) and field_type != str:
                kind = 2
            else:
                kind = 3
            init_plan.append((name, field_type, decoder, kind,
                              _is_optional(raw_type)))
        plan = (decode_names, init_plan, defaults)
        plans[cls] = plan
        return plan

    def fast_decode(cls, kvs, infer_missing):
        if _isinstance_safe(kvs, cls):
            return kvs
        decode_names, init_plan, defaults = _plan(cls)
        if kvs is None and infer_missing:
            kvs = {}
        kvs = {decode_names.get(k, k): v for k, v in kvs.items()}
        for name, dk, dv in defaults:
            if name not in kvs:
                if dk == 1:
                    kvs[name] = dv
                elif dk == 2:
                    kvs[name] = dv()
                elif infer_missing:
                    kvs[name] = None
        kvs = _handle_undefined(cls, kvs, usage="from")

        init_kwargs = {}
        for name, field_type, decoder, kind, is_optional in init_plan:
            field_value = kvs[name]
            if field_value is None:
                if not is_optional:
                    warning = (f"value of non-optional type {name} detected "
                               f"when decoding {cls.__name__}")
                    if infer_missing:
                        _warnings.warn(
                            f"Missing {warning} and was defaulted to None by "
                            f"infer_missing=True. Set infer_missing=False "
                            f"(the default) to prevent this behavior.",
                            RuntimeWarning)
                    else:
                        _warnings.warn(f"'NoneType' object {warning}.",
                                       RuntimeWarning)
                init_kwargs[name] = field_value
                continue
            if kind == 0:
                init_kwargs[name] = (field_value
                                     if field_type is type(field_value)
                                     else decoder(field_value))
            elif kind == 1:
                init_kwargs[name] = (field_value
                                     if _dc_is_dataclass(field_value)
                                     else fast_decode(field_type, field_value,
                                                      infer_missing))
            elif kind == 2:
                init_kwargs[name] = _decode_generic(field_type, field_value,
                                                    infer_missing)
            else:
                init_kwargs[name] = _support_extended_types(field_type,
                                                            field_value)
        return cls(**init_kwargs)

    fast_decode._ppsr_fast_decode = True
    installed = ["core"]
    _core._decode_dataclass = fast_decode
    for mod_name in ("dataclasses_json.api", "dataclasses_json.mm"):
        try:
            mod = __import__(mod_name, fromlist=["_decode_dataclass"])
            current = getattr(mod, "_decode_dataclass", None)
            if current is not None and not getattr(current, "_ppsr_fast_decode", False):
                setattr(mod, "_decode_dataclass", fast_decode)
                installed.append(mod_name.rsplit(".", 1)[-1])
        except BaseException:
            pass
    _stage(f"dataclasses_json: fast decoder installed in {', '.join(installed)}")


def _module_signature(module_name):
    """A cheap fingerprint of a module's file, used to invalidate caches."""
    module = sys.modules.get(module_name)
    path = getattr(module, "__file__", None)
    if not path:
        return b"-"
    try:
        stat = os.stat(path)
        return f"{stat.st_size}:{int(stat.st_mtime)}".encode()
    except OSError:
        return b"-"


def _install_decode_cache(server_dir):
    """Cache decoded from_json() results on disk, keyed by content hash.

    The server re-reads and re-decodes the 720 KB freesr-data.json on every
    launch (~27 s on device). The file only changes when the payload is
    updated, so the decoded object graph can be pickled once and reloaded on
    the next launch. The key covers the class, the JSON bytes, the Python
    version and the defining module's size+mtime, so a payload update or a
    schema change is a miss, not a stale hit; any failure falls back to the
    normal decode. Disable with a `.no-decode-cache` file in the server dir.
    """
    if os.path.exists(os.path.join(server_dir, ".no-decode-cache")):
        _stage("decode cache: disabled by .no-decode-cache")
        return
    try:
        import dataclasses_json.api as _api
        import hashlib
        import pickle
    except BaseException as exc:
        _stage(f"decode cache skipped ({exc!r})")
        return

    cache_dir = os.path.join(server_dir, ".decode-cache")
    try:
        os.makedirs(cache_dir, exist_ok=True)
    except OSError as exc:
        _stage(f"decode cache disabled (cannot create {cache_dir}: {exc!r})")
        return

    mixin = _api.DataClassJsonMixin
    existing = getattr(mixin.from_json, "__func__", mixin.from_json)
    if getattr(existing, "_ppsr_cached", False):
        return
    original = mixin.from_json.__func__
    stats = {"hit": 0, "miss": 0, "write": 0}

    def cached_from_json(cls, s, *, parse_float=None, parse_int=None,
                         parse_constant=None, infer_missing=False, **kw):
        if (kw or parse_float is not None or parse_int is not None
                or parse_constant is not None):
            return original(cls, s, parse_float=parse_float,
                            parse_int=parse_int, parse_constant=parse_constant,
                            infer_missing=infer_missing, **kw)
        try:
            digest = hashlib.sha1()
            digest.update(b"ppsr-dj-v1\0")
            digest.update(sys.version.encode("utf-8", "replace"))
            digest.update(b"\0")
            digest.update(cls.__module__.encode("utf-8", "replace"))
            digest.update(b"\0")
            digest.update(cls.__qualname__.encode("utf-8", "replace"))
            digest.update(b"\0")
            digest.update(b"1" if infer_missing else b"0")
            digest.update(b"\0")
            digest.update(_module_signature(cls.__module__))
            digest.update(b"\0")
            digest.update(s.encode("utf-8") if isinstance(s, str) else bytes(s))
            key = digest.hexdigest()
        except BaseException:
            return original(cls, s, infer_missing=infer_missing)

        path = os.path.join(cache_dir, key + ".pkl")
        try:
            with open(path, "rb") as fh:
                result = pickle.load(fh)
            stats["hit"] += 1
            return result
        except FileNotFoundError:
            pass
        except BaseException:
            pass  # unreadable/incompatible cache entry: just recompute

        stats["miss"] += 1
        result = original(cls, s, infer_missing=infer_missing)
        try:
            tmp = f"{path}.{os.getpid()}.tmp"
            with open(tmp, "wb") as fh:
                pickle.dump(result, fh, protocol=4)
            os.replace(tmp, path)
            stats["write"] += 1
        except BaseException:
            pass
        return result

    cached_from_json._ppsr_cached = True
    mixin.from_json = classmethod(cached_from_json)
    _stage(f"decode cache enabled at {cache_dir}")

    def report():
        _stage(f"decode cache: {stats['hit']} hit, {stats['miss']} miss, "
               f"{stats['write']} written")

    return report


def _log_protobuf_backend():
    """Report which protobuf backend the runtime will actually use.

    The Android build tries to bundle a native (upb) protobuf wheel. When that
    wheel is missing or its .so fails to load, protobuf silently falls back to
    the pure-Python backend, which is ~10x slower and crashed on device while
    parsing the large msg/classes_pb2 descriptor. This makes the choice, and
    the exact reason for a fallback, visible in the live log.
    """
    try:
        import google.protobuf
        import google.protobuf.internal.api_implementation as _impl
        _stage(f"protobuf {google.protobuf.__version__} at {google.protobuf.__file__}")
        _stage(f"protobuf backend: {_impl.Type()}")
    except BaseException as exc:
        _stage(f"protobuf backend probe failed: {exc!r}")
    try:
        import google._upb._message  # noqa: F401
        _stage("protobuf native upb: loaded")
    except BaseException as exc:
        _stage(f"protobuf native upb: NOT loaded ({exc!r})")


def start(server_dir, invalid_password_message="The server password is incorrect or has expired"):
    global _module
    with _lock:
        if _module is not None:
            return "already_running"
        _configure_logging(server_dir)
        os.chdir(server_dir)
        if server_dir not in sys.path:
            sys.path.insert(0, server_dir)
        try:
            _stage("Loading ppSR server module")
            _install_import_timing()
            _prefer_extracted_modules(server_dir)
            _preinstall_google_namespace()
            _install_runtime_shims()
            _cache_dataclasses_json_hot_paths()
            _install_fast_decoder()
            # Must be before the server module is imported: @dataclass_json
            # copies from_json off DataClassJsonMixin at decoration time.
            _report_decode_cache = _install_decode_cache(server_dir)
            _log_protobuf_backend()
            started = _perf_counter()
            module = _load_server_module(server_dir)
            _stage(f"Imported ppSR server module in {(_perf_counter() - started) * 1000:.0f} ms")

            _freeze_gc_for_startup()
            _stage("Loading persistent configuration")
            module.Manager.init()
            if _report_decode_cache is not None:
                _report_decode_cache()
            _stage("Validating server password")
            check = getattr(module, "check_access", None)
            if check is not None:
                key = module.Manager.persistent.get("key", "")
                valid = check() if len(inspect.signature(check).parameters) == 0 else check(key)
                if not valid:
                    raise PermissionError(invalid_password_message)

            _stage("Loading game resources")
            module.ResourceManager.load_resources()
            _stage("Starting dispatch and game servers")
            module.start_servers()
            _module = module
            _disarm_faulthandler()
            _stage("ppSR server started at 127.0.0.1:21000")
            return "started"
        except BaseException:
            traceback.print_exc()
            raise


def stop():
    global _module
    with _lock:
        if _module is None:
            return "not_running"
        try:
            print("Stopping ppSR server")
            _module.stop_servers()
        finally:
            _module = None
        return "stopped"
