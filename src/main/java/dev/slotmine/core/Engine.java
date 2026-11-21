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

/** Lógica de juego: puntos, probabilidades, pity, near miss, jackpot, dioses, eventos, logros, idle, compra y ascensión. 100% local. */
public final class Engine {

    public record BreakEvent(BlockPos pos, boolean op, boolean crit, List<BlockPos> chain, boolean shock,
                             boolean feverStart, boolean jackpot, boolean near,
                             BigInteger gain, BigInteger jackpotWin, boolean lite,
                             List<String> gods, List<BlockPos> strikes) {}

    private final Random rng = new Random();
    private final ArrayDeque<Long> recent = new ArrayDeque<>();
    private Stats stats;
    private int streak, anubisCounter;
    private long lastBreak, feverUntil, lastCombo, eventUntil;
    private BigInteger lastGain = BigInteger.ZERO;
    private int tickCount;
    private boolean dirty;

    public Stats stats() { return stats; }
    public int streak() { return System.currentTimeMillis() - lastBreak > stats.streakWindowMs ? 0 : streak; }
    public long feverRemainingMs() { return Math.max(0, feverUntil - System.currentTimeMillis()); }
    public long eventRemainingMs() { return Math.max(0, eventUntil - System.currentTimeMillis()); }
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
        if (now - lastBreak > st.streakWindowMs) streak = 0;
        streak += st.streakInc;
        lastBreak = now;
        gs.bestStreak = Math.max(gs.bestStreak, streak);
        recent.addLast(now);
        while (!recent.isEmpty() && now - recent.peekFirst() > 1000) recent.pollFirst();
        boolean lite = recent.size() >= c.liteModeBreaksPerSecond;

        // puntos base
        SlotConfig.OpBlock op = c.opOf(id);
        boolean fever = now < feverUntil;
        boolean event = now < eventUntil;
        boolean deep = pos.getY() < 0;
        double streakMult = 1 + st.streakMax * Math.min(1.0, streak / 100.0);
        double depthMult = deep ? Stats.depthDeep(st.lvlDepth) : Stats.depthShallow(st.lvlDepth);
        double hadesMult = st.lvlHades <= 0 ? 1.0 : (deep ? st.hadesDeep : st.hadesShallow);
        double mult = st.gainMult * st.coreMult * streakMult * depthMult * hadesMult
                * (fever ? st.feverMult : 1.0) * (event ? st.eventMult : 1.0);
        double luck = st.eventLuck;
        if (op != null) { mult *= op.scoreMult * st.oreMult; luck *= op.luckMult; }

        BigInteger baseGain = BigNum.fromDouble(c.valueOf(id) * mult);
        if (baseGain.signum() == 0) baseGain = BigInteger.ONE;
        BigInteger total = baseGain;
        List<String> gods = new ArrayList<>(2);

        // Crit / Spark
        boolean crit = rng.nextDouble() < Math.min(0.85, st.critChance * luck);
        if (crit) total = total.add(BigNum.mul(baseGain, st.critMult - 1));

        // Chain Arc
        List<BlockPos> chain = List.of();
        if (st.chain && rng.nextDouble() < Math.min(0.6, st.chainChance * luck)) {
            chain = findNear(world, pos, 3);
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

        // ---------- dioses ----------
        if (st.midasChance > 0 && rng.nextDouble() < st.midasChance) {
            total = total.add(BigNum.mul(baseGain, st.midasMult));
            gods.add("g_midas");
        }
        List<BlockPos> strikes = List.of();
        if (st.zeusStrikes > 0 && rng.nextDouble() < st.zeusChance) {
            strikes = zeusTargets(world, pos, st.zeusStrikes);
            total = total.add(BigNum.mul(baseGain, st.zeusMult * strikes.size()));
            gods.add("g_zeus");
        }
        if (st.anubisEvery > 0 && ++anubisCounter >= st.anubisEvery) {
            anubisCounter = 0;
            total = total.add(BigNum.mul(baseGain, st.anubisMult));
            gods.add("g_anubis");
        }
        if (crit && st.odinRagChance > 0 && rng.nextDouble() < st.odinRagChance) {
            total = total.add(BigNum.mul(baseGain, st.odinRagMult));
            gods.add("g_odin");
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
            jackpotWin = BigNum.mul(baseGain, c.jackpotBaseMult * st.jackpotPay).add(BigNum.mul(gs.points, c.jackpotPointsShare));
            total = total.add(jackpotWin);
            gs.pity = 0;
            gs.jackpots++;
        } else if (near && st.consolation > 0) {
            total = total.add(BigNum.mul(baseGain, st.consolation));
        }

        gs.addPoints(total);
        gs.blocksBroken++;
        gs.runBlocks++;
        lastGain = baseGain;
        dirty = true;

        BreakEvent ev = new BreakEvent(pos, op != null, crit, chain, shock, feverStart, jackpot, near,
                total, jackpotWin, lite, gods, strikes);
        SlotMine.hud.onScore(gs.points, jackpot);
        SlotMine.hud.onBreak(ev);
        SlotMine.fx.play(player, ev);
    }

