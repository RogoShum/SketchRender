package rogo.sketchrender.mixin.render;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.ShaderProvider;
import rogo.sketchrender.render.minecraft.uniform.McUniformWrapperFactory;
import rogo.sketchrender.render.sketch.shader.UniformHookGroup;
import rogo.sketchrender.render.sketch.uniform.UniformHookRegistry;

import java.util.Map;

@Mixin(ShaderInstance.class)
public class ShaderInstanceMixin implements ShaderProvider {
    @Shadow
    @Final
    private Map<String, Uniform> uniformMap;
    @Shadow
    @Final
    private String name;
    @Shadow
    @Final
    private int programId;
    private UniformHookGroup uniformHookGroup = new UniformHookGroup();

    @Override
    public String getIdentifier() {
        return this.name;
    }

    @Override
    public UniformHookGroup getUniformHookGroup() {
        return uniformHookGroup;
    }

    @Override
    public int getHandle() {
        return this.programId;
    }

    @Inject(method = "updateLocations", at = @At(value = "RETURN"))
    public void onPostUniform(CallbackInfo ci) {
        UniformHookRegistry.getInstance().initializeHooks(this, McUniformWrapperFactory.convertUniformMap(uniformMap));
    }
}