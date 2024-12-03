package rogo.sketchrender.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.ExtraUniform;
import rogo.sketchrender.culling.CullingRenderEvent;
import rogo.sketchrender.event.ProgramEvent;
import rogo.sketchrender.shader.uniform.UnsafeUniformMap;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance implements ExtraUniform {
    private UnsafeUniformMap unsafeUniformMap;

    @Final
    @Shadow
    private int programId;

    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V")
    public void construct(CallbackInfo ci) {
        this.unsafeUniformMap = new UnsafeUniformMap(this.programId);
        MinecraftForge.EVENT_BUS.post(new ProgramEvent.Init(this.programId, this));
    }

    @Override
    public UnsafeUniformMap getUniforms() {
        return unsafeUniformMap;
    }

    @Inject(at = @At("TAIL"), method = "apply")
    public void onApply(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new ProgramEvent.Bind(this.programId, this));
    }

    @Mixin(RenderSystem.class)
    public static class MixinRenderSystem {
        @Inject(at = @At(value = "TAIL"), method = "setupShaderLights")
        private static void shader(ShaderInstance p_157462_, CallbackInfo ci) {
            CullingRenderEvent.setUniform(p_157462_);
        }
    }
}
