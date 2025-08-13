package rogo.sketch.mixin.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.vanilla.uniform.McUniformWrapperFactory;
import rogo.sketch.render.uniform.UniformHookGroup;
import rogo.sketch.render.uniform.UniformHookRegistry;
import rogo.sketch.util.Identifier;

import java.util.Map;

@Mixin(ShaderInstance.class)
public class ShaderInstanceMixin implements ShaderProvider {
    @Shadow
    @Final
    private Map<String, Uniform> uniformMap;
    private Identifier identifier;
    @Shadow
    @Final
    private int programId;
    private UniformHookGroup uniformHookGroup = new UniformHookGroup();

    @Override
    public Identifier getIdentifier() {
        return this.identifier;
    }

    @Override
    public UniformHookGroup getUniformHookGroup() {
        return uniformHookGroup;
    }

    @Override
    public Map<Identifier, Map<Identifier, Integer>> getResourceBindings() {
        return Map.of();
    }

    @Override
    public int getHandle() {
        return this.programId;
    }

    @Override
    public void dispose() {

    }

    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V", at = @At(value = "RETURN"))
    public void onInit(ResourceProvider p_173336_, ResourceLocation shaderLocation, VertexFormat p_173338_, CallbackInfo ci) {
        identifier = Identifier.valueOf(shaderLocation);
    }

    @Inject(method = "updateLocations", at = @At(value = "RETURN"))
    public void onPostUniform(CallbackInfo ci) {
        UniformHookRegistry.getInstance().initializeHooks(this, McUniformWrapperFactory.convertUniformMap(uniformMap));
    }
}