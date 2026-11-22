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
        PRODUCCION("Producción"), SUERTE("Suerte"), ERAS("Eras"), PANTEON("Panteón"),
        BESTIAS("Bestias"), HABILIDADES("Habilidades"), EXTRAS("Extras"), ASCENSION("Ascensión"), LOGROS("Logros");
        public final String label;
        Tab(String l) { label = l; }
    }

    /** RUN: se reinicia al ascender (puntos) · PERMANENT: puntos, no se reinicia · CORE: se paga con núcleos. */
    public enum Scope { RUN, PERMANENT, CORE }

    public record Def(String id, Tab tab, Scope scope, String name, String desc,
                      double baseCost, double growth, int maxLevel,
                      double[] fixedCosts, IntFunction<String> effect) {}

    /** Ficha visual de un dios (color de acento y letra del icono). */
    public record God(String id, String name, String title, int color, String letter) {}

    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static String f1(double d) { return String.format(ES, "%.1f", d); }
    private static String f2(double d) { return String.format(ES, "%.2f", d); }
    private static String pc(double d) { return "+" + Math.round(d * 100) + "%"; }
    private static String x(double d) {
        return d < 1000 ? "×" + f2(d) : "×" + BigNum.formatPrecise(BigNum.fromDouble(d));
    }

    public static final List<God> GODS = List.of(
        new God("g_midas",  "Midas",  "rey de la codicia",      0xFFFFC94D, "M"),
        new God("g_hermes", "Hermes", "mensajero veloz",        0xFF8EE3EF, "H"),
        new God("g_tyche",  "Tyche",  "diosa del azar",         0xFF6EE7A0, "T"),
        new God("g_zeus",   "Zeus",   "señor del rayo",         0xFFFFF07A, "Z"),
        new God("g_cronos", "Cronos", "devorador del tiempo",   0xFFB39DDB, "C"),
        new God("g_anubis", "Anubis", "pesador de corazones",   0xFFE0A458, "A"),
        new God("g_hades",  "Hades",  "rey del inframundo",     0xFFFF6B4A, "H"),
        new God("g_odin",   "Odín",   "padre de todos",         0xFF7FA8FF, "O"),
        new God("g_seven",  "El Gran Siete", "dios de la tragaperras", 0xFFFF4D6D, "7")
    );

    public static God godOf(String id) {
        for (God g : GODS) if (g.id().equals(id)) return g;
        return null;
    }

    private static final List<Def> BASE = List.of(
        // ---------- PRODUCCIÓN (RUN) ----------
        new Def("vein", Tab.PRODUCCION, Scope.RUN, "Veta dorada", "Multiplica los puntos de cada bloque.",
                25, 1.6, -1, null, l -> "Ahora " + x(Stats.vein(l))),
        new Def("magnet", Tab.PRODUCCION, Scope.RUN, "Imán de minerales", "Más puntos al romper minerales OP.",
                120, 1.75, -1, null, l -> "Minerales OP " + x(Stats.magnet(l))),
        new Def("idle", Tab.PRODUCCION, Scope.RUN, "Picador fantasma", "Un minero invisible rompe bloques por ti, aunque estés quieto.",
                400, 1.65, 30, null, l -> l == 0 ? "Inactivo" : f1(Stats.idle(l)) + " bloques/s"),
        new Def("streak", Tab.PRODUCCION, Scope.RUN, "Racha", "Bonus por picar sin parar (llega al máx. con 100 bloques seguidos).",
                150, 1.8, 20, null, l -> "Máx. +" + (int) Math.round(Stats.streak(l) * 100) + "%"),
        new Def("fever_up", Tab.PRODUCCION, Scope.RUN, "Fiebre intensa", "Más duración y multiplicador de la Fiebre.",
                800, 2.0, 15, null, l -> "+" + l + "s · +" + f2(0.15 * l) + "×"),
        new Def("depth", Tab.PRODUCCION, Scope.RUN, "Cavador profundo", "Más puntos al picar, y muchos más por debajo de Y=0 (deepslate).",
                600, 1.9, 30, null, l -> "Arriba " + x(Stats.depthShallow(l)) + " · Y<0 " + x(Stats.depthDeep(l))),
        new Def("combo", Tab.PRODUCCION, Scope.RUN, "Maestro de rodillos", "Los combos de rodillos (IGUALES y 777) pagan más.",
                1_000, 2.0, 20, null, l -> "Combos " + x(Stats.comboM(l))),

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
        new Def("pulse", Tab.SUERTE, Scope.RUN, "Pulso febril", "Más probabilidad de que salte la Fiebre (requiere desbloqueo).",
                40_000, 2.0, 20, null, l -> "+" + f1(Stats.pulse(l) * 100) + "%"),
        new Def("console", Tab.SUERTE, Scope.RUN, "Premio de consolación", "Cuando casi te sale el jackpot, te dan un premio.",
                20_000, 2.2, 20, null, l -> l == 0 ? "Sin premio" : "CASI paga " + x(Stats.consolationM(l)) + " bloque"),
        new Def("jpay", Tab.SUERTE, Scope.RUN, "Jackpot jugoso", "El jackpot paga más.",
                60_000, 2.3, 20, null, l -> "Jackpot paga " + x(Stats.jpay(l))),
        new Def("evt", Tab.SUERTE, Scope.RUN, "Nubes de oro", "Más Lluvias doradas, más largas y más fuertes (requiere desbloqueo).",
                1_000_000, 2.1, 20, null, l -> "Cada ~" + (int) (180 / Stats.evtFreq(l)) + "s · " + (int) Stats.evtDur(l) + "s · " + x(Stats.evtMult(l))),

        // ---------- PANTEÓN (RUN) — requiere "Desbloquear Panteón" ----------
        new Def("g_midas", Tab.PANTEON, Scope.RUN, "Midas", "Su toque convierte bloques en oro: pagan muchísimo más al azar.",
                250_000, 2.0, 25, null, l -> l == 0 ? "Dormido" : pc(Stats.midasCh(l)) + " de tocar oro · ×" + f1(Stats.midasM(l, 1))),
        new Def("g_hermes", Tab.PANTEON, Scope.RUN, "Hermes", "La racha sube más rápido y aguanta más sin picar. Acelera al fantasma.",
                400_000, 2.0, 25, null, l -> l == 0 ? "Dormido" : "Racha +" + Stats.hermesInc(l) + "/bloque · " + f1(Stats.hermesWin(l) / 1000.0) + "s · fantasma " + x(Stats.hermesGhost(l))),
        new Def("g_tyche", Tab.PANTEON, Scope.RUN, "Tyche", "Todo lo azaroso mejora: eventos, jackpot y pity.",
                600_000, 2.05, 25, null, l -> l == 0 ? "Dormida" : "Eventos " + x(Stats.tycheEvt(l)) + " · Jackpot " + x(Stats.tycheJp(l))),
        new Def("g_zeus", Tab.PANTEON, Scope.RUN, "Zeus", "Lanza rayos sobre tus bloques con puntos extra.",
                1_000_000, 2.1, 25, null, l -> l == 0 ? "Dormido" : pc(Stats.zeusCh(l)) + " · " + Stats.zeusN(l) + " rayo(s) · ×" + f1(Stats.zeusM(l, 1))),
        new Def("g_cronos", Tab.PANTEON, Scope.RUN, "Cronos", "La Fiebre salta más y dura más.",
                2_000_000, 2.1, 25, null, l -> l == 0 ? "Dormido" : "Fiebre " + x(Stats.cronosCh(l)) + " · +" + f1(Stats.cronosSec(l)) + "s"),
        new Def("g_anubis", Tab.PANTEON, Scope.RUN, "Anubis", "Los minerales valen más y pesa tu corazón cada cierto número de bloques.",
                4_000_000, 2.15, 25, null, l -> l == 0 ? "Dormido" : "Minerales " + x(Stats.anubisOre(l)) + " · pesaje cada " + Stats.anubisEv(l)),
        new Def("g_hades", Tab.PANTEON, Scope.RUN, "Hades", "Multiplica todo lo que picas, y más aún bajo Y=0.",
                8_000_000, 2.2, 25, null, l -> l == 0 ? "Dormido" : "Y<0 " + x(Stats.hadesD(l, 1)) + " · arriba " + x(Stats.hadesS(l))),
        new Def("g_odin", Tab.PANTEON, Scope.RUN, "Odín", "Críticos brutales y, a veces, Ragnarök.",
                20_000_000, 2.25, 25, null, l -> l == 0 ? "Dormido" : "Crit +" + f1(Stats.odinCrit(l)) + "× · Ragnarök " + pc(Stats.odinRagCh(l))),
        new Def("g_seven", Tab.PANTEON, Scope.RUN, "El Gran Siete", "Los combos pagan más y encienden la Fiebre.",
                50_000_000, 2.3, 25, null, l -> l == 0 ? "Dormido" : "Combos " + x(Stats.sevenCombo(l)) + " · Fiebre +" + f1(Stats.sevenFever(l)) + "s"),

        // ---------- EXTRAS (PERMANENT) ----------
        new Def("reels", Tab.EXTRAS, Scope.PERMANENT, "Nuevo rodillo", "Desbloquea un rodillo más (máx. 6) para ver números mayores.",
                0, 1, 3, new double[]{500, 5_000, 50_000}, l -> "Rodillos: " + (3 + l) + "/6"),
        new Def("u_chain", Tab.EXTRAS, Scope.PERMANENT, "Desbloquear Chain Arc", "Arcos visuales hacia minerales cercanos + puntos extra.",
                5_000, 1, 1, null, l -> l > 0 ? "Activo" : "Bloqueado"),
        new Def("u_gods", Tab.EXTRAS, Scope.PERMANENT, "Desbloquear Panteón", "Abre la pestaña Panteón: 9 dioses que se compran como mejoras.",
                250_000, 1, 1, null, l -> l > 0 ? "Activo" : "Bloqueado"),
        new Def("u_shock", Tab.EXTRAS, Scope.PERMANENT, "Desbloquear Shockwave", "Onda expansiva con puntos extra.",
                500_000, 1, 1, null, l -> l > 0 ? "Activo" : "Bloqueado"),
        new Def("u_events", Tab.EXTRAS, Scope.PERMANENT, "Desbloquear Lluvia dorada", "Evento aleatorio: durante unos segundos todo paga el triple.",
                5_000_000, 1, 1, null, l -> l > 0 ? "Activo" : "Bloqueado"),
        new Def("u_fever", Tab.EXTRAS, Scope.PERMANENT, "Desbloquear Fever", "Racha de multiplicador temporal al azar.",
                50_000_000, 1, 1, null, l -> l > 0 ? "Activo" : "Bloqueado"),

        // ---------- ASCENSIÓN (CORE) ----------
        new Def("c_mult", Tab.ASCENSION, Scope.CORE, "Núcleo resonante", "Multiplica TODA la producción, para siempre.",
                1, 1.9, 40, null, l -> "Ahora " + x(Stats.coreMultLvl(l))),
        new Def("c_luck", Tab.ASCENSION, Scope.CORE, "Núcleo afortunado", "Más probabilidad de JACKPOT, para siempre.",
                2, 2.0, 20, null, l -> "Jackpot " + x(Stats.coreLuck(l))),
        new Def("c_start", Tab.ASCENSION, Scope.CORE, "Núcleo semilla", "Empiezas cada ascensión con puntos.",
                1, 2.2, 15, null, l -> l == 0 ? "Sin bonus" : "Inicio: " + BigNum.format(Stats.startPoints(l))),
        new Def("c_crit", Tab.ASCENSION, Scope.CORE, "Núcleo crítico", "Más probabilidad y daño de Crit, para siempre.",
                2, 1.9, 20, null, l -> "+" + f1(Stats.coreCritC(l) * 100) + "% · +" + f1(Stats.coreCritM(l)) + "×"),
        new Def("c_ghost", Tab.ASCENSION, Scope.CORE, "Núcleo fantasma", "El Picador fantasma trabaja más rápido, para siempre.",
                2, 1.9, 20, null, l -> "Fantasma " + x(Stats.coreGhost(l))),
        new Def("c_divine", Tab.ASCENSION, Scope.CORE, "Núcleo divino", "Potencia los pagos de los dioses, para siempre.",
                3, 2.0, 20, null, l -> "Poder divino " + x(Stats.coreDivine(l))),
        new Def("c_altar", Tab.ASCENSION, Scope.CORE, "Altar eterno", "Cada ascensión empiezas con todos los dioses ya despiertos.",
                3, 1.7, 10, null, l -> l == 0 ? "Sin altar" : "Dioses al nivel " + l)
    );


    /**
     * Coste objetivo del ÚLTIMO nivel (log10). El crecimiento por nivel se calcula para llegar justo ahí, de modo que cada
     * mejora con tope se termina en su etapa del juego (equilibrado con el simulador de economía).
     */
    private static final java.util.Map<String, Double> MAX_COST_LOG10 = java.util.Map.ofEntries(
        java.util.Map.entry("idle", 14.0), java.util.Map.entry("streak", 12.0), java.util.Map.entry("fever_up", 15.0),
        java.util.Map.entry("depth", 16.0), java.util.Map.entry("combo", 14.0),
        java.util.Map.entry("crit_c", 18.0), java.util.Map.entry("crit_m", 18.0), java.util.Map.entry("chain_c", 17.0),
        java.util.Map.entry("shock_c", 19.0), java.util.Map.entry("luck", 19.0), java.util.Map.entry("pity", 14.0),
        java.util.Map.entry("pulse", 16.0), java.util.Map.entry("console", 15.0), java.util.Map.entry("jpay", 17.0),
        java.util.Map.entry("evt", 20.0),
        java.util.Map.entry("g_midas", 21.0), java.util.Map.entry("g_hermes", 23.0), java.util.Map.entry("g_tyche", 25.0),
        java.util.Map.entry("g_zeus", 27.0), java.util.Map.entry("g_cronos", 29.0), java.util.Map.entry("g_anubis", 31.0),
        java.util.Map.entry("g_hades", 33.0), java.util.Map.entry("g_odin", 35.0), java.util.Map.entry("g_seven", 37.0),
        java.util.Map.entry("b_hunt", 20.0), java.util.Map.entry("b_roar", 18.0), java.util.Map.entry("b_blood", 17.0),
        java.util.Map.entry("b_tiger", 24.0), java.util.Map.entry("b_dragon", 28.0), java.util.Map.entry("b_phoenix", 32.0),
        java.util.Map.entry("b_kraken", 36.0), java.util.Map.entry("b_fenrir", 40.0), java.util.Map.entry("b_monkey", 44.0),
        java.util.Map.entry("s_fury", 13.0), java.util.Map.entry("s_quake", 15.0), java.util.Map.entry("s_storm", 18.0),
        java.util.Map.entry("s_over", 20.0), java.util.Map.entry("s_streak", 22.0), java.util.Map.entry("s_jackpot", 24.0),
        java.util.Map.entry("s_vein", 21.0)
    );

    /** Catálogo completo: base + 20 eras (100 mejoras) + bestias + habilidades. */
    public static final List<Def> ALL = buildAll();

    private static List<Def> buildAll() {
        java.util.ArrayList<Def> raw = new java.util.ArrayList<>(BASE);
        raw.addAll(Eras.defs());
        raw.addAll(Beasts.defs());
        raw.addAll(Skills.defs());
        java.util.ArrayList<Def> all = new java.util.ArrayList<>(raw.size());
        for (Def d : raw) {
            Double target = MAX_COST_LOG10.get(d.id());
            if (target != null && d.maxLevel() > 1 && d.fixedCosts() == null) {
                double growth = Math.pow(10, (target - Math.log10(d.baseCost())) / (d.maxLevel() - 1));
                d = new Def(d.id(), d.tab(), d.scope(), d.name(), d.desc(), d.baseCost(), growth, d.maxLevel(), null, d.effect());
            }
            all.add(d);
        }
        return List.copyOf(all);
    }

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
