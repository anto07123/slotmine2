package dev.slotmine.core;

import dev.slotmine.SlotMine;
import dev.slotmine.config.JsonStore;
import dev.slotmine.config.SlotConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Lógica de juego: puntos, probabilidades, pity, near miss, jackpot, idle, compra y ascensión. 100% local. */
public final class Engine {

    public record BreakEvent(BlockPos pos, boolean op, boolean crit, List<BlockPos> chain, boolean shock,
                             boolean feverStart, boolean jackpot, boolean near,
                             BigInteger gain, BigInteger jackpotWin, boolean lite) {}

    private final Random rng = new Random();
    private final ArrayDeque<Long> recent = new ArrayDeque<>();
    private Stats stats;
    private int streak;
    private long lastBreak, feverUntil, lastCombo;
    private BigInteger lastGain = BigInteger.ZERO;
    private int tickCount;
    private boolean dirty;

    public Stats stats() { return stats; }
    public int streak() { return System.currentTimeMillis() - lastBreak > 1500 ? 0 : streak; }
    public long feverRemainingMs() { return Math.max(0, feverUntil - System.currentTimeMillis()); }
    public void refreshStats() { stats = Stats.compute(SlotMine.state, SlotMine.config); }

    // ---------------------------------------------------------------- rotura de bloque
    public void onBlockBroken(ClientWorld world, ClientPlayerEntity player, BlockPos pos, BlockState bs) {
        if (bs.isAir()) return;
        long now = System.currentTimeMillis();
        SlotConfig c = SlotMine.config;
        GameState gs = SlotMine.state;
        Stats st = stats;
        Identifier id = Registries.BLOCK.getId(bs.getBlock());

        // racha y ritmo (instamine => modo lite)
        if (now - lastBreak > 1500) streak = 0;
        streak++;
        lastBreak = now;
        gs.bestStreak = Math.max(gs.bestStreak, streak);
        recent.addLast(now);
        while (!recent.isEmpty() && now - recent.peekFirst() > 1000) recent.pollFirst();
        boolean lite = recent.size() >= c.liteModeBreaksPerSecond;

        // puntos base
        SlotConfig.OpBlock op = c.opOf(id);
        boolean fever = now < feverUntil;
        double streakMult = 1 + st.streakMax * Math.min(1.0, streak / 100.0);
        double mult = st.gainMult * st.coreMult * streakMult * (fever ? st.feverMult : 1.0);
        double luck = 1.0;
        if (op != null) { mult *= op.scoreMult * st.oreMult; luck = op.luckMult; }

        BigInteger baseGain = BigNum.fromDouble(c.valueOf(id) * mult);
        if (baseGain.signum() == 0) baseGain = BigInteger.ONE;
        BigInteger total = baseGain;

        // Crit / Spark
        boolean crit = rng.nextDouble() < Math.min(0.85, st.critChance * luck);
        if (crit) total = total.add(BigNum.mul(baseGain, st.critMult - 1));

        // Chain Arc
        List<BlockPos> chain = List.of();
        if (st.chain && rng.nextDouble() < Math.min(0.6, st.chainChance * luck)) {
            chain = findChain(world, pos);
            if (!chain.isEmpty()) total = total.add(BigNum.mul(baseGain, 0.75 * chain.size()));
        }

        // Shockwave
        boolean shock = st.shock && rng.nextDouble() < Math.min(0.5, st.shockChance * luck);
        if (shock) total = total.add(BigNum.mul(baseGain, 2.0));

        // Fever
        boolean feverStart = false;
        if (st.fever && rng.nextDouble() < Math.min(0.3, st.feverChance * luck)) {
            feverStart = !fever;
            feverUntil = Math.max(feverUntil, now) + (long) (st.feverSeconds * 1000);
        }

        // Jackpot con pity + near miss (una sola tirada: ventana de jackpot dentro de la de "casi")
        gs.pity++;
        int pityMax = Math.max(30, (int) (c.pityMax * st.pityFactor));
        double pityBoost = 1 + 4.0 * Math.min(1.0, gs.pity / (double) pityMax);
        double jp = Math.min(0.5, c.jackpotChance * st.jackpotLuck * luck * pityBoost);
        double roll = rng.nextDouble();
        boolean jackpot = roll < jp || gs.pity >= pityMax;
        boolean near = !jackpot && roll < Math.min(0.9, jp * c.nearMissFactor);
        BigInteger jackpotWin = BigInteger.ZERO;
        if (jackpot) {
            jackpotWin = BigNum.mul(baseGain, c.jackpotBaseMult).add(BigNum.mul(gs.points, c.jackpotPointsShare));
            total = total.add(jackpotWin);
            gs.pity = 0;
        }

        gs.addPoints(total);
        gs.blocksBroken++;
        gs.runBlocks++;
        lastGain = baseGain;
        dirty = true;

        BreakEvent ev = new BreakEvent(pos, op != null, crit, chain, shock, feverStart, jackpot, near,
                total, jackpotWin, lite);
        SlotMine.hud.onScore(gs.points, jackpot);
        SlotMine.hud.onBreak(ev);
        SlotMine.fx.play(player, ev);
    }

