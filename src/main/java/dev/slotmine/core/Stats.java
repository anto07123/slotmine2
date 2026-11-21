package dev.slotmine.core;

import dev.slotmine.config.SlotConfig;

import java.math.BigInteger;

/** Valores derivados (multiplicadores, probabilidades) a partir del estado. Se recalcula al comprar. */
public final class Stats {
    // --- base ---
    public double gainMult, oreMult, idleBase, idlePerSec, streakMax;
    public double critChance, critMult, chainChance, shockChance, feverChance;
    public double feverSeconds, feverMult, jackpotLuck, pityFactor, coreMult;
    public boolean chain, shock, fever, gods, events;

    // --- mejoras nuevas ---
    public double comboMult, consolation, jackpotPay, eventLuck;
    public double eventChancePerSec, eventSeconds, eventMult;
    public int lvlDepth, lvlHades;
    public double godPower;

    // --- dioses ---
    public double midasChance, midasMult;
    public double zeusChance, zeusMult;
    public int zeusStrikes;
    public double hadesDeep, hadesShallow;
    public int streakInc, streakWindowMs;
    public int anubisEvery;
    public double anubisMult;
    public double odinRagChance, odinRagMult;
    public double sevenFeverSec;

    // --- fórmulas por nivel (también las usa la tienda para mostrar el efecto actual) ---
    public static double vein(int l) { return Math.pow(1.2, l); }
    public static double magnet(int l) { return 1 + 0.35 * l; }
    /** Bloques fantasma por segundo (lineal: sin bola de nieve propia). */
    public static double idle(int l) { return 0.1 * l; }
    public static double streak(int l) { return 0.05 * l; }
    public static double critC(int l) { return 0.006 * l; }
    public static double critM(int l) { return 0.5 * l; }
    public static double chainC(int l) { return 0.005 * l; }
    public static double shockC(int l) { return 0.004 * l; }
    public static double luck(int l) { return 1 + 0.2 * l; }
    public static double pity(int l) { return Math.max(0.4, 1 - 0.05 * l); }
    public static double coreMultLvl(int l) { return Math.pow(1.25, l); }
    public static double coreLuck(int l) { return 1 + 0.1 * l; }
    public static BigInteger startPoints(int l) {
        return l <= 0 ? BigInteger.ZERO : BigInteger.valueOf(100).multiply(BigInteger.valueOf(5).pow(l));
    }

    // nuevas producción / suerte
    public static double depthDeep(int l) { return 1 + 0.12 * l; }       // bloques bajo Y=0
    public static double depthShallow(int l) { return 1 + 0.02 * l; }
    public static double comboM(int l) { return 1 + 0.25 * l; }
    public static double pulse(int l) { return 0.002 * l; }
    public static double consolationM(int l) { return 4.0 * l; }
    public static double jpay(int l) { return 1 + 0.15 * l; }
    public static double evtFreq(int l) { return 1 + 0.15 * l; }
    public static double evtDur(int l) { return 15 + l; }
    public static double evtMult(int l) { return 3 + 0.1 * l; }

    // dioses (nivel l, poder divino gp)
    public static double midasCh(int l) { return Math.min(0.30, 0.015 * l); }
    public static double midasM(int l, double gp) { return (3 + 0.4 * l) * gp; }
    public static double zeusCh(int l) { return Math.min(0.40, 0.012 * l); }
    public static int zeusN(int l) { return l <= 0 ? 0 : Math.min(5, 1 + l / 6); }
    public static double zeusM(int l, double gp) { return (1.2 + 0.08 * l) * gp; }
    public static double hadesD(int l, double gp) { return 1 + 0.20 * l * gp; }
    public static double hadesS(int l) { return 1 + 0.04 * l; }
    public static int hermesInc(int l) { return 1 + l / 5; }
    public static int hermesWin(int l) { return 1500 + 80 * l; }
    public static double hermesGhost(int l) { return 1 + 0.04 * l; }
    public static double tycheEvt(int l) { return 1 + 0.03 * l; }
    public static double tycheJp(int l) { return 1 + 0.10 * l; }
    public static double tychePity(int l) { return Math.max(0.6, 1 - 0.015 * l); }
    public static double cronosCh(int l) { return 1 + 0.20 * l; }
    public static double cronosSec(int l) { return 0.3 * l; }
    public static double cronosMult(int l) { return 0.03 * l; }
    public static double anubisOre(int l) { return 1 + 0.25 * l; }
    public static int anubisEv(int l) { return Math.max(12, 40 - l); }
    public static double anubisM(int l, double gp) { return (8 + l) * gp; }
    public static double odinCrit(int l) { return 0.25 * l; }
    public static double odinRagCh(int l) { return l <= 0 ? 0 : Math.min(0.30, 0.05 + 0.01 * l); }
    public static double odinRagM(int l, double gp) { return (6 + 0.5 * l) * gp; }
    public static double sevenCombo(int l) { return 1 + 0.25 * l; }
    public static double sevenFever(int l) { return l <= 0 ? 0 : 2 + 0.2 * l; }

