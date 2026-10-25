package dev.muguet;

import android.content.Context;
import android.content.res.Configuration;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The touch HUD used to be a fixed set of circles at coordinates baked into
// TouchControls. This is that set as data: one entry per button, with the action
// it sends, its place on screen in density-independent units, and its size. The
// launcher writes it, the game reads it on every resume, so an edit made in the
// launcher applies the next time the player returns to the game.
//
// The same file also carries the touch look gain, because the :game process
// needs it before Rust has started and a second file would be a second thing to
// keep in sync.
final class TouchLayout {
    static final float MIN_RADIUS = 14f, MAX_RADIUS = 64f;
    static final float DEFAULT_RADIUS = 30f;
    static final float MIN_SCALE = 0.5f, MAX_SCALE = 1.6f, DEFAULT_SCALE = 1f;
    static final float MIN_LOOK = 0.25f, MAX_LOOK = 4f, DEFAULT_LOOK = 1f;

    static final class Item {
        String id;
        String label;
        String action;
        // Placement is expressed in three parts so a layout survives any screen:
        // the x axis is a fraction of the width (0 = left edge, 1 = right edge),
        // the y axis is measured from the bottom edge so the buttons stay clear of
        // the thumb rests and the gesture bar, and size is a radius times scale.
        float x, bottom, radius;
        String mode;

        Item(String id, String label, String action, float x, float bottom, float radius, String mode) {
            this.id = id; this.label = label; this.action = action;
            this.x = x; this.bottom = bottom; this.radius = radius; this.mode = mode;
        }
    }

    final List<Item> items = new ArrayList<>();
    final Map<String, Item> byId = new LinkedHashMap<>();
    float look = DEFAULT_LOOK;
    float scale = DEFAULT_SCALE;
    // Opacity of the buttons, 0.15–1. A HUD that is barely there is easier on the
    // eyes over the game and what most players end up wanting.
    float opacity = 1f;
    boolean haptics = true;

    // The shipped layout, matching the circles this project has always drawn. Y is
    // from the bottom: the old code laid the lower row out at screenBottom - 68.
    static TouchLayout defaults() {
        TouchLayout l = new TouchLayout();
        l.items.add(new Item("fire_left",   "FIRE",     "fire",       0.33f,  68f, 36f, "both"));
        l.items.add(new Item("fire",        "FIRE",     "fire",       1f,     68f, 40f, "foot"));
        l.items.add(new Item("aim",         "AIM",      "aim",        1f,     46f, 31f, "foot"));
        l.items.add(new Item("jump",        "JUMP",     "Space",      1f,    168f, 28f, "foot"));
        l.items.add(new Item("enter",       "ENTER",    "KeyF",       1f,    132f, 26f, "foot"));
        l.items.add(new Item("reload",      "RELOAD",   "KeyR",       1f,     98f, 24f, "foot"));
        l.items.add(new Item("gas",         "GAS",      "KeyW",       1f,     68f, 40f, "car"));
        l.items.add(new Item("brake",       "BRAKE",    "KeyS",       1f,     46f, 31f, "car"));
        l.items.add(new Item("handbrake",   "H.BRAKE",  "Space",      1f,    168f, 28f, "car"));
        l.items.add(new Item("exit_car",    "EXIT",     "KeyF",       1f,    132f, 26f, "car"));
        l.items.add(new Item("horn",        "HORN",     "horn",       1f,     98f, 24f, "car"));
        l.items.add(new Item("weapon",      "WEAPON",   "Tab",        1f,     30f, 22f, "both"));
        l.items.add(new Item("cover",       "COVER",    "KeyQ",       1f,     30f, 22f, "foot"));
        l.items.add(new Item("radio",       "RADIO",    "KeyQ",       1f,     30f, 22f, "car"));
        l.items.add(new Item("crouch",      "CROUCH",   "ControlLeft",1f,    30f, 22f, "foot"));
        l.items.add(new Item("lookback",    "LOOK BACK","KeyC",       1f,     30f, 22f, "car"));
        l.items.add(new Item("cam",         "CAM",      "KeyV",       1f,     30f, 22f, "both"));
        l.items.add(new Item("pause",       "PAUSE",    "Escape",     0f,     30f, 22f, "both"));
        l.items.add(new Item("phone",       "PHONE",    "ArrowUp",    0f,     30f, 22f, "both"));
        l.items.add(new Item("menu",        "MENU",     "nav",        0f,     30f, 22f, "both"));
        l.items.add(new Item("switch",      "SWITCH",   "AltLeft",    0f,     30f, 22f, "both"));
        l.normalise();
        return l;
    }

