package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * tvlauncher fork: the Inputs list as a translucent glass panel over whatever is on screen, for the remote's
 * Input button ({@code app_keys} action {@code inputs_overlay}, InputKeyService or HomeKeyService). Unlike the
 * stock {@code inputs} action it does not bring Home to the front: the app or HDMI picture keeps running
 * behind, Back closes the panel, and pressing Input again closes it too.
 *
 * It lists what the Home dropdown lists on a TV with a TvInputManager: HDMI ports (a CEC device takes its
 * port's row, names from Settings > Inputs first), the other passthrough inputs and Live TV. The vendor
 * routes of the Home dropdown (Coocaa sources, a source panel, input_action, input_uri_scheme) are not
 * handled here; on such sets keep the stock {@code inputs} action.
 */
public final class InputsOverlayActivity extends Activity {
    private String liveTvPkg = "";

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        View panel = findViewById(R.id.panel);
        ForkConfig.applyOpacity(panel, ForkConfig.load(this).menuOpacity);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        Config cfg = Config.load(this);
        liveTvPkg = cfg.liveTvPackage == null ? "" : cfg.liveTvPackage;
        LinearLayout rows = findViewById(R.id.rows);
        fill(rows, cfg);
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        placeLikeHome(panel);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            if (child.isFocusable()) { child.requestFocus(); break; }
        }
    }

    /**
     * The Home dropdown's geometry (MainActivity.showInputs): as wide as the Inputs pill but at least 150dp, grown to
     * the widest row up to 360dp, its left edge on the pill's (pulled in if it would leave the screen), 10dp under it.
     * Then the same drop-in: fade and unfold from the top edge.
     */
    private void placeLikeHome(View panel) {
        float d = getResources().getDisplayMetrics().density;
        android.content.SharedPreferences p = InfoWidgets.prefs(this);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int pillW = p.getInt(ForkConfig.INPUTS_WIDTH, (int) (82 * d));
        int left = p.getInt(ForkConfig.INPUTS_LEFT, screenW - (int) (56 * d) - (int) (150 * d));
        int top = p.getInt(ForkConfig.INPUTS_BOTTOM, (int) (95 * d)) + (int) (10 * d);
        int width = Math.max(pillW, (int) (150 * d));
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        width = Math.max(width, Math.min(panel.getMeasuredWidth(), (int) (360 * d)));
        left = Math.max(0, Math.min(left, screenW - width));
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
        lp.width = width;
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
        lp.setMarginStart(0);
        lp.setMarginEnd(0);
        lp.setMargins(left, top, 0, 0);
        panel.setLayoutParams(lp);
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * d);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    /** Input pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    private void fill(LinearLayout rows, Config cfg) {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        String tunerId = null;
        boolean tunerIsHw = false;
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            Set<String> ids = new HashSet<>();
            for (TvInputInfo info : inputs) ids.add(info.getId());
            Map<String, List<TvInputInfo>> cec = new HashMap<>();
            for (TvInputInfo info : inputs) {
                String parent = info.getParentId();
                if (parent == null || !ids.contains(parent)) continue;
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                List<TvInputInfo> onPort = cec.get(parent);
                if (onPort == null) cec.put(parent, onPort = new ArrayList<>());
                onPort.add(info);
            }
            int ports = 0, connected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                if (info.getParentId() != null && ids.contains(info.getParentId())) continue;
                ports++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) connected++;
            }
            boolean bogus = cfg.inputsCecStateOnly || (ports > 1 && connected == ports);
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    boolean analog = info.getId().toLowerCase(Locale.ROOT).contains("analog");
                    if (!analog && (tunerId == null || (hw && !tunerIsHw))) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                    }
                }
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                if (Integer.valueOf(-1).equals(InputSources.mapped(cfg.inputSources, info.getId()))) continue;
                if (info.getParentId() != null && ids.contains(info.getParentId())) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                CharSequence custom = InputNames.tidy(info.loadCustomLabel(this));
                boolean named = custom != null && custom.length() > 0 && !custom.toString().equals(label);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                List<TvInputInfo> devices = cec.get(info.getId());
                if (devices != null) {
                    for (TvInputInfo device : devices) {
                        CharSequence name = InputNames.tidy(device.loadCustomLabel(this));
                        if (name == null || name.length() == 0) name = named ? custom : device.loadLabel(this);
                        String deviceLabel = name == null || name.length() == 0 ? label : name + " · " + label;
                        final String deviceId = device.getId();
                        int st = bogus && !cfg.inputsCecStateOnly ? -1 : tim.getInputState(deviceId);
                        addRow(rows, R.drawable.ic_input, deviceLabel, st, () -> openInput(deviceId));
                    }
                    continue;
                }
                if (named) label = custom + " · " + label;
                final String id = info.getId();
                int st = !hdmi || bogus ? -1 : tim.getInputState(id);
                addRow(rows, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, st, () -> openInput(id));
            }
        }
        Intent liveTv = liveTvIntent(cfg, tunerId);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent target = liveTv;
            addRow(rows, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(target));
        }
    }

    /** The Live TV app tuned to the hardware tuner when it handles that URI, else its leanback entry. */
    private Intent liveTvIntent(Config cfg, String tunerId) {
        if (liveTvPkg.isEmpty()) return null;
        if (tunerId != null) {
            Intent tuned = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId),
                            TvContract.Channels.CONTENT_ITEM_TYPE)
                    .setPackage(liveTvPkg);
            if (tuned.resolveActivity(getPackageManager()) != null) return tuned;
        }
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || cfg.liveTvAction == null || cfg.liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(cfg.liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** ACTION_VIEW on the input's channel URI, aimed at the Live TV app first (as MainActivity.openInput). */
    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!liveTvPkg.isEmpty()) {
            try {
                startActivity(new Intent(view).setPackage(liveTvPkg));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        open(view);
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void addSeparator(LinearLayout rows) {
        float density = getResources().getDisplayMetrics().density;
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, (int) density));
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addRow(LinearLayout rows, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            finish();
            action.run();
        });
        rows.addView(row);
    }
}
