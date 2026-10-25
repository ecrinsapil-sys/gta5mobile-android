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
        + "&tr=udp%3A%2F%2Ftracker.gmi.gd%3A6969%2Fannounce";
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

    private static native boolean nativePrepare(String directory);
    private native void nativeImport(String archive);
    // Returns the folder inside the picked one that holds the game (data/ and
    // b/), or null when there is none.
    private native String nativeGameRoot(String path);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    // The native library is around 18 MB and takes roughly a second to load, so it
    // is loaded on the worker instead of a static initializer: the launcher can draw
    // immediately, and every native call waits for the load to finish. The worker is
    // single-threaded, so a later import is still queued behind this one.
    private final AtomicBoolean loadingNative = new AtomicBoolean();
    private final AtomicBoolean nativeLoaded = new AtomicBoolean();
    private final AtomicReference<Exception> nativeFailure = new AtomicReference<>();
    private boolean firstResume = true;
    // The folder the picker resolved to when it turned out to hold no game, so the
    // message can name it rather than leaving the user to guess.
    private String folderTried;
    private TextView status;
    private TextView savesStatus;
    private Button play, playEngine, importButton, clearFolderButton, grantAccessButton;
    private TextView folderStatus;
    private ProgressBar progress;
    private Spinner mode, heap;
    private EditText scale, name, magnetField, relayUrl, relayRoom, relayKey;
    private CheckBox mute, multiplayer, cpuBc, softwareClip;
    private SeekBar sensitivity;
    private TextView sensitivityLabel;
    private JSONObject settings;
    private boolean importing;

    // JSONObject.put and remove declare a checked JSONException. Every key here is a
    // plain string literal that cannot fail in practice, so the exception is handled
    // once in these two helpers instead of at each of the two dozen call sites.
    private void set(String key, Object value) {
        try { settings.put(key, value); } catch (Exception e) { error(e); }
    }

    private void unset(String key) { settings.remove(key); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        column.setPadding(padding, padding, padding, padding);
        scroll.addView(column);
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText("Grand V Android");
        title.setTextSize(24);
        column.addView(title);
        TextView warning = new TextView(this);
        warning.setText("ARM64 / Android 10+ / Vulkan. A high-end phone with 8–12 GB RAM is recommended, not a guarantee of compatibility. Supply your own web-port data: import a ZIP, or point the app at a folder that already holds data/ and b/<build>/ with game.wasm and shaders inside. A normal PC installation is not enough. First launch compiles the game and may take several minutes.");
        column.addView(warning);
        status = new TextView(this);
        column.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        column.addView(progress);
        // Game data comes from the user's own files: either a ZIP to extract or a folder
        // to use where it lies. The app never fetches game data from any server,
        // which keeps this project clear of mirroring Rockstar's content.
        importButton = new Button(this);
        importButton.setText("Import game ZIP");
        importButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_ZIP);
        });
        column.addView(importButton);
        // Not everyone has the game data as a ZIP. A folder that already holds
        // data/ and b/<build>/ works just as well, and is used where it lies, so
        // nothing is copied and the phone needs no free space for it.
        Button folderButton = new Button(this);
        folderButton.setText("Use a game files folder");
        folderButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            // DocumentsUI reopens wherever it was last used unless it is told where
            // to start, so the default game folder is offered explicitly.
            intent.putExtra("android.provider.extra.INITIAL_URI", folderUri(DEFAULT_GAME_FOLDER));
            startActivityForResult(intent, PICK_FOLDER);
        });
        column.addView(folderButton);
        // The release instructions name this folder, so offer it directly rather
        // than making the user find it in the system tree.
        Button defaultFolderButton = new Button(this);
        defaultFolderButton.setText("Use " + DEFAULT_GAME_FOLDER);
        defaultFolderButton.setOnClickListener(v -> useFolder(DEFAULT_GAME_FOLDER));
        column.addView(defaultFolderButton);
        folderStatus = new TextView(this);
        column.addView(folderStatus);
        clearFolderButton = new Button(this);
        clearFolderButton.setText("Go back to the imported copy");
        clearFolderButton.setOnClickListener(v -> { unset("game_folder_uri"); unset("game_folder_path"); saveSettings(); ready(); });
        column.addView(clearFolderButton);
        grantAccessButton = new Button(this);
        grantAccessButton.setText("Grant file access");
        grantAccessButton.setVisibility(android.view.View.GONE);
        grantAccessButton.setOnClickListener(v -> openUrl("package:" + getPackageName()));
        column.addView(grantAccessButton);
        TextView savesHeader = new TextView(this);
        savesHeader.setText("Saves");
        column.addView(savesHeader);
        savesStatus = new TextView(this);
        column.addView(savesStatus);
        Button playButton = new Button(this);
        playButton.setText("Play");
        column.addView(playButton);
        mode = new Spinner(this);
        heap = new Spinner(this);
        scale = new EditText(this);
        name = new EditText(this);
        magnetField = new EditText(this);
        relayUrl = new EditText(this);
        relayRoom = new EditText(this);
        relayKey = new EditText(this);
        mute = new CheckBox(this);
        multiplayer = new CheckBox(this);
        cpuBc = new CheckBox(this);
        softwareClip = new CheckBox(this);
        sensitivity = new SeekBar(this);
        sensitivityLabel = new TextView(this);
        settings = new JSONObject();
        play = playButton;
        playEngine = playButton;
        ready();
    }

    // A filesystem path as the tree URI DocumentsUI expects, so the picker can be
    // told where to start. The external-storage provider spells a path under the
    // primary volume as "primary:<rest>".
    private static Uri folderUri(String path) {
        String base = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String docId = path.startsWith(base + "/") ? "primary:" + path.substring(base.length() + 1) : "primary:";
        return android.provider.DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents", docId);
    }

    // Adopts a plain filesystem path as the game folder, without the system picker:
    // a folder already sitting at a known place needs no tree grant to be read once
    // the app holds all-files access.
    private void useFolder(String path) {
        String root = gameRoot(path);
        if (root == null) {
            status.setText(readable(new File(path))
                ? "No game data in " + path + " - expected data/ and b/<build>/ inside it."
                : "Cannot read " + path + ". Grant file access below, or import a ZIP.");
            return;
        }
        unset("game_folder_uri");
        set("game_folder_path", root);
        saveSettings();
        ready();
    }

    // The game runs in its own process, so everything the launcher knows reaches it
    // through config.json on disk rather than through Java.
    private File config() { return new File(getFilesDir(), "config.json"); }

    private void openUrl(String url) {
        try {
            if (url.startsWith("package:")) {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse(url)));
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        } catch (Exception e) { status.setText(url.replace("package:", "Settings > Apps > Special access for ")); }
    }

    // Queues the one-time library load. Nothing native may run before it lands, so
    // ready() waits here and is called again from the worker when it finishes.
    private void ensureNative() {
        if (!loadingNative.compareAndSet(false, true)) return;
        worker.execute(() -> {
            Exception thrown = null;
            try { System.loadLibrary("muguet"); }
            catch (Throwable t) { thrown = new RuntimeException("The game library could not be loaded.", t); }
            if (thrown == null) nativeLoaded.set(true); else nativeFailure.compareAndSet(null, thrown);
            final Exception failure = thrown;
            runOnUiThread(() -> {
                if (failure != null) error(failure);
                ready();
            });
        });
    }

    private void ready() {
        if (!nativeLoaded.get()) {
            play.setEnabled(false);
            if (nativeFailure.get() != null) return;
            status.setText("Loading the game library…");
            ensureNative();
            return;
        }
        try {
            String folder = folderPath();
            boolean pickedSomething = !settings.optString("game_folder_uri", "").isEmpty()
                || !settings.optString("game_folder_path", "").isEmpty();
            if (folder == null) {
                folderStatus.setText(folderTried != null
                    ? "No game data in " + folderTried + " - expected data/ and b/<build>/ inside it."
                    : pickedSomething
                    ? "That folder cannot be read. Grant file access below, or pick another folder."
                    : "No game data yet. Import a ZIP, or pick a folder that holds data/ and b/.");
                clearFolderButton.setVisibility(pickedSomething ? android.view.View.VISIBLE : android.view.View.GONE);
                grantAccessButton.setVisibility(pickedSomething && !hasAllFilesAccess() ? android.view.View.VISIBLE : android.view.View.GONE);
                play.setEnabled(false);
                status.setText(pickedSomething ? "Game folder not readable." : "Import game data to begin.");
                return;
            }
            grantAccessButton.setVisibility(android.view.View.GONE);
            if (folder.isEmpty()) {
                File dir = StoragePaths.gameDir(this);
                folderStatus.setText(dir.getAbsolutePath().contains("/Android/data/")
                    ? "Using the ZIP copy, extracted into " + dir.getAbsolutePath() + "."
                    : "Using the ZIP copy, extracted into app-private storage.");
                clearFolderButton.setVisibility(android.view.View.GONE);
            } else {
                folderStatus.setText("Game folder: " + folder);
                clearFolderButton.setVisibility(android.view.View.VISIBLE);
                if (!folder.equals(settings.optString("game_folder_path", ""))) {
                    set("game_folder_path", folder);
                    saveSettings();
                }
            }
            boolean ok = nativePrepare(getFilesDir().getAbsolutePath());
            play.setEnabled(ok && !importing);
            status.setText(ok ? "Game data ready." : "Import game data to begin.");
        } catch (Exception e) { play.setEnabled(false); error(e); }
    }

    // The picker hands back a tree Uri but the engine reads plain paths, so the tree
    // is mapped back to a real one. Shared storage has such a path; an SD card or a
    // USB volume does not, and is reported rather than silently loading nothing.
    // Returns "" when no folder was ever picked, and null when the picked one is unusable.
    private String folderPath() {
        String stored = settings.optString("game_folder_uri", "");
        if (!stored.isEmpty()) {
            Uri tree = Uri.parse(stored);
            try {
                getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Revoked, or never persistable; the check below decides.
            }
            try {
                String base = StoragePaths.treeToPath(this, tree);
                if (base != null) {
                    String root = gameRoot(base);
                    if (root != null) return root;
                    folderTried = base;
                }
            } catch (Exception ignored) {
                // Fall through to the path resolved earlier.
            }
        }
        // The tree Uri is gone or no longer granted - a reinstall, a revoked grant,
        // a cancelled pick. The path resolved from it is still readable with
        // all-files access, so keep using it: dropping the folder here is what made
        // a working install look like "Import game data to begin."
        String path = settings.optString("game_folder_path", "").trim();
        if (!path.isEmpty()) {
            String root = gameRoot(path);
            if (root != null) return root;
        }
        return stored.isEmpty() && path.isEmpty() ? "" : null;
    }

    // The release wraps the game in a folder: GrandV-assets/mirror/playgta5.com holds
    // data/ and b/<build>/, and the instructions tell people to pick the outer one.
    // So the game folder is looked up inside the picked one through the native side,
    // which does the same search the engine does.
    private String gameRoot(String path) {
        if (!nativeLoaded.get()) return null;
        try {
            String root = nativeGameRoot(path);
            if (root != null) return root;
        } catch (Exception ignored) { }
        return null;
    }

    // Opening the manifest is the test, not listing it: on /sdcard an app can stat a
    // file it is not allowed to read, and that false "yes" is what used to look like
    // a broken install instead of a missing permission.
    private static boolean readable(File dir) {
        try (InputStream in = new FileInputStream(new File(dir, "data/manifest.json"))) { return in.read() >= 0; }
        catch (Exception e) { return false; }
    }

    private static boolean hasAllFilesAccess() {
        return Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager();
    }

    private void saveSettings() {
        try {
            File temp = new File(getFilesDir(), "config.json.new");
            Files.write(temp.toPath(), settings.toString(2).getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(), config().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { error(e); }
    }

    // The engine's save root. Must match MUGUET_FSROOT in config.rs, or a backup
    // would silently miss the real save.
    private File saves() { return new File(getFilesDir(), "saves"); }
    private File logFile() { return new File(getFilesDir(), "muguet.log"); }

    private static void walkFiles(File dir, ArrayList<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File kid : kids) {
            if (kid.isDirectory()) walkFiles(kid, out);
            else if (kid.isFile()) out.add(kid);
        }
    }

    private void updateSavesStatus() {
        if (savesStatus == null) return;
        ArrayList<File> files = new ArrayList<>();
        walkFiles(saves(), files);
        long bytes = 0;
        for (File f : files) bytes += f.length();
        savesStatus.setText(files.isEmpty()
            ? "No saves yet. Play and save in-game first."
            : files.size() + " file(s), " + (bytes >> 10) + " KB, in app storage.");
    }

    // Called by Rust on the import worker, never directly updates UI there.
    public void importProgress(long done, long total) {
        runOnUiThread(() -> {
            progress.setIndeterminate(false);
            progress.setProgress(total == 0 ? 0 : (int) (1000.0 * done / total));
            status.setText("Extracting: " + (done >> 20) + " / " + (total >> 20) + " MB");
        });
    }

    private void error(Exception e) {
        if (!isFinishing() && !isDestroyed()) {
            new AlertDialog.Builder(this).setTitle("Grand V Android").setMessage(String.valueOf(e.getMessage())).setPositiveButton("OK", null).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FOLDER) {
            if (result != RESULT_OK || data == null || data.getData() == null) {
                if (result != RESULT_CANCELED) status.setText("Folder pick cancelled.");
                return;
            }
            Uri tree = data.getData();
            int keep = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                // Without a persisted grant the folder is readable for this one launch
                // only, so the picker would have to be used every time.
                getContentResolver().takePersistableUriPermission(tree, keep);
            } catch (Exception ignored) { }
            String path = StoragePaths.treeToPath(this, tree);
            if (path == null) {
                status.setText("That location has no filesystem path (an SD card, a cloud provider, or a ZIP opened in place). Copy the game into internal storage and pick it there.");
                return;
            }
            folderTried = null;
            set("game_folder_uri", tree.toString());
            set("game_folder_path", path);
            saveSettings();
            ready();
            return;
        }
        if (request != PICK_ZIP) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (result != RESULT_CANCELED) status.setText("Import cancelled.");
            return;
        }
        if (importing) { status.setText("An import is already running."); return; }
        Uri uri = data.getData();
        importing = true;
        play.setEnabled(false); importButton.setEnabled(false); progress.setIndeterminate(true);
        status.setText("Copying the archive into app storage…");
        worker.execute(() -> {
            Exception failure = null;
            File archive = new File(getCacheDir(), "import.zip");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(archive)) {
                if (in == null) throw new IOException("That file could not be opened");
                byte[] buffer = new byte[1 << 20];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            } catch (Exception e) { failure = e; }
            Exception error = failure;
            if (error == null) {
                runOnUiThread(() -> {
                    status.setText("Extracting (this can take several minutes)…");
                    progress.setIndeterminate(true);
                });
                try { nativeImport(archive.getAbsolutePath()); }
                catch (Exception e) { error = e; }
                finally { archive.delete(); }
            } else {
                archive.delete();
            }
            Exception problem = error;
            runOnUiThread(() -> {
                importing = false; importButton.setEnabled(true); progress.setIndeterminate(false);
                ready();
                if (problem != null) error(problem);
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        // Returning from the All-files settings screen has to re-test the folder.
        if (settings != null && firstResume) { firstResume = false; return; }
        if (settings != null) ready();
    }

    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
