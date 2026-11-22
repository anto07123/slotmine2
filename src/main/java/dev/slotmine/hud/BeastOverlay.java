package dev.slotmine.hud;

import dev.slotmine.core.BigNum;
import dev.slotmine.core.Beasts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.math.BigInteger;
import java.util.Locale;

/**
 * Secuencia a pantalla completa al invocar una bestia: destello, barras de cine, título gigante con "slam",
 * transformación de la pantalla con el estilo de cada bestia, zarpazos/efectos, banda de "DESTROZADOS N BLOQUES"
 * y el pago en grande. Es solo visual: no bloquea el juego.
 */
public final class BeastOverlay {
    public static final long DUR = 4400;

    private Beasts.Beast beast;
    private int idx, blocks, seed;
    private String payoutText = "", buffText = "";
    private long start;

    public boolean active() { return beast != null && System.currentTimeMillis() - start < DUR; }

    public void start(int idx, int blocks, BigInteger payout, String buff) {
        this.idx = idx;
        this.beast = Beasts.ALL.get(idx);
        this.blocks = blocks;
        this.payoutText = "+" + BigNum.formatPrecise(payout);
        this.buffText = buff == null ? "" : buff;
        this.start = System.currentTimeMillis();
        this.seed = (int) (start & 0xFFFF);
    }

    // ------------------------------------------------------------------ utilidades
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static double easeOut(double t) { t = clamp(t); return 1 - (1 - t) * (1 - t) * (1 - t); }
    private static int alpha(int c, double a) {
        int al = (int) Math.max(0, Math.min(255, a * 255));
        return (al << 24) | (c & 0xFFFFFF);
    }
    private static int mix(int a, int b, double t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
    private static int hash(int a, int b) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA6B;
        h ^= h >>> 15; h *= 0x2C1B3C6D; h ^= h >>> 12;
        return h & 0x7fffffff;
    }
    /** 0 -> 1 -> 0: aparece desde `a`, se mantiene y desaparece hacia `b` (ms). */
    private static double bump(long t, long a, long b) {
        return clamp((t - a) / 350.0) * (1 - clamp((t - (b - 600)) / 600.0));
    }

    // ------------------------------------------------------------------ render
    public void render(DrawContext ctx, TextRenderer tr, int sw, int sh) {
        if (beast == null) return;
        long t = System.currentTimeMillis() - start;
        if (t >= DUR) { beast = null; return; }

        double fin = clamp(t / 250.0), fout = 1 - clamp((t - (DUR - 600)) / 600.0), a = fin * fout;
        int color = beast.color();

        // oscurecer y teñir con el color de la bestia
        ctx.fill(0, 0, sw, sh, alpha(0x000000, 0.40 * a));
        ctx.fillGradient(0, 0, sw, sh / 2, alpha(beast.color2(), 0.60 * a), alpha(beast.color2(), 0));
        ctx.fillGradient(0, sh / 2, sw, sh, alpha(beast.color2(), 0), alpha(beast.color2(), 0.60 * a));

        pattern(ctx, t, sw, sh, a);

        // barras de cine
        int bar = (int) (sh * 0.10 * easeOut(t / 400.0) * fout);
        ctx.fill(0, 0, sw, bar, 0xFF000000);
        ctx.fill(0, sh - bar, sw, sh, 0xFF000000);
        ctx.fill(0, bar, sw, bar + 1, alpha(color, a));
        ctx.fill(0, sh - bar - 1, sw, sh - bar, alpha(color, a));

        // destello inicial
        if (t < 350) ctx.fill(0, 0, sw, sh, alpha(mix(color, 0xFFFFFF, 0.6), (1 - t / 350.0) * 0.6));

        title(ctx, tr, t, sw, sh, a);
        banner(ctx, tr, t, sw, sh, fout);
        payout(ctx, tr, t, sw, sh, fout);
    }

    private void title(DrawContext ctx, TextRenderer tr, long t, int sw, int sh, double a) {
        if (t < 150) return;
        String title = beast.name();
        int len = BitFont.norm(title).length();
        int scale = Math.max(2, Math.min(7, (int) (sw * 0.86 / (len * 6.0))));
        double ta = a * clamp((t - 150) / 120.0);
        double slam = 1 - easeOut((t - 150) / 280.0);               // 1 -> 0: empieza enorme y se estampa
        int sc = scale + (int) Math.round(slam * scale * 1.3);
        int w = BitFont.width(title, sc);
        int x = (sw - w) / 2, y = (int) (sh * 0.34 - 3.5 * sc);
        if (t < 700) { // temblor al estamparse
            double k = 1 - (t - 150) / 550.0;
            x += (int) ((hash(seed, (int) (t / 16)) % 7 - 3) * k * 1.5);
            y += (int) ((hash(seed + 7, (int) (t / 16)) % 7 - 3) * k * 1.5);
        }
        BitFont.halo(ctx, title, x, y, sc, sc * 2, alpha(beast.color(), 0.10 * ta));
        BitFont.halo(ctx, title, x, y, sc, sc, alpha(beast.color(), 0.22 * ta));
        BitFont.halo(ctx, title, x, y, sc, Math.max(1, sc / 2), alpha(0x000000, 0.95 * ta));
        BitFont.draw(ctx, title, x, y, sc, alpha(mix(beast.color(), 0xFFFFFF, 0.85), ta), alpha(beast.color(), ta));

        String sub = beast.sub();
        int subW = tr.getWidth(sub);
        ctx.drawText(tr, sub, (sw - subW) / 2, y + 7 * sc + 8, alpha(0xFFFFFF, ta), true);
    }

