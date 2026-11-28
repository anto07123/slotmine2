package dev.neonviewmodel;

import dev.neonviewmodel.fx.ViewmodelFx;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.util.Identifier;

/**
 * Punto de entrada CLIENTE. No registra ítems, bloques, entidades, comandos ni paquetes:
 * solo engancha un elemento de render a la capa de HUD. Por eso el servidor no nota nada.
 */
public final class NeonViewmodelClient implements ClientModInitializer {
    public static final String MOD_ID = "neonviewmodel";

    @Override
    public void onInitializeClient() {
        NeonConfig.load();
        // addFirst: se dibuja DETRÁS del HUD vanilla (hotbar, crosshair...) y encima de la mano/mundo.
        HudElementRegistry.addFirst(Identifier.of(MOD_ID, "viewmodel_fx"), new ViewmodelFx());
    }
}
