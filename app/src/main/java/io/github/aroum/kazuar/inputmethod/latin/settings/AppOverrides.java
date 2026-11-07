// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.content.SharedPreferences;

/** Explicit package-scoped user policy. Legacy incognito exceptions are not overrides. */
public final class AppOverrides {
    public static final String ENABLED = "enabled", LEARNING = "learning", SUGGESTIONS = "suggestions",
            CORRECTION = "correction", PERSONALIZED = "personalized", PREDICTIONS = "predictions", CAPS = "caps";
    private AppOverrides() {}
    public static boolean validPackage(String name) {
        return name != null && name.length() <= 255 && name.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    }
    public static String key(String name, String option) { return "app_override_" + name + ":" + option; }
    public static boolean enabled(SharedPreferences prefs, String name) {
        return validPackage(name) && prefs.getBoolean(key(name, ENABLED), false);
    }
    public static boolean value(SharedPreferences prefs, String name, String option, boolean fallback) {
        return enabled(prefs, name) ? prefs.getBoolean(key(name, option), fallback) : fallback;
    }
}
