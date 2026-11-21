package dev.slotmine.mixin;

import dev.slotmine.SlotMine;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Shadow @Final private MinecraftClient client;
    @Unique private BlockState slotmine$before;

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void slotmine$head(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        slotmine$before = client.world != null ? client.world.getBlockState(pos) : null;
    }

    @Inject(method = "breakBlock", at = @At("RETURN"))
    private void slotmine$return(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && slotmine$before != null && client.world != null
                && client.player != null && SlotMine.engine != null) {
            SlotMine.engine.onBlockBroken(client.world, client.player, pos, slotmine$before);
        }
        slotmine$before = null;
    }
}
