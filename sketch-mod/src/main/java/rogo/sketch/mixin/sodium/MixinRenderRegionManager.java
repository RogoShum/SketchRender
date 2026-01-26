package rogo.sketch.mixin.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rogo.sketch.compat.sodium.MeshResource;

@Mixin(value = RenderRegionManager.class, remap = false)
public abstract class MixinRenderRegionManager {

    @Inject(method = "create", at = @At(value = "RETURN", remap = false, target = "Lit/unimi/dsi/fastutil/longs/Long2ReferenceOpenHashMap;put(JLjava/lang/Object;)Ljava/lang/Object;"))
    private void onCreate(int x, int y, int z, CallbackInfoReturnable<RenderRegion> cir) {
        MeshResource.addIndexedRegion(cir.getReturnValue());
    }
}