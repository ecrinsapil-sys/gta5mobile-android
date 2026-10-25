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
// it sends, its place on screen and its size. The launcher writes it, the game
// reads it on every resume, so an edit made in the launcher applies the next time
// the player returns to the game.
//
// The same file also carries the touch look gain, because the :game process needs
// it before Rust has started and a second file would be a second thing to keep in
// sync.
final class TouchLayout {
    static final float MIN_RADIUS = 14f, MAX_RADIUS = 64f;
    static final float MIN_SCALE = 0.5f, MAX_SCALE = 1.6f, DEFAULT_SCALE = 1f;
    static final float MIN_LOOK = 0.25f, MAX_LOOK = 4f, DEFAULT_LOOK = 1f;

    static final class Item {
        final String id;
        String label;
        String action;
        // Placement is expressed so a layout survives any screen: x as a fraction
        // of the width (0 = left edge, 1 = right edge), bottom as a distance in dp
        // from the safe edge it hangs off, and size as a radius.
        float x, bottom, radius;
        // "foot", "car" or "both": which mode the button belongs to.
        String mode;
        // Top-row buttons measure from the top edge instead of the bottom one, so
        // they stay clear of the status bar and the notch on any aspect ratio.
        boolean fromTop;
        boolean visible = true;
        // Per-mode overrides. A button that does the same thing on foot and in a
        // vehicle (FIRE, JUMP, CAM) leaves them empty and keeps one entry.
        String labelCar, labelFoot, actionCar, actionFoot;

        Item(String id, String label, String action, float x, float bottom, float radius, String mode, boolean fromTop) {
            this.id = id; this.label = label; this.action = action;
            this.x = x; this.bottom = bottom; this.radius = radius;
            this.mode = mode; this.fromTop = fromTop;
        }

        String labelFor(boolean inCar) {
            if (inCar && labelCar != null) return labelCar;
            if (!inCar && labelFoot != null) return labelFoot;
            return label;
        }

        String actionFor(boolean inCar) {
            if (inCar && actionCar != null) return actionCar;
            if (!inCar && actionFoot != null) return actionFoot;
            return action;
        }
    }

    final List<Item> items = new ArrayList<>();
    final Map<String, Item> byId = new LinkedHashMap<>();
    float look = DEFAULT_LOOK;
    float scale = DEFAULT_SCALE;
    // Opacity of the buttons, 0.15-1. A HUD that is barely there is easier on the
    // eyes over the game and what most players end up wanting.
    float opacity = 1f;
    boolean haptics = true;

    private void pack() {
        byId.clear();
        for (Item it : items) byId.put(it.id, it);
    }

