package dev.slotmine.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eras: 20 tramos de puntuación (millón, billón, trillón... vigintillón). Al ganar 10^(6·era) puntos en la run
 * se desbloquean 5 mejoras nuevas de esa era. Se reinician al ascender (como el resto de mejoras de la run).
 */
public final class Eras {
    public static final int COUNT = 20;

    public static final String[] NAMES = {"Millón", "Billón", "Trillón", "Cuatrillón", "Quintillón", "Sextillón",
            "Septillón", "Octillón", "Nonillón", "Decillón", "Undecillón", "Duodecillón", "Tredecillón",
            "Cuatordecillón", "Quindecillón", "Sexdecillón", "Septendecillón", "Octodecillón", "Novendecillón", "Vigintillón"};
    public static final String[] THEMES = {"Cobre", "Hierro", "Oro", "Lapislázuli", "Redstone", "Esmeralda", "Diamante",
            "Netherita", "Obsidiana", "Amatista", "Ender", "Wither", "Sculk", "Warden", "Faro", "Dragón", "Vacío",
            "Estrella", "Cosmos", "Infinito"};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
    public static final int[] COLORS = {0xFFE0875A, 0xFFC9CED6, 0xFFFFC94D, 0xFF4D7BFF, 0xFFFF4D4D, 0xFF4DE08A, 0xFF4DD0E1,
            0xFF9C7B6E, 0xFF7A5CFF, 0xFFD36BFF, 0xFFB26BFF, 0xFFB0B0B0, 0xFF3FD0C0, 0xFF3F8FA0, 0xFFFFF07A,
            0xFFFF6BD0, 0xFF8E6BFF, 0xFFFFE08A, 0xFF6BA8FF, 0xFFFFFFFF};

    private static final Pattern ID = Pattern.compile("^e(\\d+)_(\\d)$");

    private Eras() {}

    public static BigInteger threshold(int era) { return BigInteger.TEN.pow(6 * era); }
    public static String label(int era) { return "ERA " + ROMAN[era - 1] + " · " + NAMES[era - 1].toUpperCase(Locale.ROOT); }
    public static String thresholdLabel(int era) { return "1" + BigNum.SUFFIX[era]; }
    public static int color(int era) { return COLORS[era - 1]; }
    public static boolean unlocked(int era, GameState g) { return g.runEarned.compareTo(threshold(era)) >= 0; }

    /** Era (1..20) de un id de mejora "e{era}_{k}", o 0 si no es de era. */
    public static int tierOf(String id) {
        Matcher m = ID.matcher(id);
        if (!m.matches()) return 0;
        int t = Integer.parseInt(m.group(1));
        return t >= 1 && t <= COUNT ? t : 0;
    }

    // ---- fórmulas por nivel (se acumulan entre eras) ----
    public static double gain(int l)    { return Math.pow(1.5, l); }
    public static double critC(int l)   { return 0.004 * l; }
    public static double critM(int l)   { return 0.4 * l; }
    public static double ghost(int l)   { return Math.pow(1.25, l); }
    public static double luck(int l)    { return 1 + 0.12 * l; }
    public static double evt(int l)     { return 1 + 0.02 * l; }
    public static double ore(int l)     { return Math.pow(1.2, l); }
    public static double god(int l)     { return 0.03 * l; }

    private static String f1(double d) { return String.format(Locale.forLanguageTag("es-ES"), "%.1f", d); }
    private static String x(double d) {
        return d < 1000 ? "×" + String.format(Locale.forLanguageTag("es-ES"), "%.2f", d) : "×" + BigNum.formatPrecise(BigNum.fromDouble(d));
    }

    public static List<Upgrades.Def> defs() {
        List<Upgrades.Def> out = new ArrayList<>();
        for (int t = 1; t <= COUNT; t++) {
            double T = Math.pow(10, 6.0 * t);
            String th = THEMES[t - 1];
            String tag = " · Era " + ROMAN[t - 1];
            out.add(new Upgrades.Def("e" + t + "_1", Upgrades.Tab.ERAS, Upgrades.Scope.RUN, "Filón de " + th,
                    "Multiplica los puntos de cada bloque." + tag, T * 0.004, 2.0, 30, null,
                    l -> "Ahora " + x(gain(l))));
            out.add(new Upgrades.Def("e" + t + "_2", Upgrades.Tab.ERAS, Upgrades.Scope.RUN, "Resonancia de " + th,
                    "Más probabilidad y daño de Crit." + tag, T * 0.006, 2.2, 25, null,
                    l -> "+" + f1(critC(l) * 100) + "% · +" + f1(critM(l)) + "×"));
            out.add(new Upgrades.Def("e" + t + "_3", Upgrades.Tab.ERAS, Upgrades.Scope.RUN, "Espíritu de " + th,
                    "El Picador fantasma trabaja más." + tag, T * 0.008, 2.0, 30, null,
                    l -> "Fantasma " + x(ghost(l))));
            out.add(new Upgrades.Def("e" + t + "_4", Upgrades.Tab.ERAS, Upgrades.Scope.RUN, "Fortuna de " + th,
                    "Más probabilidad de JACKPOT y de eventos." + tag, T * 0.010, 2.3, 25, null,
                    l -> "Jackpot " + x(luck(l)) + " · Eventos " + x(evt(l))));
            out.add(new Upgrades.Def("e" + t + "_5", Upgrades.Tab.ERAS, Upgrades.Scope.RUN, "Pacto de " + th,
                    "Los minerales OP y los dioses pagan más." + tag, T * 0.012, 2.2, 25, null,
                    l -> "Minerales " + x(ore(l)) + " · Dioses +" + Math.round(god(l) * 100) + "%"));
        }
        return out;
    }
}
