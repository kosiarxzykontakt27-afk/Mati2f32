package pl.elevenclients.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import pl.elevenclients.ElevenClients;
import pl.elevenclients.config.ElevenConfig;
import pl.elevenclients.module.Module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Menu modow: karuzela piec kategorii (PvP, HUD, Visuals, Player, Misc). Klik w srodkowa karte
 * rozwija panel z przelacznikami modulow, klik w modul z ustawieniami rozwija suwaki.
 * Na dole: Wardrobe, Search, Friends, Settings.
 */
public class ModsHubScreen extends Screen {
    private static final String[] CATS = {"PvP", "HUD", "Visuals", "Player", "Misc"};
    private static final int[] CAT_ICONS = {Icons.PVP, Icons.HUD, Icons.VISUALS, Icons.PLAYER, Icons.MISC};
    private static final String[] BTN_LABELS = {"Wardrobe", "Search", "Friends", "Settings"};
    private static final int[] BTN_ICONS = {Icons.WARDROBE, Icons.SEARCH, Icons.FRIENDS, Icons.SETTINGS};

    private static final int CW = 96, CH = 112, SP = 112, PW = 320, PH = 250, ROW = 20, SROW = 22;
    private static final int BTN_W = 58, BTN_H = 38, BTN_GAP = 8;

    private static int lastSel = 0;

    private static class Hit {
        int x1, y1, x2, y2;
        Runnable click;
        Module.Setting slider;
        int tx1, tx2;
    }

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<Module, Float> knob = new HashMap<>();

    private int sel;
    private float scroll;
    private boolean panelOpen, searching;
    private String query = "";
    private float openP, searchP;
    private Module expanded;
    private int listScroll, visCount, listSize;
    private Module.Setting dragS;
    private Hit dragHit;

    private final float[] cardHv = new float[5];
    private final float[] btnHv = new float[4];
    private float contentTimer = 1f;

    private long openAt = -1, lastNs = 0;
    private float dt = 0.016f;
    private float s, ax, ay;

    public ModsHubScreen(Screen parent) {
        super(Text.literal("ElevenClients"));
        this.parent = parent;
        this.sel = lastSel;
        this.scroll = lastSel;
    }

    /* ---------- pomocnicze ---------- */

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float ease(float t) {
        t = clamp01(t);
        float q = 1f - t;
        return 1f - q * q * q;
    }

    private static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    private float approach(float cur, float target, float speed) {
        float n = cur + (target - cur) * Math.min(1f, dt * speed);
        return Math.abs(n - target) < 0.002f ? target : n;
    }

    private static float wrap(float v) {
        while (v > 2.5f) v -= 5f;
        while (v <= -2.5f) v += 5f;
        return v;
    }

    private static boolean in(double x, double y, int x1, int y1, int x2, int y2) {
        return x >= x1 && x < x2 && y >= y1 && y < y2;
    }

    private void layout() {
        s = Math.max(0.3f, Math.min(ElevenConfig.guiScale, (height - 24f) / 400f));
        ax = width / 2f;
        ay = height * 0.52f;
    }

    private Hit hit(int x1, int y1, int x2, int y2, Runnable r) {
        Hit h = new Hit();
        h.x1 = x1; h.y1 = y1; h.x2 = x2; h.y2 = y2; h.click = r;
        hits.add(h);
        return h;
    }

    /** Do ktorej kategorii nalezy modul. */
    static int catOf(Module m) {
        return switch (m.name) {
            case "Hitboxes", "TriggerBot", "KillAura", "Velocity", "AutoTotem", "CPS Counter", "Hitbox Render" -> 0;
            case "Watermark", "FPS", "Coordinates", "Direction", "Clock", "Ping", "Speed HUD", "Keystrokes",
                 "Armor Status", "Potion Status", "Totem Counter", "Biome" -> 1;
            case "Crosshair", "Fullbright", "Hurt Cam", "Fire Overlay", "Time Changer", "Weather Changer",
                 "ESP", "Item ESP", "No Bob" -> 2;
            case "Toggle Sprint", "Toggle Sneak", "Zoom", "Fly", "SpeedBoost", "HighJump", "Jesus", "AutoWalk",
                 "AutoJump", "InvMove", "Scaffold", "Nuker" -> 3;
            default -> 4;
        };
    }

