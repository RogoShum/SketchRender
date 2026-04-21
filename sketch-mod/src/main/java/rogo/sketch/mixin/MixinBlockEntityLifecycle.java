package rogo.sketch.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.vanilla.MinecraftHostAdapter;
import rogo.sketch.vanilla.PipelineUtil;

@Mixin(BlockEntity.class)
public class MixinBlockEntityLifecycle {
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void sketch$onSetLevel(Level level, CallbackInfo ci) {
        if (level == null) {
            MinecraftHostAdapter.getInstance().onBlockEntityRemoved(PipelineUtil.pipeline(), (BlockEntity) (Object) this);
            return;
        }
        if (level.isClientSide()) {
            MinecraftHostAdapter.getInstance().onBlockEntityLevelSet(PipelineUtil.pipeline(), (BlockEntity) (Object) this);
        }
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void sketch$onSetRemoved(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (!blockEntity.hasLevel() || blockEntity.getLevel().isClientSide()) {
            MinecraftHostAdapter.getInstance().onBlockEntityRemoved(PipelineUtil.pipeline(), blockEntity);
        }
    }
}
