package dev.muguet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

final class TouchControls extends View {
    static final class Input {
        final HashSet<String> keys = new HashSet<>();
        int buttons;
        boolean horn;
    }
    private static final class Button {
        final float x, y, radius;
        final String label, action;
        Button(float x, float y, float radius, String label, String action) {
            this.x = x; this.y = y; this.radius = radius; this.label = label; this.action = action;
        }
    }
    private static final class Grip {
        int button;
        float startX, startY, x, y;
        Grip(int button, float x, float y) { this.button = button; startX = this.x = x; startY = this.y = y; }
    }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final HashMap<Integer, Grip> grips = new HashMap<>();
    private final HashMap<String, Long> keyUntil = new HashMap<>();
    private long fireUntil;
    private TouchLayout layout;
    private final float density;
    private float left, top, right, bottom;
    private boolean inCar, aim, nav, sprinted;
    // Screen-relative geometry: x is a fraction of the width, y is measured off the
    // safe edge the row hangs from, so one layout fits a phone and a tablet alike.
    private float safeX, safeTop, safeBottom, widthDp;
    private static final long PRESS_NS = 120_000_000L;

    TouchControls(Context context, TouchLayout layout) {
        super(context);
        this.layout = layout;
        density = getResources().getDisplayMetrics().density;
        setFocusable(false);
    }

    // Re-read after the player edits the controls in the launcher and comes back.
    void reloadLayout(TouchLayout next) {
        if (next == null) return;
        release();
        layout = next;
        layoutButtons();
        invalidate();
    }

    // The camera key the layout binds; the gamepad's Select follows it.
    String camKey() {
        TouchLayout.Item cam = layout.find("cam");
        return cam == null ? "KeyV" : cam.actionFor(inCar);
    }

