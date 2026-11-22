package dev.slotmine.ui;

import dev.slotmine.SlotMine;
import dev.slotmine.core.Achievements;
import dev.slotmine.core.BigNum;
import dev.slotmine.core.Beasts;
import dev.slotmine.core.Eras;
import dev.slotmine.core.Skills;
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
import java.util.Locale;

/** Tienda idle: 6 pestañas con scroll, tarjetas con barra de nivel, dioses con icono, logros, compra x1/x10/MAX y Ascensión. */
public final class ShopScreen extends Screen {
    private static final int GOLD = 0xFFFFC94D, CYAN = 0xFF4DD0E1, TEXT = 0xFFE8ECF4, DIM = 0xFF7C8799, GREEN = 0xFF6EE7A0;

    private static Upgrades.Tab lastTab = Upgrades.Tab.PRODUCCION;
    private static int buyMode = 1; // 1, 10, 1000 (= MAX)

    /** Fila de la lista: mejora, logro, cabecera de era o era bloqueada. */
    private record Entry(Upgrades.Def def, Achievements.Def ach, String header, int color, int lockedEra) {
        static Entry of(Upgrades.Def d) { return new Entry(d, null, null, 0, 0); }
        static Entry of(Achievements.Def a) { return new Entry(null, a, null, 0, 0); }
        static Entry header(String text, int color) { return new Entry(null, null, text, color, 0); }
        static Entry locked(int era) { return new Entry(null, null, null, Eras.color(era), era); }
    }
    private record Row(Entry entry, int y) {}
    private record Quote(int count, BigInteger total, BigInteger next, boolean maxed) {}

    private Upgrades.Tab tab = lastTab;
    private boolean confirmAscend;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int scroll, visible = 6, headerH;
    private int px, py, pw = 400, ph, rowH, contentY;
    private BigInteger lastPoints = BigInteger.ZERO;
    private int ticks;

    public ShopScreen() { super(Text.literal("SlotMine")); }

    @Override public boolean shouldPause() { return false; }

    private static String modeLabel() { return buyMode == 1 ? "x1" : buyMode == 10 ? "x10" : "MAX"; }

    private static int alpha(int color, double a) {
        int al = (int) Math.max(0, Math.min(255, a * 255));
        return (al << 24) | (color & 0xFFFFFF);
    }

    private boolean godsUnlocked() { return SlotMine.state.perm.getOrDefault("u_gods", 0) > 0; }

