package dev.slotmine;

import dev.slotmine.config.JsonStore;
import dev.slotmine.config.SlotConfig;
import dev.slotmine.core.Engine;
import dev.slotmine.core.GameState;
import dev.slotmine.fx.FxManager;
import dev.slotmine.hud.ReelHud;
import dev.slotmine.ui.ShopScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SlotMineClient implements ClientModInitializer {
    private static KeyBinding shopKey;

    @Override
    public void onInitializeClient() {
        SlotMine.dir = FabricLoader.getInstance().getConfigDir().resolve(SlotMine.MOD_ID);

        SlotMine.config = JsonStore.load(SlotMine.dir.resolve("config.json"), SlotConfig.class, SlotConfig::new);
        JsonStore.save(SlotMine.dir.resolve("config.json"), SlotMine.config); // añade campos nuevos al archivo
        SlotMine.state = JsonStore.load(SlotMine.dir.resolve("state.json"), GameState.class, GameState::new);
        SlotMine.state.sanitize();

        SlotMine.engine = new Engine();
        SlotMine.engine.refreshStats();
        SlotMine.fx = new FxManager();
        SlotMine.hud = new ReelHud();
        SlotMine.hud.onScore(SlotMine.state.points, false);

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(SlotMine.MOD_ID, "main"));
        shopKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.slotmine.shop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (shopKey.wasPressed()) {
                if (mc.player != null && mc.currentScreen == null) mc.setScreen(new ShopScreen());
            }
            SlotMine.engine.tick(mc);
            SlotMine.fx.tick(mc);
        });

        // Hook de rotura 100% cliente (sin paquetes): se dispara cuando el cliente rompe el bloque.
        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) ->
                SlotMine.engine.onBlockBroken(world, player, pos, state));

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(SlotMine.MOD_ID, "reels"), SlotMine.hud);

        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> SlotMine.engine.save());
    }
}