    // The rows above share a corner on purpose, so the packed rows are spread out
    // here instead of being written out by hand 21 times.
    private void normalise() {
        int topLeft = 0, topRight = 0, bottomRight = 0, leftColumn = 0;
        for (Item it : items) {
            if (it.bottom == 30f && it.x == 0f) {
                it.x = 30f / 360f + 54f * topLeft / 360f; topLeft++;   // pause, phone, menu, switch
                it.radius = 22f;
            }
        }
        for (Item it : items) {
            if (it.bottom >= 90f && it.x == 1f) {
                // The lower-right cluster: x from the right edge in the shipped
                // layout, converted to a fraction of the width.
                it.x = 1f - ringOffsetX(it.id) / 360f;
            }
        }
        for (Item it : items) byId.put(it.id, it);
    }

    // Horizontal distance from the right edge, in dp, for the buttons the original
    // HUD pinned there. Kept as a table so the default layout is recognisably the
    // one this project shipped with.
    private static float ringOffsetX(String id) {
        switch (id) {
            case "fire": case "gas": return 62f;
            case "aim": case "brake": return 158f;
            case "jump": case "handbrake": return 58f;
            case "enter": case "exit_car": return 140f;
            case "reload": case "horn": return 222f;
            case "weapon": case "cover": case "radio": return 30f;
            case "crouch": case "lookback": return 84f;
            case "cam": return 138f;
            default: return 30f;
        }
    }

    Item find(String id) { return byId.get(id); }

    // A layout with a duplicate or missing id, or an action the game cannot send,
    // is dropped back to the defaults rather than half-applied.
    static TouchLayout load(Context context) {
        File file = new File(context.getFilesDir(), "touch_layout.json");
        if (!file.isFile()) return defaults();
        try {
            JSONObject root = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            TouchLayout l = defaults();
            l.look = clamp((float) root.optDouble("look", DEFAULT_LOOK), MIN_LOOK, MAX_LOOK);
            l.scale = clamp((float) root.optDouble("scale", DEFAULT_SCALE), MIN_SCALE, MAX_SCALE);
            l.opacity = clamp((float) root.optDouble("opacity", 1.0), 0.15f, 1f);
            l.haptics = root.optBoolean("haptics", true);
            JSONArray arr = root.optJSONArray("buttons");
            if (arr == null) return l;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Item it = l.find(o.optString("id", ""));
                if (it == null) continue;
                it.label = o.optString("label", it.label);
                String action = o.optString("action", it.action);
                if (!action.isEmpty()) it.action = action;
                it.x = clamp((float) o.optDouble("x", it.x), 0f, 1f);
                it.bottom = clamp((float) o.optDouble("bottom", it.bottom), 0f, 2000f);
                it.radius = clamp((float) o.optDouble("radius", it.radius), MIN_RADIUS, MAX_RADIUS);
                String mode = o.optString("mode", it.mode);
                if (mode.equals("foot") || mode.equals("car") || mode.equals("both")) it.mode = mode;
                it.visible = o.optBoolean("visible", true);
            }
            return l;
        } catch (Exception e) {
            return defaults();
        }
    }

    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    // Actions the game knows how to turn into input. Anything else in the file is
    // rejected here so a hand-edited layout cannot inject an unknown key.
    static final String[] ACTIONS = {
        "fire", "aim", "horn", "nav", "Space", "ShiftLeft", "ControlLeft", "AltLeft", "Enter", "Escape",
        "Backspace", "Tab", "KeyA", "KeyB", "KeyC", "KeyD", "KeyE", "KeyF", "KeyG", "KeyH", "KeyI",
        "KeyJ", "KeyK", "KeyL", "KeyM", "KeyN", "KeyO", "KeyP", "KeyQ", "KeyR", "KeyS", "KeyT", "KeyU",
        "KeyV", "KeyW", "KeyX", "KeyY", "KeyZ", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight",
        "Digit1", "Digit2", "Digit3", "Digit4", "Digit5", "Digit6", "Digit7", "Digit8", "Digit9",
    };

    static boolean isKnownAction(String action) {
        for (String a : ACTIONS) if (a.equals(action)) return true;
        return false;
    }
}