    /** Hasta `limit` bloques valiosos cercanos, ordenados por distancia. */
    private List<BlockPos> findNear(ClientWorld w, BlockPos o, int limit) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.iterate(o.add(-4, -4, -4), o.add(4, 4, 4))) {
            if (p.equals(o)) continue;
            BlockState s = w.getBlockState(p);
            if (s.isAir()) continue;
            if (SlotMine.config.isValuable(Registries.BLOCK.getId(s.getBlock()))) found.add(p.toImmutable());
        }
        found.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(o)));
        return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    /** Objetivos de los rayos de Zeus: primero minerales cercanos, el resto cae al azar alrededor. */
    private List<BlockPos> zeusTargets(ClientWorld w, BlockPos o, int n) {
        List<BlockPos> t = new ArrayList<>(findNear(w, o, n));
        while (t.size() < n) {
            t.add(o.add(rng.nextInt(7) - 3, rng.nextInt(3) - 1, rng.nextInt(7) - 3));
        }
        return t;
    }

    // ---------------------------------------------------------------- combos de rodillos (los evalúa el HUD)
    /** kind 1 = rodillos iguales (x3 del último bloque) · kind 2 = 777 (x7). Cooldown 2,5 s. */
    public BigInteger grantCombo(int kind) {
        long now = System.currentTimeMillis();
        if (now - lastCombo < 2500 || lastGain.signum() == 0) return BigInteger.ZERO;
        lastCombo = now;
        BigInteger bonus = BigNum.mul(lastGain, (kind == 2 ? 7 : 3) * stats.comboMult);
        GameState gs = SlotMine.state;
        gs.addPoints(bonus);
        if (stats.sevenFeverSec > 0) {
            feverUntil = Math.max(feverUntil, now) + (long) (stats.sevenFeverSec * 1000);
        }
        dirty = true;
        SlotMine.hud.onScore(gs.points, false);
        SlotMine.fx.combo(kind);
        return bonus;
    }

    // ---------------------------------------------------------------- tick (idle, eventos, logros, autoguardado)
    public void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        tickCount++;
        GameState gs = SlotMine.state;
        long now = System.currentTimeMillis();

        if (tickCount % 20 == 0) {
            // Picador fantasma
            if (stats.idleBase > 0) {
                double per = stats.idlePerSec * (now < eventUntil ? stats.eventMult : 1.0);
                BigInteger g = BigNum.fromDouble(per);
                if (g.signum() > 0) {
                    gs.addPoints(g);
                    dirty = true;
                    SlotMine.hud.onScore(gs.points, false);
                }
            }
            // Lluvia dorada
            if (stats.events && now >= eventUntil && rng.nextDouble() < stats.eventChancePerSec) {
                eventUntil = now + (long) (stats.eventSeconds * 1000);
                gs.eventsSeen++;
                dirty = true;
                SlotMine.hud.onEventStart(stats.eventMult);
                SlotMine.fx.eventStart();
            }
        }
        if (tickCount % 40 == 0) checkAchievements();
        if (tickCount % 600 == 0) save();
    }

    private void checkAchievements() {
        GameState gs = SlotMine.state;
        boolean any = false;
        for (Achievements.Def a : Achievements.ALL) {
            if (gs.achievements.contains(a.id())) continue;
            if (a.check().test(gs)) {
                gs.achievements.add(a.id());
                any = true;
                SlotMine.hud.onAchievement(a.name());
                SlotMine.fx.achievement();
            }
        }
        if (any) {
            refreshStats();
            dirty = true;
        }
    }

    public void save() {
        if (!dirty) return;
        dirty = false;
        JsonStore.save(SlotMine.dir.resolve("state.json"), SlotMine.state);
    }

    // ---------------------------------------------------------------- tienda
    public int buy(Upgrades.Def d, int count) {
        GameState gs = SlotMine.state;
        if (d.tab() == Upgrades.Tab.PANTEON && gs.perm.getOrDefault("u_gods", 0) <= 0) return 0;
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
            if (d.tab() == Upgrades.Tab.PANTEON) SlotMine.fx.godBuy(); else SlotMine.fx.uiBuy();
            checkAchievements();
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
        // Altar eterno: cada ascensión los dioses ya empiezan despiertos
        int altar = gs.core.getOrDefault("c_altar", 0);
        if (altar > 0 && gs.perm.getOrDefault("u_gods", 0) > 0) {
            for (Upgrades.God g : Upgrades.GODS) gs.run.put(g.id(), altar);
        }
        gs.runEarned = BigInteger.ZERO;
        gs.runBlocks = 0;
        gs.pity = 0;
        gs.points = Stats.startPoints(gs.core.getOrDefault("c_start", 0));
        feverUntil = 0;
        eventUntil = 0;
        anubisCounter = 0;
        refreshStats();
        dirty = true;
        SlotMine.hud.onScore(gs.points, true);
        SlotMine.fx.ascend();
        checkAchievements();
        save();
        return true;
    }
}
