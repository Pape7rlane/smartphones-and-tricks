import importlib.machinery
import importlib.util
import inspect
import os
import sys
import threading
import traceback
from datetime import datetime

_module = None
_lock = threading.Lock()
_log_file = None
_MAX_LOG_FILE_BYTES = 2 * 1024 * 1024


class _TeeStream:
    def __init__(self, original, log_file, lock):
        self.original = original
        self.log_file = log_file
        self.lock = lock
        self.encoding = "utf-8"

    def write(self, value):
        if not value:
            return 0
        with self.lock:
            if self.original is not None:
                self.original.write(value)
                self.original.flush()
            if self.log_file.tell() >= _MAX_LOG_FILE_BYTES:
                self.log_file.seek(0)
                self.log_file.truncate(0)
                self.log_file.write("[log rotated at 2 MB]\n")
            self.log_file.write(value)
            self.log_file.flush()
        return len(value)

    def flush(self):
        with self.lock:
            if self.original is not None:
                self.original.flush()
            self.log_file.flush()

    def isatty(self):
        return False

    def __getattr__(self, name):
        if self.original is None:
            raise AttributeError(name)
        return getattr(self.original, name)


def _configure_logging(server_dir):
    global _log_file
    log_dir = os.path.join(server_dir, "logs")
    os.makedirs(log_dir, exist_ok=True)
    log_path = os.path.join(log_dir, "server.log")
    open(log_path, "w", encoding="utf-8").close()
    _log_file = open(log_path, "a", encoding="utf-8", buffering=1)
    stream_lock = threading.RLock()
    sys.stdout = _TeeStream(sys.stdout, _log_file, stream_lock)
    sys.stderr = _TeeStream(sys.stderr, _log_file, stream_lock)
    print(f"[{datetime.now().astimezone().isoformat(timespec='seconds')}] "
          "ppSR 4.6.51 live log started")


def start(server_dir):
    global _module
    with _lock:
        if _module is not None:
            return "already_running"
        _configure_logging(server_dir)
        os.chdir(server_dir)
        if server_dir not in sys.path:
            sys.path.insert(0, server_dir)
        try:
            path = os.path.join(server_dir, "ppsr.pyc")
            loader = importlib.machinery.SourcelessFileLoader("ppsr_embedded", path)
            spec = importlib.util.spec_from_loader(loader.name, loader)
            module = importlib.util.module_from_spec(spec)
            loader.exec_module(module)

            module.Manager.init()
            check = getattr(module, "check_access", None)
            if check is not None:
                key = module.Manager.persistent.get("key", "")
                valid = check() if len(inspect.signature(check).parameters) == 0 else check(key)
                if not valid:
                    raise PermissionError("服务器密码错误或已过期，请输入服主发布的新密码")

            module.ResourceManager.load_resources()
            module.start_servers()
            _module = module
            print("ppSR server started at 127.0.0.1:21000")
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