    // ------------------------------------------------------------------ construcción
    @Override
    protected void init() {
        rows.clear();
        entries.clear();
        GameState gs = SlotMine.state;
        int top = 88; // cabecera + 2 filas de pestañas
        int baseVisible = Math.max(3, Math.min(6, (height - 8 - top - 34) / 20));
        rowH = Math.max(20, Math.min(30, (height - 8 - top - 34) / baseVisible));
        ph = top + rowH * baseVisible + 34;
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        contentY = py + top;
        lastPoints = gs.points;

        headerH = (tab == Upgrades.Tab.ASCENSION || tab == Upgrades.Tab.LOGROS) ? 34 : 0;
        visible = Math.max(2, (rowH * baseVisible - headerH) / rowH);

        // entradas de la pestaña actual
        if (tab == Upgrades.Tab.LOGROS) {
            for (Achievements.Def a : Achievements.ALL) entries.add(Entry.of(a));
        } else if (tab == Upgrades.Tab.ERAS) {
            for (int e = 1; e <= Eras.COUNT; e++) {
                if (!Eras.unlocked(e, gs)) { entries.add(Entry.locked(e)); break; }
                entries.add(Entry.header(Eras.label(e) + "  (" + Eras.thresholdLabel(e) + ")", Eras.color(e)));
                for (Upgrades.Def d : Upgrades.ALL) if (d.tab() == tab && Eras.tierOf(d.id()) == e) entries.add(Entry.of(d));
            }
        } else if (tab != Upgrades.Tab.PANTEON || godsUnlocked()) {
            for (Upgrades.Def d : Upgrades.ALL) if (d.tab() == tab) entries.add(Entry.of(d));
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visible)));

        // pestañas
        Upgrades.Tab[] tabs = Upgrades.Tab.values();
        int gap = 3, perRow = 5;
        int tw = (pw - 16 - (perRow - 1) * gap) / perRow;
        for (int i = 0; i < tabs.length; i++) {
            final Upgrades.Tab t = tabs[i];
            int col = i % perRow, rowi = i / perRow;
            ButtonWidget b = ButtonWidget.builder(Text.literal(t.label), btn -> {
                lastTab = t; tab = t; scroll = 0; confirmAscend = false; clearAndInit();
            }).dimensions(px + 8 + col * (tw + gap), py + 42 + rowi * 20, tw, 18).build();
            b.active = t != tab;
            addDrawableChild(b);
        }

        // modo de compra
        addDrawableChild(ButtonWidget.builder(Text.literal("Compra: " + modeLabel()), btn -> {
            buyMode = buyMode == 1 ? 10 : buyMode == 10 ? 1000 : 1;
            clearAndInit();
        }).dimensions(px + pw - 8 - 84, py + 6, 84, 16).build());

        // filas visibles
        int y = contentY + headerH;
        for (int i = scroll; i < Math.min(entries.size(), scroll + visible); i++) {
            Entry en = entries.get(i);
            rows.add(new Row(en, y));
            if (en.def() != null) {
                final Upgrades.Def d = en.def();
                Quote q = quote(d);
                boolean core = d.scope() == Upgrades.Scope.CORE;
                String cost = fmt(q.count() > 0 ? q.total() : q.next(), core);
                String label = q.maxed() && q.count() == 0 ? "MAX"
                        : (q.count() > 1 ? "x" + q.count() + " · " : "") + cost + (core ? " núc." : "");
                int bh = Math.min(18, rowH - 6);
                ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
                    SlotMine.engine.buy(d, buyMode);
                    clearAndInit();
                }).dimensions(px + pw - 14 - 104, y + (rowH - 3 - bh) / 2, 104, bh).build();
                b.active = q.count() > 0;
                b.setTooltip(Tooltip.of(Text.literal(d.desc())));
                addDrawableChild(b);
            }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, entries.size() - visible);
        int ns = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount)));
        if (ns != scroll) {
            scroll = ns;
            clearAndInit();
        }
        return true;
    }

    @Override
    public void removed() { SlotMine.engine.save(); }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        long now = System.currentTimeMillis();
        GameState gs = SlotMine.state;

        // marco con brillo dorado suave
        double glow = 0.55 + 0.25 * Math.sin(now / 600.0);
        ctx.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, alpha(GOLD, 0.10 * glow));
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF2A3140);
        ctx.fillGradient(px, py, px + pw, py + ph, 0xF2151B28, 0xF20B0E14);
        ctx.fillGradient(px, py + 2, px + pw, py + 42, 0xFF202A3F, 0x00101319);

        // motas doradas flotando
        for (int i = 0; i < 26; i++) {
            double ph1 = (now / (26.0 + (i % 5) * 7) + i * 41) % ph;
            int sx = px + 6 + (int) ((i * 97L) % (pw - 12));
            int sy = py + ph - (int) ph1;
            double a = 0.10 + 0.12 * Math.sin(now / 400.0 + i);
            int sz = (i % 4 == 0) ? 2 : 1;
            ctx.fill(sx, sy, sx + sz, sy + sz, alpha(i % 3 == 0 ? CYAN : GOLD, a));
        }

        // línea superior con destello que recorre
        ctx.fill(px, py, px + pw, py + 2, 0xFF8A6A1F);
        int shx = px + (int) ((now / 7) % (pw + 80)) - 40;
        for (int k = 0; k < 40; k++) {
            int lx = shx + k;
            if (lx < px || lx >= px + pw) continue;
            double a = 1.0 - Math.abs(k - 20) / 20.0;
            ctx.fill(lx, py, lx + 1, py + 2, alpha(0xFFE9A8, a));
        }

        // tarjetas
        for (Row r : rows) {
            Entry en = r.entry();
            int accent = GOLD;
            int lvl = 0;
            boolean can = false, done = false;
            if (en.header() != null || en.lockedEra() > 0) { drawSpecialRow(ctx, r, en, gs, now); continue; }
            if (en.def() != null) {
                Upgrades.Def d = en.def();
                lvl = gs.level(d);
                can = quote(d).count() > 0;
                Upgrades.God g = Upgrades.godOf(d.id());
                if (g != null) accent = g.color();
                int era = Eras.tierOf(d.id());
                if (era > 0) accent = Eras.color(era);
                for (Skills.Skill sk : Skills.ALL) if (sk.id().equals(d.id())) accent = sk.color();
                Beasts.Beast bst = Beasts.byId(d.id());
                if (bst != null) accent = bst.color();
            } else {
                done = gs.achievements.contains(en.ach().id());
                accent = done ? GREEN : 0xFF2A3140;
            }
            int x0 = px + 8, x1 = px + pw - 8 - 6, y0 = r.y(), y1 = r.y() + rowH - 3;
            ctx.fillGradient(x0, y0, x1, y1, can ? 0xFF222C42 : 0xFF181D29, can ? 0xFF1A2132 : 0xFF121620);
            ctx.fill(x0, y0, x0 + 3, y1, (lvl > 0 || done) ? accent : 0xFF2A3140);
            if (can) { // contorno pulsante: se puede comprar
                int oc = alpha(accent, 0.35 + 0.25 * Math.sin(now / 250.0));
                ctx.fill(x0, y0, x1, y0 + 1, oc);
                ctx.fill(x0, y1 - 1, x1, y1, oc);
                ctx.fill(x1 - 1, y0, x1, y1, oc);
            }
            int max = en.def() != null ? en.def().maxLevel() : 0;
            if (max > 0 && rowH >= 26) {
                int bx = px + 16 + (hasIcon(en.def()) ? 22 : 0);
                int bw = px + pw - 14 - 104 - 8 - bx;
                ctx.fill(bx, y0 + rowH - 8, bx + bw, y0 + rowH - 5, 0xFF0B0D12);
                ctx.fill(bx, y0 + rowH - 8, bx + (int) (bw * (lvl / (double) max)), y0 + rowH - 5,
                        lvl >= max ? GREEN : accent == GOLD ? CYAN : accent);
            }
            // icono de dios / habilidad
            if (en.def() != null && hasIcon(en.def())) {
                int ix = px + 16, iy = y0 + Math.max(1, (rowH - 3 - 18) / 2);
                ctx.fill(ix - 1, iy - 1, ix + 19, iy + 19, alpha(accent, lvl > 0 ? 0.9 : 0.3));
                ctx.fillGradient(ix, iy, ix + 18, iy + 18, alpha(accent, lvl > 0 ? 0.55 : 0.18), 0xFF0B0D12);
            }
        }

        // barra de scroll
        if (entries.size() > visible) {
            int top = contentY + headerH, h = visible * rowH - 3;
            int sx = px + pw - 12;
            ctx.fill(sx, top, sx + 3, top + h, 0xFF0B0D12);
            int th = Math.max(10, h * visible / entries.size());
            int ty = top + (h - th) * scroll / Math.max(1, entries.size() - visible);
            ctx.fill(sx, ty, sx + 3, ty + th, GOLD);
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
            pts += "  (+" + BigNum.formatPrecise(BigNum.fromDouble(st.idlePerSec)) + "/s)";
        }
        ctx.drawText(textRenderer, textRenderer.trimToWidth(pts, pw - 20), px + 10, py + 20, TEXT, true);
        String sub = "Mult. " + BigNum.formatPrecise(BigNum.fromDouble(st.gainMult * st.coreMult))
                + " · Núcleos " + gs.cores + " · Racha máx. " + gs.bestStreak
                + " · Logros " + gs.achievements.size() + "/" + Achievements.ALL.size();
        ctx.drawText(textRenderer, textRenderer.trimToWidth(sub, pw - 20), px + 10, py + 31, DIM, false);

        for (Row r : rows) {
            Entry en = r.entry();
            if (en.header() != null || en.lockedEra() > 0) { renderSpecialRow(ctx, r, en, gs); continue; }
            if (en.ach() != null) { renderAchievement(ctx, r, en.ach(), gs); continue; }

            Upgrades.Def d = en.def();
            int lvl = gs.level(d);
            Upgrades.God g = Upgrades.godOf(d.id());
            Skills.Skill skill = null;
            for (Skills.Skill sk : Skills.ALL) if (sk.id().equals(d.id())) skill = sk;
            Beasts.Beast beast = Beasts.byId(d.id());
            int tx = px + 16 + (g != null || skill != null || beast != null ? 22 : 0);
            int era = Eras.tierOf(d.id());
            int nameColor = lvl > 0 ? (g != null ? g.color() : skill != null ? skill.color() : beast != null ? beast.color() : era > 0 ? Eras.color(era) : GOLD) : TEXT;
            if (beast != null) {
                int iy = r.y() + Math.max(1, (rowH - 3 - 18) / 2);
                int lw = textRenderer.getWidth(beast.letter());
                ctx.drawText(textRenderer, beast.letter(), px + 16 + (18 - lw) / 2, iy + 5, lvl > 0 ? 0xFFFFFFFF : 0xFF8A93A6, true);
            }
            if (skill != null) {
                int iy = r.y() + Math.max(1, (rowH - 3 - 18) / 2);
                int lw = textRenderer.getWidth(skill.key());
                ctx.drawText(textRenderer, skill.key(), px + 16 + (18 - lw) / 2, iy + 5, lvl > 0 ? 0xFFFFFFFF : 0xFF8A93A6, true);
            }
            if (g != null) {
                int iy = r.y() + Math.max(1, (rowH - 3 - 18) / 2);
                int lw = textRenderer.getWidth(g.letter());
                ctx.drawText(textRenderer, g.letter(), px + 16 + (18 - lw) / 2, iy + 5, lvl > 0 ? 0xFFFFFFFF : 0xFF8A93A6, true);
            }
            ctx.drawText(textRenderer, d.name(), tx, r.y() + 3, nameColor, true);
            String lv = "Nv. " + lvl + (d.maxLevel() > 0 ? "/" + d.maxLevel() : "");
            int lx = tx + textRenderer.getWidth(d.name()) + 6;
            ctx.drawText(textRenderer, lv, lx, r.y() + 3, DIM, false);
            if (g != null) {
                int gx = lx + textRenderer.getWidth(lv) + 6;
                int room = px + pw - 14 - 104 - 8 - gx;
                if (room > 30) ctx.drawText(textRenderer, textRenderer.trimToWidth("· " + g.title(), room), gx, r.y() + 3, 0xFF4F596B, false);
            }
            int room = px + pw - 14 - 104 - 8 - tx;
            ctx.drawText(textRenderer, textRenderer.trimToWidth(d.effect().apply(lvl), room), tx, r.y() + 13, CYAN, false);
        }

        if (tab == Upgrades.Tab.PANTEON && !godsUnlocked()) {
            String m1 = "El Panteón está cerrado.";
            String m2 = "Desbloquéalo en la pestaña Extras (250k puntos).";
            ctx.drawText(textRenderer, m1, px + (pw - textRenderer.getWidth(m1)) / 2, contentY + 30, GOLD, true);
            ctx.drawText(textRenderer, m2, px + (pw - textRenderer.getWidth(m2)) / 2, contentY + 44, DIM, false);
        }

        if (tab == Upgrades.Tab.ASCENSION) {
            BigInteger n = SlotMine.engine.coresOnAscend();
            ctx.drawText(textRenderer, "Ganado en esta ascensión: " + BigNum.formatPrecise(gs.runEarned),
                    px + 12, contentY + 2, TEXT, false);
            ctx.drawText(textRenderer, textRenderer.trimToWidth("Núcleos al ascender: +" + n + " (necesitas 1.000M para el primero)", pw - 24),
                    px + 12, contentY + 13, n.signum() > 0 ? GREEN : DIM, false);
            ctx.drawText(textRenderer, "Bonus de núcleos: ×" + String.format(java.util.Locale.ROOT, "%.2f", Stats.coreBonus(gs.coresEarnedTotal)) + " · Ascensiones: " + gs.ascensions,
                    px + 12, contentY + 24, DIM, false);
        }

        if (tab == Upgrades.Tab.LOGROS) {
            int n = gs.achievements.size();
            ctx.drawText(textRenderer, "Logros desbloqueados: " + n + "/" + Achievements.ALL.size(), px + 12, contentY + 2, TEXT, false);
            ctx.drawText(textRenderer, "Cada logro da +2% a TODA la producción, para siempre.", px + 12, contentY + 13, DIM, false);
            ctx.drawText(textRenderer, String.format(Locale.ROOT, "Bonus actual: ×%.2f", Stats.achMult(gs)), px + 12, contentY + 24, GREEN, false);
        }

        if (tab == Upgrades.Tab.BESTIAS && entries.size() <= visible) {
            ctx.drawText(textRenderer, "Al romper bloques puede despertar una bestia (muy raro)", px + 90, py + ph - 19, DIM, false);
        }
        if (tab == Upgrades.Tab.HABILIDADES && entries.size() <= visible) {
            ctx.drawText(textRenderer, "Teclas Z X C V B N R (cámbialas en Controles)", px + 90, py + ph - 19, DIM, false);
        }
        if (entries.size() > visible) {
            String hint = "Rueda del ratón para ver más (" + (scroll + 1) + "-" + Math.min(entries.size(), scroll + visible) + "/" + entries.size() + ")";
            ctx.drawText(textRenderer, hint, px + 90, py + ph - 19, DIM, false);
        }
    }

    private boolean hasIcon(Upgrades.Def d) {
        if (Upgrades.godOf(d.id()) != null) return true;
        for (Skills.Skill sk : Skills.ALL) if (sk.id().equals(d.id())) return true;
        return Beasts.byId(d.id()) != null;
    }

    private void drawSpecialRow(DrawContext ctx, Row r, Entry en, GameState gs, long now) {
        int x0 = px + 8, x1 = px + pw - 14, y0 = r.y(), y1 = r.y() + rowH - 3;
        if (en.header() != null) {
            ctx.fillGradient(x0, y0, x1, y1, alpha(en.color(), 0.38), alpha(en.color(), 0.05));
            ctx.fill(x0, y0, x0 + 3, y1, en.color());
            ctx.fill(x0, y1 - 1, x1, y1, alpha(en.color(), 0.5));
        } else {
            ctx.fillGradient(x0, y0, x1, y1, 0xFF14181F, 0xFF0F1218);
            ctx.fill(x0, y0, x0 + 3, y1, 0xFF3A4254);
        }
    }

    private void renderSpecialRow(DrawContext ctx, Row r, Entry en, GameState gs) {
        int tx = px + 16;
        if (en.header() != null) {
            ctx.drawText(textRenderer, en.header(), tx, r.y() + Math.max(3, (rowH - 3 - 8) / 2), en.color(), true);
            String five = "5 mejoras";
            ctx.drawText(textRenderer, five, px + pw - 20 - textRenderer.getWidth(five), r.y() + Math.max(3, (rowH - 3 - 8) / 2), DIM, false);
            return;
        }
        int era = en.lockedEra();
        ctx.drawText(textRenderer, "BLOQUEADA · " + Eras.label(era), tx, r.y() + 3, 0xFF6F7788, true);
        ctx.drawText(textRenderer, textRenderer.trimToWidth("Gana " + Eras.thresholdLabel(era) + " en esta run (llevas "
                + BigNum.formatPrecise(gs.runEarned) + ")", pw - 40), tx, r.y() + 13, 0xFF4F596B, false);
        if (rowH >= 26) {
            double frac = Math.min(1.0, Math.log10(gs.runEarned.doubleValue() + 1) / (6.0 * era));
            int bx = tx, bw = pw - 40;
            ctx.fill(bx, r.y() + rowH - 8, bx + bw, r.y() + rowH - 5, 0xFF0B0D12);
            ctx.fill(bx, r.y() + rowH - 8, bx + (int) (bw * frac), r.y() + rowH - 5, en.color());
        }
    }

    private void renderAchievement(DrawContext ctx, Row r, Achievements.Def a, GameState gs) {
        boolean done = gs.achievements.contains(a.id());
        int tx = px + 16;
        ctx.drawText(textRenderer, (done ? "★ " : "☆ ") + a.name(), tx, r.y() + 3, done ? GREEN : TEXT, true);
        ctx.drawText(textRenderer, textRenderer.trimToWidth(a.desc(), pw - 40), tx, r.y() + 13, done ? 0xFF8FBFA2 : DIM, false);
        String tag = done ? "+2%" : "bloqueado";
        ctx.drawText(textRenderer, tag, px + pw - 20 - textRenderer.getWidth(tag), r.y() + 3, done ? GREEN : 0xFF4F596B, false);
    }
}
