package rogo.sketch.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.feature.culling.CullingStateManager;

@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRender {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;tryRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/lang/Runnable;)V"), cancellable = true)
    public <E extends BlockEntity> void onShouldRender(E p_112268_, float p_112269_, PoseStack p_112270_, MultiBufferSource p_112271_, CallbackInfo ci) {
        if (CullingStateManager.shouldSkipBlockEntity(p_112268_, p_112268_.getBlockPos()))
            ci.cancel();
    }
}
