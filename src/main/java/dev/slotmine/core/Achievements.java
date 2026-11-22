package dev.slotmine.core;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Predicate;

/** Logros permanentes: cada uno da +2% a toda la producción. */
public final class Achievements {
    public record Def(String id, String name, String desc, Predicate<GameState> check) {}

    private static final BigInteger M = BigInteger.valueOf(1_000_000L);
    private static BigInteger pow10(int e) { return BigInteger.TEN.pow(e); }

    private static boolean anyGod(GameState g, int min) {
        for (Upgrades.God god : Upgrades.GODS) if (g.run.getOrDefault(god.id(), 0) >= min) return true;
        return false;
    }
    private static boolean allGods(GameState g) {
        for (Upgrades.God god : Upgrades.GODS) if (g.run.getOrDefault(god.id(), 0) < 1) return false;
        return true;
    }

    public static final List<Def> ALL = List.of(
        new Def("a_first",  "Primer bloque",       "Rompe tu primer bloque.",              g -> g.blocksBroken >= 1),
        new Def("a_1k",     "Minero aficionado",   "Rompe 1.000 bloques.",                 g -> g.blocksBroken >= 1_000),
        new Def("a_10k",    "Minero veterano",     "Rompe 10.000 bloques.",                g -> g.blocksBroken >= 10_000),
        new Def("a_100k",   "Máquina de picar",    "Rompe 100.000 bloques.",               g -> g.blocksBroken >= 100_000),
        new Def("a_s50",    "En racha",            "Llega a una racha de 50.",             g -> g.bestStreak >= 50),
        new Def("a_s100",   "Imparable",           "Llega a una racha de 100.",            g -> g.bestStreak >= 100),
        new Def("a_1m",     "Millonario",          "Gana 1M de puntos en total.",          g -> g.lifetimeEarned.compareTo(M) >= 0),
        new Def("a_1b",     "Multimillonario",     "Gana 1B de puntos en total.",          g -> g.lifetimeEarned.compareTo(pow10(12)) >= 0),
        new Def("a_1t",     "Dueño del casino",    "Gana 1T de puntos en total.",          g -> g.lifetimeEarned.compareTo(pow10(18)) >= 0),
        new Def("a_jp1",    "¡Premio!",            "Gana tu primer jackpot.",              g -> g.jackpots >= 1),
        new Def("a_jp10",   "Chorro de monedas",   "Gana 10 jackpots.",                    g -> g.jackpots >= 10),
        new Def("a_ghost",  "Casa encantada",      "Picador fantasma al nivel 10.",        g -> g.run.getOrDefault("idle", 0) >= 10),
        new Def("a_god1",   "Primera plegaria",    "Despierta a un dios.",                 g -> anyGod(g, 1)),
        new Def("a_god10",  "Devoto",              "Sube un dios al nivel 10.",            g -> anyGod(g, 10)),
        new Def("a_godall", "Panteón completo",    "Despierta a los 9 dioses a la vez.",   Achievements::allGods),
        new Def("a_rain",   "Lluvia de monedas",   "Vive 5 Lluvias doradas.",              g -> g.eventsSeen >= 5),
        new Def("a_1qa",    "Cuatrillonario",      "Gana 1Qa de puntos en total.",         g -> g.lifetimeEarned.compareTo(pow10(24)) >= 0),
        new Def("a_1dc",    "Más allá de lo medible", "Gana 1Dc (decillón) en total.",     g -> g.lifetimeEarned.compareTo(pow10(60)) >= 0),
        new Def("a_1vg",    "Vigintillonario",     "Gana 1Vg (vigintillón) en total.",     g -> g.lifetimeEarned.compareTo(pow10(120)) >= 0),
        new Def("a_skill1", "Poderes",             "Usa una habilidad.",                   g -> g.skillsUsed >= 1),
        new Def("a_skill50","Maestro de poderes",  "Usa 50 habilidades.",                  g -> g.skillsUsed >= 50),
        new Def("a_beast1", "Domador",             "Invoca a tu primera bestia.",          g -> g.beastsSummoned >= 1),
        new Def("a_beast25","Señor de las bestias","Invoca 25 bestias.",                   g -> g.beastsSummoned >= 25),
        new Def("a_asc1",   "Renacido",            "Asciende por primera vez.",            g -> g.ascensions >= 1),
        new Def("a_asc10",  "Eterno retorno",      "Asciende 10 veces.",                   g -> g.ascensions >= 10)
    );
}
