package pl.elevenclients.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import pl.elevenclients.ElevenClients;
import pl.elevenclients.config.ElevenConfig;

import java.util.Random;

public class ElevenTitleScreen extends Screen {
    private static final int BW = 270, BH = 30;
    private static final int STARS = 90;

    private final float[] wa = new float[STARS], wr = new float[STARS], wv = new float[STARS];
    private final Random wRand = new Random(11);

    private final String[] labels = {
            "Tryb jednoosobowy", "Tryb wieloosobowy", "ElevenClients", "Konto",
            "Ustawienia...", "Wyjd\u017a z gry"
    };
    /** Prostokaty przyciskow w lokalnym ukladzie (0 = srodek menu): x1, x2, y. */
    private final int[] bx1 = {-135, -135, -135, -135, -135, 4};
    private final int[] bx2 = {135, 135, 135, 135, -4, 135};
    private final int[] ys = {-70, -34, 2, 38, 82, 82};

    private static final String[] NEWS = {
            "Co nowego",
            "Beta 0.2.0",
            "",
            "Nowe",
            "\u2022 Menu mod\u00f3w: karuzela kategorii",
            "  PvP, HUD, Visuals, Player, Misc",
            "\u2022 Wyszukiwarka modu\u0142\u00f3w",
            "\u2022 Trzy nieba t\u0142a: Mono,",
            "  Glacier i Dusk",
            "\u2022 Szklany styl paneli",
            "\u2022 P\u0142ynne animacje i hover"
    };

    private static boolean introPlayed;
    private static final float INTRO_START = 0.6f, INTRO_END = 2.2f;
    private final boolean intro;
    private float openT = -1f;
    private float lastT = -1f;
    private float curA = 1f;

    public ElevenTitleScreen() {
        super(Text.literal("ElevenClients"));
        this.intro = !introPlayed;
        introPlayed = true;
        for (int i = 0; i < STARS; i++) {
            wa[i] = wRand.nextFloat() * 6.2832f;
            wr[i] = 2 + wRand.nextFloat() * 360;
            wv[i] = 0.6f + wRand.nextFloat() * 1.2f;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    private int accent() { return 0xFF000000 | ElevenConfig.accent; }
    private int f(int color) { return Draw.fade(color, curA); }
    private float s() { return ElevenConfig.guiScale; }
    private double lx(double mx, double ax) { return (mx - ax) / s(); }
    private double ly(double my, double ay) { return (my - ay) / s(); }
    private float menuAx() { return width / 2f; }
    private float menuAy() { return height * 0.54f; }

    private boolean over(double lx, double ly, int x1, int y1, int x2, int y2) {
        return lx >= x1 && lx < x2 && ly >= y1 && ly < y2;
    }

    /** Animacja wjazdu: 0 -> 1 (ease-out) po opoznieniu delay. */
    private float ease(float tt, float delay) {
        float p = Math.max(0f, Math.min(1f, (tt - delay) / 0.7f));
        float q = 1f - p;
        return 1f - q * q * q;
    }

    /* ---------- render ---------- */

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        float now = Util.getMeasuringTimeMs() / 1000f;
        if (openT < 0) openT = now;
        float tt = now - openT - (intro ? INTRO_START : 0f);
        tickHover();

        drawBackground(ctx, now, ease(tt, 0f));
        drawLogo(ctx, ease(tt, 0.10f));
        drawButtons(ctx, mx, my, tt);
        drawSkin(ctx, mx, my, now, ease(tt, 0.35f));
        drawNews(ctx, ease(tt, 0.60f));
        drawWindowButtons(ctx, mx, my, ease(tt, 0.20f));
        drawSkyButton(ctx, mx, my, ease(tt, 0.30f));
        drawVersions(ctx, ease(tt, 0.80f));
        if (introActive(now)) drawIntro(ctx, now - openT);
    }

    private boolean introActive(float now) {
        return intro && (openT < 0 || now - openT < INTRO_END);
    }

    /** Intro jak w nagraniu: czern z napisem na dole, spod niej wyplywa menu, napis zjezdza w dol i gasnie. */
    private void drawIntro(DrawContext c, float e) {
        float veil = e < 0.45f ? 1f : Math.max(0f, 1f - (e - 0.45f) / 1.0f);
        if (veil > 0f) c.fill(0, 0, width, height, Draw.argb(veil, 0, 0, 0));
        float in = Math.min(1f, e / 0.4f);
        float out = e < 1.5f ? 0f : Math.min(1f, (e - 1.5f) / 0.6f);
        float ta = in * (1f - out);
        if (ta <= 0.02f) return;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(width / 2f, height * 0.80f + out * out * 22f * s(), 0);
        m.scale(s() * 2.2f, s() * 2.2f, 1f);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("Przedstawiamy..."), 0, 0, Draw.fade(0xFFFFFFFF, ta));
        m.pop();
    }