    private void banner(DrawContext ctx, TextRenderer tr, long t, int sw, int sh, double fout) {
        if (t < 900) return;
        double pe = easeOut((t - 900) / 350.0);
        int bh = 24, by = (int) (sh * 0.60);
        int bx = (int) (-(1 - pe) * sw);
        ctx.fill(bx, by - 1, bx + sw, by + bh + 1, alpha(beast.color(), 0.95 * fout));
        ctx.fillGradient(bx, by, bx + sw, by + bh, alpha(0x0A1230, 0.94 * fout), alpha(0x1B2C5E, 0.94 * fout));
        int count = (int) Math.round(blocks * clamp((t - 1000) / 800.0));
        String l1 = "DESTROZADOS ", num = String.valueOf(count), l2 = " BLOQUES";
        int w = tr.getWidth(l1) + tr.getWidth(num) + tr.getWidth(l2);
        int x = bx + (sw - w) / 2, ty = by + 8;
        ctx.drawText(tr, l1, x, ty, alpha(0xFFFFFF, fout), true);
        ctx.drawText(tr, num, x + tr.getWidth(l1), ty, alpha(beast.color(), fout), true);
        ctx.drawText(tr, l2, x + tr.getWidth(l1) + tr.getWidth(num), ty, alpha(0xFFFFFF, fout), true);
        // remates a los lados
        for (int i = 0; i < 3; i++) {
            ctx.fill(bx + 8 + i * 6, by + 8, bx + 12 + i * 6, by + 16, alpha(beast.color(), (0.9 - i * 0.28) * fout));
            ctx.fill(bx + sw - 12 - i * 6, by + 8, bx + sw - 8 - i * 6, by + 16, alpha(beast.color(), (0.9 - i * 0.28) * fout));
        }
    }

