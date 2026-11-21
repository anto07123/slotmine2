package dev.slotmine.ui;

import dev.slotmine.SlotMine;
import dev.slotmine.core.BigNum;
import dev.slotmine.core.GameState;
import dev.slotmine.core.Stats;
import dev.slotmine.core.Upgrades;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Tienda idle: 4 pestañas, tarjetas con barra de nivel, compra x1/x10/MAX y Ascensión con confirmación. */
public final class ShopScreen extends Screen {
    private static final int GOLD = 0xFFFFC94D, CYAN = 0xFF4DD0E1, TEXT = 0xFFE8ECF4, DIM = 0xFF7C8799, GREEN = 0xFF6EE7A0;

    private static Upgrades.Tab lastTab = Upgrades.Tab.PRODUCCION;
    private static int buyMode = 1; // 1, 10, 1000 (= MAX)

    private record Row(Upgrades.Def def, int y) {}
    private record Quote(int count, BigInteger total, BigInteger next, boolean maxed) {}

    private Upgrades.Tab tab = lastTab;
    private boolean confirmAscend;
    private final List<Row> rows = new ArrayList<>();
    private int px, py, pw = 320, ph, rowH, contentY;
    private BigInteger lastPoints = BigInteger.ZERO;
    private int ticks;

    public ShopScreen() { super(Text.literal("SlotMine")); }

    @Override public boolean shouldPause() { return false; }

    private static String modeLabel() { return buyMode == 1 ? "x1" : buyMode == 10 ? "x10" : "MAX"; }

