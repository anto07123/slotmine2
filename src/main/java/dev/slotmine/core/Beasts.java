package dev.slotmine.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bestias: cada bloque que picas carga el Espíritu. Al llenarse se invoca una bestia con una secuencia a pantalla
 * completa que "destroza" decenas de bloques de golpe y paga una montaña de puntos.
 */
public final class Beasts {
    public record Beast(String id, String name, String sub, int color, int color2, String letter,
                        double mult, double baseCost, double growth, String perk) {}

    public static final double SPIRIT_MAX = 100;

    public static final List<Beast> ALL = List.of(
        new Beast("b_tiger",   "Tigre Dorado",     "¡El tigre ha despertado!",    0xFFFFA726, 0xFF2B1A0A, "T", 1.5, 1_000_000,       2.00, "Zarpazos de oro"),
        new Beast("b_dragon",  "Dragón Esmeralda", "¡El dragón escupe fuego!",    0xFF4DE08A, 0xFF0B2A1A, "D", 1.4, 8_000_000,       2.10, "Enciende la Fiebre 8s"),
        new Beast("b_phoenix", "Fénix Solar",      "¡El fénix renace en llamas!", 0xFFFF6B3D, 0xFF3A0F05, "F", 1.3, 60_000_000,      2.15, "Racha máxima 10s"),
        new Beast("b_kraken",  "Kraken Abisal",    "¡El kraken sale del abismo!", 0xFF3FA9FF, 0xFF06182E, "K", 1.4, 500_000_000,     2.20, "Acerca el jackpot"),
        new Beast("b_fenrir",  "Lobo Fenrir",      "¡Fenrir aúlla a la luna!",    0xFFBFE9FF, 0xFF0B1A2E, "L", 1.6, 4_000_000_000.0, 2.25, "Recarga habilidades"),
        new Beast("b_monkey",  "Mono Rey",         "¡El Rey Mono rompe el cielo!",0xFFFFD84D, 0xFF3A2A05, "M", 3.0, 40_000_000_000.0, 2.30, "Clones: paga el triple")
    );

    private Beasts() {}

    public static Beast byId(String id) {
        for (Beast b : ALL) if (b.id().equals(id)) return b;
        return null;
    }

    public static int level(GameState g, int i) { return g.run.getOrDefault(ALL.get(i).id(), 0); }

    /** Bloques que destroza de media una bestia de nivel l. */
    public static int blocks(int l) { return 20 + 4 * l; }
    public static double fervor(int l) { return 1 + 0.12 * l; }
    public static double roar(int l) { return 1 + 0.10 * l; }

    private static String f1(double d) { return String.format(Locale.forLanguageTag("es-ES"), "%.1f", d); }

    public static List<Upgrades.Def> defs() {
        List<Upgrades.Def> out = new ArrayList<>();
        out.add(new Upgrades.Def("b_fervor", Upgrades.Tab.BESTIAS, Upgrades.Scope.RUN, "Fervor de la manada",
                "El Espíritu se carga más rápido con cada bloque. Necesitas al menos una bestia despierta.",
                800_000, 2.0, 20, null, l -> "Carga ×" + f1(fervor(l))));
        out.add(new Upgrades.Def("b_roar", Upgrades.Tab.BESTIAS, Upgrades.Scope.RUN, "Rugido ancestral",
                "Todas las bestias pagan más puntos.",
                2_000_000, 2.1, 20, null, l -> "Bestias ×" + f1(roar(l))));
        for (Beast b : ALL) {
            out.add(new Upgrades.Def(b.id(), Upgrades.Tab.BESTIAS, Upgrades.Scope.RUN, b.name(),
                    "Se invoca al llenar el Espíritu. " + b.perk() + ".",
                    b.baseCost(), b.growth(), 25, null,
                    l -> l == 0 ? "Dormida" : "Destroza ~" + blocks(l) + " bloques · ×" + f1(b.mult())));
        }
        return out;
    }
}