    private List<Module> currentList() {
        List<Module> out = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (Module m : ElevenClients.modules.getAll()) {
            if (searching) {
                if (q.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(q)) out.add(m);
            } else if (catOf(m) == sel) {
                out.add(m);
            }
        }
        return out;
    }

    private static boolean intMode(Module.Setting st) {
        return "px".equals(st.unit) || st.max - st.min > 12f;
    }

    private static String fmt(Module.Setting st) {
        if (intMode(st)) return Math.round(st.value) + st.unit;
        if (st.max - st.min <= 2f) return String.format(Locale.ROOT, "%.2f%s", st.value, st.unit);
        return String.format(Locale.ROOT, "%.1f%s", st.value, st.unit);
    }

    /** Zmiana kategorii przy otwartym panelu: karuzela sie obraca, tresc przenika. */
    private void setSel(int i) {
        if (searching) return;
        int n = ((i % 5) + 5) % 5;
        if (n == sel) return;
        sel = n;
        lastSel = n;
        listScroll = 0;
        expanded = null;
        contentTimer = 0f;
    }

    private void openPanel(int idx) {
        sel = idx;
        lastSel = idx;
        scroll = idx;
        searching = false;
        panelOpen = true;
        listScroll = 0;
        expanded = null;
        contentTimer = 0f;
    }

    private void toggleSearch() {
        searching = !searching;
        query = "";
        listScroll = 0;
        expanded = null;
        if (searching) {
            panelOpen = false;
            scroll = sel;
            contentTimer = 0f;
        }
    }

    /* ---------- render ---------- */

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        if (openAt < 0) openAt = now;
        dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        contentTimer += dt;
        float intro = ease((now - openAt) / 300e6f);
        float tSec = Util.getMeasuringTimeMs() / 1000f;

        layout();

        float diff = wrap(sel - scroll);
        scroll += diff * Math.min(1f, dt * 11f);
        if (Math.abs(diff) < 0.002f) scroll = sel;
        scroll = ((scroll % 5f) + 5f) % 5f;
        openP = approach(openP, panelOpen && !searching ? 1f : 0f, 9f);
        searchP = approach(searchP, searching ? 1f : 0f, 9f);
        float pp = smooth(Math.max(openP, searchP));

        if (client != null && client.world == null) AuroraBackground.draw(c, width, height, tSec, intro);
        else c.fill(0, 0, width, height, Draw.argb(0.72f * intro, 0, 0, 0));

