package dev.slotmine;

import dev.slotmine.config.SlotConfig;
import dev.slotmine.core.Engine;
import dev.slotmine.core.GameState;
import dev.slotmine.fx.FxManager;
import dev.slotmine.hud.ReelHud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Contenedor estático de los singletons del mod (todo vive en el cliente). */
public final class SlotMine {
    public static final String MOD_ID = "slotmine";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static Path dir;          // config/slotmine/
    public static SlotConfig config;
    public static GameState state;
    public static Engine engine;
    public static FxManager fx;
    public static ReelHud hud;

    private SlotMine() {}
}