    /* ---------- animowany hover ---------- */
    private final float[] hv = new float[6];
    private float hvSky, hvWard, hvStep = 0.2f;
    private long lastHvNs;

    private void tickHover() {
        long n = System.nanoTime();
        float dt = lastHvNs == 0 ? 0.016f : Math.min(0.1f, (n - lastHvNs) / 1e9f);
        lastHvNs = n;
        hvStep = Math.min(1f, dt * 14f);
    }

    private float ap(float cur, boolean on) { return cur + ((on ? 1f : 0f) - cur) * hvStep; }

    /** Szklany przycisk; k = 0..1 plynne przejscie do jasnego wypelnienia z ciemnym tekstem (jak w nagraniu). */
    private void glassBtn(DrawContext c, int x1, int y1, int x2, int y2, int r, float k, String label) {
        Draw.glass(c, x1, y1, x2, y2, r, f(Draw.lerpColor(0x16FFFFFF, 0xE6E4E4EC, k)), f(Draw.lerpColor(0x55FFFFFF, 0xFFFFFFFF, k)));
        Draw.textC(c, textRenderer, label, (x1 + x2) / 2f, y1 + (y2 - y1 - 8) / 2f, f(Draw.lerpColor(0xFFE0E0E8, 0xFF16161C, k)), 1f);
    }

    private void drawBackground(DrawContext c, float t, float bgA) {
        AuroraBackground.draw(c, width, height, t, bgA);
    }

    private void drawLogo(DrawContext c, float p) {
        drawLogoAt(c, p, height * 0.14f - (1 - p) * 26f);
    }

    private void drawLogoAt(DrawContext c, float p, float y) {
        if (p < 0.05f) return;
        curA = p;
        String name = "ElevenClients";
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(width / 2f, y, 0);
        m.scale(s(), s(), 1f);
        int tw = textRenderer.getWidth(name) * 4;
        int total = 40 + tw;
        AuroraBackground.mark(c, -total / 2, 2, 1.1f, p);
        m.push();
        m.translate(-total / 2f + 40, 0, 0);
        m.scale(4f, 4f, 1f);
        c.drawText(textRenderer, name, 1, 1, f(0xFF5A5A63), false);
        c.drawText(textRenderer, name, 0, 0, f(0xFFF4F4F8), false);
        m.pop();
        m.pop();
    }

