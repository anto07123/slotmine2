package dev.slotmine.core;

import dev.slotmine.config.SlotConfig;

import java.math.BigInteger;

/** Valores derivados (multiplicadores, probabilidades) a partir del estado. Se recalcula al comprar. */
public final class Stats {
    public double gainMult, oreMult, idleBase, streakMax;
    public double critChance, critMult, chainChance, shockChance, feverChance;
    public double feverSeconds, feverMult, jackpotLuck, pityFactor, coreMult;
    public boolean chain, shock, fever;

    // --- fórmulas por nivel (también las usa la tienda para mostrar el efecto actual) ---
    public static double vein(int l) { return Math.pow(1.3, l); }
    public static double magnet(int l) { return 1 + 0.35 * l; }
    public static double idle(int l) { return 1.5 * l * Math.pow(1.22, l); }
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

    public static Stats compute(GameState g, SlotConfig c) {
        Stats s = new Stats();
        s.gainMult = vein(r(g, "vein"));
        s.oreMult = magnet(r(g, "magnet"));
        s.idleBase = idle(r(g, "idle"));
        s.streakMax = streak(r(g, "streak"));
        int fu = r(g, "fever_up");
        s.feverSeconds = c.feverSeconds + fu;
        s.feverMult = c.feverMultiplier + 0.15 * fu;
        s.critChance = Math.min(0.75, c.critChance + critC(r(g, "crit_c")));
        s.critMult = c.critMultiplier + critM(r(g, "crit_m"));
        s.chain = g.perm.getOrDefault("u_chain", 0) > 0;
        s.shock = g.perm.getOrDefault("u_shock", 0) > 0;
        s.fever = g.perm.getOrDefault("u_fever", 0) > 0;
        s.chainChance = c.chainChance + chainC(r(g, "chain_c"));
        s.shockChance = c.shockwaveChance + shockC(r(g, "shock_c"));
        s.feverChance = c.feverChance;
        s.jackpotLuck = luck(r(g, "luck")) * coreLuck(g.core.getOrDefault("c_luck", 0));
        s.pityFactor = pity(r(g, "pity"));
        s.coreMult = (1 + 0.20 * g.coresEarnedTotal) * coreMultLvl(g.core.getOrDefault("c_mult", 0));
        return s;
    }

    private static int r(GameState g, String id) { return g.run.getOrDefault(id, 0); }
}
