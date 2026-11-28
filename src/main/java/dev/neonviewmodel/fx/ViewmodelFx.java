package dev.neonviewmodel.fx;

import dev.neonviewmodel.NeonConfig;
import dev.neonviewmodel.NeonViewmodelClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;

/**
 * Efecto visual en primera persona (100 % cliente, sin mixins), pensado para parecerse al vídeo:
 *  1. Viñeta neón en los bordes de la pantalla al girar rápido o golpear (los "latigazos" magenta/violeta).
 *  2. Líneas de velocidad en la dirección del giro de cámara.
 *  3. Base oscura bajo el brillo -> contraste marcado, el neón destaca incluso sobre cielo o nieve.
 *  4. Aura de doble tono + núcleo caliente, con pulso suave.
 *  5. Estela de cometa y rayas de movimiento ligadas al desplazamiento de la mano.
 *  6. Destellos en forma de estrella que giran alrededor del arma.
 */
public final class ViewmodelFx implements HudElement {
    private static Identifier tex(String n) { return Identifier.of(NeonViewmodelClient.MOD_ID, "textures/gui/" + n + ".png"); }
    private static final Identifier GLOW = tex("glow"), STREAK = tex("streak"),
            VIGNETTE = tex("vignette"), SPARKLE = tex("sparkle");

    private static final long TRAIL_NANOS = 240_000_000L;
    private static final long VEL_WINDOW_NANOS = 100_000_000L;
    private static final int DARK = 0x0A0620;   // azul-violeta casi negro (base de contraste)

    // Posiciones fijas (fracción de pantalla) de las líneas de velocidad
    private static final float[] LX = {.20f, .70f, .40f, .85f, .15f, .60f, .30f, .50f};
    private static final float[] LY = {.12f, .27f, .41f, .55f, .68f, .80f, .91f, .34f};
    private static final float[] LL = {1.0f, .7f, .9f, .6f, .8f, 1.1f, .65f, .85f};

    private record Sample(float x, float y, long t) {}

    private final ArrayDeque<Sample> history = new ArrayDeque<>();
    private final float[] c1 = new float[3], c2 = new float[3];
    private boolean init;
    private long lastNanos;
    private float lastYaw, lastPitch, yawVel, pitchVel, presence;

    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        NeonConfig cfg = NeonConfig.get();

        if (!cfg.enabled || player == null || mc.currentScreen != null
                || !mc.options.getPerspective().isFirstPerson() || player.isSpectator()) {
            reset();
            return;
        }

        // ---- Tiempo y velocidad angular de cámara (suavizada) ----
        long now = System.nanoTime();
        float dt = init ? MathHelper.clamp((now - lastNanos) / 1.0e9f, 0.001f, 0.1f) : 0.016f;
        lastNanos = now;
        float yaw = player.getYaw(), pitch = player.getPitch();
        if (init) {
            float k = 1f - (float) Math.exp(-dt * 14f);
            yawVel += (MathHelper.wrapDegrees(yaw - lastYaw) / dt - yawVel) * k;
            pitchVel += ((pitch - lastPitch) / dt - pitchVel) * k;
        }
        lastYaw = yaw;
        lastPitch = pitch;
        init = true;

        // ---- Preset del ítem + transiciones suaves ----
        NeonPreset preset = NeonPreset.resolve(player.getMainHandStack(), cfg);
        presence += ((preset != null ? 1f : 0f) - presence) * (1f - (float) Math.exp(-dt * 10f));
        if (preset != null) {
            float kc = presence < 0.05f ? 1f : 1f - (float) Math.exp(-dt * 8f);
            blend(c1, preset.primary, kc);
            blend(c2, preset.secondary, kc);
        }
        if (presence < 0.01f) { history.clear(); return; }

        // ---- Ancla de la mano y movimiento ----
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float side = mc.options.getMainArm().getValue() == Arm.LEFT ? -1f : 1f;
        float swing = player.getHandSwingProgress(tickCounter.getTickProgress(false));
        float arc = MathHelper.sin(swing * (float) Math.PI);
        float ax = w * 0.5f + side * w * 0.27f
                - MathHelper.clamp(yawVel / 360f, -1f, 1f) * w * 0.09f - side * arc * w * 0.20f;
        float ay = h * 0.80f
                - MathHelper.clamp(pitchVel / 360f, -1f, 1f) * h * 0.12f - arc * h * 0.12f;

        history.addLast(new Sample(ax, ay, now));
        while (history.size() > 1 && now - history.peekFirst().t() > TRAIL_NANOS) history.removeFirst();
        Sample ref = history.peekFirst();
        for (Sample s : history) if (now - s.t() <= VEL_WINDOW_NANOS) { ref = s; break; }
        float vx = ax - ref.x(), vy = ay - ref.y();
        float motion = MathHelper.clamp((float) Math.sqrt(vx * vx + vy * vy) / (0.035f * w), 0f, 1f);
        float angle = (float) Math.atan2(vy, vx);

        // Movimiento solo de cámara (para viñeta y líneas de velocidad)
        float camMotion = MathHelper.clamp((float) Math.hypot(yawVel, pitchVel) / 500f, 0f, 1f);
        float camAngle = (float) Math.atan2(-pitchVel, -yawVel);

        double sec = now / 1.0e9;
        float pulse = 1f + 0.10f * (float) Math.sin(sec * 2.4);
        float I = cfg.intensity * presence;
        int rgb1 = pack(c1), rgb2 = pack(c2), hot = pack(mix(c1, 255f, 0.6f));
        float boost = 1f + 0.9f * arc;

