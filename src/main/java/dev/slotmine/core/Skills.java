package dev.slotmine.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Habilidades activas (v2): se compran (permanentes), suben de nivel y se lanzan con una tecla. Cada una trabaja sobre
 * un eje distinto, y ninguno coincide con las bendiciones de las bestias:
 *  Frenesí = ventana de multiplicador · Sismo = pago instantáneo · Tormenta = pago por segundo · Sobrecarga = fantasma
 *  Racha eterna = racha fija + bonus · Golpe maestro = jackpot garantizado · Veta viva = todo cuenta como mineral
 */
public final class Skills {
    public record Skill(String id, String name, String key, int color, double baseCd) {}

    public static final List<Skill> ALL = List.of(
        new Skill("s_fury",    "Frenesí",        "Z", 0xFF4DD0E1, 60),
        new Skill("s_quake",   "Sismo",          "X", 0xFFE0A458, 40),
        new Skill("s_storm",   "Tormenta",       "C", 0xFFFFF07A, 90),
        new Skill("s_over",    "Sobrecarga",     "V", 0xFFB26BFF, 100),
        new Skill("s_streak",  "Racha eterna",   "B", 0xFFFF8A3D, 75),
        new Skill("s_jackpot", "Golpe maestro",  "N", 0xFFFF4D6D, 240),
        new Skill("s_vein",    "Veta viva",      "R", 0xFF4DE08A, 90)
    );

    public static final int FURY = 0, QUAKE = 1, STORM = 2, OVER = 3, STREAK = 4, JACKPOT = 5, VEIN = 6;
    public static final int MAX_LEVEL = 10;

    private Skills() {}

    public static int level(GameState g, int i) { return g.perm.getOrDefault(ALL.get(i).id(), 0); }

    public static double cdFactor(int l) { return Math.max(0.45, 1 - 0.04 * l); }
    public static double cooldown(int i, int l) { return ALL.get(i).baseCd() * cdFactor(l); }

    public static double furyDur(int l)      { return 6 + 0.5 * l; }
    public static double furyMult(int l)     { return 1.5 + 0.12 * l; }   // se SUMA con Fiebre y Lluvia dorada
    public static double quakeMult(int l)    { return 35 + 12 * l; }
    public static double stormDur(int l)     { return 6 + 0.5 * l; }
    public static double stormMult(int l)    { return 2.5 + 0.6 * l; }      // x bloque por segundo
    public static double overDur(int l)      { return 12 + l; }
    public static double overMult(int l)     { return 2 + 0.3 * l; }
    public static double streakDur(int l)    { return 12 + l; }
    public static double streakBonus(int l)  { return 0.30 + 0.03 * l; }
    public static double jackpotBoost(int l) { return 1 + 0.10 * l; }
    public static double veinDur(int l)      { return 8 + 0.5 * l; }
    public static double veinMult(int l)     { return 2.5 + 0.3 * l; }

    private static String f(double d) { return String.format(Locale.forLanguageTag("es-ES"), "%.1f", d).replace(",0", ""); }

    public static List<Upgrades.Def> defs() {
        double[] base = {2_000_000, 5_000_000, 25_000_000, 100_000_000, 500_000_000, 5_000_000_000.0, 1_000_000_000};
        String[] desc = {
            "Durante unos segundos TODO paga más (se suma a la Fiebre, no se multiplica).",
            "Un seísmo paga de golpe una montaña de puntos.",
            "Una tormenta eléctrica cae a tu alrededor y paga cada segundo.",
            "El Picador fantasma se desboca durante unos segundos.",
            "Racha al máximo y fija, con un bonus extra de puntos mientras dura.",
            "El siguiente bloque es JACKPOT garantizado.",
            "Durante unos segundos CUALQUIER bloque paga como si fuera un mineral."
        };
        List<Upgrades.Def> out = new ArrayList<>();
        for (int i = 0; i < ALL.size(); i++) {
            final int idx = i;
            Skill s = ALL.get(i);
            out.add(new Upgrades.Def(s.id(), Upgrades.Tab.HABILIDADES, Upgrades.Scope.PERMANENT, s.name(),
                    desc[i] + " Tecla " + s.key() + " (configurable en Controles).",
                    base[i], 2.4, MAX_LEVEL, null, l -> {
                        if (l == 0) return "Bloqueada · tecla " + s.key();
                        String cd = " · enfría " + (int) cooldown(idx, l) + "s";
                        return switch (idx) {
                            case FURY -> "+" + f(furyMult(l) - 1) + "× " + f(furyDur(l)) + "s" + cd;
                            case QUAKE -> "Paga ×" + (int) quakeMult(l) + " bloque" + cd;
                            case STORM -> f(stormDur(l)) + "s · ×" + f(stormMult(l)) + "/s" + cd;
                            case OVER -> "Fantasma ×" + f(overMult(l)) + " · " + f(overDur(l)) + "s" + cd;
                            case STREAK -> f(streakDur(l)) + "s · +" + (int) Math.round(streakBonus(l) * 100) + "%" + cd;
                            case JACKPOT -> "Jackpot ×" + f(jackpotBoost(l)) + cd;
                            default -> "Todo ×" + f(veinMult(l)) + " · " + f(veinDur(l)) + "s" + cd;
                        };
                    }));
        }
        return out;
    }
}