    void setInCar(boolean value) { release(); inCar = value; layoutButtons(); invalidate(); }
    void release() { grips.clear(); keyUntil.clear(); fireUntil = 0; aim = false; sprinted = false; invalidate(); }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { release(); layoutButtons(); }
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) { release(); layoutButtons(); return super.onApplyWindowInsets(insets); }

    private void layoutButtons() {
        WindowInsets insets = getRootWindowInsets();
        android.graphics.Insets safe = android.graphics.Insets.NONE;
        if (insets != null) {
            if (android.os.Build.VERSION.SDK_INT >= 30) safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            else {
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                safe = android.graphics.Insets.of(Math.max(insets.getSystemWindowInsetLeft(), cutout == null ? 0 : cutout.getSafeInsetLeft()),
                    Math.max(insets.getSystemWindowInsetTop(), cutout == null ? 0 : cutout.getSafeInsetTop()),
                    Math.max(insets.getSystemWindowInsetRight(), cutout == null ? 0 : cutout.getSafeInsetRight()),
                    Math.max(insets.getSystemWindowInsetBottom(), cutout == null ? 0 : cutout.getSafeInsetBottom()));
            }
        }
        left = safe.left / density; top = safe.top / density;
        right = (getWidth() - safe.right) / density; bottom = (getHeight() - safe.bottom) / density;
        widthDp = right - left;
        safeX = left;
        safeTop = top;
        safeBottom = bottom;
        buttons.clear();
        // Placement comes from the layout the launcher writes: x as a fraction of
        // the width, and the distance from whichever edge the row hangs off. Top-row
        // buttons are measured from the top so they stay clear of the status bar and
        // the notch whatever the aspect ratio.
        for (TouchLayout.Item it : layout.items) {
            if (!it.visible || !shows(it)) continue;
            float px = safeX + widthDp * it.x;
            float py = it.fromTop ? safeTop + it.bottom : safeBottom - it.bottom;
            add(px, py, it.radius * layout.scale, it.labelFor(inCar), it.actionFor(inCar));
        }
        if (nav) {
            String[] labels = {"◀", "▲", "▼", "▶", "OK", "BACK"};
            String[] keys = {"ArrowLeft", "ArrowUp", "ArrowDown", "ArrowRight", "Enter", "Backspace"};
            for (int i = 0; i < keys.length; i++) add(safeX + widthDp / 2 - 120 + i * 48, safeTop + 82, 21, labels[i], keys[i]);
        }
    }

    // A button belongs to one mode: on foot, in a vehicle, or both.
    private boolean shows(TouchLayout.Item it) {
        if ("both".equals(it.mode)) return true;
        return inCar ? "car".equals(it.mode) : "foot".equals(it.mode);
    }

    private float stickRadius() { return 58 * layout.scale; }
    private void add(float x, float y, float radius, String text, String action) { buttons.add(new Button(x, y, radius, text, action)); }
    private int buttonAt(float x, float y) {
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            if (Math.hypot(x - b.x, y - b.y) <= b.radius + 8) return i;
        }
        return -1;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = event.getX(index) / density, y = event.getY(index) / density;
            int button = buttonAt(x, y);
            if (button >= 0) {
                String a = buttons.get(button).action;
                if (a.equals("aim")) aim = !aim;
                else if (a.equals("nav")) { grips.clear(); nav = !nav; layoutButtons(); button = -1; }
                else if (a.equals("fire")) fireUntil = System.nanoTime() + PRESS_NS;
                // horn is continuous: it stays on for as long as the grip is held,
                // which input() reads. A timed press would honk in bursts.
                else if (!a.equals("horn")) keyUntil.put(a, System.nanoTime() + PRESS_NS);
                if (layout.haptics) performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            } else if (x < left + (right - left) * 0.4 && y > top + 110 && grips.values().stream().noneMatch(g -> g.button == -2)) button = -2;
            grips.put(event.getPointerId(index), new Grip(button, x, y));
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                Grip grip = grips.get(event.getPointerId(i));
                if (grip == null) continue;
                float x = event.getX(i) / density, y = event.getY(i) / density;
                if (grip.button != -2 && !isWheel(grip)) GameActivity.nativeLook((x - grip.x) * 3 * layout.look, (y - grip.y) * 3 * layout.look);
                grip.x = x; grip.y = y;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) grips.remove(event.getPointerId(index));
        else if (action == MotionEvent.ACTION_CANCEL) release();
        invalidate(); return true;
    }
    // A wheel button turns its drag into camera movement instead of a key, so the
    // WEAPON / SWITCH / RADIO buttons can be dragged to pick.
    private boolean isWheel(Grip grip) {
        if (grip.button < 0 || grip.button >= buttons.size()) return false;
        String a = buttons.get(grip.button).action;
        return a.equals("Tab") || a.equals("AltLeft") || (inCar && a.equals("KeyQ"));
    }
    Input input() {
        Input input = new Input();
        long now = System.nanoTime();
        keyUntil.entrySet().removeIf(e -> e.getValue() <= now);
        input.keys.addAll(keyUntil.keySet());
        if (fireUntil > now) input.buttons |= 1;
        if (aim && !inCar) input.buttons |= 2;
        for (Grip g : grips.values()) {
            if (g.button == -2) {
                float dx = (g.x - g.startX) / stickRadius(), dy = (g.y - g.startY) / stickRadius();
                if (dx < -0.35) input.keys.add("KeyA"); if (dx > 0.35) input.keys.add("KeyD");
                if (dy < -0.35) input.keys.add(inCar ? "Numpad8" : "KeyW"); if (dy > 0.35) input.keys.add(inCar ? "Numpad2" : "KeyS");
                if (!inCar && Math.hypot(dx, dy) > 0.95) input.keys.add("ShiftLeft");
            } else if (g.button >= 0 && g.button < buttons.size()) {
                String a = buttons.get(g.button).action;
                if (a.equals("fire")) input.buttons |= 1;
                else if (a.equals("horn")) input.horn = true;
                else if (!a.equals("aim") && !a.equals("nav")) input.keys.add(a);
                if (isWheel(g)) {
                    float dx = g.x - g.startX, dy = g.y - g.startY;
                    double length = Math.hypot(dx, dy);
                    if (length > 10) {
                        float push = (float) (40 * layout.look * Math.min((length - 10) / 45, 1) / length);
                        GameActivity.nativeLook(dx * push, dy * push);
                    }
                }
            }
        }
        return input;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save(); canvas.scale(density, density);
        int alpha = (int) (0xff * layout.opacity);
        float sx = safeX + 98, sy = safeBottom - 88, dx = 0, dy = 0;
        for (Grip g : grips.values()) if (g.button == -2) {
            sx = g.startX; sy = g.startY; dx = g.x - sx; dy = g.y - sy;
            double length = Math.hypot(dx, dy); float r = stickRadius(); if (length > r) { dx *= r / length; dy *= r / length; }
        }
        paint.setColor(((0x30 * alpha) / 0xff) << 24 | 0xffffff); canvas.drawCircle(sx, sy, stickRadius(), paint);
        paint.setColor(((0x70 * alpha) / 0xff) << 24 | 0xffffff); canvas.drawCircle(sx + dx, sy + dy, 26 * layout.scale, paint);
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i); final int index = i;
            boolean held = grips.values().stream().anyMatch(g -> g.button == index) || (aim && b.action.equals("aim")) || (nav && b.action.equals("nav"));
            paint.setColor((((held ? 0x80 : 0x30) * alpha) / 0xff) << 24 | 0xffffff); canvas.drawCircle(b.x, b.y, b.radius, paint);
            paint.setColor((0xee * alpha) / 0xff << 24 | 0xffffff); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize((b.label.length() > 6 ? 8 : 10) * Math.min(1.4f, layout.scale));
            canvas.drawText(b.label, b.x, b.y + 4, paint);
        }
        canvas.restore();
    }
}
