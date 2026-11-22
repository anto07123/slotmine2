package dev.slotmine.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bestias (v2): cada bloque que picas tiene una probabilidad MUY baja de despertar a una de tus bestias. Cuando ocurre,
 * hay secuencia a pantalla completa, un pago inmediato (en "bloques destrozados") y una bendición única con su propio
 * mecanismo: ninguna se parece a otra ni a las habilidades del jugador.
 *  - Tigre Dorado: críticos garantizados   - Dragón Esmeralda: arcos y ondas por todas partes
 *  - Fénix Solar: suerte x3 en todo         - Kraken Abisal: los dioses se desatan (x3)
 *  - Lobo Fenrir: habilidades al instante y recarga x4  - Mono Rey: tributo de tus puntos
 * Solo cuentan los bloques que ROMPES tú (el Picador fantasma no las activa).
 */
public final class Beasts {
    public record Beast(String id, String name, String sub, int color, int color2, String letter,
                        double chance, double payBase, double paySlope, double baseCost, double growth,
                        String buffName, String perk) {}

    public static final int TIGER = 0, DRAGON = 1, PHOENIX = 2, KRAKEN = 3, FENRIR = 4, MONKEY = 5;
    /** Segundos de bloqueo tras una invocación (evita solapes). */
    public static final int LOCKOUT_SEC = 60;

    public static final List<Beast> ALL = List.of(
        new Beast("b_tiger",   "Tigre Dorado",     "¡El tigre ha despertado!",    0xFFFFA726, 0xFF2B1A0A, "T",
                0.00100, 154, 46, 1_000_000, 2.00, "PRESA MARCADA", "Tus próximos golpes son críticos seguros."),
        new Beast("b_dragon",  "Dragón Esmeralda", "¡El dragón escupe fuego!",    0xFF4DE08A, 0xFF0B2A1A, "D",
                0.00075, 196, 59, 8_000_000, 2.10, "ALIENTO ARDIENTE", "Arcos en cadena y ondas expansivas por doquier."),
        new Beast("b_phoenix", "Fénix Solar",      "¡El fénix renace en llamas!", 0xFFFF6B3D, 0xFF3A0F05, "F",
                0.00055, 273, 80, 60_000_000, 2.15, "SOL DE LA FORTUNA", "Toda tu suerte se multiplica."),
        new Beast("b_kraken",  "Kraken Abisal",    "¡El kraken sale del abismo!", 0xFF3FA9FF, 0xFF06182E, "K",
                0.00040, 400, 115, 500_000_000, 2.20, "TENTÁCULOS DIVINOS", "Tus dioses se desatan (x3)."),
        new Beast("b_fenrir",  "Lobo Fenrir",      "¡Fenrir aúlla a la luna!",    0xFFBFE9FF, 0xFF0B1A2E, "L",
                0.00030, 546, 157, 4_000_000_000.0, 2.25, "VENTISCA", "Habilidades listas al instante y recarga x4."),
        new Beast("b_monkey",  "Mono Rey",         "¡El Rey Mono rompe el cielo!",0xFFFFD84D, 0xFF3A2A05, "M",
                0.00015, 1050, 315, 40_000_000_000.0, 2.30, "TRIBUTO REAL", "Además cobra un porcentaje de tus puntos.")
    );

    private Beasts() {}

    public static Beast byId(String id) {
        for (Beast b : ALL) if (b.id().equals(id)) return b;
        return null;
    }

    public static int level(GameState g, int i) { return g.run.getOrDefault(ALL.get(i).id(), 0); }

    /** Bloques que destroza (pago inmediato = bloques x multiplicador de tu último bloque). */
    public static double blocks(int i, int l) { Beast b = ALL.get(i); return b.payBase() + b.paySlope() * l; }
    /** Probabilidad por bloque roto de despertar (nivel l, sin multiplicadores globales). */
    public static double chance(int i, int l) { return ALL.get(i).chance() * (1 + 0.02 * l); }

    // bendiciones (duraciones en segundos, antes de multiplicar por la mejora Pacto de sangre)
    public static int tigerCharges(int l)   { return 6 + l / 3; }
    public static double tigerWindow()      { return 25; }
    public static double dragonDur(int l)   { return 12 + 0.25 * l; }
    public static double dragonChain()      { return 0.50; }
    public static double dragonShock()      { return 0.35; }
    public static double phoenixDur(int l)  { return 15 + 0.3 * l; }
    public static double phoenixLuck(int l) { return 3 + 0.08 * l; }
    public static double krakenDur(int l)   { return 15 + 0.3 * l; }
    public static double krakenGods()       { return 3.0; }
    public static double fenrirDur(int l)   { return 20 + 0.4 * l; }
    public static double fenrirHaste()      { return 4.0; }
    public static double monkeyPct(int l)   { return 0.004 + 0.0003 * l; }
    public static double monkeyCap(int l)   { return 600 + 40 * l; } // en bloques

    // mejoras compartidas
    public static double hunt(int l) { return 1 + 0.08 * l; }
    public static double roar(int l) { return 1 + 0.10 * l; }
    public static double blood(int l) { return 1 + 0.06 * l; }

    private static String f1(double d) { return String.format(Locale.forLanguageTag("es-ES"), "%.1f", d); }
    private static String pc(double d) { return String.format(Locale.forLanguageTag("es-ES"), "%.1f%%", d * 100); }

    private static String every(int i, int l) { return "1 cada ~" + BigNum.dotted(Math.round(1.0 / chance(i, l))) + " bloques"; }

    public static List<Upgrades.Def> defs() {
        List<Upgrades.Def> out = new ArrayList<>();
        out.add(new Upgrades.Def("b_hunt", Upgrades.Tab.BESTIAS, Upgrades.Scope.RUN, "Instinto cazador",
                "Todas las bestias despiertan con más frecuencia (solo cuentan bloques que rompes tú).",
                800_000, 2.0, 25, null, l -> "Frecuencia ×" + f1(hunt(l))));
        out.add(new Upgrades.Def("b_roar", Upgrades.Tab.BESTIAS, Upgrades.Scope.RUN, "Rugido ancestral",
                "Todas las bestias pagan más puntos al despertar.",
                2_000_000, 2.1, 20, null, l -> "Pago ×" + f1(roar(l))));
        out.add(new Upgrades.Def("b_blood", Upgrades.Tab.BESTIAS, Upgrades.Scope.RUN, "Pacto de sangre",
                "Las bendiciones de las bestias duran más.",
                5_000_000, 2.1, 20, null, l -> "Duración ×" + f1(blood(l))));
        for (int i = 0; i < ALL.size(); i++) {
            final int idx = i;
            Beast b = ALL.get(i);
            out.add(new Upgrades.Def(b.id(), Upgrades.Tab.BESTIAS, Upgrades.Scope.RUN, b.name(),
                    b.perk() + " Bendición: " + b.buffName() + ".",
                    b.baseCost(), b.growth(), 25, null,
                    l -> l == 0 ? "Dormida · " + every(idx, 1).replace(" cada ~", " cada ~")
                            : "Destroza ~" + (int) blocks(idx, l) + " bloques · " + every(idx, l)));
        }
        return out;
    }
}
