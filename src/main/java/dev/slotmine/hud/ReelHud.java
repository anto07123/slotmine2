package dev.slotmine.hud;

import dev.slotmine.SlotMine;
import dev.slotmine.config.SlotConfig;
import dev.slotmine.core.BigNum;
import dev.slotmine.core.Engine;
import dev.slotmine.core.GameState;
import dev.slotmine.core.Stats;
import dev.slotmine.core.Upgrades;
import dev.slotmine.ui.ShopScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** HUD lateral compacto con hasta 6 rodillos que giran y frenan en cascada. */
public final class ReelHud implements HudElement {
    private static final int CW = 11, CH = 15, GAP = 2, PAD = 4, SUF_W = 18;
    private static final int GOLD = 0xFFFFC94D, CYAN = 0xFF4DD0E1, ORANGE = 0xFFFF8A3D;
    private static final int TEXT = 0xFFE8ECF4, DIM = 0xFF5A6478, RED = 0xFFFF5555, GREEN = 0xFF6EE7A0;

    private record Popup(String text, int color, long born, int dx) {}
    private record Toast(String text, int color, long born, long until) {}

    private final List<Popup> popups = new ArrayList<>();
    private final ArrayDeque<Toast> toasts = new ArrayDeque<>();
    private int popupCounter;
    private long eventBannerUntil;

    private final double[] pos = new double[6];
    private final int[] target = new int[6];
    private final long[] stopAt = new long[6];
    private final boolean[] spinning = new boolean[6];
    private final boolean[] lead = new boolean[6];
    private final long[] flashAt = new long[6];

    private boolean overflow, wasSpinning;
    private int tier = -1;
    private long lastFrame = System.currentTimeMillis();
    private long nearUntil, jackpotUntil, tierFlashUntil, msgUntil;
    private String msg = "";
    private int msgColor = TEXT;

    // ------------------------------------------------------------------ entrada de datos
    /** Actualiza los objetivos de los rodillos. Solo giran los dígitos que cambian (cascada izq -> der). */
    public void onScore(BigInteger value, boolean big) {
        long now = System.currentTimeMillis();
        int r = SlotMine.state.reelCount();
        BigNum.Scaled s = BigNum.scale(value);
        int cap = (int) Math.pow(10, r) - 1;
        int m = s.mantissa();
        overflow = m > cap;
        if (overflow) m = cap;

        if (s.tier() != tier) {
            if (tier >= 0 && s.tier() > tier) {
                tierFlashUntil = now + 900;
                show("¡Escala " + (s.tier() == 0 ? "" : BigNum.SUFFIX[s.tier()]) + "!", GOLD, 1200);
            }
            tier = s.tier();
        }
        for (int i = 0; i < r; i++) {
            int div = (int) Math.pow(10, r - 1 - i);
            int d = (m / div) % 10;
            lead[i] = i < r - 1 && m < div;
            if (d != target[i] || big) {
                target[i] = d;
                spinning[i] = true;
                long extra = big ? 600 + i * 110L : 0;
                stopAt[i] = Math.max(stopAt[i], now + 170 + i * 65L + extra);
            }
        }
    }

    public void onEventStart(double mult) {
        long now = System.currentTimeMillis();
        eventBannerUntil = now + 2500;
        toast("¡LLUVIA DORADA! todo ×" + String.format(Locale.ROOT, "%.1f", mult), GOLD, 3200);
    }

    public void onAchievement(String name) {
        toast("★ Logro: " + name, GREEN, 3200);
    }

    private void toast(String text, int color, int ms) {
        long now = System.currentTimeMillis();
        long start = toasts.isEmpty() ? now : Math.max(now, toasts.peekLast().until());
        if (toasts.size() < 5) toasts.addLast(new Toast(text, color, start, start + ms));
    }

    private void addPopup(Engine.BreakEvent e) {
        if (!SlotMine.config.popupsEnabled) return;
        if (e.lite() && (popupCounter++ % 4) != 0) return;
        int color = e.jackpot() ? GOLD : !e.gods().isEmpty() ? godColor(e.gods().get(0)) : e.crit() ? GOLD : e.op() ? CYAN : 0xFFB8C2D3;
        String t = (e.crit() ? "CRIT " : "") + "+" + BigNum.format(e.gain());
        if (popups.size() >= 7) popups.remove(0);
        popups.add(new Popup(t, color, System.currentTimeMillis(), (int) (Math.random() * 14) - 7));
    }

