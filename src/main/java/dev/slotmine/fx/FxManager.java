package dev.slotmine.fx;

import dev.slotmine.SlotMine;
import dev.slotmine.core.Engine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Efectos 100% locales (partículas + sonidos de cliente, sin paquetes).
 * Optimizado para instamine: presupuesto de partículas por tick, cooldown de sonidos,
 * y "modo lite" automático cuando se rompen muchos bloques por segundo.
 */
public final class FxManager {
    private record Task(long due, Runnable run) {}

    private static final ParticleEffect GOLD = new DustParticleEffect(0xFFC94D, 1.0f);
    private static final ParticleEffect CYAN = new DustParticleEffect(0x4DD0E1, 1.0f);
    private static final ParticleEffect ORANGE = new DustParticleEffect(0xFF8A3D, 1.0f);
    private static final ParticleEffect RED = new DustParticleEffect(0xFF4040, 1.0f);
    private static final ParticleEffect SPARK = ParticleTypes.ELECTRIC_SPARK;

    private final List<Task> tasks = new ArrayList<>();
    private final Random rng = new Random();
    private final Map<String, SoundEvent> sounds = new HashMap<>();
    private final Map<String, Long> lastSound = new HashMap<>();
    private long tick;
    private int budget;
    private int breakCounter;

