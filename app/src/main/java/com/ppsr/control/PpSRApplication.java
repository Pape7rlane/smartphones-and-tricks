package com.ppsr.control;

import android.app.Application;

/**
 * Deliberately does NOT start Python here, unlike Chaquopy's
 * {@code com.chaquo.python.android.PyApplication}.
 *
 * Python is only needed by the server, which runs in the {@code :server} process
 * and starts the interpreter on demand from {@link PythonServerService}. Starting
 * it from {@code Application.onCreate} would initialize CPython in <em>every</em>
 * process — including the UI process, which never touches Python — so opening the
 * app paid for a full interpreter start-up (loading {@code libpython3.14.so} and,
 * on first run, extracting the Chaquopy assets) for nothing.
 *
 * Keeping it lazy means the UI process opens without any Python work, and the
 * interpreter is initialized exactly once, in the server process, when the server
 * is actually started. See {@code PythonServerService.startServer()}.
 */
public class PpSRApplication extends Application {
}
