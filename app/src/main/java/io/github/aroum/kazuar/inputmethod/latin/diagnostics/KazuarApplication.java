// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.diagnostics;

import android.app.Application;

public final class KazuarApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
    @Override public void onTerminate() {
        CrashReporter.uninstall();
        super.onTerminate();
    }
}