        hits.clear();
        float se = s * (0.96f + 0.04f * intro);
        double lmx = (mx - ax) / se, lmy = (my - ay) / se;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ax, ay, 0);
        m.scale(se, se, 1f);

        drawTitle(c, pp, intro);
        drawBack(c, lmx, lmy, intro);
        drawCards(c, lmx, lmy, pp, now);
        drawBottom(c, lmx, lmy, pp, intro);

        m.pop();
    }

    private void drawTitle(DrawContext c, float pp, float a) {
        String name = "ElevenClients";
        float y = Draw.lerp(-104f, -176f, pp);
        Draw.textC(c, textRenderer, name, 1.5f, y + 1, Draw.fade(0xFF5A5A63, a), 3f);
        Draw.textC(c, textRenderer, name, 0, y, Draw.fade(0xFFF4F4F8, a), 3f);
    }

    private void drawBack(DrawContext c, double lmx, double lmy, float a) {
        int x1 = Math.round(-width / 2f / s) + 8, y1 = Math.round(-ay / s) + 8;
        int x2 = x1 + 56, y2 = y1 + 20;
        boolean hov = in(lmx, lmy, x1, y1, x2, y2);
        Draw.glass(c, x1, y1, x2, y2, 7, Draw.fade(hov ? 0x30FFFFFF : 0x16FFFFFF, a), Draw.fade(hov ? 0x90FFFFFF : 0x50FFFFFF, a));
        Draw.textC(c, textRenderer, "< Wr\u00f3\u0107", (x1 + x2) / 2f, y1 + 6, Draw.fade(0xFFE9E9EF, a), 1f);
        hit(x1, y1, x2, y2, this::close);
    }

    private void drawCards(DrawContext c, double lmx, double lmy, float pp, long nowNs) {
        MatrixStack m = c.getMatrices();
        boolean open = pp > 0.001f;
        float se = smooth(searchP);
        float[] dd = new float[5];
        for (int i = 0; i < 5; i++) dd[i] = Draw.lerp(i - 2f, wrap(i - scroll), pp);
        Integer[] order = {0, 1, 2, 3, 4};
        java.util.Arrays.sort(order, (p, q) -> Float.compare(Math.abs(dd[q]), Math.abs(dd[p])));

        float shift = (PW - CW) / 2f + 6f;
        float born = (nowNs - openAt) / 300e6f;
        for (int idx : order) {
            float d = dd[idx];
            float ad = Math.abs(d);
            boolean center = open && idx == sel;
            float stag = ease(born - 0.06f * idx);
            float xoff = d * SP + (open && ad > 0.3f ? Math.signum(d) * Math.min(1f, ad) * shift * pp : 0f);
            float shrink = 1f - 0.16f * Math.min(1f, ad) * pp;
            float hvT = cardHv[idx];
            float sc = shrink * (1f + 0.12f * hvT) * (0.92f + 0.08f * stag);
            float alpha = (1f - 0.22f * Math.min(ad, 2f) * pp) * stag * (center ? 1f : 1f - se * 0.85f);
            int w = center ? Math.round(Draw.lerp(CW, PW, pp)) : CW;
            int h = center ? Math.round(Draw.lerp(CH, PH, pp)) : CH;
            int x1 = -w / 2, y1 = -h / 2, x2 = x1 + w, y2 = y1 + h;

            boolean pointerIn = in((lmx - xoff) / sc, lmy / sc, x1, y1, x2, y2);
            boolean hovTarget = pointerIn && !center && dragS == null && searchP < 0.5f;
            cardHv[idx] += ((hovTarget ? 1f : 0f) - cardHv[idx]) * Math.min(1f, dt * 14f);

            m.push();
            m.translate(xoff, 0, 0);
            m.scale(sc, sc, 1f);

            int fill = Draw.lerpColor(center ? 0x2CFFFFFF : 0x16FFFFFF, 0xE0E4E4EA, hvT);
            int border = Draw.lerpColor(center ? 0x80FFFFFF : 0x46FFFFFF, 0xFFFFFFFF, hvT);
            if (center && pp > 0.5f) fill = 0x30101014;
            Draw.glass(c, x1, y1, x2, y2, 12, Draw.fade(fill, alpha), Draw.fade(border, alpha));

            float colA = alpha * clamp01(1f - pp / 0.5f);
            if (colA > 0.03f || (!center && alpha > 0.03f)) {
                float ca = center ? colA : alpha;
                int ink = Draw.lerpColor(0xFFE9E9EF, 0xFF16161C, hvT);
                Icons.draw(c, CAT_ICONS[idx], 0, -14, 3f, Draw.fade(ink, ca));
                c.fill(-9, 8, 9, 9, Draw.fade(Draw.lerpColor(0x77FFFFFF, 0xFF16161C, hvT), ca));
                Draw.textC(c, textRenderer, CATS[idx], 0, 22, Draw.fade(ink, ca), 1f);
            }

            float panA = alpha * clamp01((pp - 0.5f) / 0.5f);
            if (center && panA > 0.03f) drawPanel(c, lmx, lmy, w, h, panA, pp);

            m.pop();

            int hx1 = Math.round(xoff - w * sc / 2f), hx2 = Math.round(xoff + w * sc / 2f);
            int hy1 = Math.round(-h * sc / 2f), hy2 = Math.round(h * sc / 2f);
            final int target = idx;
            if (center) {
                if (panelOpen) hit(hx1, hy1, hx2, hy1 + 50, () -> { panelOpen = false; expanded = null; });
            } else if (open) {
                hit(hx1, hy1, hx2, hy2, () -> {
                    if (searching) { searching = false; openPanel(target); }
                    else setSel(target);
                });
            } else {
                hit(hx1, hy1, hx2, hy2, () -> openPanel(target));
            }
        }
    }

    /** Naglowek + lista modulow wewnatrz rozwinietej karty (uklad w lokalnych wspolrzednych srodka). */
    private void drawPanel(DrawContext c, double lmx, double lmy, int w, int h, float pa, float pp) {
        float ct = clamp01(contentTimer / 0.45f);
        float a = pa * ease(ct * 1.6f);
        int x1 = -w / 2 + 10, x2 = w / 2 - 10;
        int top = -h / 2;
        int rowsTop;

        if (searching) {
            int fy1 = top + 12, fy2 = top + 34;
            Draw.glass(c, x1, fy1, x2, fy2, 7, Draw.fade(0x18FFFFFF, a), Draw.fade(0x70FFFFFF, a));
            Icons.draw(c, Icons.SEARCH, x1 + 13, (fy1 + fy2) / 2f, 1.3f, Draw.fade(0xFFB5B5C0, a));
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            String shown = query.isEmpty() ? "Szukaj modu\u0142u..." : query + (blink ? "_" : "");
            c.drawText(textRenderer, shown, x1 + 26, fy1 + 7, Draw.fade(query.isEmpty() ? 0xFF8D8D99 : 0xFFF4F4F8, a), false);
            rowsTop = top + 44;
        } else {
            Icons.draw(c, CAT_ICONS[sel], 0, top + 20, 1.8f, Draw.fade(0xFFE9E9EF, a));
            Draw.textC(c, textRenderer, CATS[sel], 0, top + 34, Draw.fade(0xFFE9E9EF, a), 1f);
            c.fill(x1, top + 46, x2, top + 47, Draw.fade(0x22FFFFFF, a));
            rowsTop = top + 52;
        }

        List<Module> list = currentList();
        listSize = list.size();
        int limit = h / 2 - 8;
        int y = rowsTop;
        int shown = 0;
        boolean interactive = pp > 0.95f;

        if (list.isEmpty()) {
            Draw.textC(c, textRenderer, "Brak wynik\u00f3w", 0, rowsTop + 20, Draw.fade(0xFF8D8D99, a), 1f);
        }

        for (int k = listScroll; k < list.size(); k++) {
            Module mod = list.get(k);
            a = pa * clamp01((ct - 0.07f * (k - listScroll)) / 0.35f);
            if (a > 0.02f && a < pa * 0.7f) {
                Draw.glass(c, x1 - 3, y, x2 - 3, y + ROW, 6, Draw.fade(0x14FFFFFF, a * 0.5f), Draw.fade(0x28FFFFFF, a * 0.5f));
                Draw.glass(c, x1 + 3, y, x2 + 3, y + ROW, 6, Draw.fade(0x14FFFFFF, a * 0.5f), Draw.fade(0x28FFFFFF, a * 0.5f));
            }
            boolean ex = mod == expanded && !mod.settings.isEmpty();
            int bh = ROW + (ex ? mod.settings.size() * SROW + 4 : 0);
            if (y + bh > limit) break;

            boolean hov = in(lmx, lmy, x1, y, x2, y + ROW);
            Draw.glass(c, x1, y, x2, y + ROW, 6, Draw.fade(hov ? 0x26FFFFFF : 0x12FFFFFF, a), Draw.fade(0x30FFFFFF, a));
            c.drawText(textRenderer, mod.name, x1 + 9, y + 6, Draw.fade(0xFFE9E9EF, a), false);

            if (!mod.settings.isEmpty()) {
                int cx = x2 - 42, cy = y + ROW / 2;
                int col = Draw.fade(0xFF9A9AA6, a);
                if (ex) {
                    c.fill(cx - 3, cy - 1, cx + 3, cy, col);
                    c.fill(cx - 2, cy, cx + 2, cy + 1, col);
                    c.fill(cx - 1, cy + 1, cx + 1, cy + 2, col);
                } else {
                    c.fill(cx - 1, cy - 3, cx, cy + 3, col);
                    c.fill(cx, cy - 2, cx + 1, cy + 2, col);
                    c.fill(cx + 1, cy - 1, cx + 2, cy + 1, col);
                }
            }

            float kv = knob.getOrDefault(mod, mod.isEnabled() ? 1f : 0f);
            kv = approach(kv, mod.isEnabled() ? 1f : 0f, 14f);
            knob.put(mod, kv);
            int sx1 = x2 - 30, sy1 = y + 4;
            Draw.round(c, sx1, sy1, sx1 + 24, sy1 + 12, 6, Draw.fade(Draw.lerpColor(0x33FFFFFF, 0xFFE9E9EF, kv), a));
            int kx = Math.round(sx1 + 2 + kv * 12);
            Draw.round(c, kx, sy1 + 2, kx + 8, sy1 + 10, 4, Draw.fade(Draw.lerpColor(0xFFB0B0BC, 0xFF1A1A20, kv), a));

            if (interactive) {
                final Module target = mod;
                hit(x1, y, x2 - 36, y + ROW, () -> {
                    if (target.settings.isEmpty()) target.toggle();
                    else expanded = expanded == target ? null : target;
                });
                hit(x2 - 36, y, x2, y + ROW, target::toggle);
            }

            if (ex) {
                int idx = 0;
                for (Module.Setting st : mod.settings) {
                    int sy = y + ROW + 2 + idx * SROW;
                    c.drawText(textRenderer, st.label, x1 + 9, sy + 2, Draw.fade(0xFFB5B5C0, a), false);
                    String val = fmt(st);
                    c.drawText(textRenderer, val, x2 - 9 - textRenderer.getWidth(val), sy + 2, Draw.fade(0xFFE9E9EF, a), false);
                    int tx1 = x1 + 9, tx2 = x2 - 9, ty = sy + 13;
                    float f = clamp01((st.value - st.min) / (st.max - st.min));
                    Draw.round(c, tx1, ty, tx2, ty + 3, 1, Draw.fade(0x33FFFFFF, a));
                    int fx = tx1 + Math.round((tx2 - tx1) * f);
                    Draw.round(c, tx1, ty, Math.max(tx1 + 2, fx), ty + 3, 1, Draw.fade(0xFFE9E9EF, a));
                    Draw.round(c, fx - 3, ty - 2, fx + 3, ty + 5, 3, Draw.fade(0xFFFFFFFF, a));
                    if (interactive) {
                        Hit sh = hit(tx1 - 4, sy + 8, tx2 + 4, sy + 21, null);
                        sh.slider = st;
                        sh.tx1 = tx1;
                        sh.tx2 = tx2;
                    }
                    idx++;
                }
            }

            y += bh + 3;
            shown++;
        }
        visCount = shown;
        a = pa * ease(ct * 1.6f);

        if (list.size() > Math.max(1, shown) && shown > 0) {
            int trackTop = rowsTop, trackH = limit - rowsTop;
            int barH = Math.max(10, trackH * shown / list.size());
            int maxScroll = Math.max(1, list.size() - shown);
            int barY = trackTop + (trackH - barH) * Math.min(listScroll, maxScroll) / maxScroll;
            Draw.round(c, x2 + 3, barY, x2 + 5, barY + barH, 1, Draw.fade(0x66FFFFFF, a));
        }
    }

    private void drawBottom(DrawContext c, double lmx, double lmy, float pp, float intro) {
        int total = 4 * BTN_W + 3 * BTN_GAP;
        int y1 = Math.round(Draw.lerp(88f, 147f, pp));
        int y2 = y1 + BTN_H;
        for (int i = 0; i < 4; i++) {
            int x1 = -total / 2 + i * (BTN_W + BTN_GAP), x2 = x1 + BTN_W;
            boolean hov = in(lmx, lmy, x1, y1, x2, y2);
            boolean active = i == 1 && searching;
            btnHv[i] += ((hov ? 1f : 0f) - btnHv[i]) * Math.min(1f, dt * 14f);
            float k = Math.max(btnHv[i], active ? 0.35f : 0f);
            float a = intro * ease((System.nanoTime() - openAt) / 300e6f - 0.25f - 0.05f * i);
            Draw.glass(c, x1, y1, x2, y2, 8, Draw.fade(Draw.lerpColor(0x14FFFFFF, 0xE0E4E4EA, k), a),
                    Draw.fade(Draw.lerpColor(0x48FFFFFF, 0xFFFFFFFF, k), a));
            int ink = Draw.lerpColor(0xFFE9E9EF, 0xFF16161C, k);
            Icons.draw(c, BTN_ICONS[i], (x1 + x2) / 2f, y1 + 14, 1.5f, Draw.fade(ink, a));
            Draw.textC(c, textRenderer, BTN_LABELS[i], (x1 + x2) / 2f, y1 + 28,
                    Draw.fade(Draw.lerpColor(0xFFB5B5C0, 0xFF16161C, k), a), 0.75f);
            final int id = i;
            hit(x1, y1, x2, y2, () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                switch (id) {
                    case 0 -> mc.setScreen(new ClickGuiScreen(this, 3));
                    case 1 -> toggleSearch();
                    case 2 -> mc.setScreen(new ClickGuiScreen(this, 2));
                    default -> mc.setScreen(new ClickGuiScreen(this, 4));
                }
            });
        }
    }

    /* ---------- wejscie ---------- */

    private void updateDrag(double lmx) {
        if (dragS == null || dragHit == null) return;
        float f = (float) Math.max(0, Math.min(1, (lmx - dragHit.tx1) / (double) (dragHit.tx2 - dragHit.tx1)));
        float v = dragS.min + f * (dragS.max - dragS.min);
        float range = dragS.max - dragS.min;
        if (intMode(dragS)) v = Math.round(v);
        else if (range <= 2f) v = Math.round(v * 20f) / 20f;
        else v = Math.round(v * 10f) / 10f;
        dragS.value = Math.max(dragS.min, Math.min(dragS.max, v));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        double lmx = (mx - ax) / s, lmy = (my - ay) / s;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!in(lmx, lmy, h.x1, h.y1, h.x2, h.y2)) continue;
            if (h.slider != null) {
                dragS = h.slider;
                dragHit = h;
                updateDrag(lmx);
                return true;
            }
            if (h.click != null) {
                h.click.run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragS != null) {
            updateDrag((mx - ax) / s);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragS != null) {
            dragS = null;
            dragHit = null;
            ElevenClients.modules.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (vAmt == 0) return super.mouseScrolled(mx, my, hAmt, vAmt);
        if (panelOpen || searching) {
            int max = Math.max(0, listSize - Math.max(1, visCount));
            listScroll = Math.max(0, Math.min(max, listScroll - (int) Math.signum(vAmt)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searching) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searching = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                listScroll = 0;
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && panelOpen) {
            panelOpen = false;
            expanded = null;
            return true;
        }
        if (panelOpen && keyCode == GLFW.GLFW_KEY_LEFT) { setSel(sel - 1); return true; }
        if (panelOpen && keyCode == GLFW.GLFW_KEY_RIGHT) { setSel(sel + 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (panelOpen) { panelOpen = false; expanded = null; }
            else openPanel(sel);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searching && chr >= 32 && chr != 127 && query.length() < 24) {
            query += chr;
            listScroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void removed() {
        lastSel = sel;
        ElevenClients.modules.save();
        super.removed();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
