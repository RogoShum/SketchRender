package rogo.sketchrender.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.EntitiesForRender;
import rogo.sketchrender.culling.CullingStateManager;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRender implements EntitiesForRender {

    @Mutable
    @Final
    @Shadow
    private ObjectArrayList<LevelRenderer.RenderChunkInfo> renderChunksInFrustum;


    @Inject(method = "applyFrustum", at = @At(value = "HEAD"))
    public void onApplyFrustumHead(Frustum p_194355_, CallbackInfo ci) {
        CullingStateManager.applyFrustum = true;
        CullingStateManager.updating();
    }

    @Inject(method = "applyFrustum", at = @At(value = "RETURN"))
    public void onApplyFrustumReturn(Frustum p_194355_, CallbackInfo ci) {
        CullingStateManager.applyFrustum = false;
    }

    @Inject(method = "prepareCullFrustum", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;<init>(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    public void onPrepareCullFrustum(PoseStack p_172962_, Vec3 p_172963_, Matrix4f p_172964_, CallbackInfo ci) {
        CullingStateManager.PROJECTION_MATRIX = new Matrix4f(p_172964_);
    }

    @Override
    public ObjectArrayList<?> renderChunksInFrustum() {
        return renderChunksInFrustum;
    }
}

