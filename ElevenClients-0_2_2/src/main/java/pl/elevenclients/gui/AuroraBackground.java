package pl.elevenclients.gui;

import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/** Tlo w stylu Aurory: migoczace gwiazdy, pionowe zaslony zorzy, mgla i faliste gory. Tylko fill/fillGradient. */
public final class AuroraBackground {
    private static final int STARS = 110, CURT = 48;
    private static final float[] sx = new float[STARS], sy = new float[STARS], sa = new float[STARS],
            ss = new float[STARS], sp = new float[STARS];
    private static final float[] cInt = new float[CURT], cPh = new float[CURT];

    static {
        Random r = new Random(5);
        for (int i = 0; i < STARS; i++) {
            sx[i] = r.nextFloat();
            sy[i] = r.nextFloat() * 0.66f;
            sa[i] = 0.2f + r.nextFloat() * 0.5f;
            ss[i] = 0.6f + r.nextFloat() * 2f;
            sp[i] = r.nextFloat() * 6.28f;
        }
        for (int i = 0; i < CURT; i++) {
            cInt[i] = 0.35f + r.nextFloat() * 0.65f;
            cPh[i] = r.nextFloat() * 6.28f;
        }
    }

    /** Warianty nieba: 0 = Mono, 1 = Glacier, 2 = Dusk. */
    public static final String[] SKY_NAMES = {"Mono", "Glacier", "Dusk"};
    public static int sky = 0;
    private static final int[] CURTAIN = {0xCDCDDC, 0x5CE0C8, 0xA878E8};
    private static final float[] BOOST = {1f, 1.9f, 1.9f};
    private static final int[][] GRAD = {
            {0xFF040406, 0xFF08080B, 0xFF0D0D10, 0xFF050507},
            {0xFF03080C, 0xFF061018, 0xFF0A1820, 0xFF04090C},
            {0xFF08040C, 0xFF0E0618, 0xFF150A20, 0xFF07040B}
    };

    private AuroraBackground() {}

    public static void cycleSky() { sky = (sky + 1) % SKY_NAMES.length; }

    /* Aktualny (plynnie przechodzacy do docelowego) stan kolorow nieba. */
    private static final float[] cc = {205, 205, 220};
    private static final float[][] cg4 = new float[4][3];
    private static float cBoost = 1f;
    private static boolean skyInit;
    private static long lastSkyNs;

    private static void stepSky() {
        long n = System.nanoTime();
        float dt = lastSkyNs == 0 ? 0.016f : Math.min(0.1f, (n - lastSkyNs) / 1e9f);
        lastSkyNs = n;
        float k = skyInit ? Math.min(1f, dt * 2.6f) : 1f;
        skyInit = true;
        int tc = CURTAIN[sky];
        cc[0] += (((tc >> 16) & 255) - cc[0]) * k;
        cc[1] += (((tc >> 8) & 255) - cc[1]) * k;
        cc[2] += ((tc & 255) - cc[2]) * k;
        cBoost += (BOOST[sky] - cBoost) * k;
        for (int q = 0; q < 4; q++) {
            int g = GRAD[sky][q];
            cg4[q][0] += (((g >> 16) & 255) - cg4[q][0]) * k;
            cg4[q][1] += (((g >> 8) & 255) - cg4[q][1]) * k;
            cg4[q][2] += ((g & 255) - cg4[q][2]) * k;
        }
    }

    private static int gcol(int q) {
        return 0xFF000000 | (Math.round(cg4[q][0]) << 16) | (Math.round(cg4[q][1]) << 8) | Math.round(cg4[q][2]);
    }

    private static int col(float a, int r, int g, int b) {
        return Draw.argb(Math.max(0f, Math.min(1f, a)), r, g, b);
    }

