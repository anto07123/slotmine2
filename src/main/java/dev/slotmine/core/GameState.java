package dev.slotmine.core;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/** Progreso guardado en config/slotmine/state.json (los BigInteger se serializan como números exactos). */
public final class GameState {
    public int version = 1;

    public BigInteger points = BigInteger.ZERO;        // saldo gastable (lo que muestran los rodillos)
    public BigInteger runEarned = BigInteger.ZERO;     // ganado en esta ascensión
    public BigInteger lifetimeEarned = BigInteger.ZERO;
    public long blocksBroken;
    public long runBlocks;
    public int pity;
    public int bestStreak;
    public int ascensions;

    public long cores;             // núcleos sin gastar
    public long coresEarnedTotal;  // núcleos ganados en total (dan bonus pasivo)

    public Map<String, Integer> run = new HashMap<>();   // mejoras que se reinician al ascender
    public Map<String, Integer> perm = new HashMap<>();  // rodillos y desbloqueos (permanentes)
    public Map<String, Integer> core = new HashMap<>();  // mejoras de núcleos (permanentes)

    public int reelCount() {
        return Math.max(3, Math.min(6, 3 + perm.getOrDefault("reels", 0)));
    }

    private Map<String, Integer> map(Upgrades.Scope s) {
        return switch (s) {
            case RUN -> run;
            case PERMANENT -> perm;
            case CORE -> core;
        };
    }

    public int level(Upgrades.Def d) { return map(d.scope()).getOrDefault(d.id(), 0); }

    public void setLevel(Upgrades.Def d, int lvl) { map(d.scope()).put(d.id(), lvl); }

    public void addPoints(BigInteger amount) {
        if (amount == null || amount.signum() <= 0) return;
        points = BigNum.clamp(points.add(amount));
        runEarned = BigNum.clamp(runEarned.add(amount));
        lifetimeEarned = BigNum.clamp(lifetimeEarned.add(amount));
    }

    /** Se llama tras cargar: protege contra JSON editado/corrupto. */
    public void sanitize() {
        points = BigNum.clamp(points);
        runEarned = BigNum.clamp(runEarned);
        lifetimeEarned = BigNum.clamp(lifetimeEarned);
        if (run == null) run = new HashMap<>();
        if (perm == null) perm = new HashMap<>();
        if (core == null) core = new HashMap<>();
        for (Map<String, Integer> m : java.util.List.of(run, perm, core)) {
            m.replaceAll((k, v) -> v == null ? 0 : Math.max(0, Math.min(1000, v)));
        }
        perm.put("reels", Math.min(3, perm.getOrDefault("reels", 0)));
        cores = Math.max(0, cores);
        coresEarnedTotal = Math.max(0, coresEarnedTotal);
        pity = Math.max(0, pity);
    }
}
