package dev.neonviewmodel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración mínima en config/neonviewmodel.json (se edita a mano; sin menús).
 */
public final class NeonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NeonConfig instance = new NeonConfig();

    /** Activa/desactiva todo el efecto. */
    public boolean enabled = true;
    /** Multiplicador global de brillo (0.0 - 2.0). */
    public float intensity = 1.0f;
    /** Estelas de movimiento. */
    public boolean trails = true;
    /** Viñeta de color en los bordes de la pantalla al girar rápido o golpear. */
    public boolean edgeGlow = true;
    /** Líneas de velocidad al girar la cámara. */
    public boolean speedLines = true;
    /** Destellos en forma de estrella alrededor del brillo. */
    public boolean sparkles = true;
    /** false = solo espadas/hachas/herramientas/arcos/tridente/maza. true = cualquier ítem en mano. */
    public boolean affectAllItems = false;
    /** AUTO | PLASMA_PINK | ELECTRIC_BLUE | EMERALD */
    public String preset = "AUTO";

    public static NeonConfig get() { return instance; }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("neonviewmodel.json");
        try {
            if (Files.exists(file)) {
                NeonConfig read = GSON.fromJson(Files.readString(file), NeonConfig.class);
                if (read != null) instance = read;
            }
            instance.intensity = Math.max(0f, Math.min(2f, instance.intensity));
            if (instance.preset == null) instance.preset = "AUTO";
            Files.writeString(file, GSON.toJson(instance)); // normaliza / crea el archivo
        } catch (IOException | RuntimeException e) {
            instance = new NeonConfig(); // ante cualquier error, valores por defecto
        }
    }
}
