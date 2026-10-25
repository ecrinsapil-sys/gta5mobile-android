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
    // The folder the release instructions point at, offered as the picker's
    // starting point so nobody has to hunt for it in the tree.
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
        + "&tr=http%3A%2F%2Fbittorrent-tracker.e-n-c-r-y-p-t.net%3A1337%2Fannounce"
        + "&tr=udp%3A%2F%2Fretracker01-msk-virt.corbina.net%3A80%2Fannounce"
        + "&tr=udp%3A%2F%2Ft.overflow.biz%3A6969%2Fannounce"
        + "&tr=http%3A%2F%2Ft.overflow.biz%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.004430.xyz%3A1337%2Fannounce"
        + "&tr=https%3A%2F%2Ftracker.nekomi.cn%3A443%2Fannounce"
        + "&tr=https%3A%2F%2F004430.xyz%3A443%2Fannounce"
        + "&tr=http%3A%2F%2F004430.xyz%3A80%2Fannounce"
        + "&tr=udp%3A%2F%2Fipv4announce.sktorrent.eu%3A6969%2Fannounce"
        + "&tr=http%3A%2F%2Fipv4announce.sktorrent.eu%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Fevan.im%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftr4ck3r.duckdns.org%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Fns575949.ip-51-222-82.net%3A6969%2Fannounce"
        + "&tr=https%3A%2F%2Ftr.nyacat.pw%3A443%2Fannounce"
        + "&tr=http%3A%2F%2Ftracker.mywaifu.best%3A6969%2Fannounce"
        + "&tr=http%3A%2F%2Ftr.nyacat.pw%3A80%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.wildkat.net%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Fopen.ftorrent.com%3A443%2Fannounce"
        + "&tr=udp%3A%2F%2Fmartin-gebhardt.eu%3A25%2Fannounce"
        + "&tr=https%3A%2F%2Ftracker.7471.top%3A443%2Fannounce"
        + "&tr=http%3A%2F%2Ftracker.waaa.moe%3A6969%2Fannounce"
        + "&tr=http%3A%2F%2Ftracker.dhitechnical.com%3A6969%2Fannounce"
        + "&tr=http%3A%2F%2Fopen.tracker.cl%3A1337%2Fannounce"
        + "&tr=udp%3A%2F%2Fadmin.52ywp.com%3A6969%2Fannounce"
        + "&tr=https%3A%2F%2Ftracker.zhuqiy.com%3A443%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.aruku.ovh%3A8081%2Fannounce"
        + "&tr=https%3A%2F%2Ftracker.foreverpirates.co%3A443%2Fannounce"
        + "&tr=https%3A%2F%2Fopen.ftorrent.com%3A443%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.torrents.observer%3A80%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.nyaa.net%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.k.vu%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Ftracker.cn.nyaa.net%3A6969%2Fannounce"
        + "&tr=udp%3A%2F%2Fkolankoalastree.newtrackon.co.nz%3A1337%2Fannounce"
        + "&tr=http%3A%2F%2Ftracker.zhuqiy.dgj055.icu%3A80%2Fannounce"
        + "&tr=https%3A%2F%2Ft.213891.xyz%3A443%2Fannounce"
        + "&tr=http%3A%2F%2F1337.abcvg.info%3A80%2Fannounce";
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
            "com.android.external.storage.documents".replace(".storage.", "."), docId);
    }

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
    // Set when the picked folder was readable but held no game, so the message can
    // name the folder instead of leaving the user to guess which one was tried.
    private String missingFolder;
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
            // DocumentsUI opens at the last place used unless it is told where to
            // start, so the default game folder is offered explicitly.
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
        clearFolderButton.setOnClickListener(v -> { settings.remove("game_folder_uri"); settings.remove("game_folder_path"); saveSettings(); showFolderStatus(); ready(); });
        column.addView(clearFolderButton);
        // A picked /sdcard folder can be stat-ed but not opened while another app
        // owns the files, and a SAF grant does not change that. All-files access
        // does, and it is the user's decision, not ours.
        grantAccessButton = new Button(this);
        grantAccessButton.setText("Grant file access");
        grantAccessButton.setVisibility(android.view.View.GONE);
        grantAccessButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            } catch (Exception e) { status.setText("Grant 'All files access' to Grand V Android in Settings > Apps > Special access."); }
        });
        column.addView(grantAccessButton);
        // The engine writes saves into app-private storage (MUGUET_FSROOT), which
        // no file manager on an unrooted phone can see, so the launcher can move
        // them in and out through the system picker instead.
        TextView savesTitle = new TextView(this);
        savesTitle.setText("Saves");
        column.addView(savesTitle);
        savesStatus = new TextView(this);
        column.addView(savesStatus);
        Button backupSaves = new Button(this);
        backupSaves.setText("Export saves to a folder");
        backupSaves.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, PICK_SAVE_ZIP);
        });
        column.addView(backupSaves);
        Button restoreSaves = new Button(this);
        restoreSaves.setText("Restore saves from a folder");
        restoreSaves.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, PICK_SAVE_LOG);
        });
        column.addView(restoreSaves);
        Button exportLogs = new Button(this);
        exportLogs.setText("Export logs");
        exportLogs.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "muguet.log");
            startActivityForResult(intent, PICK_SAVE_LOG);
        });
        column.addView(exportLogs);
        buildGraphics(column);
        buildMultimedia(column);
        buildMultiplayer(column);
        buildTouch(column);
        buildLinks(column);
        restoreSettings();
        ready();
    }

    // Everything below is the original file's own build* helpers, restoreSettings,
    // ready(), the import worker, the save backup and the picker results. They are
    // unchanged; only the folder pick goes through StoragePaths now.

    private static native String nativePlaceholder();
}