    private List<BlockPos> findChain(ClientWorld w, BlockPos o) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.iterate(o.add(-4, -4, -4), o.add(4, 4, 4))) {
            if (p.equals(o)) continue;
            BlockState s = w.getBlockState(p);
            if (s.isAir()) continue;
            if (SlotMine.config.isValuable(Registries.BLOCK.getId(s.getBlock()))) found.add(p.toImmutable());
        }
        found.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(o)));
        return found.size() > 3 ? new ArrayList<>(found.subList(0, 3)) : found;
    }

    // ---------------------------------------------------------------- combos de rodillos (los evalúa el HUD)
    /** kind 1 = rodillos iguales (x3 del último bloque) · kind 2 = 777 (x7). Cooldown 2,5 s. */
    public BigInteger grantCombo(int kind) {
        long now = System.currentTimeMillis();
        if (now - lastCombo < 2500 || lastGain.signum() == 0) return BigInteger.ZERO;
        lastCombo = now;
        BigInteger bonus = BigNum.mul(lastGain, kind == 2 ? 7 : 3);
        GameState gs = SlotMine.state;
        gs.addPoints(bonus);
        dirty = true;
        SlotMine.hud.onScore(gs.points, false);
        SlotMine.fx.combo(kind);
        return bonus;
    }

    // ---------------------------------------------------------------- tick (idle + autoguardado)
    public void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        tickCount++;
        GameState gs = SlotMine.state;
        if (tickCount % 20 == 0 && stats.idleBase > 0) {
            BigInteger g = BigNum.fromDouble(stats.idleBase * stats.gainMult * stats.coreMult);
            if (g.signum() > 0) {
                gs.addPoints(g);
                dirty = true;
                SlotMine.hud.onScore(gs.points, false);
            }
        }
        if (tickCount % 600 == 0) save();
    }

    public void save() {
        if (!dirty) return;
        dirty = false;
        JsonStore.save(SlotMine.dir.resolve("state.json"), SlotMine.state);
    }

    // ---------------------------------------------------------------- tienda
    public int buy(Upgrades.Def d, int count) {
        GameState gs = SlotMine.state;
        int bought = 0;
        for (int i = 0; i < count && i < 1000; i++) {
            int lvl = gs.level(d);
            if (d.maxLevel() > 0 && lvl >= d.maxLevel()) break;
            BigInteger cost = Upgrades.costOf(d, lvl);
            if (d.scope() == Upgrades.Scope.CORE) {
                if (BigInteger.valueOf(gs.cores).compareTo(cost) < 0) break;
                gs.cores -= cost.longValue();
            } else {
                if (gs.points.compareTo(cost) < 0) break;
                gs.points = gs.points.subtract(cost);
            }
            gs.setLevel(d, lvl + 1);
            bought++;
        }
        if (bought > 0) {
            refreshStats();
            dirty = true;
            SlotMine.hud.onScore(gs.points, false);
            SlotMine.fx.uiBuy();
            save();
        }
        return bought;
    }

    // ---------------------------------------------------------------- ascensión
    /** Núcleos = floor(sqrt(ganado_en_la_run / 10^9)). */
    public BigInteger coresOnAscend() {
        return SlotMine.state.runEarned.divide(BigInteger.TEN.pow(9)).sqrt();
    }

    public boolean ascend() {
        GameState gs = SlotMine.state;
        BigInteger n = coresOnAscend();
        if (n.signum() <= 0) return false;
        long add = n.min(BigInteger.valueOf(1_000_000_000L)).longValue();
        gs.cores += add;
        gs.coresEarnedTotal += add;
        gs.ascensions++;
        gs.run.clear();
        gs.runEarned = BigInteger.ZERO;
        gs.runBlocks = 0;
        gs.pity = 0;
        gs.points = Stats.startPoints(gs.core.getOrDefault("c_start", 0));
        feverUntil = 0;
        refreshStats();
        dirty = true;
        SlotMine.hud.onScore(gs.points, true);
        SlotMine.fx.ascend();
        save();
        return true;
    }
}
