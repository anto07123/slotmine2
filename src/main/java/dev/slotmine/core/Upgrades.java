package dev.slotmine.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

/** Catálogo de mejoras. Coste = baseCost * growth^nivel (exponencial). */
public final class Upgrades {
    public enum Tab {
        PRODUCCION("Producción"), SUERTE("Suerte"), DESBLOQUEOS("Desbloqueos"), ASCENSION("Ascensión");
        public final String label;
        Tab(String l) { label = l; }
    }

    /** RUN: se reinicia al ascender (puntos) · PERMANENT: puntos, no se reinicia · CORE: se paga con núcleos. */
    public enum Scope { RUN, PERMANENT, CORE }

    public record Def(String id, Tab tab, Scope scope, String name, String desc,
                      double baseCost, double growth, int maxLevel,
                      double[] fixedCosts, IntFunction<String> effect) {}

    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static String f1(double d) { return String.format(ES, "%.1f", d); }
    private static String f2(double d) { return String.format(ES, "%.2f", d); }
    private static String x(double d) {
        return d < 1000 ? "×" + f2(d) : "×" + BigNum.formatPrecise(BigNum.fromDouble(d));
    }

    public static final List<Def> ALL = List.of(
        // ---------- PRODUCCIÓN (RUN) ----------
        new Def("vein", Tab.PRODUCCION, Scope.RUN, "Veta dorada", "Multiplica los puntos de cada bloque.",
                25, 1.6, -1, null, l -> "Ahora " + x(Stats.vein(l))),
        new Def("magnet", Tab.PRODUCCION, Scope.RUN, "Imán de minerales", "Más puntos al romper minerales OP.",
                120, 1.75, -1, null, l -> "Minerales OP " + x(Stats.magnet(l))),
        new Def("idle", Tab.PRODUCCION, Scope.RUN, "Generador pasivo", "Puntos por segundo aunque no piques.",
                200, 1.7, -1, null, l -> l == 0 ? "Inactivo" : "Base " + x(Stats.idle(l)) + "/s"),
        new Def("streak", Tab.PRODUCCION, Scope.RUN, "Racha", "Bonus por picar sin parar (llega al máx. con 100 bloques seguidos).",
                150, 1.8, 20, null, l -> "Máx. +" + (int) Math.round(Stats.streak(l) * 100) + "%"),
        new Def("fever_up", Tab.PRODUCCION, Scope.RUN, "Fiebre intensa", "Más duración y multiplicador de la Fiebre.",
                800, 2.0, 15, null, l -> "+" + l + "s · +" + f2(0.15 * l) + "×"),

        // ---------- SUERTE (RUN) ----------
        new Def("crit_c", Tab.SUERTE, Scope.RUN, "Chispa crítica", "Más probabilidad de Crit.",
                300, 1.9, 40, null, l -> "+" + f1(Stats.critC(l) * 100) + "%"),
        new Def("crit_m", Tab.SUERTE, Scope.RUN, "Daño crítico", "Más multiplicador de Crit.",
                500, 2.1, 30, null, l -> "+" + f1(Stats.critM(l)) + "×"),
        new Def("chain_c", Tab.SUERTE, Scope.RUN, "Chain Arc", "Más probabilidad de arco en cadena (requiere desbloqueo).",
                2_000, 2.0, 30, null, l -> "+" + f1(Stats.chainC(l) * 100) + "%"),
        new Def("shock_c", Tab.SUERTE, Scope.RUN, "Shockwave", "Más probabilidad de onda expansiva (requiere desbloqueo).",
                100_000, 2.0, 30, null, l -> "+" + f1(Stats.shockC(l) * 100) + "%"),
        new Def("luck", Tab.SUERTE, Scope.RUN, "Trébol", "Más probabilidad de JACKPOT.",
                10_000, 2.4, 25, null, l -> "Jackpot " + x(Stats.luck(l))),
        new Def("pity", Tab.SUERTE, Scope.RUN, "Piedad", "El jackpot garantizado llega antes.",
                50_000, 2.6, 12, null, l -> "Pity -" + (100 - (int) Math.round(Stats.pity(l) * 100)) + "%"),

        // ---------- DESBLOQUEOS (PERMANENT) ----------
        new Def("reels", Tab.DESBLOQUEOS, Scope.PERMANENT, "Nuevo rodillo", "Desbloquea un rodillo más (máx. 6) para ver números mayores.",
                0, 1, 3, new double[]{500, 5_000, 50_000}, l -> "Rodillos: " + (3 + l) + "/6"),
        new Def("u_chain", Tab.DESBLOQUEOS, Scope.PERMANENT, "Desbloquear Chain Arc", "Arcos visuales hacia minerales cercanos + puntos extra.",
                5_000, 1, 1, null, l -> l > 0 ? "Activo" : "Bloqueado"),
        new Def("u_shock", Tab.DESBLOQUEOS, Scope.PERMANENT, "Desbloquear Shockwave", "Onda expansiva con puntos extra.",
                500_000, 1, 1, null, l -> l > 0 ? "Activo" : "Bloqueado"),
        new Def("u_fever", Tab.DESBLOQUEOS, Scope.PERMANENT, "Desbloquear Fever", "Racha de multiplicador temporal al azar.",
                50_000_000, 1, 1, null, l -> l > 0 ? "Activo" : "Bloqueado"),

        // ---------- ASCENSIÓN (CORE) ----------
        new Def("c_mult", Tab.ASCENSION, Scope.CORE, "Núcleo resonante", "Multiplica TODA la producción, para siempre.",
                1, 1.9, 40, null, l -> "Ahora " + x(Stats.coreMultLvl(l))),
        new Def("c_luck", Tab.ASCENSION, Scope.CORE, "Núcleo afortunado", "Más probabilidad de JACKPOT, para siempre.",
                2, 2.0, 20, null, l -> "Jackpot " + x(Stats.coreLuck(l))),
        new Def("c_start", Tab.ASCENSION, Scope.CORE, "Núcleo semilla", "Empiezas cada ascensión con puntos.",
                1, 2.2, 15, null, l -> l == 0 ? "Sin bonus" : "Inicio: " + BigNum.format(Stats.startPoints(l)))
    );

    /** Coste del nivel `lvl` -> `lvl+1` (puntos o núcleos según el scope). */
    public static BigInteger costOf(Def d, int lvl) {
        if (d.fixedCosts() != null) {
            return BigDecimal.valueOf(d.fixedCosts()[Math.min(lvl, d.fixedCosts().length - 1)]).toBigInteger();
        }
        BigDecimal c = BigDecimal.valueOf(d.baseCost())
                .multiply(BigDecimal.valueOf(d.growth()).pow(lvl, MathContext.DECIMAL64));
        return c.setScale(0, RoundingMode.CEILING).toBigInteger();
    }

    public static Def byId(String id) {
        for (Def d : ALL) if (d.id().equals(id)) return d;
        return null;
    }
}
