package pl.elevenclients.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import pl.elevenclients.gui.Draw;

import java.util.ArrayList;
import java.util.List;

/** Male powiadomienia w prawym dolnym rogu: wjezdzaja z prawej i znikaja po chwili. */
public final class Notify {
    private static final class Item { String text; boolean on; long t0; }
    private static final List<Item> LIST = new ArrayList<>();
    private static final long LIFE = 2400, FADE = 300;

    private Notify() {}

    public static void push(String name, boolean on) {
        if (MinecraftClient.getInstance().player == null) return;
        Item n = new Item();
        n.text = name + (on ? " ON" : " OFF");
        n.on = on;
        n.t0 = System.currentTimeMillis();
        LIST.add(n);
        while (LIST.size() > 4) LIST.remove(0);
    }

    public static void render(DrawContext c) {
        if (LIST.isEmpty()) return;
        long now = System.currentTimeMillis();
        LIST.removeIf(n -> now - n.t0 > LIFE);
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int y = sh - 26;
        for (int i = LIST.size() - 1; i >= 0; i--) {
            Item n = LIST.get(i);
            float age = now - n.t0;
            float in = Math.min(1f, age / 220f);
            in = 1f - (1f - in) * (1f - in) * (1f - in);
            float out = age > LIFE - FADE ? (LIFE - age) / FADE : 1f;
            if (out < 0.1f) continue;
            int w = mc.textRenderer.getWidth(n.text) + 26;
            int x2 = sw - 8 + Math.round((1f - in) * (w + 12));
            int col = n.on ? 0xFF22C55E : 0xFFEF4444;
            Draw.round(c, x2 - w, y, x2, y + 18, 6, Draw.fade(0xE6090E1C, out));
            c.fill(x2 - w + 4, y + 4, x2 - w + 6, y + 14, Draw.fade(col, out));
            c.drawText(mc.textRenderer, n.text, x2 - w + 11, y + 5, Draw.fade(0xFFEEF3FF, out), false);
            y -= 22;
        }
    }
}
