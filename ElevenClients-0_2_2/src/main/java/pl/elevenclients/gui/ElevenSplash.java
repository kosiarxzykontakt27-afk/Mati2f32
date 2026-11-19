package pl.elevenclients.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

/** Ekran ladowania gry (zamiast czerwonego Mojanga). Bez tekstu i tekstur - dziala zanim zaladuja sie zasoby. */
public final class ElevenSplash {
    private static long start = -1;

    private ElevenSplash() {}

    private static float ease(float p) {
        p = Math.max(0f, Math.min(1f, p));
        float q = 1f - p;
        return 1f - q * q * q;
    }

    public static void draw(DrawContext c) {
        long now = Util.getMeasuringTimeMs();
        if (start < 0) start = now;
        float t = (now - start) / 1000f;
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();

        c.fill(0, 0, w, h, 0xFF000000);
        AuroraBackground.draw(c, w, h, t, ease(t / 1.2f));

        float in = ease((t - 0.3f) / 0.9f);
        float u = Math.max(1.5f, h / 110f);
        int mx = Math.round(w / 2f - 14 * u);
        int my = Math.round(h * 0.40f - 14 * u + (1f - in) * -12f);
        AuroraBackground.mark(c, mx, my, u, in);

        int bw = Math.max(60, w / 4), bh = Math.max(1, h / 200);
        int bx = w / 2 - bw / 2, by = (int) (h * 0.58f);
        c.fill(bx, by, bx + bw, by + bh, Draw.fade(0x33FFFFFF, in));
        float seg = bw * 0.35f;
        float pos = ((t * 0.7f) % 1.35f) * bw - seg * 0.2f;
        int sx1 = Math.max(bx, bx + (int) pos), sx2 = Math.min(bx + bw, bx + (int) (pos + seg));
        if (sx2 > sx1) c.fill(sx1, by, sx2, by + bh, Draw.fade(0xFFE9E9EF, in));
    }
}