    // ------------------------------------------------------------------ infraestructura
    public void tick(MinecraftClient mc) {
        tick++;
        budget = SlotMine.config.particleBudgetPerTick;
        if (tasks.isEmpty()) return;
        List<Runnable> due = null;
        for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.due <= tick) {
                if (due == null) due = new ArrayList<>();
                due.add(t.run);
                it.remove();
            }
        }
        if (due != null) for (Runnable r : due) r.run();
    }

    private void later(int ticks, Runnable r) {
        if (tasks.size() < 200) tasks.add(new Task(tick + ticks, r));
    }

    private void p(ParticleEffect e, double x, double y, double z, double vx, double vy, double vz, boolean force) {
        if (!SlotMine.config.effectsEnabled) return;
        if (!force) {
            if (budget <= 0) return;
            budget--;
        }
        MinecraftClient.getInstance().particleManager.addParticle(e, x, y, z, vx, vy, vz);
    }

    public void sound(String path, float pitch, float vol, long minGapMs) {
        if (!SlotMine.config.soundEnabled) return;
        long now = System.currentTimeMillis();
        Long last = lastSound.get(path);
        if (minGapMs > 0 && last != null && now - last < minGapMs) return;
        lastSound.put(path, now);
        SoundEvent ev = sounds.computeIfAbsent(path, k -> SoundEvent.of(Identifier.of("minecraft", k)));
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(ev, pitch, vol * SlotMine.config.soundVolume));
    }

    private Vec3d randUnit() {
        Vec3d v = new Vec3d(rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian());
        return v.lengthSquared() < 1e-6 ? new Vec3d(0, 1, 0) : v.normalize();
    }

    private Vec3d tip(ClientPlayerEntity pl) {
        Vec3d eye = pl.getEyePos();
        Vec3d look = pl.getRotationVec(1.0f);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
        return eye.add(look.multiply(0.7)).add(right.multiply(0.38)).add(0, -0.28, 0);
    }

    // ------------------------------------------------------------------ primitivas visuales
    private void beam(Vec3d a, Vec3d b, int n) {
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n;
            p(SPARK, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, 0, 0, 0, false);
        }
    }

    private void implosion(Vec3d c, ParticleEffect e, int n) {
        for (int i = 0; i < n; i++) {
            Vec3d d = randUnit();
            p(e, c.x + d.x * 0.9, c.y + d.y * 0.9, c.z + d.z * 0.9, -d.x * 0.2, -d.y * 0.2, -d.z * 0.2, false);
        }
    }

    private void burst(Vec3d c, int n) {
        for (int i = 0; i < n; i++) {
            Vec3d d = randUnit();
            p(SPARK, c.x, c.y, c.z, d.x * 0.18, d.y * 0.18, d.z * 0.18, false);
        }
    }

    private void arc(Vec3d a, Vec3d b, int n) {
        Vec3d m = a.add(b).multiply(0.5).add(0, 0.6, 0);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, u = 1 - t;
            double x = u * u * a.x + 2 * u * t * m.x + t * t * b.x;
            double y = u * u * a.y + 2 * u * t * m.y + t * t * b.y;
            double z = u * u * a.z + 2 * u * t * m.z + t * t * b.z;
            p(SPARK, x, y, z, 0, 0, 0, false);
        }
    }

    private void ring(Vec3d c, double r, int n, ParticleEffect e, double out) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double dx = Math.cos(a), dz = Math.sin(a);
            p(e, c.x + dx * r, c.y - 0.2, c.z + dz * r, dx * out, 0.02, dz * out, false);
        }
    }

    // ------------------------------------------------------------------ eventos
    public void play(ClientPlayerEntity pl, Engine.BreakEvent e) {
        final Vec3d c = Vec3d.ofCenter(e.pos());
        final boolean lite = e.lite();
        breakCounter++;

        // base: haz fino pico -> bloque, implosión y destello de pocos ticks
        beam(tip(pl), c, lite ? 3 : 6);
        if (!lite || breakCounter % 3 == 0) {
            implosion(c, e.op() ? GOLD : SPARK, lite ? 4 : 7);
            later(2, () -> burst(c, lite ? 3 : 6));
        }
        sound("block.note_block.hat", 1.4f + rng.nextFloat() * 0.5f, 0.35f, lite ? 75 : 30);

        // mineral OP
        if (e.op()) {
            ring(c, 0.7, lite ? 6 : 12, GOLD, 0.05);
            sound("block.amethyst_block.chime", 1.0f + rng.nextFloat() * 0.4f, 0.55f, 60);
            sound("entity.experience_orb.pickup", 1.3f, 0.4f, 90);
        }

        // crit / spark
        if (e.crit()) {
            for (int i = 0; i < (lite ? 4 : 8); i++) {
                Vec3d d = randUnit();
                p(ParticleTypes.CRIT, c.x, c.y, c.z, d.x * 0.5, d.y * 0.5 + 0.1, d.z * 0.5, false);
            }
            sound("block.note_block.bit", 1.7f, 0.45f, 50);
        }

        // chain arc
        int i = 0;
        for (BlockPos t : e.chain()) {
            final Vec3d tc = Vec3d.ofCenter(t);
            final int idx = i++;
            later(idx * 2, () -> {
                arc(c, tc, 8);
                burst(tc, 3);
                sound("block.note_block.chime", 1.5f + 0.2f * idx, 0.4f, 0);
            });
        }

        // shockwave
        if (e.shock()) {
            for (int k = 1; k <= 4; k++) {
                final double r = 0.6 * k;
                later(k, () -> ring(c, r, 14, CYAN, 0.06));
            }
            sound("block.beacon.power_select", 2.0f, 0.4f, 200);
        }

        // fever
        if (e.feverStart()) {
            sound("block.note_block.pling", 1.0f, 0.5f, 0);
            later(3, () -> sound("block.note_block.pling", 1.26f, 0.5f, 0));
            later(6, () -> sound("block.note_block.pling", 1.5f, 0.6f, 0));
            Vec3d pp = pl.getEyePos();
            for (int k = 0; k < 8; k++) {
                Vec3d d = randUnit();
                p(ParticleTypes.END_ROD, pp.x + d.x, pp.y + d.y * 0.5, pp.z + d.z, 0, 0.03, 0, true);
            }
        }

        // near miss: tensión
        if (e.near()) {
            sound("block.note_block.bass", 0.7f, 0.6f, 0);
            later(5, () -> sound("block.note_block.bass", 0.55f, 0.6f, 0));
            for (int k = 0; k < 6; k++) {
                Vec3d d = randUnit();
                p(RED, c.x + d.x * 0.4, c.y + d.y * 0.4, c.z + d.z * 0.4, 0, 0.02, 0, false);
            }
        }

        // JACKPOT
        if (e.jackpot()) jackpot(pl);
    }

    private void jackpot(ClientPlayerEntity pl) {
        for (int k = 0; k < 10; k++) {
            final float pitch = 0.8f + k * 0.08f;
            later(k * 2, () -> sound("entity.experience_orb.pickup", pitch, 0.7f, 0));
        }
        later(12, () -> sound("ui.toast.challenge_complete", 1.0f, 0.6f, 0));
        Vec3d pp = pl.getEyePos();
        for (int k = 0; k < 24; k++) {
            double a = Math.PI * 2 * k / 24;
            p(ParticleTypes.FIREWORK, pp.x + Math.cos(a) * 1.2, pp.y - 0.3, pp.z + Math.sin(a) * 1.2,
                    Math.cos(a) * 0.05, 0.12, Math.sin(a) * 0.05, true);
        }
        for (int k = 0; k < 16; k++) {
            Vec3d d = randUnit();
            p(ParticleTypes.END_ROD, pp.x + d.x * 0.8, pp.y + d.y * 0.6, pp.z + d.z * 0.8, 0, 0.08, 0, true);
        }
        for (int k = 0; k < 16; k++) {
            Vec3d d = randUnit();
            p(GOLD, pp.x + d.x, pp.y + d.y * 0.7, pp.z + d.z, d.x * 0.05, 0.05, d.z * 0.05, true);
        }
    }

    public void combo(int kind) {
        sound("block.note_block.chime", kind == 2 ? 2.0f : 1.6f, 0.6f, 0);
        later(3, () -> sound("block.note_block.chime", kind == 2 ? 2.0f : 1.6f, 0.6f, 0));
    }

    public void clack() { sound("block.stone_button.click_on", 1.8f, 0.22f, 25); }
    public void uiBuy() { sound("block.note_block.pling", 1.6f, 0.4f, 0); }

    public void ascend() {
        sound("entity.player.levelup", 0.8f, 1.0f, 0);
        sound("block.beacon.activate", 1.2f, 0.7f, 0);
    }
}
