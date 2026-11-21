package dev.slotmine.hud;

import dev.slotmine.SlotMine;
import dev.slotmine.config.SlotConfig;
import dev.slotmine.core.BigNum;
import dev.slotmine.core.Engine;
import dev.slotmine.ui.ShopScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.math.BigInteger;
import java.util.Locale;

/** HUD lateral compacto con hasta 6 rodillos que giran y frenan en cascada. */
public final class ReelHud implements HudElement {
    private static final int CW = 11, CH = 15, GAP = 2, PAD = 4, SUF_W = 18;
    private static final int GOLD = 0xFFFFC94D, CYAN = 0xFF4DD0E1, ORANGE = 0xFFFF8A3D;
    private static final int TEXT = 0xFFE8ECF4, DIM = 0xFF5A6478, RED = 0xFFFF5555;

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

    public void onBreak(Engine.BreakEvent e) {
        long now = System.currentTimeMillis();
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
        int panelH = PAD * 2 + CH + 11;
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

        ctx.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, border);
        ctx.fill(x, y, x + panelW, y + panelH, 0xDD0E1117);
        ctx.fill(x, y, x + panelW, y + 1, accent);

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