        // 1) Viñeta de bordes (violeta/secundario detrás, primario encima)
        if (cfg.edgeGlow) {
            float e = MathHelper.clamp(0.75f * camMotion + 0.55f * arc, 0f, 1f) * I;
            draw(ctx, VIGNETTE, 256, 256, w / 2f, h / 2f, w * 1.12f, h * 1.12f, 0f, .5f, rgb2, 0.55f * e);
            draw(ctx, VIGNETTE, 256, 256, w / 2f, h / 2f, w, h, 0f, .5f, rgb1, 0.40f * e);
        }

        // 2) Líneas de velocidad
        if (cfg.speedLines && camMotion > 0.12f) {
            float cx = (float) Math.cos(camAngle), cy = (float) Math.sin(camAngle);
            for (int i = 0; i < LX.length; i++) {
                float phase = (float) ((sec * 1.8 + i * 0.137) % 1.0);
                float slide = (phase - 0.5f) * w * 0.30f;
                float a = 0.50f * I * camMotion * MathHelper.sin(phase * (float) Math.PI);
                draw(ctx, STREAK, 256, 32, w * LX[i] + cx * slide, h * LY[i] + cy * slide,
                        w * 0.24f * LL[i] * (0.4f + camMotion), h * 0.018f, camAngle, 1f,
                        (i & 1) == 0 ? rgb1 : rgb2, a);
            }
        }

        // 3) Base oscura: da contraste al neón sobre fondos claros
        float dark = Math.min(I, 1f);
        draw(ctx, GLOW, 256, 256, ax, ay, h * 0.80f, h * 0.80f, 0f, .5f, DARK, 0.34f * dark);

        // 4/5) Estela, aura y núcleo
        if (cfg.trails && motion > 0.02f) {
            for (Sample s : history) {
                float f = (now - s.t()) / (float) TRAIL_NANOS;
                float sz = h * 0.32f * (1f - 0.55f * f);
                draw(ctx, GLOW, 256, 256, s.x(), s.y(), sz, sz, 0f, .5f, pack(lerp(c1, c2, f)),
                        0.40f * I * motion * (float) Math.pow(1f - f, 1.5));
            }
        }
        draw(ctx, GLOW, 256, 256, ax, ay, h * 0.66f, h * 0.66f, 0f, .5f, rgb2, 0.36f * I * pulse * boost);
        draw(ctx, GLOW, 256, 256, ax, ay, h * 0.36f, h * 0.36f, 0f, .5f, rgb1, 0.52f * I * pulse * boost);
        draw(ctx, GLOW, 256, 256, ax, ay, h * 0.13f, h * 0.13f, 0f, .5f, hot, 0.60f * I * boost);

        // 6) Destellos en estrella orbitando el arma
        if (cfg.sparkles) {
            for (int i = 0; i < 5; i++) {
                float ph = (float) (sec * (0.6 + 0.13 * i) + i * 2.4);
                float r = h * (0.10f + 0.05f * (i % 3));
                float tw = (float) Math.pow(Math.max(0, Math.sin(sec * 3.0 + i * 1.7)), 3);
                float sz = h * (0.05f + 0.025f * (i % 2)) * (1f + arc);
                draw(ctx, SPARKLE, 128, 128, ax + (float) Math.cos(ph) * r, ay + (float) Math.sin(ph) * r * 0.8f,
                        sz, sz, ph * 0.5f, .5f, (i & 1) == 0 ? hot : rgb1, 0.85f * I * tw);
            }
        }

        // Rayas de movimiento de la mano (cabeza en el ancla)
        if (cfg.trails && motion > 0.05f) {
            float len = h * 0.08f + motion * w * 0.30f;
            float thick = h * 0.05f * (0.6f + 0.4f * motion);
            draw(ctx, STREAK, 256, 32, ax, ay, len, thick, angle, 1f, rgb1, 0.80f * I * motion);
            draw(ctx, STREAK, 256, 32, ax, ay + thick * 0.35f, len * 0.7f, thick * 0.6f,
                    angle + 0.12f, 1f, rgb2, 0.65f * I * motion);
        }
    }

    // ------------------------------------------------------------------ utilidades

    /** Dibuja una textura tintada usando la pila de matrices 2D del DrawContext (1.21.6+). */
    private static void draw(DrawContext ctx, Identifier tex, int texW, int texH,
                             float x, float y, float width, float height,
                             float angle, float pivotX, int rgb, float alpha) {
        int a = MathHelper.clamp((int) (alpha * 255f), 0, 255);
        if (a < 3) return;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.rotate(angle);
        m.scale(width / texW, height / texH);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                Math.round(-texW * pivotX), -texH / 2, 0f, 0f, texW, texH, texW, texH, (a << 24) | rgb);
        m.popMatrix();
    }

    private void reset() { init = false; presence = 0f; history.clear(); }

    private static void blend(float[] c, int rgb, float k) {
        c[0] += (((rgb >> 16) & 0xFF) - c[0]) * k;
        c[1] += (((rgb >> 8) & 0xFF) - c[1]) * k;
        c[2] += ((rgb & 0xFF) - c[2]) * k;
    }
    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }
    private static float[] mix(float[] c, float target, float t) {
        return new float[]{c[0] + (target - c[0]) * t, c[1] + (target - c[1]) * t, c[2] + (target - c[2]) * t};
    }
    private static int pack(float[] c) {
        return (MathHelper.clamp((int) c[0], 0, 255) << 16) | (MathHelper.clamp((int) c[1], 0, 255) << 8)
                | MathHelper.clamp((int) c[2], 0, 255);
    }
}
