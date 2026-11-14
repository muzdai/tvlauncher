package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.json.JSONObject;

/**
 * Keys of the tvlauncher fork, read from the theme's config.json only. Every key is off by default, so
 * a theme that does not set them gets the stock CleanYourTV behaviour.
 *
 * <ul>
 *   <li>{@code menu_opacity} (30-100, default 100): opacity of the glass menus (Inputs dropdown, quick settings
 *       panel, tile menu, App library, Inputs overlay), so the wallpaper or the app behind shows through.</li>
 *   <li>{@code home_dock_only} (default false): Home shows only {@code dock_apps} and the apps the owner added
 *       from the App library; every other app is reachable from the library only.</li>
 *   <li>{@code library_button_left} (default false): the App library button goes first in the top bar,
 *       left of Inputs, instead of next to Settings.</li>
 *   <li>{@code show_weather}, {@code show_nbg_rates} (default false): the weather under the clock and the
 *       National Bank of Georgia's USD/EUR rates next to the greeting (InfoWidgets); Launcher settings can
 *       switch each on or off and picks the city and the forecast length.</li>
 *   <li>{@code wallpaper_groups} (["id:Label", ...]): Launcher settings offers groups instead of single pictures;
 *       a group rotates only its own pictures (wallpapers/id-*.jpg), "Random" rotates all of them.</li>
 * </ul>
 */
final class ForkConfig {
    /** Where Home's Inputs pill was last laid out, on screen (InfoWidgets prefs); InputsOverlayActivity hangs under it. */
    static final String INPUTS_LEFT = "inputs_pill_left", INPUTS_BOTTOM = "inputs_pill_bottom", INPUTS_WIDTH = "inputs_pill_width";
    final int menuOpacity;
    final boolean homeDockOnly;
    final boolean libraryButtonLeft;
    final boolean showWeather;
    final boolean showNbgRates;
    /** wallpaper_groups: ["id:Label", ...]; pictures of a group are named "<id>-..." in wallpapers/. */
    final java.util.List<String[]> wallpaperGroups;
    /** tile_menu_close: the tile menu reads Move, Close, Remove, App info (no Replace with another app). */
    final boolean tileMenuClose;
    /** show_news: a ticker of the day's top 10 world headlines under the dock (InfoWidgets). */
    final boolean showNews;

    private ForkConfig(int menuOpacity, boolean homeDockOnly, boolean libraryButtonLeft,
                       boolean showWeather, boolean showNbgRates, java.util.List<String[]> wallpaperGroups,
                       boolean tileMenuClose, boolean showNews) {
        this.menuOpacity = menuOpacity;
        this.homeDockOnly = homeDockOnly;
        this.libraryButtonLeft = libraryButtonLeft;
        this.showWeather = showWeather;
        this.showNbgRates = showNbgRates;
        this.wallpaperGroups = java.util.Collections.unmodifiableList(wallpaperGroups);
        this.tileMenuClose = tileMenuClose;
        this.showNews = showNews;
    }

    static final String GROUP_PREFIX = "group:";
    static final String GROUP_ALL = "*";

    /** The pictures for an on-TV group choice, or null when the choice is not a group (or the group is empty). */
    static java.util.List<java.io.File> groupChoice(java.util.List<java.io.File> collection, String choice) {
        if (choice == null || !choice.startsWith(GROUP_PREFIX)) return null;
        String id = choice.substring(GROUP_PREFIX.length());
        java.util.List<java.io.File> out = new java.util.ArrayList<>();
        for (java.io.File f : collection) {
            if (GROUP_ALL.equals(id) || f.getName().startsWith(id + "-")) out.add(f);
        }
        return out.isEmpty() ? null : out;
    }

    static ForkConfig load(Context ctx) {
        JSONObject json = null;
        try {
            json = Config.overlayJson(ctx);
        } catch (RuntimeException ignored) {
            // no overlay: built-in defaults
        }
        android.content.res.Resources r = ctx.getResources();
        boolean dockOnly = r.getBoolean(R.bool.fork_home_dock_only), libLeft = r.getBoolean(R.bool.fork_library_button_left);
        boolean close = r.getBoolean(R.bool.fork_tile_menu_close), weather = r.getBoolean(R.bool.fork_show_weather);
        boolean rates = r.getBoolean(R.bool.fork_show_nbg_rates), news = r.getBoolean(R.bool.fork_show_news);
        int opacity = r.getInteger(R.integer.fork_menu_opacity);
        java.util.List<String[]> groups = new java.util.ArrayList<>();
        if (json != null) {
            opacity = json.optInt("menu_opacity", opacity);
            dockOnly = json.optBoolean("home_dock_only", dockOnly);
            libLeft = json.optBoolean("library_button_left", libLeft);
            weather = json.optBoolean("show_weather", weather);
            rates = json.optBoolean("show_nbg_rates", rates);
            close = json.optBoolean("tile_menu_close", close);
            news = json.optBoolean("show_news", news);
            groups = groups(json);
        }
        opacity = Math.max(30, Math.min(100, opacity));
        return new ForkConfig(opacity, dockOnly, libLeft, weather, rates, groups, close, news);
    }

    private static java.util.List<String[]> groups(JSONObject json) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        org.json.JSONArray a = json.optJSONArray("wallpaper_groups");
        for (int i = 0; a != null && i < a.length(); i++) {
            String e = a.optString(i, "");
            int c = e.indexOf(':');
            if (c <= 0 || c == e.length() - 1) continue;
            String id = e.substring(0, c).trim();
            if (!id.matches("[a-z0-9_]+")) continue;
            out.add(new String[]{id, e.substring(c + 1).trim()});
        }
        return out;
    }

    /** Applies menu_opacity to a glass panel's background; 100 leaves it untouched. */
    static void applyOpacity(View panel, int percent) {
        if (panel == null || percent >= 100) return;
        Drawable bg = panel.getBackground();
        if (bg == null) return;
        bg = bg.mutate();
        bg.setAlpha(Math.round(255f * percent / 100f));
        panel.setBackground(bg);
    }
}