    // ------------------------------------------------------------------ construcción
    @Override
    protected void init() {
        rows.clear();
        GameState gs = SlotMine.state;
        rowH = Math.max(20, Math.min(30, (height - 8 - 68 - 34) / 6));
        ph = 68 + rowH * 6 + 34;
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        contentY = py + 68;
        lastPoints = gs.points;

        // pestañas
        Upgrades.Tab[] tabs = Upgrades.Tab.values();
        int tw = (pw - 16 - 3 * 4) / 4;
        for (int i = 0; i < tabs.length; i++) {
            final Upgrades.Tab t = tabs[i];
            ButtonWidget b = ButtonWidget.builder(Text.literal(t.label), btn -> {
                lastTab = t; tab = t; confirmAscend = false; clearAndInit();
            }).dimensions(px + 8 + i * (tw + 4), py + 44, tw, 18).build();
            b.active = t != tab;
            addDrawableChild(b);
        }

        // modo de compra
        addDrawableChild(ButtonWidget.builder(Text.literal("Compra: " + modeLabel()), btn -> {
            buyMode = buyMode == 1 ? 10 : buyMode == 10 ? 1000 : 1;
            clearAndInit();
        }).dimensions(px + pw - 8 - 84, py + 6, 84, 16).build());

        // filas de mejoras
        int y = contentY + (tab == Upgrades.Tab.ASCENSION ? 34 : 0);
        for (Upgrades.Def d : Upgrades.ALL) {
            if (d.tab() != tab) continue;
            rows.add(new Row(d, y));
            Quote q = quote(d);
            boolean core = d.scope() == Upgrades.Scope.CORE;
            String cost = fmt(q.count() > 0 ? q.total() : q.next(), core);
            String label = q.maxed() && q.count() == 0 ? "MAX"
                    : (q.count() > 1 ? "x" + q.count() + " · " : "") + cost + (core ? " núc." : "");
            int bh = Math.min(18, rowH - 6);
            ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
                SlotMine.engine.buy(d, buyMode);
                clearAndInit();
            }).dimensions(px + pw - 8 - 104, y + (rowH - 3 - bh) / 2, 104, bh).build();
            b.active = q.count() > 0;
            b.setTooltip(Tooltip.of(Text.literal(d.desc())));
            addDrawableChild(b);
            y += rowH;
        }

        // pie
        int fy = py + ph - 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Cerrar"), btn -> close())
                .dimensions(px + 8, fy, 70, 18).build());
        if (tab == Upgrades.Tab.ASCENSION) {
            BigInteger n = SlotMine.engine.coresOnAscend();
            String label = confirmAscend ? "¿Seguro? Reinicia la run" : "ASCENDER (+" + BigNum.dotted(n.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue()) + " núcleos)";
            ButtonWidget asc = ButtonWidget.builder(Text.literal(label), btn -> {
                if (n.signum() == 0) return;
                if (!confirmAscend) { confirmAscend = true; clearAndInit(); return; }
                SlotMine.engine.ascend();
                confirmAscend = false;
                clearAndInit();
            }).dimensions(px + pw - 8 - 190, fy, 190, 18).build();
            asc.active = n.signum() > 0;
            addDrawableChild(asc);
        }
    }

    private Quote quote(Upgrades.Def d) {
        GameState gs = SlotMine.state;
        BigInteger wallet = d.scope() == Upgrades.Scope.CORE ? BigInteger.valueOf(gs.cores) : gs.points;
        int lvl = gs.level(d), n = 0;
        BigInteger total = BigInteger.ZERO;
        while (n < buyMode) {
            if (d.maxLevel() > 0 && lvl + n >= d.maxLevel()) break;
            BigInteger c = Upgrades.costOf(d, lvl + n);
            if (total.add(c).compareTo(wallet) > 0) break;
            total = total.add(c);
            n++;
        }
        boolean atMax = d.maxLevel() > 0 && lvl + n >= d.maxLevel();
        BigInteger next = atMax ? total : Upgrades.costOf(d, lvl + n);
        return new Quote(n, total, next, atMax);
    }

    private static String fmt(BigInteger v, boolean core) {
        return core ? v.toString() : BigNum.formatPrecise(v);
    }

    // ------------------------------------------------------------------ ciclo de vida
    @Override
    public void tick() {
        // refresca botones si los puntos cambian (idle) para mantener "asequible" al día
        if (++ticks % 10 == 0 && !SlotMine.state.points.equals(lastPoints)) clearAndInit();
    }

    @Override
    public void removed() { SlotMine.engine.save(); }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF2A3140);
        ctx.fill(px, py, px + pw, py + ph, 0xF0101319);
        ctx.fillGradient(px, py + 2, px + pw, py + 40, 0xFF1A2030, 0x00101319);
        ctx.fill(px, py, px + pw, py + 2, GOLD);

        GameState gs = SlotMine.state;
        for (Row r : rows) {
            int lvl = gs.level(r.def());
            boolean can = quote(r.def()).count() > 0;
            ctx.fill(px + 8, r.y(), px + pw - 8, r.y() + rowH - 3, can ? 0xFF1A2030 : 0xFF14171F);
            ctx.fill(px + 8, r.y(), px + 11, r.y() + rowH - 3, lvl > 0 ? GOLD : 0xFF2A3140);
            int max = r.def().maxLevel();
            if (max > 0 && rowH >= 26) {
                int bw = pw - 16 - 104 - 28;
                ctx.fill(px + 16, r.y() + rowH - 8, px + 16 + bw, r.y() + rowH - 5, 0xFF0B0D12);
                ctx.fill(px + 16, r.y() + rowH - 8, px + 16 + (int) (bw * (lvl / (double) max)), r.y() + rowH - 5,
                        lvl >= max ? GREEN : CYAN);
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        GameState gs = SlotMine.state;
        Stats st = SlotMine.engine.stats();

        ctx.drawText(textRenderer, "TRAGAPERRAS · TIENDA", px + 10, py + 8, GOLD, true);
        String pts = "Puntos: " + BigNum.formatPrecise(gs.points);
        if (st.idleBase > 0) {
            pts += "  (+" + BigNum.formatPrecise(BigNum.fromDouble(st.idleBase * st.gainMult * st.coreMult)) + "/s)";
        }
        ctx.drawText(textRenderer, textRenderer.trimToWidth(pts, pw - 20), px + 10, py + 20, TEXT, true);
        String sub = "Mult. " + BigNum.formatPrecise(BigNum.fromDouble(st.gainMult * st.coreMult))
                + " · Núcleos " + gs.cores + " · Racha máx. " + gs.bestStreak;
        ctx.drawText(textRenderer, textRenderer.trimToWidth(sub, pw - 20), px + 10, py + 31, DIM, false);

        for (Row r : rows) {
            Upgrades.Def d = r.def();
            int lvl = gs.level(d);
            ctx.drawText(textRenderer, d.name(), px + 16, r.y() + 3, lvl > 0 ? GOLD : TEXT, true);
            String lv = "Nv. " + lvl + (d.maxLevel() > 0 ? "/" + d.maxLevel() : "");
            ctx.drawText(textRenderer, lv, px + 16 + textRenderer.getWidth(d.name()) + 6, r.y() + 3, DIM, false);
            ctx.drawText(textRenderer, textRenderer.trimToWidth(d.effect().apply(lvl), pw - 16 - 104 - 28),
                    px + 16, r.y() + 13, CYAN, false);
        }

        if (tab == Upgrades.Tab.ASCENSION) {
            BigInteger n = SlotMine.engine.coresOnAscend();
            ctx.drawText(textRenderer, "Ganado en esta ascensión: " + BigNum.formatPrecise(gs.runEarned),
                    px + 12, contentY + 2, TEXT, false);
            ctx.drawText(textRenderer, textRenderer.trimToWidth("Núcleos al ascender: +" + n + " (necesitas 1.000M para el primero)"
                    + "", pw - 24), px + 12, contentY + 13, n.signum() > 0 ? GREEN : DIM, false);
            ctx.drawText(textRenderer, "Bonus de núcleos: +" + (gs.coresEarnedTotal * 20) + "% · Ascensiones: " + gs.ascensions,
                    px + 12, contentY + 24, DIM, false);
        }
    }
}