    private static int godColor(String id) {
        Upgrades.God g = Upgrades.godOf(id);
        return g == null ? GOLD : g.color();
    }

    private static int alpha(int color, double a) {
        int al = (int) Math.max(0, Math.min(255, a * 255));
        return (al << 24) | (color & 0xFFFFFF);
    }

    public void onBreak(Engine.BreakEvent e) {
        long now = System.currentTimeMillis();
        addPopup(e);
        if (e.jackpot()) {
            jackpotUntil = now + 4500;
            show("¡JACKPOT! +" + BigNum.formatPrecise(e.jackpotWin()), GOLD, 4500);
            int r = SlotMine.state.reelCount();
            for (int i = 0; i < r; i++) { spinning[i] = true; stopAt[i] = now + 500 + i * 140L; }
        } else if (e.near()) {
            nearUntil = now + 1300;
            show("¡CASI!", ORANGE, 1300);
            int last = SlotMine.state.reelCount() - 1;
            spinning[last] = true;
            stopAt[last] = Math.max(stopAt[last], now + 1100);
        } else if (e.feverStart()) {
            show("¡FIEBRE!", CYAN, 1200);
        } else if (!e.gods().isEmpty()) {
            Upgrades.God g = Upgrades.godOf(e.gods().get(0));
            if (g != null) show("¡" + g.name().toUpperCase(Locale.ROOT) + "!", g.color(), 850);
        } else if (!e.lite()) {
            if (e.shock()) show("SHOCKWAVE", CYAN, 700);
            else if (!e.chain().isEmpty()) show("CHAIN ARC", CYAN, 700);
            else if (e.crit()) show("CRIT", GOLD, 600);
        }
    }

    private void show(String text, int color, int ms) {
        msg = text;
        msgColor = color;
        msgUntil = System.currentTimeMillis() + ms;
    }

    // ------------------------------------------------------------------ animación
    private void animate(long now, int r) {
        double dt = Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        boolean any = false;
        for (int i = 0; i < r; i++) {
            if (spinning[i]) {
                any = true;
                pos[i] = (pos[i] + dt * 26) % 10;
                if (now >= stopAt[i]) {
                    spinning[i] = false;
                    pos[i] = target[i];
                    flashAt[i] = now;
                    SlotMine.fx.clack();
                }
            } else {
                pos[i] = target[i];
            }
        }
        if (wasSpinning && !any) onCascadeEnd(r);
        wasSpinning = any;
    }

    /** Evalúa alineaciones cuando todos los rodillos han frenado. */
    private void onCascadeEnd(int r) {
        if (r < 3 || lead[0]) return;
        StringBuilder sb = new StringBuilder();
        boolean allEq = true;
        for (int i = 0; i < r; i++) {
            if (lead[i]) continue;
            sb.append(target[i]);
            if (target[i] != target[0]) allEq = false;
        }
        String ds = sb.toString();
        int kind = ds.contains("777") ? 2 : (allEq && target[0] != 0 ? 1 : 0);
        if (kind == 0) return;
        BigInteger bonus = SlotMine.engine.grantCombo(kind);
        if (bonus.signum() > 0) show((kind == 2 ? "¡777! +" : "¡IGUALES! +") + BigNum.format(bonus), GOLD, 1500);
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SlotConfig cfg = SlotMine.config;
        if (!cfg.hudEnabled || mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof ShopScreen) return;

        long now = System.currentTimeMillis();
        TextRenderer tr = mc.textRenderer;
        int r = SlotMine.state.reelCount();
        animate(now, r);

        int panelW = PAD * 2 + r * CW + (r - 1) * GAP + 3 + SUF_W;
        int panelH = PAD * 2 + CH + 11 + 7;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = "LEFT".equalsIgnoreCase(cfg.hudSide) ? cfg.hudMarginX : sw - panelW - cfg.hudMarginX;
        int y = sh / 2 - panelH / 2 + cfg.hudOffsetY;

        boolean jackpot = now < jackpotUntil;
        boolean near = now < nearUntil;
        boolean fever = SlotMine.engine.feverRemainingMs() > 0;
        boolean blink = (now / 120) % 2 == 0;

        int border = 0xFF2A3140;
        int accent = DIM;
        if (fever) { border = 0xFF1E6F7A; accent = CYAN; }
        if (near) { border = blink ? 0xFFB5601F : 0xFF5A3010; accent = ORANGE; }
        if (jackpot) { border = blink ? 0xFFFFC94D : 0xFF8A6A1F; accent = GOLD; }
        if (now < tierFlashUntil) { border = 0xFFFFE08A; accent = GOLD; }

        boolean event = SlotMine.engine.eventRemainingMs() > 0;
        if (event) { border = blink ? 0xFFFFC94D : 0xFFB8892A; accent = GOLD; }

        drawPopups(ctx, tr, now, x, y, panelW, panelH, sw);

        ctx.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, border);
        ctx.fillGradient(x, y, x + panelW, y + panelH, 0xEE1A2231, 0xEE090C11);
        ctx.fill(x, y, x + panelW, y + 1, accent);
        // esquinas
        ctx.fill(x - 1, y - 1, x + 2, y, accent);
        ctx.fill(x + panelW - 2, y - 1, x + panelW + 1, y, accent);
        ctx.fill(x - 1, y + panelH, x + 2, y + panelH + 1, accent);
        ctx.fill(x + panelW - 2, y + panelH, x + panelW + 1, y + panelH + 1, accent);
        drawGodPips(ctx, now, x, y);

