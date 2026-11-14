package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;
    /** tvlauncher fork: weather and NBG rates rows (InfoWidgets). */
    private ForkConfig fork;
    private TextView weatherCityChoice, weatherRangeChoice, ratesChoice, newsChoice, newsTopicChoice;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        addInfoRows();
        applyForkLayout();
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        if (fork != null && !fork.wallpaperGroups.isEmpty()) {
            showWallpaperGroupPicker();
            return;
        }
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        String groupLabel = groupLabel(wallpaperCurrent);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : groupLabel != null ? groupLabel
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
        refreshInfoRows();
    }

    // ---- tvlauncher fork: settings page for home_dock_only ----------------------------------------------

    private TextView columnsLabel, dockLabel;

    /**
     * home_dock_only: Home is one dock row, so "Rows below the dock", "Default home" and "TV settings" go; apps per
     * row and the dock's transparency become sliders (D-pad Left/Right) instead of fixed choices.
     */
    private void applyForkLayout() {
        if (fork == null) fork = ForkConfig.load(this);
        if (!fork.homeDockOnly) return;
        hideRowWithLabel(rowChoices[0]);
        View home = findViewById(R.id.defaultHomeChoice);
        if (home != null && home.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup list = (android.view.ViewGroup) home.getParent();
            int i = list.indexOfChild(home);
            home.setVisibility(View.GONE);
            if (i + 1 < list.getChildCount()) list.getChildAt(i + 1).setVisibility(View.GONE);   // its help text
        }
        View tv = findViewById(R.id.tvSettingsChoice);
        if (tv != null) tv.setVisibility(View.GONE);
        columnsLabel = hideRowKeepLabel(columnChoices[0]);
        addSlider(columnChoices[0], 8 - 4, 1, columns - 4, value -> {
            columns = value + 4;
            choices.edit().putInt("columns", columns).apply();
            changed();
            sliderLabels();
        });
        dockLabel = hideRowKeepLabel(dockChoices[0]);
        addSlider(dockChoices[0], 70, 5, 100 - dockOpacity, value -> {
            dockOpacity = 100 - value;
            choices.edit().putInt(Config.DOCK_OPACITY_KEY, dockOpacity).apply();
            changed();
            sliderLabels();
        });
        sliderLabels();
    }

    private void sliderLabels() {
        if (columnsLabel != null) columnsLabel.setText(getString(R.string.columns_label) + ": " + columns);
        if (dockLabel != null) dockLabel.setText(getString(R.string.fork_dock_transparency) + ": " + (100 - dockOpacity) + "%");
    }

    /** Hides a row of choices and the label above it. */
    private void hideRowWithLabel(View choice) {
        TextView label = hideRowKeepLabel(choice);
        if (label != null) label.setVisibility(View.GONE);
    }

    /** Hides a row of choices; returns the label above it. */
    private TextView hideRowKeepLabel(View choice) {
        View row = (View) choice.getParent();
        row.setVisibility(View.GONE);
        android.view.ViewGroup list = (android.view.ViewGroup) row.getParent();
        int i = list.indexOfChild(row);
        View label = i > 0 ? list.getChildAt(i - 1) : null;
        return label instanceof TextView ? (TextView) label : null;
    }

    private TextView newsSizeLabel, newsSpeedLabel, newsBgLabel;

    private void newsLabels() {
        android.content.SharedPreferences np = InfoWidgets.prefs(this);
        if (newsSizeLabel != null) newsSizeLabel.setText(getString(R.string.fork_news_size) + ": "
                + np.getInt(InfoWidgets.KEY_NEWS_SIZE, InfoWidgets.NEWS_SIZE_DEFAULT));
        if (newsSpeedLabel != null) newsSpeedLabel.setText(getString(R.string.fork_news_speed) + ": "
                + np.getInt(InfoWidgets.KEY_NEWS_SPEED, InfoWidgets.NEWS_SPEED_DEFAULT));
        if (newsBgLabel != null) newsBgLabel.setText(getString(R.string.fork_news_bg) + ": "
                + np.getInt(InfoWidgets.KEY_NEWS_BG, InfoWidgets.NEWS_BG_DEFAULT) + "%");
    }

    private TextView sliderLabel(android.view.ViewGroup list, int index) {
        TextView t = new TextView(this);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (18 * getResources().getDisplayMetrics().density);
        list.addView(t, index, lp);
        return t;
    }

    /** A remote-friendly slider in place of the hidden row that holds {@code choice}. */
    private void addSlider(View choice, int max, int step, int value, java.util.function.IntConsumer onChange) {
        View row = (View) choice.getParent();
        android.view.ViewGroup list = (android.view.ViewGroup) row.getParent();
        sliderAt(list, list.indexOfChild(row) + 1, max, step, value, onChange);
    }

    /** A remote-friendly slider (D-pad Left/Right) inserted at {@code index}. */
    private void sliderAt(android.view.ViewGroup list, int index, int max, int step, int value,
                          java.util.function.IntConsumer onChange) {
        android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(max);
        bar.setKeyProgressIncrement(step);
        bar.setProgress(Math.max(0, Math.min(max, value)));
        bar.setFocusable(true);
        bar.setBackgroundResource(R.drawable.settings_choice_bg);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        bar.setPadding(pad, pad / 2, pad, pad / 2);
        android.content.res.ColorStateList white = android.content.res.ColorStateList.valueOf(0xFFFFFFFF);
        bar.setProgressTintList(white);
        bar.setThumbTintList(white);
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int progress, boolean fromUser) {
                if (fromUser) onChange.accept(progress);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) { }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (52 * getResources().getDisplayMetrics().density));
        lp.topMargin = (int) (10 * getResources().getDisplayMetrics().density);
        list.addView(bar, index, lp);
    }

    // ---- tvlauncher fork: wallpaper groups ------------------------------------------------------------

    /** The label of a "group:<id>" choice, or null for any other choice. */
    private String groupLabel(String choice) {
        if (choice == null || !choice.startsWith(ForkConfig.GROUP_PREFIX)) return null;
        String id = choice.substring(ForkConfig.GROUP_PREFIX.length());
        if (ForkConfig.GROUP_ALL.equals(id)) return getString(R.string.fork_wallpaper_random);
        if (fork != null) for (String[] g : fork.wallpaperGroups) if (g[0].equals(id)) return g[1];
        return id;
    }

    /** Random (every group), each group, None; the neutral button goes back to the theme's own setting. */
    private void showWallpaperGroupPicker() {
        List<String[]> groups = fork.wallpaperGroups;
        String[] labels = new String[groups.size() + 2];
        labels[0] = getString(R.string.fork_wallpaper_random);
        for (int i = 0; i < groups.size(); i++) labels[i + 1] = groups.get(i)[1];
        labels[labels.length - 1] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = -1;
        if ((ForkConfig.GROUP_PREFIX + ForkConfig.GROUP_ALL).equals(current)) checked = 0;
        else if ("none".equals(current)) checked = labels.length - 1;
        else for (int i = 0; i < groups.size(); i++) {
            if ((ForkConfig.GROUP_PREFIX + groups.get(i)[0]).equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String value = which == 0 ? ForkConfig.GROUP_PREFIX + ForkConfig.GROUP_ALL
                            : which == labels.length - 1 ? "none"
                            : ForkConfig.GROUP_PREFIX + groups.get(which - 1)[0];
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, value).apply();
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- tvlauncher fork: weather and NBG rates --------------------------------------------------------

    private TextView settingsRow(android.view.ViewGroup parent, int index, int topMarginDp) {
        TextView row = new TextView(this, null, 0, R.style.SettingsRow);
        android.view.ViewGroup.MarginLayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (52 * getResources().getDisplayMetrics().density));
        lp.topMargin = (int) (topMarginDp * getResources().getDisplayMetrics().density);
        parent.addView(row, index, lp);
        return row;
    }

    /** Three rows after the wallpaper row: the weather's city, the forecast length, the NBG rates. */
    private void addInfoRows() {
        fork = ForkConfig.load(this);
        if (!(wallpaperChoice.getParent() instanceof LinearLayout)) return;
        LinearLayout list = (LinearLayout) wallpaperChoice.getParent();
        int at = list.indexOfChild(wallpaperChoice) + 1;
        weatherCityChoice = settingsRow(list, at, 28);
        weatherRangeChoice = settingsRow(list, at + 1, 10);
        ratesChoice = settingsRow(list, at + 2, 10);
        newsChoice = settingsRow(list, at + 3, 10);
        newsTopicChoice = settingsRow(list, at + 4, 10);
        newsTopicChoice.setOnClickListener(v -> showNewsTopicPicker());
        android.content.SharedPreferences np = InfoWidgets.prefs(this);
        newsSizeLabel = sliderLabel(list, at + 5);
        sliderAt(list, at + 6, 40 - 16, 2, np.getInt(InfoWidgets.KEY_NEWS_SIZE, InfoWidgets.NEWS_SIZE_DEFAULT) - 16, v -> {
            InfoWidgets.prefs(this).edit().putInt(InfoWidgets.KEY_NEWS_SIZE, v + 16).apply();
            changed();
            newsLabels();
        });
        newsSpeedLabel = sliderLabel(list, at + 7);
        sliderAt(list, at + 8, 200 - 20, 10, np.getInt(InfoWidgets.KEY_NEWS_SPEED, InfoWidgets.NEWS_SPEED_DEFAULT) - 20, v -> {
            InfoWidgets.prefs(this).edit().putInt(InfoWidgets.KEY_NEWS_SPEED, v + 20).apply();
            changed();
            newsLabels();
        });
        newsBgLabel = sliderLabel(list, at + 9);
        sliderAt(list, at + 10, 100, 5, np.getInt(InfoWidgets.KEY_NEWS_BG, InfoWidgets.NEWS_BG_DEFAULT), v -> {
            InfoWidgets.prefs(this).edit().putInt(InfoWidgets.KEY_NEWS_BG, v).apply();
            changed();
            newsLabels();
        });
        newsLabels();
        newsChoice.setOnClickListener(v -> {
            boolean on = !InfoWidgets.newsOn(this, fork);
            InfoWidgets.prefs(this).edit().putBoolean(InfoWidgets.KEY_NEWS_ON, on).apply();
            changed();
            refresh();
        });
        TextView credit = new TextView(this);
        credit.setText(R.string.fork_info_credit);
        credit.setTextColor(0x80FFFFFF);
        credit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
        cp.setMarginStart((int) (18 * getResources().getDisplayMetrics().density));
        list.addView(credit, at + 11, cp);
        addKeyRows(list, at + 12);
        weatherCityChoice.setOnClickListener(v -> showCitySearch());
        weatherRangeChoice.setOnClickListener(v -> showRangePicker());
        ratesChoice.setOnClickListener(v -> {
            boolean on = !InfoWidgets.ratesOn(this, fork);
            InfoWidgets.prefs(this).edit().putBoolean(InfoWidgets.KEY_RATES_ON, on).apply();
            changed();
            refresh();
        });
    }

    private void refreshInfoRows() {
        if (weatherCityChoice == null) return;
        android.content.SharedPreferences p = InfoWidgets.prefs(this);
        String city = InfoWidgets.weatherOn(this, fork) ? p.getString(InfoWidgets.KEY_CITY, "") : "";
        weatherCityChoice.setText(getString(R.string.fork_weather_state,
                city.isEmpty() ? getString(R.string.off) : city));
        String[] labels = getResources().getStringArray(R.array.fork_weather_ranges);
        String range = InfoWidgets.range(this);
        int i = java.util.Arrays.asList(InfoWidgets.RANGES).indexOf(range);
        weatherRangeChoice.setText(getString(R.string.fork_weather_range_state, labels[Math.max(0, i)]));
        weatherRangeChoice.setEnabled(!city.isEmpty());
        weatherRangeChoice.setAlpha(city.isEmpty() ? 0.5f : 1f);
        ratesChoice.setText(getString(R.string.fork_rates_state,
                getString(InfoWidgets.ratesOn(this, fork) ? R.string.on : R.string.off)));
        newsChoice.setText(getString(R.string.fork_news_state,
                getString(InfoWidgets.newsOn(this, fork) ? R.string.on : R.string.off)));
        String[] topics = getResources().getStringArray(R.array.fork_news_topics);
        int ti = java.util.Arrays.asList(InfoWidgets.NEWS_TOPICS).indexOf(InfoWidgets.newsTopic(this));
        newsTopicChoice.setText(getString(R.string.fork_news_topic_state, topics[Math.max(0, ti)]));
    }

    /** Type a city, pick it from Open-Meteo's matches; or switch the weather off. */
    private void showCitySearch() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.fork_weather_city_hint);
        input.setText(InfoWidgets.prefs(this).getString(InfoWidgets.KEY_CITY, ""));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.fork_weather_city_title)
                .setView(input)
                .setPositiveButton(R.string.fork_weather_search, (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (q.isEmpty()) return;
                    String lang = getResources().getConfiguration().getLocales().get(0).getLanguage();
                    InfoWidgets.searchCity(q, lang, places -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (places.isEmpty()) {
                            Toast.makeText(this, R.string.fork_weather_not_found, Toast.LENGTH_LONG).show();
                            return;
                        }
                        String[] names = new String[places.size()];
                        for (int k = 0; k < names.length; k++) names[k] = places.get(k).label;
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.fork_weather_city_title)
                                .setItems(names, (d2, which) -> {
                                    InfoWidgets.Place pl = places.get(which);
                                    InfoWidgets.prefs(this).edit()
                                            .putString(InfoWidgets.KEY_CITY, pl.name)
                                            .putFloat(InfoWidgets.KEY_LAT, (float) pl.lat)
                                            .putFloat(InfoWidgets.KEY_LON, (float) pl.lon)
                                            .putBoolean(InfoWidgets.KEY_WEATHER_ON, true).apply();
                                    InfoWidgets.invalidateWeather(this);
                                    changed();
                                    refresh();
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    });
                })
                .setNeutralButton(R.string.off, (d, w) -> {
                    InfoWidgets.prefs(this).edit().putBoolean(InfoWidgets.KEY_WEATHER_ON, false).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- tvlauncher fork: what the remote's app buttons open -----------------------------------------

    private final java.util.List<TextView> keyRows = new java.util.ArrayList<>();

    private void addKeyRows(android.view.ViewGroup list, int index) {
        TextView title = sliderLabel(list, index++);
        title.setText(R.string.fork_keys_title);
        keyRows.clear();
        for (int[] button : ForkKeys.BUTTONS) {
            if (button[0] <= 0) continue;
            TextView row = settingsRow(list, index++, 10);
            final int code = button[0], label = button[1];
            row.setOnClickListener(v -> showKeyPicker(code, label));
            row.setTag(button);
            keyRows.add(row);
        }
        refreshKeyRows();
    }

    private void refreshKeyRows() {
        for (TextView row : keyRows) {
            int[] button = (int[]) row.getTag();
            row.setText(getString(button[1]) + ": " + targetLabel(ForkKeys.target(this, button[0])));
        }
    }

    /** Fixed choices first (value null = the theme's or firmware's own behaviour), then every TV app by name. */
    private java.util.List<String[]> keyChoices() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        out.add(new String[]{getString(R.string.fork_key_default), null});
        out.add(new String[]{getString(R.string.fork_key_nothing), "ignore"});
        out.add(new String[]{getString(R.string.inputs), RemoteKeys.INPUTS_OVERLAY});
        String tuner = ForkKeys.tunerTarget(this);
        if (tuner != null) out.add(new String[]{getString(R.string.fork_key_live_tv_action), tuner});
        out.add(new String[]{getString(R.string.fork_qs_settings_panel), "com.gathin.tvhome/.QuickSettingsActivity"});
        PackageManager pm = getPackageManager();
        java.util.List<String[]> apps = new java.util.ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        out.addAll(apps);
        return out;
    }

    private String targetLabel(String target) {
        for (String[] c : keyChoices()) {
            if (target == null ? c[1] == null : target.equals(c[1])) return c[0];
        }
        return target;
    }

    private void showKeyPicker(int code, int labelRes) {
        java.util.List<String[]> choices = keyChoices();
        String current = ForkKeys.target(this, code);
        String[] labels = new String[choices.size()];
        int checked = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = choices.get(i)[0];
            String v = choices.get(i)[1];
            if (current == null ? v == null : current.equals(v)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(labelRes)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    ForkKeys.set(this, code, choices.get(which)[1]);
                    refreshKeyRows();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showNewsTopicPicker() {
        String[] labels = getResources().getStringArray(R.array.fork_news_topics);
        int checked = java.util.Arrays.asList(InfoWidgets.NEWS_TOPICS).indexOf(InfoWidgets.newsTopic(this));
        new AlertDialog.Builder(this)
                .setTitle(R.string.fork_news_topic_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    InfoWidgets.prefs(this).edit().putString(InfoWidgets.KEY_NEWS_TOPIC, InfoWidgets.NEWS_TOPICS[which]).apply();
                    InfoWidgets.invalidateNews(this);
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRangePicker() {
        String[] labels = getResources().getStringArray(R.array.fork_weather_ranges);
        int checked = java.util.Arrays.asList(InfoWidgets.RANGES).indexOf(InfoWidgets.range(this));
        new AlertDialog.Builder(this)
                .setTitle(R.string.fork_weather_range_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    InfoWidgets.prefs(this).edit().putString(InfoWidgets.KEY_RANGE, InfoWidgets.RANGES[which]).apply();
                    InfoWidgets.invalidateWeather(this);
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
