package pl.elevenclients.gui;

import net.minecraft.client.gui.DrawContext;

/** Proste ikony rysowane liniami (bez tekstur), skalowane wspolczynnikiem k. */
public final class Icons {
    public static final int PVP = 0, HUD = 1, VISUALS = 2, PLAYER = 3, MISC = 4,
            WARDROBE = 5, SEARCH = 6, FRIENDS = 7, SETTINGS = 8;

    private Icons() {}

    private static void seg(DrawContext c, float x0, float y0, float x1, float y1, int t, int col) {
        float dx = x1 - x0, dy = y1 - y0;
        int n = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        int h = t / 2;
        for (int i = 0; i <= n; i++) {
            int x = Math.round(x0 + dx * i / n), y = Math.round(y0 + dy * i / n);
            c.fill(x - h, y - h, x - h + t, y - h + t, col);
        }
    }

    /** p = pary x,y w jednostkach ikony (srodek 0,0, mniej wiecej -7..7). */
    private static void path(DrawContext c, float cx, float cy, float k, int t, int col, boolean close, float... p) {
        int n = p.length / 2;
        for (int i = 0; i + 1 < n; i++) {
            seg(c, cx + p[2 * i] * k, cy + p[2 * i + 1] * k, cx + p[2 * i + 2] * k, cy + p[2 * i + 3] * k, t, col);
        }
        if (close && n > 2) {
            seg(c, cx + p[2 * (n - 1)] * k, cy + p[2 * (n - 1) + 1] * k, cx + p[0] * k, cy + p[1] * k, t, col);
        }
    }

    private static void person(DrawContext c, float cx, float cy, float k, int t, int col) {
        path(c, cx, cy, k, t, col, true, -2.2f, -6f, 2.2f, -6f, 2.2f, -1.8f, -2.2f, -1.8f);
        path(c, cx, cy, k, t, col, false, -5.5f, 6f, -4.5f, 2.2f, 4.5f, 2.2f, 5.5f, 6f);
    }

    public static void draw(DrawContext c, int id, float cx, float cy, float k, int col) {
        int t = Math.max(1, Math.round(k * 0.7f));
        switch (id) {
            case PVP -> {
                path(c, cx, cy, k, t, col, true, -5f, -5f, 0f, -7f, 5f, -5f, 5f, 0f, 0f, 6.5f, -5f, 0f);
                path(c, cx, cy, k, t, col, false, -2f, 0f, -0.5f, 1.8f, 2.5f, -2f);
            }
            case HUD -> {
                path(c, cx, cy, k, t, col, true, -6f, -5f, 6f, -5f, 6f, 5f, -6f, 5f);
                path(c, cx, cy, k, t, col, false, -6f, -2f, 6f, -2f);
                path(c, cx, cy, k, t, col, false, -3f, 1.5f, 1.5f, 1.5f);
            }
            case VISUALS -> {
                path(c, cx, cy, k, t, col, false, -6f, 6f, 1f, -1f);
                path(c, cx, cy, k, t, col, false, 3f, -6.5f, 3f, -2f);
                path(c, cx, cy, k, t, col, false, 0.8f, -4.2f, 5.2f, -4.2f);
                path(c, cx, cy, k, t, col, false, 5.5f, 0f, 6.5f, 0f);
            }
            case PLAYER -> {
                path(c, cx, cy, k, t, col, false, -6f, -4.5f, -1.5f, 0f, -6f, 4.5f);
                path(c, cx, cy, k, t, col, false, 0f, -4.5f, 4.5f, 0f, 0f, 4.5f);
            }
            case MISC -> {
                path(c, cx, cy, k, t, col, false, -6f, -4f, 6f, -4f);
                path(c, cx, cy, k, t, col, false, -6f, 0f, 6f, 0f);
                path(c, cx, cy, k, t, col, false, -6f, 4f, 6f, 4f);
            }
            case WARDROBE -> person(c, cx, cy, k, t, col);
            case SEARCH -> {
                float[] o = new float[16];
                for (int a = 0; a < 8; a++) {
                    double ang = Math.toRadians(a * 45.0);
                    o[a * 2] = (float) (-1.5 + 4.5 * Math.cos(ang));
                    o[a * 2 + 1] = (float) (-1.5 + 4.5 * Math.sin(ang));
                }
                path(c, cx, cy, k, t, col, true, o);
                path(c, cx, cy, k, t, col, false, 2f, 2f, 6f, 6f);
            }
            case FRIENDS -> {
                person(c, cx - 3.2f * k, cy, k * 0.72f, t, col);
                person(c, cx + 3.2f * k, cy, k * 0.72f, t, col);
            }
            case SETTINGS -> {
                path(c, cx, cy, k, t, col, true, -6f, -1f, 0f, -4.5f, 6f, -1f, 0f, 2.5f);
                path(c, cx, cy, k, t, col, false, -6f, 2f, 0f, 5.5f, 6f, 2f);
            }
            default -> { }
        }
    }
}
