package dev.slotmine.hud;

import net.minecraft.client.gui.DrawContext;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Fuente arcade 5x7 dibujada con rectángulos: sirve para títulos gigantes con degradado, contorno y brillo. */
public final class BitFont {
    private static final Map<Character, String[]> G = new HashMap<>();

    private static void def(char c, String... rows) { G.put(c, rows); }

    static {
        def('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        def('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        def('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        def('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        def('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        def('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        def('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###.");
        def('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        def('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
        def('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..");
        def('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        def('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        def('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        def('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        def('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        def('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        def('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        def('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        def('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        def('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        def('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        def('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        def('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
        def('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        def('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        def('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        def('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        def('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        def('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        def('3', "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###.");
        def('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        def('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        def('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.");
        def('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        def('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        def('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..");
        def('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        def('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        def('.', ".....", ".....", ".....", ".....", ".....", ".##..", ".##..");
        def(',', ".....", ".....", ".....", ".....", ".##..", "..#..", ".#...");
        def('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..");
        def(':', ".....", ".##..", ".##..", ".....", ".##..", ".##..", ".....");
        def('x', ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", ".....");
        def(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....");
    }

    private BitFont() {}

    public static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    public static int width(String text, int scale) {
        String t = norm(text);
        return t.isEmpty() ? 0 : t.length() * 6 * scale - scale;
    }

    public static int height(int scale) { return 7 * scale; }

    private static int lerp(int a, int b, double t) {
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Dibuja el texto con degradado vertical de `top` a `bottom`. */
    public static void draw(DrawContext ctx, String text, int x, int y, int scale, int top, int bottom) {
        String t = norm(text);
        int cx = x;
        for (int i = 0; i < t.length(); i++) {
            String[] rows = G.get(t.charAt(i));
            if (rows != null) {
                for (int gy = 0; gy < 7; gy++) {
                    int col = lerp(top, bottom, gy / 6.0);
                    String row = rows[gy];
                    for (int gx = 0; gx < 5; gx++) {
                        if (row.charAt(gx) == '#') ctx.fill(cx + gx * scale, y + gy * scale, cx + (gx + 1) * scale, y + (gy + 1) * scale, col);
                    }
                }
            }
            cx += 6 * scale;
        }
    }

    /** Contorno y brillo: 4 copias desplazadas de un solo color. */
    public static void halo(DrawContext ctx, String text, int x, int y, int scale, int off, int color) {
        draw(ctx, text, x - off, y, scale, color, color);
        draw(ctx, text, x + off, y, scale, color, color);
        draw(ctx, text, x, y - off, scale, color, color);
        draw(ctx, text, x, y + off, scale, color, color);
    }
}
