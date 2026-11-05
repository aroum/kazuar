// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.keyboard.internal;

import java.util.Locale;

/** Exact aliases supported by imported layouts; an arbitrary name never becomes a command. */
enum CustomKeyStyle {
    DELETE("delete", "delete", false), SPACE("space", "space", false),
    SHIFT("shift", "shift", false), ENTER("enter", "enter", true),
    SYMBOLS("switch_alpha_symbol", null, false);

    final String code;
    final String icon;
    final boolean action;
    CustomKeyStyle(String code, String icon, boolean action) {
        this.code = code;
        this.icon = icon;
        this.action = action;
    }

    static CustomKeyStyle resolve(String name) {
        if (name == null) return null;
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "delete": case "backspace": case "deletekeystyle": return DELETE;
            case "space": case "spacebar": case "spacekeystyle": return SPACE;
            case "shift": case "shiftkeystyle": return SHIFT;
            case "enter": case "return": case "action":
            case "enterkeystyle": case "returnkeystyle": case "actionkeystyle": return ENTER;
            case "toalpha": case "toalphakeystyle":
            case "tomoresymbol": case "tomoresymbolkeystyle":
            case "tosymbol": case "tosymbolkeystyle":
            case "backfrommoresymbolkeystyle": return SYMBOLS;
            default: return null;
        }
    }
}