    // The shipped layout, matching the circles this project has always drawn.
    // Top-row buttons sit 30 dp below the top edge; the rest hang off the bottom.
    // The rows share a corner on purpose in the original, so the packed rows are
    // laid out by the ring below rather than written out by hand 21 times.
    static TouchLayout defaults() {
        TouchLayout l = new TouchLayout();
        // Lower-right cluster: distance from the right edge in dp, as before.
        l.items.add(new Item("fire_left", "FIRE",     "fire",  0.33f, 68f,  36f, "foot", false));
        l.items.add(new Item("fire",      "FIRE",     "fire",  0f,    68f,  40f, "both", false));
        l.items.add(new Item("aim",       "AIM",      "aim",   0f,    46f,  31f, "foot", false));
        l.items.add(new Item("jump",      "JUMP",     "Space", 0f,    168f, 28f, "foot", false));
        l.items.add(new Item("enter",     "ENTER",    "KeyF",  0f,    132f, 26f, "foot", false));
        l.items.add(new Item("reload",    "RELOAD",   "KeyR",  0f,    98f,  24f, "foot", false));
        l.items.add(new Item("gas",       "GAS",      "KeyW",  0f,    68f,  40f, "car",  false));
        l.items.add(new Item("brake",     "BRAKE",    "KeyS",  0f,    46f,  31f, "car",  false));
        l.items.add(new Item("handbrake", "H.BRAKE",  "Space", 0f,    168f, 28f, "car",  false));
        l.items.add(new Item("exit_car",  "EXIT",     "KeyF",  0f,    132f, 26f, "car",  false));
        l.items.add(new Item("horn",      "HORN",     "horn",  0f,    98f,  24f, "car",  false));
        l.items.add(new Item("weapon",    "WEAPON",   "Tab",   0f,    30f,  22f, "both", true));
        l.items.add(new Item("cover",     "COVER",    "KeyQ",  0f,    30f,  22f, "foot", true));
        l.items.add(new Item("radio",     "RADIO",    "KeyQ",  0f,    30f,  22f, "car",  true));
        l.items.add(new Item("crouch",    "CROUCH",   "ControlLeft", 0f, 30f, 22f, "foot", true));
        l.items.add(new Item("lookback",  "LOOK BACK", "KeyC", 0f,   30f,  22f, "car",  true));
        l.items.add(new Item("cam",       "CAM",      "KeyV",  0f,    30f,  22f, "both", true));
        l.items.add(new Item("pause",     "PAUSE",    "Escape", 30f / 360f, 30f, 22f, "both", true));
        l.items.add(new Item("phone",     "PHONE",    "ArrowUp", 84f / 360f, 30f, 22f, "both", true));
        l.items.add(new Item("menu",      "MENU",     "nav",   138f / 360f, 30f, 22f, "both", true));
        l.items.add(new Item("switch",    "SWITCH",   "AltLeft", 192f / 360f, 30f, 22f, "both", true));
        l.ring();
        l.pack();
        return l;
    }

    // Converts the right-anchored and top-row entries into the two axes the game
    // draws from: x as a fraction of the width, and the distance from the edge the
    // row hangs off.
    private void ring() {
        for (Item it : items) {
            if (it.x == 0f && it.bottom >= 40f) it.x = 1f - ringOffsetX(it.id) / 360f;
        }
    }

    // Horizontal distance from the right edge, in dp, for the buttons the original
    // HUD pinned there, so the default layout is recognisably the one this project
    // shipped with.
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

    // A layout with a missing id, or an action the game cannot send, is dropped
    // back to the defaults rather than half-applied.
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
                String label = o.optString("label", null);
                if (label != null && !label.isEmpty()) it.label = label;
                String action = o.optString("action", null);
                if (action != null && isKnownAction(action)) it.action = action;
                it.x = clamp((float) o.optDouble("x", it.x), 0f, 1f);
                it.bottom = clamp((float) o.optDouble("bottom", it.bottom), 0f, 2000f);
                it.radius = clamp((float) o.optDouble("radius", it.radius), MIN_RADIUS, MAX_RADIUS);
                it.fromTop = o.optBoolean("fromTop", it.fromTop);
                it.visible = o.optBoolean("visible", it.visible);
                String mode = o.optString("mode", it.mode);
                if (mode.equals("foot") || mode.equals("car") || mode.equals("both")) it.mode = mode;
                it.labelCar = text(o, "labelCar");
                it.labelFoot = text(o, "labelFoot");
                String ac = text(o, "actionCar");
                if (ac != null && isKnownAction(ac)) it.actionCar = ac;
                String af = text(o, "actionFoot");
                if (af != null && isKnownAction(af)) it.actionFoot = af;
            }
            return l;
        } catch (Exception e) {
            return defaults();
        }
    }

    private static String text(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        String v = o.optString(key, "").trim();
        return v.isEmpty() ? null : v;
    }

    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    // A tablet in landscape has far more room than a phone, so the top row is
    // nudged outwards there rather than piled in one corner.
    static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
            >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    // Actions the game turns into input. Anything else in the file is rejected so
    // a hand-edited layout cannot inject an unknown key.
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
