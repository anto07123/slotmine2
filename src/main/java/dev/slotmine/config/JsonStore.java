package dev.slotmine.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.slotmine.SlotMine;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/** Carga/guardado JSON seguro: escritura atómica (tmp + move) y copia de archivos corruptos. */
public final class JsonStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonStore() {}

    public static <T> T load(Path file, Class<T> type, Supplier<T> fresh) {
        if (!Files.exists(file)) return fresh.get();
        try (Reader r = Files.newBufferedReader(file)) {
            T v = GSON.fromJson(r, type);
            return v != null ? v : fresh.get();
        } catch (Exception e) {
            SlotMine.LOG.error("No se pudo leer {} — se renombra y se empieza de cero", file, e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
            return fresh.get();
        }
    }

    public static void save(Path file, Object data) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, w);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            SlotMine.LOG.error("No se pudo guardar {}", file, e);
        }
    }
}
