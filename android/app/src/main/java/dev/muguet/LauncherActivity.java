package dev.muguet;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ApplicationExitInfo;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class LauncherActivity extends Activity {
    private static final int PICK_ZIP = 1;
    private static final int PICK_MAGNET = 2;
    private static final int PICK_FOLDER = 3;
    private static final int PICK_SAVE_ZIP = 4;
    private static final int PICK_SAVE_LOG = 6;
    // The folder the release instructions point at. Offered as the picker's
    // starting point and as a one-tap button, so nobody has to hunt for it.
    private static final String DEFAULT_GAME_FOLDER = "/storage/emulated/0/GTA5B";
    private static final String DEFAULT_MAGNET =
        "magnet:?xt=urn:btih:60a2d95f23154e4f7c75a965307133c24d9c7178&dn=GrandV-assets"
        + "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451"
        + "&tr=http%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
        + "&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce"
        + "&tr=http%3A%2F%2Ftracker.qu.ax%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.skynetcloud.site%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.qu.ax%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.ducks.party%3A1984%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.tryhackx.org%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.corpscorp.online%3A80%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.bittor.pw%3A1337%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker-udp.gbitt.info%3A80%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.nyaa.vc%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.gmi.gd%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker2.dler.org%3A80%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.dler.org%3A6969%2Fannounce"
        + "&tr=http%3A%2F%2Ftracker2.dler.org%3A80%2Fannounce"
        + "&tr=http%3A%2F%2Ftracker.dler.org%3A80%2Fannounce"
        + "&tr=http%3A%2F%2Ftracker.dler.com%3A6969%2Fannounce"
        + "&tr=http%3A%2F%2Ftracker.renfei.net%3A8080%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.farted.net%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.peerfect.org%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.opentrackr.com%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.ilibr.org%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Fmail.segso.net%3A6969%2Fannounce"
        + "&tr=https%3A%2F%2Ftracker.pmman.tech%3A443%2Fannounce"
        + "&tr=https%3A%2F%2Fht.therarbg.to%3A443%2Fannounce"
        + "&tr=udp%3A%2F%2Fretracker01-msk-virt.corbina.net%3A80%2Fannounce";
    // Every graphics knob the web port exposes, in the same order, with the same
    // choices, taken from graphics_settings.js on the game data. Columns are
    // label, engine flag, then the engine's own values. DISPLAY below renames
    // those values for people who do not know what a LOD bias is; index N of a
    // DISPLAY row is the same knob as GRAPHICS[i][2 + N].
    private static final String[][] GRAPHICS = {
        {"Frame limit", "-frameLimit", "auto", "24", "30", "42", "60"},
        {"Texture quality", "-textureQuality", "auto", "0", "1", "2"},
        {"Reflection quality (mirrors, glass)", "-reflectionQuality", "auto", "0", "1", "2", "3"},
        {"Water quality", "-waterQuality", "auto", "0", "1"},
        {"Particle quality (smoke, sparks)", "-particleQuality", "auto", "0", "1", "2"},
        {"Grass quality", "-grassQuality", "auto", "0", "1", "2", "3"},
        {"Texture sharpness at angles", "-anisotropicQualityLevel", "auto", "0", "2", "4", "8", "16"},
        {"Scenery detail at a distance", "-lodScale", "auto", "0", "0.25", "0.5", "0.75", "1"},
        {"Pedestrian detail at a distance", "-pedLodBias", "auto", "0", "0.25", "0.5", "0.75", "1"},
        {"Vehicle detail at a distance", "-vehicleLodBias", "auto", "0", "0.25", "0.5", "0.75", "1"},
        {"City density (crowds, traffic)", "-cityDensity", "auto", "0", "0.25", "0.5", "0.75", "1"},
        {"Pedestrian variety", "-pedVariety", "auto", "0", "0.25", "0.5", "0.75", "1"},
        {"Vehicle variety", "-vehicleVariety", "auto", "0", "0.25", "0.5", "0.75", "1"},
        {"Fullscreen effects (bloom, grade)", "-postFX", "auto", "0", "1", "2", "3"},
        {"Motion blur", "-MotionBlurStrength", "auto", "0", "0.25", "0.5", "1"},
        {"Depth of field (blurred background)", "-noInGameDOF", "auto", "1", "0"},
        {"Shadow quality", "-shadowQuality", "auto", "-1", "0", "1", "2"},
        {"Shadow edges", "-shadowSoftness", "auto", "0", "1", "2", "3", "4", "5"},
        {"Ambient occlusion (corners and creases)", "-SSAO", "auto", "0", "1", "2"},
        {"Render distance (how far you can see)", "-farclip", "auto", "10", "25", "50", "100", "200", "500", "1000", "2000", "5000"},
        {"Fog you can walk through", "-fogVolumes", "auto", "0", "1"},
        {"Shadows from smoke and fire", "-particleShadows", "auto", "0", "1"},
        {"Long shadows from the sun", "-shadowLongShadows", "auto", "0", "1"},
        {"Loading assets ahead of you", "-HDStreamingInFlight", "auto", "0", "1"},
        {"Lights on far-away buildings", "-nolodlights", "auto", "on", "off"},
    };
    private static final String[][] DISPLAY = {
        {"Automatic", "24 fps", "30 fps", "42 fps", "60 fps"},
        {"Automatic", "Very low", "Low", "High"},
        {"Automatic", "Off", "Low", "Medium", "High"},
        {"Automatic", "Off", "High"},
        {"Automatic", "Off", "Low", "Medium", "High"},
        {"Automatic", "Off", "Very low", "Low", "High"},
        {"Automatic", "Off", "2x", "4x", "8x", "16x"},
        {"Automatic", "Lowest (fastest)", "Low", "Medium", "High", "Full"},
        {"Automatic", "Lowest (fastest)", "Low", "Medium", "High", "Full"},
        {"Automatic", "Lowest (fastest)", "Low", "Medium", "High", "Full"},
        {"Automatic", "Empty", "Quiet", "Medium", "Busy", "Full"},
        {"Automatic", "None", "Low", "Medium", "High", "Full"},
        {"Automatic", "None", "Low", "Medium", "High", "Full"},
        {"Automatic", "Off", "Low", "Medium", "High"},
        {"Automatic", "Off", "Light", "Medium", "Full"},
        {"Automatic", "Off", "On"},
        {"Automatic", "None at all", "Off (fewer)", "Low", "High"},
        {"Automatic", "Hard edges", "Hard", "Normal", "Soft", "Softer", "Softest"},
        {"Automatic", "Off", "Low", "High"},
        {"Automatic", "Extreme close", "Very close", "Close", "Near", "Short", "Medium", "Long", "Very long", "Full"},
        {"Automatic", "Off", "On"},
        {"Automatic", "Off", "On"},
        {"Automatic", "Off", "On"},
        {"Automatic", "Off", "On"},
        {"Automatic", "On", "Off"},
    };
    // Same three presets as the web launcher. "Low" is the phone default: 30 fps,
    // everything cheap, shadows gone entirely and the render distance at 100 m,
    // which is what a mid-range phone needs. Depth of field is inverted to match
    // the engine's own reading, and shadow quality uses -1, the value
    // graphics_settings.js passes when the web launcher turns shadows off.
    // Every row must have one entry per GRAPHICS row, in the same order.
    private static final String[][] PRESETS = {
        {"30", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0.25", "0", "0", "0", "0", "1", "-1", "0", "0", "100", "0", "0", "0", "0", "off"},
        {"auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto", "auto"},
        {"60", "2", "3", "1", "2", "3", "16", "1", "1", "1", "1", "1", "1", "3", "1", "0", "2", "3", "2", "2000", "1", "1", "1", "1", "on"},
    };
    private Spinner[] graphicsSpinners;
    private Spinner graphicsPreset;
    private boolean loadingGraphics;

    // A filesystem path as the tree URI DocumentsUI expects. The external-storage
    // provider spells a path under the primary volume as "primary:<rest>".
    private static Uri folderUri(String path) {
        String base = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String docId = path.startsWith(base + "/") ? "primary:" + path.substring(base.length() + 1) : "primary:";
        return android.provider.DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents", docId);
    }

    private void buildGraphics(LinearLayout column) {
        TextView title = new TextView(this);
        title.setText("Graphics");
        title.setTextSize(18);
        column.addView(title);
        graphicsPreset = new Spinner(this);
        graphicsPreset.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Low (phone)", "Engine default", "Max"}));
        graphicsPreset.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (loadingGraphics) return;
                applyPreset(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        column.addView(graphicsPreset);
        graphicsSpinners = new Spinner[GRAPHICS.length];
        for (int i = 0; i < GRAPHICS.length; i++) {
            TextView label = new TextView(this);
            label.setText(GRAPHICS[i][0]);
            column.addView(label);
            Spinner spinner = new Spinner(this);
            spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DISPLAY[i]));
            final int index = i;
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    if (loadingGraphics) return;
                    graphicsPreset.setSelection(1);
                    saveGraphics();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
            graphicsSpinners[i] = spinner;
            column.addView(spinner);
        }
    }

    // The launcher owns the frame limit and the start mode, and the graphics knobs
    // ride along as engine arguments rather than save-file edits, because the
    // engine rewrites its own settings on every save.
    private void saveGraphics() {
        JSONArray extra = new JSONArray();
        for (int i = 0; i < GRAPHICS.length; i++) {
            int position = graphicsSpinners[i].getSelectedItemPosition();
            if (position == 0) continue;
            extra.put(GRAPHICS[i][1] + "=" + GRAPHICS[i][2 + position]);
        }
        settings.put("extra_args", extra);
        saveSettings();
    }

    private void applyPreset(int which) {
        if (which < 0 || which >= PRESETS.length) return;
        loadingGraphics = true;
        for (int i = 0; i < GRAPHICS.length; i++) {
            int position = 0;
            for (int k = 2; k < GRAPHICS[i].length; k++) {
                if (GRAPHICS[i][k].equals(PRESETS[which][i])) { position = k - 1; break; }
            }
            graphicsSpinners[i].setSelection(position);
        }
        loadingGraphics = false;
        saveGraphics();
    }

    private void buildMultimedia(LinearLayout column) {
        TextView title = new TextView(this);
        title.setText("Display and audio");
        title.setTextSize(18);
        column.addView(title);
        scale = new EditText(this);
        scale.setHint("Render scale, 0.25 to 1.0");
        scale.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        column.addView(scale);
        heap = new Spinner(this);
        heap.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"1 GB heap", "2 GB heap", "3 GB heap", "4 GB heap", "5 GB heap", "6 GB heap", "7 GB heap", "8 GB heap"}));
        column.addView(heap);
        mode = new Spinner(this);
        mode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Story mode", "Sandbox"}));
        column.addView(mode);
        mute = new CheckBox(this);
        mute.setText("Mute audio");
        column.addView(mute);
        cpuBc = new CheckBox(this);
        cpuBc.setText("Decode BC textures on CPU");
        column.addView(cpuBc);
        softwareClip = new CheckBox(this);
        softwareClip.setText("Force software clip distances");
        column.addView(softwareClip);
    }

    private void buildMultiplayer(LinearLayout column) {
        TextView title = new TextView(this);
        title.setText("Multiplayer");
        title.setTextSize(18);
        column.addView(title);
        multiplayer = new CheckBox(this);
        multiplayer.setText("Enable multiplayer, join a relay room");
        column.addView(multiplayer);
        relayUrl = new EditText(this);
        relayUrl.setHint("Relay address, ws://host:8012/relay");
        column.addView(relayUrl);
        relayRoom = new EditText(this);
        relayRoom.setHint("Room name");
        column.addView(relayRoom);
        relayKey = new EditText(this);
        relayKey.setHint("Access code");
        column.addView(relayKey);
        name = new EditText(this);
        name.setHint("Player name");
        column.addView(name);
    }

    private void buildTouch(LinearLayout column) {
        TextView title = new TextView(this);
        title.setText("Touch controls");
        title.setTextSize(18);
        column.addView(title);
        sensitivityLabel = new TextView(this);
        column.addView(sensitivityLabel);
        sensitivity = new SeekBar(this);
        sensitivity.setMax(15);
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                sensitivityLabel.setText("Look sensitivity: " + LookSensitivity.fromSlider(progress) + "x");
                if (fromUser) saveTouch(false);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        column.addView(sensitivity);
        Button editor = new Button(this);
        editor.setText("Edit controls (move, resize, rebind)");
        editor.setOnClickListener(v -> startActivity(new Intent(this, ControlsActivity.class)));
        column.addView(editor);
    }

    // Writes touch_layout.json, which is both the geometry the game draws from and
    // the look gain the :game process reads before Rust has started.
    private void saveTouch(boolean rebuild) {
        try {
            JSONObject root = new JSONObject();
            root.put("look", LookSensitivity.fromSlider(sensitivity.getProgress()));
            root.put("scale", 1.0);
            root.put("opacity", 1.0);
            root.put("haptics", true);
            File file = new File(getFilesDir(), "touch_layout.json");
            if (file.isFile()) {
                JSONObject existing = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                if (existing.has("buttons")) root.put("buttons", existing.getJSONArray("buttons"));
                root.put("scale", existing.optDouble("scale", 1.0));
                root.put("opacity", existing.optDouble("opacity", 1.0));
                root.put("haptics", existing.optBoolean("haptics", true));
            }
            Files.write(file.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { error(e); }
    }

    private void buildLinks(LinearLayout column) {
        TextView title = new TextView(this);
        title.setText("About");
        title.setTextSize(18);
        column.addView(title);
        Button issue = new Button(this);
        issue.setText("Report a bug on GitHub");
        issue.setOnClickListener(v -> open("https://github.com/bytarch/gta5mobile-android/issues"));
        column.addView(issue);
        Button contact = new Button(this);
        contact.setText("Contact on X");
        contact.setOnClickListener(v -> open("https://x.com/MikeJohndlvl"));
        column.addView(contact);
    }

    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { status.setText(url); }
    }

    private void restoreSettings() {
        loadingGraphics = true;
        try {
            scale.setText(String.valueOf(settings.optDouble("scale", 0.5)));
            heap.setSelection((int) Math.max(0, Math.min(7, settings.optLong("android_heap_gb", 5) - 1)));
            mode.setSelection(settings.optInt("start_mode", 2) == 1 ? 0 : 1);
            mute.setChecked(settings.optBoolean("mute", false));
            cpuBc.setChecked(settings.optBoolean("gpu_cpu_bc", false));
            softwareClip.setChecked(settings.optBoolean("gpu_software_clip", false));
            multiplayer.setChecked(settings.optBoolean("multiplayer", false));
            relayUrl.setText(settings.optString("relay_url", ""));
            relayRoom.setText(settings.optString("relay_room", ""));
            relayKey.setText(settings.optString("relay_key", ""));
            name.setText(settings.optString("player_name", ""));
            sensitivity.setProgress(LookSensitivity.toSlider((float) settings.optDouble("touch_sensitivity", 1.0)));
            // Graphics: apply the stored engine arguments back onto the spinners.
            JSONArray extra = settings.optJSONArray("extra_args");
            if (extra != null) {
                for (int i = 0; i < GRAPHICS.length; i++) {
                    for (int k = 0; k < extra.length(); k++) {
                        String arg = extra.optString(k, "");
                        if (arg.startsWith(GRAPHICS[i][1] + "=")) {
                            String value = arg.substring(GRAPHICS[i][1].length() + 1);
                            for (int p = 2; p < GRAPHICS[i].length; p++) {
                                if (GRAPHICS[i][p].equals(value)) { graphicsSpinners[i].setSelection(p - 1); break; }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { error(e); }
        loadingGraphics = false;
        showFolderStatus();
        savesStatus.setText("Saves live in the app's private storage; export them here to keep them.");
    }

    private void saveSettings() {
        try {
            settings.put("scale", Double.parseDouble(scale.getText().toString().trim().replace(',', '.')));
        } catch (Exception ignored) {}
        settings.put("android_heap_gb", heap.getSelectedItemPosition() + 1);
        settings.put("start_mode", mode.getSelectedItemPosition() == 0 ? 1 : 2);
        settings.put("mute", mute.isChecked());
        settings.put("gpu_cpu_bc", cpuBc.isChecked());
        settings.put("gpu_software_clip", softwareClip.isChecked());
        settings.put("multiplayer", multiplayer.isChecked());
        settings.put("relay_url", relayUrl.getText().toString().trim());
        settings.put("relay_room", relayRoom.getText().toString().trim());
        settings.put("relay_key", relayKey.getText().toString());
        settings.put("player_name", name.getText().toString().trim());
        settings.put("touch_sensitivity", LookSensitivity.fromSlider(sensitivity.getProgress()));
        try {
            File file = new File(getFilesDir(), "config.json");
            Files.write(file.toPath(), settings.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { error(e); }
        saveTouch(false);
    }

    private void error(Exception e) {
        status.setText(String.valueOf(e));
    }

    private JSONObject loadSettings() {
        try {
            File file = new File(getFilesDir(), "config.json");
            if (!file.isFile()) return new JSONObject();
            return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) { return new JSONObject(); }
    }

    // A plain filesystem path adopted as the game folder. Reachable without the
    // system picker, which is the point: a folder the user already has at a known
    // place needs no tree grant to be read when the app holds all-files access.
    private void useFolder(String path) {
        String root = gameRoot(path);
        if (root == null) {
            status.setText(StoragePaths.isReadableDir(path)
                ? "No game data in " + path + " - expected data/ and b/<build>/ inside it."
                : "Cannot read " + path + ". Grant file access, or import a ZIP.");
            return;
        }
        settings.remove("game_folder_uri");
        settings.put("game_folder_path", root);
        saveSettings();
        showFolderStatus();
        ready();
    }
}