    private void drawButtons(DrawContext c, int mx, int my, float tt) {
        MatrixStack m = c.getMatrices();
        double lmx = lx(mx, menuAx()), lmy = ly(my, menuAy());
        m.push();
        m.translate(menuAx(), menuAy(), 0);
        m.scale(s(), s(), 1f);
        for (int i = 0; i < labels.length; i++) {
            float p = ease(tt, 0.25f + 0.07f * i);
            if (p < 0.05f) continue;
            curA = p;
            int y = ys[i];
            int off = Math.round((1 - p) * 16f);
            boolean hov = p > 0.9f && over(lmx, lmy, bx1[i], y, bx2[i], y + BH);
            hv[i] = ap(hv[i], hov);
            glassBtn(c, bx1[i], y + off, bx2[i], y + BH + off, 9, hv[i], labels[i]);
        }
        if (!IasBridge.status.isEmpty()) {
            curA = 1f;
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(IasBridge.status), 0, 122, f(0xFFEF4444));
        }
        m.pop();
    }

    private float skinAx() { return Math.min(width - 56 * s() - 8, width / 2f + 300 * s()); }

    private void drawSkin(DrawContext c, int mx, int my, float t, float p) {
        if (p < 0.05f) return;
        curA = p;
        MinecraftClient mc = MinecraftClient.getInstance();
        float ax = skinAx() + (1 - p) * 30f, ay = menuAy();
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ax, ay, 0);
        m.scale(s(), s(), 1f);

        String nick = mc.getSession().getUsername();
        int tw = textRenderer.getWidth(nick) + 24;
        Draw.glass(c, -tw / 2, -104, tw / 2, -86, 9, f(0x1AFFFFFF), f(0x66FFFFFF));
        c.fill(-tw / 2 + 7, -97, -tw / 2 + 10, -94, f(0xFF22C55E));
        c.drawText(textRenderer, nick, -tw / 2 + 14, -99, f(0xFFEEF3FF), false);

        Draw.round(c, -42, 102, 42, 110, 4, f(0x99000000));

        try {
            Identifier tex = SkinUtil.skin(nick);
            int oy = -72 + (int) Math.round(Math.sin(t * 2f) * 2);
            part(c, tex, -20, oy, 8, 8, 8, 8);
            part(c, tex, -20, oy + 40, 20, 20, 8, 12);
            part(c, tex, -40, oy + 40, 44, 20, 4, 12);
            part(c, tex, 20, oy + 40, 36, 52, 4, 12);
            part(c, tex, -20, oy + 100, 4, 20, 4, 12);
            part(c, tex, 0, oy + 100, 20, 52, 4, 12);
            part(c, tex, -20, oy, 40, 8, 8, 8);
            part(c, tex, -20, oy + 40, 20, 36, 8, 12);
            part(c, tex, -40, oy + 40, 44, 36, 4, 12);
            part(c, tex, 20, oy + 40, 52, 52, 4, 12);
            part(c, tex, -20, oy + 100, 4, 36, 4, 12);
            part(c, tex, 0, oy + 100, 4, 52, 4, 12);
        } catch (Throwable ignored) { }

        boolean hov = over(lx(mx, ax), ly(my, ay), -42, 114, 42, 136);
        hvWard = ap(hvWard, hov);
        glassBtn(c, -42, 114, 42, 136, 8, hvWard, "Garderoba");
        m.pop();
    }

    private void part(DrawContext c, Identifier tex, int x, int y, int u, int v, int w, int h) {
        final int k = 5;
        c.drawTexture(RenderLayer::getGuiTextured, tex, x, y, (float) u, (float) v, w * k, h * k, w, h, 64, 64);
    }

    private void drawWindowButtons(DrawContext c, int mx, int my, float p) {
        if (p < 0.05f) return;
        curA = p;
        MatrixStack m = c.getMatrices();
        float ax = width - 8, ay = 8 - (1 - p) * 20f;
        double lmx = lx(mx, width - 8), lmy = ly(my, 8);
        m.push();
        m.translate(ax, ay, 0);
        m.scale(s(), s(), 1f);
        String[] icons = {"\u2013", "\u25A1", "\u00D7"};
        for (int i = 0; i < 3; i++) {
            int x2 = -(2 - i) * 31, x1 = x2 - 26;
            boolean hov = over(lmx, lmy, x1, 0, x1 + 26, 22);
            int border = hov ? (i == 2 ? 0xFFEF4444 : 0x99FFFFFF) : 0x55FFFFFF;
            Draw.glass(c, x1, 0, x1 + 26, 22, 7, f(hov ? 0x30FFFFFF : 0x16FFFFFF), f(border));
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(icons[i]), x1 + 13, 7, f(hov ? 0xFFFFFFFF : 0xFFB5B5C0));
        }
        m.pop();
    }

    private void drawNews(DrawContext c, float p) {
        if (p < 0.05f) return;
        float x = Math.max(12f, width / 2f - 325 * s());
        if (x + 170 * s() > width / 2f - 145 * s()) return;
        curA = p;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x - (1 - p) * 20f, menuAy() - 36 * s(), 0);
        m.scale(s(), s(), 1f);
        for (int i = 0; i < NEWS.length; i++) {
            int col = i == 0 ? 0xFFE9E9EF : (i == 3 ? 0xFFB5B5C0 : 0xFF8D8D99);
            if (i == 1) col = 0xFFB5B5C0;
            Draw.text(c, textRenderer, NEWS[i], 0, i * 11, f(col), 1f);
        }
        m.pop();
    }

    private int skyWidth() {
        return 20 + textRenderer.getWidth("Niebo: " + AuroraBackground.SKY_NAMES[AuroraBackground.sky]);
    }

    private void drawSkyButton(DrawContext c, int mx, int my, float p) {
        if (p < 0.05f) return;
        curA = p;
        String label = "Niebo: " + AuroraBackground.SKY_NAMES[AuroraBackground.sky];
        int pw = skyWidth();
        boolean hov = over(lx(mx, 8), ly(my, 8), 0, 0, pw, 22);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(8 - (1 - p) * 30f, 8, 0);
        m.scale(s(), s(), 1f);
        hvSky = ap(hvSky, hov);
        glassBtn(c, 0, 0, pw, 22, 8, hvSky, label);
        m.pop();
    }

    private void drawVersions(DrawContext c, float p) {
        if (p < 0.05f) return;
        curA = p;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(8, height - 8, 0);
        m.scale(s(), s(), 1f);
        c.drawText(textRenderer, ElevenClients.NAME + " " + ElevenClients.VERSION, 0, -22, f(0xFFEEF3FF), true);
        c.drawText(textRenderer, "Fabric \u00B7 " + ElevenClients.modules.getAll().size() + " modu\u0142\u00F3w", 0, -11, f(0xFF8D8D99), true);
        m.pop();
        m.push();
        m.translate(width - 8, height - 8, 0);
        m.scale(s(), s(), 1f);
        String v = "Minecraft 1.21.4 (Fabric)";
        c.drawText(textRenderer, v, -textRenderer.getWidth(v), -11, f(0xFF8D8D99), true);
        m.pop();
    }

    /* ---------- klikniecia ---------- */

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (introActive(Util.getMeasuringTimeMs() / 1000f)) return true;
        if (button != 0) return super.mouseClicked(mx, my, button);
        MinecraftClient mc = MinecraftClient.getInstance();

        if (over(lx(mx, 8), ly(my, 8), 0, 0, skyWidth(), 22)) {
            AuroraBackground.cycleSky();
            return true;
        }

        if (over(lx(mx, skinAx()), ly(my, menuAy()), -42, 114, 42, 136)) {
            mc.setScreen(new ClickGuiScreen(this, 3));
            return true;
        }

        double lmx = lx(mx, menuAx()), lmy = ly(my, menuAy());
        for (int i = 0; i < labels.length; i++) {
            if (over(lmx, lmy, bx1[i], ys[i], bx2[i], ys[i] + BH)) {
                switch (i) {
                    case 0 -> mc.setScreen(new SelectWorldScreen(this));
                    case 1 -> mc.setScreen(new MultiplayerScreen(this));
                    case 2 -> mc.setScreen(new ModsHubScreen(this));
                    case 3 -> IasBridge.open(this);
                    case 4 -> mc.setScreen(new OptionsScreen(this, mc.options));
                    case 5 -> mc.scheduleStop();
                }
                return true;
            }
        }

        double wx = lx(mx, width - 8), wy = ly(my, 8);
        for (int i = 0; i < 3; i++) {
            int x2 = -(2 - i) * 31, x1 = x2 - 26;
            if (over(wx, wy, x1, 0, x1 + 26, 22)) {
                long h = mc.getWindow().getHandle();
                if (i == 0) GLFW.glfwIconifyWindow(h);
                else if (i == 1) {
                    if (GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) GLFW.glfwRestoreWindow(h);
                    else GLFW.glfwMaximizeWindow(h);
                } else mc.scheduleStop();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
