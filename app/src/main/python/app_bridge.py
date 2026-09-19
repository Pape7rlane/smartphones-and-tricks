import importlib.machinery
import importlib.util
import inspect
import os
import sys
import threading

_module = None
_lock = threading.Lock()


def start(server_dir):
    global _module
    with _lock:
        if _module is not None:
            return "already_running"
        os.chdir(server_dir)
        if server_dir not in sys.path:
            sys.path.insert(0, server_dir)
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
        return "started"


def stop():
    global _module
    with _lock:
        if _module is None:
            return "not_running"
        try:
            _module.stop_servers()
        finally:
            _module = None
        return "stopped"
