package rogo.sketch.mixin;

import com.mojang.blaze3d.shaders.Shader;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.api.ExtraUniform;
import rogo.sketch.feature.culling.CullingRenderEvent;
import rogo.sketch.event.ProgramEvent;
import rogo.sketch.render.shader.uniform.UnsafeUniformMap;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance implements ExtraUniform {
    private UnsafeUniformMap unsafeUniformMap;

    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V")
    public void construct(CallbackInfo ci) {
        this.unsafeUniformMap = new UnsafeUniformMap(((Shader) this).getId());
        MinecraftForge.EVENT_BUS.post(new ProgramEvent.Init(((Shader) this).getId(), this));
    }

    @Override
    public UnsafeUniformMap getUniforms() {
        return unsafeUniformMap;
    }

    @Inject(at = @At("TAIL"), method = "apply")
    public void onApply(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new ProgramEvent.Bind(((Shader) this).getId(), this));
    }

    @Mixin(RenderSystem.class)
    public static class MixinRenderSystem {
        @Inject(at = @At(value = "TAIL"), method = "setupShaderLights")
        private static void shader(ShaderInstance p_157462_, CallbackInfo ci) {
            CullingRenderEvent.setUniform(p_157462_);
        }
    }
}
