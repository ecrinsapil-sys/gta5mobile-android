package dev.muguet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// The control editor. TouchLayout already describes every button, so this screen is
// just a view onto it: the same coordinates the game draws with, at the same
// proportions. Dragging a circle moves it, pinching with two fingers resizes it,
// and tapping one opens its binding so the key can be changed.
public final class ControlsActivity extends Activity {
    private Board board;
    private TextView hint;
    private TouchLayout layout;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        layout = TouchLayout.load(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        column.setPadding(pad, pad, pad, pad);
        setContentView(column);

        TextView title = new TextView(this);
        title.setText("Touch controls");
        title.setTextSize(20);
        column.addView(title);

        hint = new TextView(this);
        hint.setText("Drag to move. Pinch with two fingers to resize. Tap a button to change the key it presses.");
        column.addView(hint);

        board = new Board(this);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        column.addView(board, boardParams);

        // Overall size and transparency apply to every button at once, which is what
        // most players actually want to change; per-button resizing is the pinch.
        addSlider(column, "Button size", 50, 160, Math.round(layout.scale * 100), value -> {
            layout.scale = value / 100f;
            board.invalidate();
        });
        addSlider(column, "Transparency", 15, 100, Math.round(layout.opacity * 100), value -> {
            layout.opacity = value / 100f;
            board.invalidate();
        });
        addSlider(column, "Look sensitivity", 25, 400, Math.round(layout.look * 100), value -> {
            layout.look = value / 100f;
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        Button reset = new Button(this);
        reset.setText("Reset");
        reset.setOnClickListener(v -> {
            layout = TouchLayout.defaults();
            board.setLayout(layout);
            recreate();
        });
        actions.addView(reset);
        Button done = new Button(this);
        done.setText("Save");
        done.setOnClickListener(v -> { save(); finish(); });
        actions.addView(done);
        column.addView(actions);
    }

    private interface OnChange { void apply(int value); }

    private void addSlider(LinearLayout column, String label, int min, int max, int initial, OnChange change) {
        TextView text = new TextView(this);
        text.setText(label + ": " + initial + "%");
        column.addView(text);
        SeekBar bar = new SeekBar(this);
        // The bars all report 0..100 of their own range so one label format fits all.
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, initial - min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                text.setText(label + ": " + value + "%");
                if (fromUser) change.apply(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        column.addView(bar);
    }

    // Writes the same file the game reads on every resume, so an edit applies the
    // next time the player returns to the game rather than at some later launch.
    private void save() {
        try {
            JSONObject root = new JSONObject();
            root.put("look", (double) layout.look);
            root.put("scale", (double) layout.scale);
            root.put("opacity", (double) layout.opacity);
            root.put("haptics", layout.haptics);
            JSONArray buttons = new JSONArray();
            for (TouchLayout.Item item : layout.items) {
                JSONObject o = new JSONObject();
                o.put("id", item.id);
                o.put("label", item.label);
                o.put("action", item.action);
                o.put("x", (double) item.x);
                o.put("bottom", (double) item.bottom);
                o.put("radius", (double) item.radius);
                o.put("mode", item.mode);
                o.put("fromTop", item.fromTop);
                o.put("visible", item.visible);
                if (item.labelCar != null) o.put("labelCar", item.labelCar);
                if (item.labelFoot != null) o.put("labelFoot", item.labelFoot);
                if (item.actionCar != null) o.put("actionCar", item.actionCar);
                if (item.actionFoot != null) o.put("actionFoot", item.actionFoot);
                buttons.put(o);
            }
            root.put("buttons", buttons);
            File file = new File(getFilesDir(), "touch_layout.json");
            File temp = new File(getFilesDir(), "touch_layout.json.new");
            Files.write(temp.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Toast.makeText(this, "Controls saved.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            hint.setText("Could not save: " + e);
        }
    }

    @Override public void onBackPressed() { save(); super.onBackPressed(); }

    // The drawing surface. It mirrors TouchControls' own placement maths so a button
    // sits where the game will draw it: x is a fraction of the width, and each row is
    // measured from the top or bottom edge.
    private final class Board extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint text = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float density;
        private TouchLayout.Item dragging;
        private float dragX, dragY;
        // Pinch state: the distance and radius when the second finger landed.
        private TouchLayout.Item pinching;
        private float pinchStart, pinchRadius;

        Board(Activity context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            text.setColor(0xffffffff);
            text.setTextAlign(android.graphics.Paint.Align.CENTER);
        }

        void setLayout(TouchLayout next) { layout = next; invalidate(); }

        private float px(TouchLayout.Item item) { return getWidth() * item.x; }
        private float py(TouchLayout.Item item) {
            return item.fromTop ? item.bottom * density : getHeight() - item.bottom * density;
        }

        private TouchLayout.Item at(float x, float y) {
            for (int i = layout.items.size() - 1; i >= 0; i--) {
                TouchLayout.Item item = layout.items.get(i);
                if (!item.visible) continue;
                if (Math.hypot(x - px(item), y - py(item)) <= item.radius * layout.scale * density + 8) return item;
            }
            return null;
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            int action = event.getActionMasked();
            if (event.getPointerCount() >= 2) {
                if (pinching == null) {
                    pinching = at(event.getX(0), event.getY(0));
                    if (pinching == null) pinching = at(event.getX(1), event.getY(1));
                    if (pinching != null) {
                        pinchStart = Math.max(1f, (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0)));
                        pinchRadius = pinching.radius;
                        dragging = null;
                    }
                } else if (action == android.view.MotionEvent.ACTION_MOVE) {
                    float now = (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
                    pinching.radius = TouchLayout.clamp(pinchRadius * now / pinchStart,
                        TouchLayout.MIN_RADIUS, TouchLayout.MAX_RADIUS);
                    invalidate();
                }
                if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) pinching = null;
                return true;
            }
            switch (action) {
                case android.view.MotionEvent.ACTION_DOWN:
                    dragging = at(event.getX(), event.getY());
                    dragX = event.getX(); dragY = event.getY();
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (dragging == null) return true;
                    dragging.x = TouchLayout.clamp(dragging.x + (event.getX() - dragX) / getWidth(), 0f, 1f);
                    float delta = event.getY() - dragY;
                    dragging.bottom = TouchLayout.clamp(dragging.bottom + (dragging.fromTop ? delta : -delta) / density, 0f, 400f);
                    dragX = event.getX(); dragY = event.getY();
                    invalidate();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    // A tap that did not move opens the binding list.
                    if (dragging != null && Math.hypot(event.getX() - dragX, event.getY() - dragY) < 6 * density
                        && Math.abs(event.getY() - dragY) < 6 * density) {
                        pickBinding(dragging);
                    }
                    dragging = null;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            int alpha = (int) (0xff * layout.opacity);
            for (TouchLayout.Item item : layout.items) {
                if (!item.visible) continue;
                float x = px(item), y = py(item), r = item.radius * layout.scale * density;
                boolean active = item == dragging || item == pinching;
                paint.setColor((((active ? 0x90 : 0x40) * alpha) / 0xff) << 24 | 0xffffff);
                canvas.drawCircle(x, y, r, paint);
                paint.setColor((0xee * alpha / 0xff) << 24 | 0xffffff);
                text.setTextSize((item.label.length() > 6 ? 8 : 10) * density * Math.min(1.4f, layout.scale));
                canvas.drawText(item.label, x, y + 4 * density, text);
            }
        }

        // A short list of the keys the game understands, grouped so the common
        // rebinds are the first thing offered.
        private void pickBinding(TouchLayout.Item item) {
            final String[][] choices = {
                {"FIRE", "fire"}, {"AIM (toggle)", "aim"}, {"HORN", "horn"}, {"MENU keys", "nav"},
                {"JUMP / HANDRAKE", "Space"}, {"ENTER / EXIT VEHICLE", "KeyF"}, {"RELOAD", "KeyR"},
                {"COVER / RADIO", "KeyQ"}, {"CROUCH", "ControlLeft"}, {"LOOK BACK", "KeyC"},
                {"CAMERA", "KeyV"}, {"PAUSE", "Escape"}, {"PHONE", "ArrowUp"},
                {"SWITCH (wheel)", "AltLeft"}, {"WEAPON (wheel)", "Tab"},
                {"SPRINT", "ShiftLeft"}, {"BACKSPACE", "Backspace"}, {"ENTER", "Enter"},
                {"LEFT", "ArrowLeft"}, {"RIGHT", "ArrowRight"}, {"DOWN", "ArrowDown"},
            };
            String[] labels = new String[choices.length];
            for (int i = 0; i < choices.length; i++) labels[i] = choices[i][0];
            new android.app.AlertDialog.Builder(this.getContext())
                .setTitle(item.label + " - key it presses")
                .setItems(labels, (dialog, which) -> {
                    item.action = choices[which][1];
                    Toast.makeText(getContext(), item.label + " now presses " + choices[which][0], Toast.LENGTH_SHORT).show();
                    invalidate();
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }
}
