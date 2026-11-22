package dev.slotmine.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Habilidades activas: se compran (permanentes), se suben de nivel y se lanzan con una tecla, con enfriamiento. */
public final class Skills {
    public record Skill(String id, String name, String key, int color, double baseCd) {}

    public static final List<Skill> ALL = List.of(
        new Skill("s_fury",    "Furia de Fiebre",  "Z", 0xFF4DD0E1, 60),
        new Skill("s_quake",   "Sismo",            "X", 0xFFE0A458, 45),
        new Skill("s_storm",   "Tormenta",         "C", 0xFFFFF07A, 90),
        new Skill("s_over",    "Sobrecarga",       "V", 0xFFB26BFF, 120),
        new Skill("s_streak",  "Racha eterna",     "B", 0xFFFF8A3D, 75),
        new Skill("s_jackpot", "Golpe maestro",    "N", 0xFFFF4D6D, 300)
    );

    public static final int FURY = 0, QUAKE = 1, STORM = 2, OVER = 3, STREAK = 4, JACKPOT = 5;

    private Skills() {}

    public static int level(GameState g, int i) { return g.perm.getOrDefault(ALL.get(i).id(), 0); }

    public static double cdFactor(int l) { return Math.max(0.4, 1 - 0.05 * l); }
    public static double cooldown(int i, int l) { return ALL.get(i).baseCd() * cdFactor(l); }

    // potencia por nivel
    public static double furyDur(int l)     { return 6 + l; }
    public static double quakeMult(int l)   { return 60 + 30 * l; }
    public static double stormDur(int l)    { return 6 + 0.5 * l; }
    public static double stormMult(int l)   { return 4 + l; }           // x bloque por segundo
    public static double overDur(int l)     { return 15 + l; }
    public static double overMult(int l)    { return 3 + 0.5 * l; }
    public static double streakDur(int l)   { return 12 + l; }
    public static double jackpotBoost(int l){ return 1 + 0.10 * l; }

    private static String f(double d) { return String.format(Locale.forLanguageTag("es-ES"), "%.1f", d).replace(",0", ""); }

    public static List<Upgrades.Def> defs() {
        double[] base = {2_000_000, 5_000_000, 25_000_000, 100_000_000, 500_000_000, 5_000_000_000.0};
        List<Upgrades.Def> out = new ArrayList<>();
        String[] desc = {
            "Enciende la Fiebre al instante (no necesita el desbloqueo).",
            "Un seísmo paga de golpe una montaña de puntos.",
            "Una tormenta eléctrica cae a tu alrededor y paga cada segundo.",
            "El Picador fantasma se desboca durante unos segundos.",
            "Racha al máximo al instante y no se pierde mientras dura.",
            "El siguiente bloque es JACKPOT garantizado."
        };
        for (int i = 0; i < ALL.size(); i++) {
            final int idx = i;
            Skill s = ALL.get(i);
            out.add(new Upgrades.Def(s.id(), Upgrades.Tab.HABILIDADES, Upgrades.Scope.PERMANENT, s.name(),
                    desc[i] + " Tecla " + s.key() + " (configurable en Controles).",
                    base[i], 2.4, 10, null, l -> {
                        if (l == 0) return "Bloqueada · tecla " + s.key();
                        String cd = " · enfría " + (int) cooldown(idx, l) + "s";
                        return switch (idx) {
                            case FURY -> "Fiebre " + f(furyDur(l)) + "s" + cd;
                            case QUAKE -> "Paga ×" + (int) quakeMult(l) + " bloque" + cd;
                            case STORM -> f(stormDur(l)) + "s · ×" + (int) stormMult(l) + "/s" + cd;
                            case OVER -> "Fantasma ×" + f(overMult(l)) + " · " + f(overDur(l)) + "s" + cd;
                            case STREAK -> f(streakDur(l)) + "s sin perderla" + cd;
                            default -> "Jackpot ×" + f(jackpotBoost(l)) + cd;
                        };
                    }));
        }
        return out;
    }
}