        int cy = y + PAD;
        for (int i = 0; i < r; i++) {
            drawCell(ctx, tr, x + PAD + i * (CW + GAP), cy, i, now, jackpot, near && i == r - 1);
        }

        // caja de sufijo
        int sx = x + PAD + r * (CW + GAP) + 1;
        ctx.fill(sx, cy, sx + SUF_W, cy + CH, 0xFF1B2230);
        String suf = tier <= 0 ? "pts" : BigNum.SUFFIX[tier];
        int sc = overflow ? (blink ? RED : DIM) : (tier <= 0 ? DIM : GOLD);
        if (overflow) suf = "+";
        ctx.drawText(tr, suf, sx + (SUF_W - tr.getWidth(suf)) / 2, cy + 4, sc, false);

        // línea de estado
        String status;
        int color = DIM;
        if (now < msgUntil) {
            status = msg;
            color = msgColor;
        } else if (event) {
            status = String.format(Locale.ROOT, "LLUVIA DORADA %.0fs", SlotMine.engine.eventRemainingMs() / 1000.0);
            color = GOLD;
        } else if (fever) {
            status = String.format(Locale.ROOT, "FIEBRE %.1fs", SlotMine.engine.feverRemainingMs() / 1000.0);
            color = CYAN;
        } else if (overflow) {
            status = "Falta rodillo · " + BigNum.formatPrecise(SlotMine.state.points);
            color = ORANGE;
        } else {
            int st = SlotMine.engine.streak();
            status = BigNum.formatPrecise(SlotMine.state.points) + (st >= 5 ? " · x" + st : "");
        }
        ctx.drawText(tr, tr.trimToWidth(status, panelW - 8), x + PAD, y + PAD + CH + 3, color, false);