    private void payout(DrawContext ctx, TextRenderer tr, long t, int sw, int sh, double fout) {
        if (t < 1900) return;
        double pp = easeOut((t - 1900) / 320.0);
        int base = sw >= 560 ? 4 : 3;
        int len = payoutText.length();
        int sc = Math.max(2, Math.min(base, (int) (sw * 0.8 / (len * 6.0)))) + (int) Math.round((1 - pp) * 3);
        int w = BitFont.width(payoutText, sc);
        int x = (sw - w) / 2, y = (int) (sh * 0.60) + 24 + 12;
        double al = fout * clamp((t - 1900) / 120.0);
        BitFont.halo(ctx, payoutText, x, y, sc, Math.max(1, sc / 2), alpha(0x000000, 0.9 * al));
        BitFont.draw(ctx, payoutText, x, y, sc, alpha(0xFFF4B0, al), alpha(0xFFB300, al));
        // bendición única de la bestia
        if (t >= 2300 && !buffText.isEmpty()) {
            double ba = fout * clamp((t - 2300) / 250.0);
            int bw = tr.getWidth(buffText) + 14;
            int bx = (sw - bw) / 2, by = y + 7 * sc + 8;
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + 15, alpha(beast.color(), 0.9 * ba));
            ctx.fill(bx, by, bx + bw, by + 14, alpha(0x0A0E18, 0.92 * ba));
            ctx.drawText(tr, buffText, bx + 7, by + 3, alpha(0xFFFFFF, ba), true);
        }
    }

    // ------------------------------------------------------------------ transformación de pantalla por bestia
    private void pattern(DrawContext ctx, long t, int sw, int sh, double a) {
        double pi = a * bump(t, 300, DUR);
        switch (idx) {
            case 0 -> tiger(ctx, t, sw, sh, pi);
            case 1 -> dragon(ctx, t, sw, sh, pi);
            case 2 -> phoenix(ctx, t, sw, sh, pi);
            case 3 -> kraken(ctx, t, sw, sh, pi);
            case 4 -> fenrir(ctx, t, sw, sh, pi);
            default -> monkey(ctx, t, sw, sh, pi);
        }
    }

    /** Línea que se va dibujando de (x0,y0) a (x1,y1): brillo de color + núcleo blanco, más fina en las puntas. */
    private void slash(DrawContext ctx, int x0, int y0, int x1, int y1, double progress, double fade, int color, int maxThick) {
        if (progress <= 0 || fade <= 0) return;
        double len = Math.hypot(x1 - x0, y1 - y0);
        int n = Math.max(1, (int) (len / 3));
        int upto = (int) (n * clamp(progress));
        for (int k = 0; k <= upto; k++) {
            double f = k / (double) n;
            int px = (int) (x0 + (x1 - x0) * f), py = (int) (y0 + (y1 - y0) * f);
            int th = Math.max(1, (int) (maxThick * Math.pow(Math.sin(Math.PI * Math.min(1, f)), 0.7)));
            int g = th + 4;
            ctx.fill(px - g / 2, py - g / 2, px + (g + 1) / 2, py + (g + 1) / 2, alpha(color, 0.30 * fade));
            ctx.fill(px - th / 2, py - th / 2, px + (th + 1) / 2, py + (th + 1) / 2, alpha(mix(color, 0xFFFFFF, 0.8), 0.95 * fade));
        }
    }

    private void claws(DrawContext ctx, long t, int sw, int sh, int color, int count, long t0, boolean flip) {
        for (int i = 0; i < count; i++) {
            long ts = t0 + i * 210L;
            double prog = (t - ts) / 220.0;
            double fade = 1 - clamp((t - ts - 220) / 700.0);
            int xo = (int) (sw * (0.12 + 0.17 * i));
            int xa = flip ? sw - xo : xo, xb = flip ? sw - xo - (int) (sw * 0.45) : xo + (int) (sw * 0.45);
            slash(ctx, xa, (int) (sh * 0.08), xb, (int) (sh * 0.92), prog, fade, color, 7);
        }
    }

    private void tiger(DrawContext ctx, long t, int sw, int sh, double pi) {
        // piel de tigre: franjas verticales onduladas
        int cell = 28, cols = sw / cell + 2, seg = Math.max(14, sh / 18);
        for (int c = 0; c < cols; c++) {
            int h = hash(seed, c);
            boolean dark = ((h >> 8) & 3) != 0;
            int w = (int) (cell * (0.30 + 0.45 * ((h & 255) / 255.0)));
            int x0 = c * cell + ((h >> 10) & 7) - 4;
            int col = dark ? 0x140A02 : 0xFFA726;
            double al = (dark ? 0.50 : 0.34) * pi;
            for (int y = 0; y < sh; y += seg) {
                int off = (int) (Math.sin(y * 0.025 + c * 1.7 + t / 500.0) * 6);
                ctx.fill(x0 + off, y, x0 + off + w, Math.min(sh, y + seg), alpha(col, al));
            }
        }
        claws(ctx, t, sw, sh, 0xFFFF9A2E, 3, 1000, false);
    }

    private void dragon(DrawContext ctx, long t, int sw, int sh, double pi) {
        for (int r = 0; r < 3; r++) {
            int baseY = (int) (sh * (0.22 + 0.24 * r));
            for (int x = 0; x < sw; x += 6) {
                int y = baseY + (int) (Math.sin(x * 0.02 + t / 300.0 + r * 2) * sh * 0.06);
                ctx.fill(x, y - 4, x + 6, y + 5, alpha(0x1FA85A, 0.50 * pi));
                ctx.fill(x, y - 1, x + 6, y + 2, alpha(0xB8FFD0, 0.55 * pi));
            }
        }
        // aliento de fuego de izquierda a derecha
        if (t > 1200 && t < 2600) {
            double p = easeOut((t - 1200) / 700.0), fd = 1 - clamp((t - 1900) / 700.0);
            int head = (int) (sw * p), cy = (int) (sh * 0.47);
            ctx.fillGradient(0, cy - 22, head, cy, alpha(0xFF6A00, 0), alpha(0xFFE066, 0.85 * fd));
            ctx.fillGradient(0, cy, head, cy + 22, alpha(0xFFE066, 0.85 * fd), alpha(0xFF6A00, 0));
            ctx.fill(Math.max(0, head - 14), cy - 12, head, cy + 12, alpha(0xFFFFFF, 0.9 * fd));
        }
    }

    private void phoenix(DrawContext ctx, long t, int sw, int sh, double pi) {
        for (int x = 0; x < sw; x += 12) {
            int h = hash(seed, x / 12);
            double wob = 0.5 + 0.5 * Math.sin(t / 180.0 + x * 0.3);
            int fh = (int) (sh * (0.20 + 0.50 * ((h & 255) / 255.0) * (0.6 + 0.4 * wob)) * clamp(pi * 1.5));
            ctx.fillGradient(x, sh - fh, x + 10, sh, alpha(0xFF3D00, 0), alpha(0xFFC24D, 0.80 * pi));
        }
        for (int i = 0; i < 36; i++) { // ascuas
            int h = hash(seed + 3, i);
            int x = h % Math.max(1, sw);
            int y = sh - (int) ((t * 0.12 + (h >> 8) % sh) % sh);
            ctx.fill(x, y, x + 2, y + 2, alpha(0xFFD27A, 0.8 * pi));
        }
    }

    private void kraken(DrawContext ctx, long t, int sw, int sh, double pi) {
        for (int r = 0; r < 4; r++) {
            int baseY = (int) (sh * (0.25 + 0.18 * r));
            for (int x = 0; x < sw; x += 5) {
                int y = baseY + (int) (Math.sin(x * 0.03 - t / 260.0 + r * 1.3) * 7);
                ctx.fill(x, y, x + 5, y + 3, alpha(0x7FD0FF, 0.55 * pi));
            }
        }
        for (int s = 0; s < 2; s++) { // tentáculos desde las esquinas
            double grow = easeOut((t - 900 - s * 250) / 900.0) * (1 - clamp((t - 2800) / 700.0));
            int baseX = s == 0 ? (int) (sw * 0.12) : (int) (sw * 0.88);
            int n = 46;
            for (int k = 0; k < n * grow; k++) {
                double f = k / (double) n;
                int y = sh - (int) (f * sh * 0.75);
                int x = baseX + (int) (Math.sin(f * 5 + t / 250.0 + s) * 30 * (1 - 0.5 * f));
                int th = (int) (12 * (1 - f)) + 3;
                ctx.fill(x - th / 2 - 1, y - 3, x + th / 2 + 2, y + 4, alpha(0x0A3A66, 0.85 * pi));
                ctx.fill(x - th / 2, y - 2, x + th / 2 + 1, y + 3, alpha(0x3FA9FF, 0.75 * pi));
            }
        }
    }

    private void fenrir(DrawContext ctx, long t, int sw, int sh, double pi) {
        claws(ctx, t, sw, sh, 0xFFBFE9FF, 5, 900, true);
        for (int i = 0; i < 44; i++) { // nieve
            int h = hash(seed + 5, i);
            int x = (h % Math.max(1, sw)) + (int) (Math.sin(t / 400.0 + i) * 6);
            int y = (int) ((t * 0.10 + (h >> 8) % sh) % sh);
            ctx.fill(x, y, x + 2, y + 2, alpha(0xFFFFFF, 0.85 * pi));
        }
        ctx.fillGradient(0, 0, sw, sh, alpha(0x7FC8FF, 0.10 * pi), alpha(0x2F6FFF, 0.18 * pi));
    }

    private void monkey(DrawContext ctx, long t, int sw, int sh, double pi) {
        int cx = sw / 2, cy = (int) (sh * 0.38), rad = (int) (sh * 0.17);
        for (int dy = -rad; dy <= rad; dy += 2) { // sol
            int half = (int) Math.sqrt((double) rad * rad - (double) dy * dy);
            ctx.fill(cx - half, cy + dy, cx + half, cy + dy + 2, alpha(0xFFD84D, 0.34 * pi));
        }
        for (int r = 0; r < 12; r++) { // rayos
            double ang = r * Math.PI / 6 + t / 900.0;
            for (int k = 0; k < 10; k++) {
                int d = rad + 6 + k * 4;
                int x = cx + (int) (Math.cos(ang) * d), y = cy + (int) (Math.sin(ang) * d);
                ctx.fill(x - 1, y - 1, x + 2, y + 2, alpha(0xFFF0A0, (0.8 - k * 0.07) * pi));
            }
        }
        // bastón gigante que gira
        double ang = -0.9 + easeOut((t - 700) / 900.0) * 1.8;
        int len = (int) (sh * 0.42);
        slash(ctx, cx - (int) (Math.cos(ang) * len), cy - (int) (Math.sin(ang) * len),
                cx + (int) (Math.cos(ang) * len), cy + (int) (Math.sin(ang) * len), 1.0, pi, 0xFFFFC94D, 9);
        for (int i = 0; i < 4; i++) { // clones
            double a2 = t / 500.0 + i * Math.PI / 2;
            int x = cx + (int) (Math.cos(a2) * sh * 0.30), y = cy + (int) (Math.sin(a2) * sh * 0.14);
            ctx.fill(x - 5, y - 5, x + 6, y + 6, alpha(0xFFD84D, 0.85 * pi));
            ctx.fill(x - 3, y - 3, x + 4, y + 4, alpha(0xFFFFFF, 0.9 * pi));
        }
    }
}
