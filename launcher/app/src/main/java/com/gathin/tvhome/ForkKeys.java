package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * tvlauncher fork: the owner's choice of what a remote app button opens, set in Launcher settings and read by the key
 * helpers on every press. A choice wins over the theme's app_keys; no choice leaves the button as the theme or the
 * firmware has it. Values are an app package, an action word RemoteKeys understands ("settings", "inputs_overlay",
 * "ignore"), or a "package/.Activity".
 */
final class ForkKeys {
    private ForkKeys() { }

    static final String PREFIX = "remote_key_";
    /** The buttons offered in settings: [keycode, label resource]. Filled in from the remote's measured keycodes. */
    static final int[][] BUTTONS = {
            {4062, R.string.fork_key_netflix},
            {4099, R.string.fork_key_prime},
            {190, R.string.fork_key_youtube},
            {4020, R.string.fork_key_tcl_channel},
            {4001, R.string.fork_key_live_tv},
    };

    /** "input:<id>" for the TV's hardware tuner (Live TV), or null when the TV has none. */
    static String tunerTarget(Context ctx) {
        android.media.tv.TvInputManager tim =
                (android.media.tv.TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return null;
        String best = null;
        for (android.media.tv.TvInputInfo info : tim.getTvInputList()) {
            if (info.getType() != android.media.tv.TvInputInfo.TYPE_TUNER) continue;
            if ((info.getServiceInfo().applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) continue;
            String id = info.getId();
            if (id.toLowerCase(java.util.Locale.ROOT).contains("analog")) continue;
            if (best == null || (id.matches(".*/HW\\d+$") && !best.matches(".*/HW\\d+$"))) best = id;
        }
        return best == null ? null : "input:" + best;
    }

    static String target(Context ctx, int keyCode) {
        if (keyCode <= 0) return null;
        SharedPreferences p = InfoWidgets.prefs(ctx);
        String v = p.getString(PREFIX + keyCode, null);
        return v == null || v.isEmpty() ? null : v;
    }

    static void set(Context ctx, int keyCode, String target) {
        SharedPreferences.Editor e = InfoWidgets.prefs(ctx).edit();
        if (target == null) e.remove(PREFIX + keyCode);
        else e.putString(PREFIX + keyCode, target);
        e.apply();
    }
}