        drawBars(ctx, now, x, y, panelW, panelH, fever, event);
        drawToasts(ctx, tr, now, x, y, panelW, panelH, sw);
    }

    private void drawGodPips(DrawContext ctx, long now, int x, int y) {
        GameState gs = SlotMine.state;
        if (gs.perm.getOrDefault("u_gods", 0) <= 0) return;
        int px = x;
        for (Upgrades.God g : Upgrades.GODS) {
            int lvl = gs.run.getOrDefault(g.id(), 0);
            if (lvl <= 0) continue;
            double pulse = 0.65 + 0.35 * Math.sin(now / 350.0 + px);
            ctx.fill(px, y - 5, px + 5, y - 2, alpha(g.color(), pulse));
            px += 7;
        }
    }

    private void drawBars(DrawContext ctx, long now, int x, int y, int panelW, int panelH, boolean fever, boolean event) {
        Stats st = SlotMine.engine.stats();
        int bx = x + PAD, bw = panelW - PAD * 2;
        int by1 = y + panelH - 6, by2 = y + panelH - 3;
        // barra de tiempo (evento > fiebre)
        ctx.fill(bx, by1, bx + bw, by1 + 2, 0xFF0B0D12);
        if (event) {
            double f = SlotMine.engine.eventRemainingMs() / (st.eventSeconds * 1000.0);
            ctx.fill(bx, by1, bx + (int) (bw * Math.min(1.0, f)), by1 + 2, GOLD);
        } else if (fever) {
            double f = SlotMine.engine.feverRemainingMs() / (st.feverSeconds * 1000.0);
            ctx.fill(bx, by1, bx + (int) (bw * Math.min(1.0, f)), by1 + 2, CYAN);
        }
        // barra de pity (progreso al jackpot garantizado)
        int pityMax = Math.max(30, (int) (SlotMine.config.pityMax * st.pityFactor));
        double p = Math.min(1.0, SlotMine.state.pity / (double) pityMax);
        ctx.fill(bx, by2, bx + bw, by2 + 2, 0xFF0B0D12);
        int col = p > 0.85 ? (((now / 150) % 2 == 0) ? GOLD : ORANGE) : 0xFF8A6A1F;
        ctx.fill(bx, by2, bx + (int) (bw * p), by2 + 2, col);
    }

    private void drawPopups(DrawContext ctx, TextRenderer tr, long now, int x, int y, int panelW, int panelH, int sw) {
        if (popups.isEmpty()) return;
        boolean left = "LEFT".equalsIgnoreCase(SlotMine.config.hudSide);
        popups.removeIf(p -> now - p.born() > 1000);
        for (Popup p : popups) {
            double t = (now - p.born()) / 1000.0;
            int w = tr.getWidth(p.text());
            int tx = left ? x + panelW + 6 + p.dx() : x - w - 6 + p.dx();
            int ty = y + panelH / 2 - 4 - (int) (30 * (1 - Math.pow(1 - t, 2)));
            ctx.drawText(tr, p.text(), tx, ty, alpha(p.color(), 1.0 - t * t), true);
        }
    }

    private void drawToasts(DrawContext ctx, TextRenderer tr, long now, int x, int y, int panelW, int panelH, int sw) {
        while (!toasts.isEmpty() && now > toasts.peekFirst().until()) toasts.pollFirst();
        Toast t = toasts.peekFirst();
        if (t == null || now < t.born()) return;
        double in = Math.min(1.0, (now - t.born()) / 250.0);
        double out = Math.min(1.0, (t.until() - now) / 350.0);
        double a = Math.min(in, out);
        int w = tr.getWidth(t.text()) + 12;
        boolean left = "LEFT".equalsIgnoreCase(SlotMine.config.hudSide);
        int tx = left ? x : x + panelW - w;
        int ty = y + panelH + 4 + (int) ((1 - in) * -6);
        ctx.fill(tx - 1, ty - 1, tx + w + 1, ty + 15, alpha(t.color(), a * 0.9));
        ctx.fill(tx, ty, tx + w, ty + 14, alpha(0x0E1117, a * 0.93));
        ctx.drawText(tr, t.text(), tx + 6, ty + 3, alpha(t.color(), a), true);
    }

    private void drawCell(DrawContext ctx, TextRenderer tr, int cx, int cy, int i, long now, boolean jackpot, boolean tense) {
        boolean flash = now - flashAt[i] < 120;
        ctx.fill(cx, cy, cx + CW, cy + CH, flash ? 0xFF2F3A52 : 0xFF161B26);
        ctx.enableScissor(cx, cy, cx + CW, cy + CH);
        if (spinning[i]) {
            double p = pos[i];
            int base = (int) Math.floor(p);
            int off = (int) Math.round((p - base) * CH);
            drawDigit(ctx, tr, cx, cy + 4 - off, base % 10, 0xFF9FB3C8);
            drawDigit(ctx, tr, cx, cy + 4 - off + CH, (base + 1) % 10, 0xFF9FB3C8);
        } else {
            int c = lead[i] ? 0xFF3A4458 : TEXT;
            if (jackpot && (now / 150) % 2 == 0) c = GOLD;
            if (tense) c = (now / 100) % 2 == 0 ? ORANGE : TEXT;
            drawDigit(ctx, tr, cx, cy + 4, target[i], c);
        }
        ctx.disableScissor();
        ctx.fillGradient(cx, cy, cx + CW, cy + 4, 0xAA000000, 0x00000000);
        ctx.fillGradient(cx, cy + CH - 4, cx + CW, cy + CH, 0x00000000, 0xAA000000);
    }

    private void drawDigit(DrawContext ctx, TextRenderer tr, int cx, int y, int d, int color) {
        String s = Integer.toString(d);
        ctx.drawText(tr, s, cx + (CW - tr.getWidth(s)) / 2, y, color, true);
    }
}