    /** t = czas w sekundach, k = przezroczystosc calosci (0..1). */
    public static void draw(DrawContext c, int w, int h, float t, float k) {
        stepSky();
        int cr = Math.round(cc[0]), cg = Math.round(cc[1]), cb = Math.round(cc[2]);
        float boost = cBoost;
        c.fillGradient(0, 0, w, (int) (h * 0.45f), gcol(0), gcol(1));
        c.fillGradient(0, (int) (h * 0.45f), w, (int) (h * 0.7f), gcol(1), gcol(2));
        c.fillGradient(0, (int) (h * 0.7f), w, h, gcol(2), gcol(3));

        for (int i = 0; i < STARS; i++) {
            float al = sa[i] * (0.55f + 0.45f * (float) Math.sin(t * ss[i] + sp[i])) * k;
            int x = Math.round(sx[i] * w), y = Math.round(sy[i] * h);
            int z = sa[i] > 0.65f ? 2 : 1;
            c.fill(x, y, x + z, y + z, col(al, 225, 230, 245));
        }

        int y0 = (int) (h * 0.12f), ym = (int) (h * 0.42f), yb = (int) (h * 0.66f);
        float sw = w / (float) CURT;
        for (int i = 0; i < CURT; i++) {
            float cx = (i + 0.5f) * sw + (float) Math.sin(t * 0.15f + i * 0.7f) * sw * 0.8f;
            float side = Math.abs(cx - w / 2f) / (w / 2f);
            float al = 0.16f * boost * cInt[i] * (0.55f + 0.45f * (float) Math.sin(t * 0.3f + cPh[i])) * (0.25f + 0.75f * side) * k;
            int x1 = Math.round(cx - sw * 0.7f), x2 = Math.round(cx + sw * 0.7f);
            c.fillGradient(x1, y0, x2, ym, col(0f, cr, cg, cb), col(al, cr, cg, cb));
            c.fillGradient(x1, ym, x2, yb, col(al, cr, cg, cb), col(0f, cr, cg, cb));
        }

        float fog = 0.06f + 0.03f * (float) Math.sin(t * 0.2f);
        c.fillGradient(0, (int) (h * 0.55f), w, (int) (h * 0.74f), col(0f, cr, cg, cb), col(fog * k, cr, cg, cb));

        mountain(c, w, h, t, 0.74f, 0.035f, 0.012f, 0.9f, 0xD918181C, k);
        mountain(c, w, h, t, 0.82f, 0.030f, 0.016f, 1.3f, 0xFF0E0E11, k);
        mountain(c, w, h, t, 0.91f, 0.025f, 0.020f, 1.7f, 0xFF060607, k);
    }

    private static void mountain(DrawContext c, int w, int h, float t, float base, float amp, float speed,
                                 float freq, int color, float k) {
        int fc = Draw.fade(color, k);
        double ph = t * speed;
        for (int x = 0; x < w; x += 3) {
            double u = x / (double) w;
            double v = Math.sin(u * 5.0 * freq + ph * 3) * 0.6 + Math.sin(u * 11.0 * freq + ph * 5 + 1.7) * 0.4;
            int y = (int) (h * (base + amp * v));
            c.fill(x, y, x + 3, h, fc);
        }
    }

    private static void row(DrawContext c, int x, int y, float u, float x1, float x2, int row, float k) {
        int color = Draw.fade(Draw.lerpColor(0xFFF4F4F8, 0xFF8A8A96, row / 28f), k);
        c.fill(x + Math.round(x1 * u), y + Math.round(row * u), x + Math.round(x2 * u), y + Math.round((row + 1) * u), color);
    }

    /** Znak "E" z pochylonymi belkami (siatka 28x28, u = rozmiar jednostki w px, k = przezroczystosc). */
    public static void mark(DrawContext c, int x, int y, float u, float k) {
        for (int r = 3; r < 25; r++) row(c, x, y, u, 4, 9, r, k);
        for (int r = 3; r < 8; r++) row(c, x, y, u, 11, 24 - (r - 3) * 0.6f, r, k);
        for (int r = 11; r < 16; r++) row(c, x, y, u, 11, 21 - (r - 11) * 0.6f, r, k);
        for (int r = 19; r < 25; r++) row(c, x, y, u, 11, 24 - (r - 19) * 0.6f, r, k);
    }
}