    // núcleos nuevos
    public static double coreGhost(int l) { return 1 + 0.25 * l; }
    public static double coreDivine(int l) { return 1 + 0.10 * l; }
    public static double coreCritC(int l) { return 0.005 * l; }
    public static double coreCritM(int l) { return 0.25 * l; }

    /** Multiplicador global por logros: +2% cada uno. */
    public static double achMult(GameState g) { return 1 + 0.02 * g.achievements.size(); }

    public static Stats compute(GameState g, SlotConfig c) {
        Stats s = new Stats();

        // niveles de dioses
        int midas = r(g, "g_midas"), zeus = r(g, "g_zeus"), hades = r(g, "g_hades"), hermes = r(g, "g_hermes");
        int tyche = r(g, "g_tyche"), cronos = r(g, "g_cronos"), anubis = r(g, "g_anubis");
        int odin = r(g, "g_odin"), seven = r(g, "g_seven");

        s.gods = g.perm.getOrDefault("u_gods", 0) > 0;
        s.events = g.perm.getOrDefault("u_events", 0) > 0;
        if (!s.gods) { midas = zeus = hades = hermes = tyche = cronos = anubis = odin = seven = 0; }
        s.godPower = coreDivine(g.core.getOrDefault("c_divine", 0));

        // producción
        s.gainMult = vein(r(g, "vein"));
        s.oreMult = magnet(r(g, "magnet")) * anubisOre(anubis);
        s.idleBase = idle(r(g, "idle"));
        s.streakMax = streak(r(g, "streak"));
        s.lvlDepth = r(g, "depth");
        s.lvlHades = hades;
        s.hadesDeep = hadesD(hades, s.godPower);
        s.hadesShallow = hadesS(hades);
        s.comboMult = comboM(r(g, "combo")) * sevenCombo(seven);
        s.sevenFeverSec = sevenFever(seven);

        // fiebre
        int fu = r(g, "fever_up");
        s.feverSeconds = c.feverSeconds + fu + cronosSec(cronos);
        s.feverMult = c.feverMultiplier + 0.15 * fu + cronosMult(cronos);
        s.feverChance = (c.feverChance + pulse(r(g, "pulse"))) * cronosCh(cronos);

        // crit / probabilidades
        s.critChance = Math.min(0.75, c.critChance + critC(r(g, "crit_c")) + coreCritC(g.core.getOrDefault("c_crit", 0)));
        s.critMult = c.critMultiplier + critM(r(g, "crit_m")) + coreCritM(g.core.getOrDefault("c_crit", 0)) + odinCrit(odin);
        s.chain = g.perm.getOrDefault("u_chain", 0) > 0;
        s.shock = g.perm.getOrDefault("u_shock", 0) > 0;
        s.fever = g.perm.getOrDefault("u_fever", 0) > 0;
        s.chainChance = c.chainChance + chainC(r(g, "chain_c"));
        s.shockChance = c.shockwaveChance + shockC(r(g, "shock_c"));

        // suerte
        s.jackpotLuck = luck(r(g, "luck")) * coreLuck(g.core.getOrDefault("c_luck", 0)) * tycheJp(tyche);
        s.pityFactor = pity(r(g, "pity")) * tychePity(tyche);
        s.eventLuck = tycheEvt(tyche);
        s.consolation = consolationM(r(g, "console"));
        s.jackpotPay = jpay(r(g, "jpay"));

        // eventos (Lluvia dorada)
        int ev = r(g, "evt");
        s.eventChancePerSec = (1.0 / 180.0) * evtFreq(ev);
        s.eventSeconds = evtDur(ev);
        s.eventMult = evtMult(ev);

        // dioses con efecto directo
        s.midasChance = midasCh(midas);
        s.midasMult = midasM(midas, s.godPower);
        s.zeusChance = zeusCh(zeus);
        s.zeusStrikes = zeusN(zeus);
        s.zeusMult = zeusM(zeus, s.godPower);
        s.streakInc = hermes <= 0 ? 1 : hermesInc(hermes);
        s.streakWindowMs = hermes <= 0 ? 1500 : hermesWin(hermes);
        s.anubisEvery = anubis <= 0 ? 0 : anubisEv(anubis);
        s.anubisMult = anubisM(anubis, s.godPower);
        s.odinRagChance = odinRagCh(odin);
        s.odinRagMult = odinRagM(odin, s.godPower);

        // global: núcleos (cantidad y nivel) x logros
        s.coreMult = (1 + 0.20 * g.coresEarnedTotal) * coreMultLvl(g.core.getOrDefault("c_mult", 0)) * achMult(g);

        // Picador fantasma: rompe `idleBase` bloques normales por segundo (sin minerales, racha, fiebre ni crits)
        s.idlePerSec = s.idleBase * hermesGhost(hermes) * coreGhost(g.core.getOrDefault("c_ghost", 0))
                * c.defaultBlockValue * s.gainMult * s.coreMult;
        return s;
    }

    private static int r(GameState g, String id) { return g.run.getOrDefault(id, 0); }
}
