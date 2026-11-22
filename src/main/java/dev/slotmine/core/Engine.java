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

import java.math.BigDecimal;
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

    // habilidades
    private final long[] skillReady = new long[Skills.ALL.size()];
    private final long[] skillTotal = new long[Skills.ALL.size()];
    private final long[] skillActiveUntil = new long[Skills.ALL.size()];
    // bestias: bendiciones activas
    private final long[] beastUntil = new long[Beasts.ALL.size()];
    private final long[] beastTotal = new long[Beasts.ALL.size()];
    private long beastLockUntil;
    private int tigerCharges;
    private double phoenixLuckVal = 1;
    private boolean forceJackpot;
    private double jackpotBoost = 1;

    public Stats stats() { return stats; }
    public int streak() {
        long now = System.currentTimeMillis();
        return (now - lastBreak > stats.streakWindowMs && now >= skillActiveUntil[Skills.STREAK]) ? 0 : streak;
    }
    public long feverRemainingMs() { return Math.max(0, feverUntil - System.currentTimeMillis()); }
    public long eventRemainingMs() { return Math.max(0, eventUntil - System.currentTimeMillis()); }
    /** Fracción restante (0..1) de la bendición de la bestia i; 0 si no está activa. */
    public double beastBuffFrac(int i) {
        long now = System.currentTimeMillis();
        if (now >= beastUntil[i] || beastTotal[i] <= 0) return 0;
        return Math.min(1.0, (beastUntil[i] - now) / (double) beastTotal[i]);
    }
    public int tigerCharges() { return tigerCharges; }
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
        if (now - lastBreak > st.streakWindowMs && now >= skillActiveUntil[Skills.STREAK]) streak = 0;
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
        // Los buffs temporales se SUMAN (Fiebre + Lluvia dorada + Frenesí), no se multiplican entre sí
        int furyL = Skills.level(gs, Skills.FURY);
        boolean frenzy = now < skillActiveUntil[Skills.FURY];
        double temp = 1 + (fever ? st.feverMult - 1 : 0) + (event ? st.eventMult - 1 : 0) + (frenzy ? Skills.furyMult(furyL) - 1 : 0);
        double mult = st.gainMult * st.coreMult * streakMult * depthMult * hadesMult * temp;
        if (now < skillActiveUntil[Skills.STREAK]) mult *= 1 + Skills.streakBonus(Skills.level(gs, Skills.STREAK));
        double luck = st.eventLuck;
        if (now < beastUntil[Beasts.PHOENIX]) luck *= phoenixLuckVal;
        if (op != null) { mult *= op.scoreMult * st.oreMult; luck *= op.luckMult; }
        else if (now < skillActiveUntil[Skills.VEIN]) mult *= Skills.veinMult(Skills.level(gs, Skills.VEIN)); // Veta viva

        BigInteger baseGain = BigNum.fromDouble(c.valueOf(id) * mult);
        if (baseGain.signum() == 0) baseGain = BigInteger.ONE;
        BigInteger total = baseGain;
        List<String> gods = new ArrayList<>(2);

        // Crit / Spark
        boolean forcedCrit = tigerCharges > 0 && now < beastUntil[Beasts.TIGER];
        if (forcedCrit) tigerCharges--;
        boolean crit = forcedCrit || rng.nextDouble() < Math.min(0.85, st.critChance * luck);
        if (crit) total = total.add(BigNum.mul(baseGain, st.critMult - 1));

        // Chain Arc
        List<BlockPos> chain = List.of();
        boolean dragon = now < beastUntil[Beasts.DRAGON];
        double chainP = Math.min(0.6, st.chainChance * luck);
        if (dragon) chainP = Math.max(chainP, Beasts.dragonChain());
        if ((st.chain || dragon) && rng.nextDouble() < chainP) {
            chain = findNear(world, pos, 3);
            if (!chain.isEmpty()) total = total.add(BigNum.mul(baseGain, 0.75 * chain.size()));
        }

        // Shockwave
        double shockP = Math.min(0.5, st.shockChance * luck);
        if (dragon) shockP = Math.max(shockP, Beasts.dragonShock());
        boolean shock = (st.shock || dragon) && rng.nextDouble() < shockP;
        if (shock) total = total.add(BigNum.mul(baseGain, 2.0));

        // Fever
        boolean feverStart = false;
        if (st.fever && rng.nextDouble() < Math.min(0.3, st.feverChance * luck)) {
            feverStart = !fever;
            feverUntil = Math.max(feverUntil, now) + (long) (st.feverSeconds * 1000);
        }

        // ---------- dioses ----------
        double gb = now < beastUntil[Beasts.KRAKEN] ? Beasts.krakenGods() : 1.0;
        if (st.midasChance > 0 && rng.nextDouble() < Math.min(0.9, st.midasChance * gb)) {
            total = total.add(BigNum.mul(baseGain, st.midasMult));
            gods.add("g_midas");
        }
        List<BlockPos> strikes = List.of();
        if (st.zeusStrikes > 0 && rng.nextDouble() < Math.min(0.9, st.zeusChance * gb)) {
            strikes = zeusTargets(world, pos, st.zeusStrikes);
            total = total.add(BigNum.mul(baseGain, st.zeusMult * strikes.size()));
            gods.add("g_zeus");
        }
        if (st.anubisEvery > 0 && (anubisCounter += (int) gb) >= st.anubisEvery) {
            anubisCounter = 0;
            total = total.add(BigNum.mul(baseGain, st.anubisMult));
            gods.add("g_anubis");
        }
        if (crit && st.odinRagChance > 0 && rng.nextDouble() < Math.min(0.9, st.odinRagChance * gb)) {
            total = total.add(BigNum.mul(baseGain, st.odinRagMult));
            gods.add("g_odin");
        }

        // Jackpot con pity + near miss (una sola tirada: ventana de jackpot dentro de la de "casi")
        gs.pity++;
        int pityMax = Math.max(30, (int) (c.pityMax * st.pityFactor));
        double pityBoost = 1 + 4.0 * Math.min(1.0, gs.pity / (double) pityMax);
        double jp = Math.min(0.5, c.jackpotChance * st.jackpotLuck * luck * pityBoost);
        double roll = rng.nextDouble();
        boolean jackpot = roll < jp || gs.pity >= pityMax || forceJackpot;
        double jpBoost = forceJackpot ? jackpotBoost : 1.0;
        forceJackpot = false;
        boolean near = !jackpot && roll < Math.min(0.9, jp * c.nearMissFactor);
        BigInteger jackpotWin = BigInteger.ZERO;
        if (jackpot) {
            jackpotWin = BigNum.mul(baseGain, c.jackpotBaseMult * st.jackpotPay * jpBoost).add(BigNum.mul(gs.points, c.jackpotPointsShare));
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
        tryBeasts(baseGain, op != null, now); // solo cuentan los bloques que rompes tú (no el fantasma)

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
            boolean over = now < skillActiveUntil[Skills.OVER];
            if (stats.idleBase > 0 || over) {
                double per = stats.idleBase > 0 ? stats.idlePerSec : refUnit() * 0.5;
                if (over) per *= Skills.overMult(Skills.level(gs, Skills.OVER));
                per *= (now < eventUntil ? stats.eventMult : 1.0);
                BigInteger g = BigNum.fromDouble(per);
                if (g.signum() > 0) {
                    gs.addPoints(g);
                    dirty = true;
                    SlotMine.hud.onScore(gs.points, false);
                }
            }
            // Ventisca de Fenrir: las habilidades se recargan x4
            if (now < beastUntil[Beasts.FENRIR]) for (int i = 0; i < skillReady.length; i++) skillReady[i] -= 3000;
            // Tormenta: paga cada segundo mientras dura
            if (now < skillActiveUntil[Skills.STORM]) {
                BigInteger pay = BigNum.mul(refGain(), Skills.stormMult(Skills.level(gs, Skills.STORM)));
                gs.addPoints(pay);
                dirty = true;
                SlotMine.hud.onScore(gs.points, false);
                SlotMine.fx.stormStrike(mc.player);
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

    // ---------------------------------------------------------------- bestias
    /** Cada bloque roto tiene una probabilidad muy baja de despertar a una de tus bestias (con bloqueo entre invocaciones). */
    private void tryBeasts(BigInteger baseGain, boolean ore, long now) {
        if (now < beastLockUntil || SlotMine.hud.beastActive()) return;
        GameState gs = SlotMine.state;
        double boost = stats.beastChance * (ore ? 2.0 : 1.0);   // los minerales OP llaman más a las bestias
        int n = Beasts.ALL.size();
        int start = rng.nextInt(n);
        for (int k = 0; k < n; k++) {
            int i = (start + k) % n;
            int l = Beasts.level(gs, i);
            if (l <= 0) continue;
            if (rng.nextDouble() < Math.min(0.02, Beasts.chance(i, l) * boost)) { summonBeast(i, baseGain, now); return; }
        }
    }

    private boolean anyGod(GameState gs) {
        for (Upgrades.God g : Upgrades.GODS) if (gs.run.getOrDefault(g.id(), 0) > 0) return true;
        return false;
    }
    private boolean anySkill(GameState gs) {
        for (int i = 0; i < Skills.ALL.size(); i++) if (Skills.level(gs, i) > 0) return true;
        return false;
    }

    private static String sec(double s) { return String.format(java.util.Locale.ROOT, "%.0f", s); }

    private void summonBeast(int idx, BigInteger baseGain, long now) {
        GameState gs = SlotMine.state;
        Stats st = stats;
        Beasts.Beast b = Beasts.ALL.get(idx);
        int l = Math.max(1, Beasts.level(gs, idx));
        double dm = st.beastDur;
        int blocks = Math.max(10, (int) Math.round(Beasts.blocks(idx, l) * (0.85 + 0.30 * rng.nextDouble())));
        double payMult = blocks * st.beastPower;
        String buff;
        long dur = 0;
        switch (idx) {
            case Beasts.TIGER -> {
                tigerCharges = Beasts.tigerCharges(l);
                dur = (long) (Beasts.tigerWindow() * 1000 * dm);
                buff = b.buffName() + " · " + tigerCharges + " críticos seguros";
            }
            case Beasts.DRAGON -> {
                dur = (long) (Beasts.dragonDur(l) * 1000 * dm);
                buff = b.buffName() + " · arcos y ondas " + sec(dur / 1000.0) + "s";
            }
            case Beasts.PHOENIX -> {
                phoenixLuckVal = Beasts.phoenixLuck(l);
                dur = (long) (Beasts.phoenixDur(l) * 1000 * dm);
                buff = b.buffName() + " · suerte ×" + String.format(java.util.Locale.ROOT, "%.1f", phoenixLuckVal) + " · " + sec(dur / 1000.0) + "s";
            }
            case Beasts.KRAKEN -> {
                dur = (long) (Beasts.krakenDur(l) * 1000 * dm);
                if (anyGod(gs)) buff = b.buffName() + " · dioses ×3 · " + sec(dur / 1000.0) + "s";
                else { payMult *= 1.5; buff = b.buffName() + " · sin dioses: +50% de pago"; }
            }
            case Beasts.FENRIR -> {
                dur = (long) (Beasts.fenrirDur(l) * 1000 * dm);
                if (anySkill(gs)) {
                    java.util.Arrays.fill(skillReady, 0);
                    buff = b.buffName() + " · habilidades listas · recarga ×4 · " + sec(dur / 1000.0) + "s";
                } else { payMult *= 1.5; buff = b.buffName() + " · sin habilidades: +50% de pago"; }
            }
            default -> buff = b.buffName();
        }
        BigInteger payout = BigNum.mul(baseGain, payMult);
        if (idx == Beasts.MONKEY) {
            BigInteger wealth = BigNum.mul(gs.points, Beasts.monkeyPct(l));
            BigInteger cap = BigNum.mul(baseGain, Beasts.monkeyCap(l) * st.beastPower);
            BigInteger tribute = wealth.min(cap);
            payout = payout.add(tribute);
            buff = b.buffName() + " · +" + BigNum.formatPrecise(tribute) + " de tus puntos";
        }
        if (dur > 0) { beastUntil[idx] = now + dur; beastTotal[idx] = dur; }
        beastLockUntil = now + Beasts.LOCKOUT_SEC * 1000L;
        gs.addPoints(payout);
        gs.beastsSummoned++;
        dirty = true;
        SlotMine.hud.onScore(gs.points, false);
        SlotMine.hud.onBeast(idx, blocks, payout, buff);
        SlotMine.fx.beast(idx);
        checkAchievements();
    }

    // ---------------------------------------------------------------- habilidades
    /** Valor de un bloque normal con todos los multiplicadores base (sin azar). */
    private double refUnit() { return SlotMine.config.defaultBlockValue * stats.gainMult * stats.coreMult; }

    /** Último bloque, o un bloque medio si aún no has picado. */
    private BigInteger refGain() {
        if (lastGain.signum() > 0) return lastGain;
        BigInteger b = BigNum.fromDouble(refUnit());
        return b.signum() == 0 ? BigInteger.ONE : b;
    }

    public int skillLevel(int i) { return Skills.level(SlotMine.state, i); }
    public boolean skillActive(int i) { return System.currentTimeMillis() < skillActiveUntil[i]; }
    /** 1 = lista; 0..1 = progreso del enfriamiento. */
    public double skillReadyFrac(int i) {
        long now = System.currentTimeMillis();
        if (now >= skillReady[i] || skillTotal[i] <= 0) return 1.0;
        return 1.0 - (skillReady[i] - now) / (double) skillTotal[i];
    }
    public double skillActiveFrac(int i) {
        long now = System.currentTimeMillis();
        return now >= skillActiveUntil[i] ? 0 : Math.min(1.0, (skillActiveUntil[i] - now) / (double) Math.max(1, skillTotal[i] > 0 ? activeLen(i) : 1));
    }
    private long activeLen(int i) {
        int l = skillLevel(i);
        return (long) (1000 * switch (i) {
            case Skills.FURY -> Skills.furyDur(l);
            case Skills.STORM -> Skills.stormDur(l);
            case Skills.OVER -> Skills.overDur(l);
            case Skills.STREAK -> Skills.streakDur(l);
            case Skills.VEIN -> Skills.veinDur(l);
            default -> 1;
        });
    }

    public void activateSkill(int i) {
        if (i < 0 || i >= Skills.ALL.size()) return;
        GameState gs = SlotMine.state;
        int l = Skills.level(gs, i);
        if (l <= 0) { SlotMine.hud.onSkillMsg("Habilidad bloqueada: cómprala en la tienda", 0xFFFF5555); return; }
        long now = System.currentTimeMillis();
        if (now < skillReady[i]) {
            SlotMine.hud.onSkillMsg(Skills.ALL.get(i).name() + " en enfriamiento: " + ((skillReady[i] - now) / 1000 + 1) + "s", 0xFF7C8799);
            return;
        }
        Skills.Skill sk = Skills.ALL.get(i);
        switch (i) {
            case Skills.FURY -> skillActiveUntil[i] = now + activeLen(i);      // Frenesí: ventana de multiplicador (aditivo)
            case Skills.VEIN -> skillActiveUntil[i] = now + activeLen(i);      // Veta viva: todo cuenta como mineral
            case Skills.QUAKE -> {
                BigInteger pay = BigNum.mul(refGain(), Skills.quakeMult(l));
                gs.addPoints(pay);
                SlotMine.hud.onSkillMsg("¡SISMO! +" + BigNum.formatPrecise(pay), sk.color());
            }
            case Skills.STORM -> skillActiveUntil[i] = now + activeLen(i);
            case Skills.OVER -> skillActiveUntil[i] = now + activeLen(i);
            case Skills.STREAK -> {
                streak = 100;
                lastBreak = now;
                skillActiveUntil[i] = now + activeLen(i);
            }
            case Skills.JACKPOT -> {
                forceJackpot = true;
                jackpotBoost = Skills.jackpotBoost(l);
                SlotMine.hud.onSkillMsg("Golpe maestro listo: el próximo bloque es JACKPOT", sk.color());
            }
            default -> { }
        }
        if (i != Skills.QUAKE && i != Skills.JACKPOT) SlotMine.hud.onSkillMsg("¡" + sk.name().toUpperCase(java.util.Locale.ROOT) + "!", sk.color());
        long cd = (long) (Skills.cooldown(i, l) * 1000);
        skillReady[i] = now + cd;
        skillTotal[i] = cd;
        gs.skillsUsed++;
        dirty = true;
        SlotMine.hud.onScore(gs.points, false);
        SlotMine.fx.skill(i);
        checkAchievements();
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
        if (d.tab() == Upgrades.Tab.ERAS && !Eras.unlocked(Eras.tierOf(d.id()), gs)) return 0;
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
    /**
     * Núcleos = floor(sqrt(ganado_en_la_run / 10^9)) hasta 10^8 núcleos; a partir de ahí crece suave (cúbica del
     * logaritmo) para que los números enormes (hasta vigintillones) no desborden.
     */
    public BigInteger coresOnAscend() {
        BigInteger e = SlotMine.state.runEarned;
        if (e.compareTo(BigInteger.TEN.pow(9)) < 0) return BigInteger.ZERO;
        double x = new BigDecimal(e).divide(BigDecimal.valueOf(1e9), java.math.MathContext.DECIMAL64).doubleValue();
        return BigDecimal.valueOf(Stats.coresFor(x)).toBigInteger();
    }

    private static long satAdd(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE / 4 : Math.min(r, Long.MAX_VALUE / 4);
    }

    public boolean ascend() {
        GameState gs = SlotMine.state;
        BigInteger n = coresOnAscend();
        if (n.signum() <= 0) return false;
        long add = n.min(BigInteger.valueOf(1_000_000_000_000L)).longValue();
        gs.cores = satAdd(gs.cores, add);
        gs.coresEarnedTotal = satAdd(gs.coresEarnedTotal, add);
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
        java.util.Arrays.fill(skillActiveUntil, 0);
        forceJackpot = false;
        java.util.Arrays.fill(beastUntil, 0);
        tigerCharges = 0;
        beastLockUntil = 0;
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
