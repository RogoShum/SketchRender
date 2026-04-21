package rogo.sketch.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.vanilla.MinecraftHostAdapter;
import rogo.sketch.vanilla.PipelineUtil;

@Mixin(Entity.class)
public class MixinEntityLifecycle {
    @Inject(method = "onAddedToWorld", at = @At("TAIL"))
    private void sketch$onAddedToWorld(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() != null && entity.level().isClientSide()) {
            MinecraftHostAdapter.getInstance().onEntityAdded(PipelineUtil.pipeline(), entity);
        }
    }

    @Inject(method = "onRemovedFromWorld", at = @At("TAIL"))
    private void sketch$onRemovedFromWorld(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() == null || entity.level().isClientSide()) {
            MinecraftHostAdapter.getInstance().onEntityRemoved(PipelineUtil.pipeline(), (Entity) (Object) this);
        }
    }
}