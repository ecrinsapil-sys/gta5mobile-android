package dev.muguet;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// Touch look gain, read from touch_layout.json -- the same file that carries the
// button geometry, so the :game process has one file to read before Rust starts.
// A config.json that still carries the old touch_sensitivity value is honoured as
// a fallback so an existing install does not lose its setting.
// Clamped so a damaged file can never leave touch look unusable.
final class LookSensitivity {
    static final float MIN = 0.25f, MAX = 4f, DEFAULT = 1f;
    private LookSensitivity() {}

    static float fromConfig(File layoutFile) {
        Float direct = read(layoutFile, "look");
        if (direct != null) return direct;
        // Older installs wrote the value into config.json under the old name.
        Float legacy = read(new File(layoutFile.getParentFile(), "config.json"), "touch_sensitivity");
        return legacy == null ? DEFAULT : legacy;
    }

    private static Float read(File file, String key) {
        if (file == null || !file.isFile()) return null;
        try {
            JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            double value = json.optDouble(key, Double.NaN);
            if (!Double.isFinite(value)) return null;
            return (float) Math.max(MIN, Math.min(MAX, value));
        } catch (Exception e) { return null; }
    }

    // Kept for the launcher's slider: 1..15 keeps a non-zero gain, 4/4 = 1.0
    // matches the shipped tuning.
    static float fromSlider(int progress) { return Math.max(1, Math.min(15, progress)) / 4.0f; }
    static int toSlider(float gain) { return Math.max(1, Math.min(15, Math.round(gain * 4))); }
}
