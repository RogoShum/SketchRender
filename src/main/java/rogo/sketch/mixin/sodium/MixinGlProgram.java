package rogo.sketch.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.GlObject;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.api.ExtraUniform;
import rogo.sketch.compat.sodium.ChunkShaderTracker;
import rogo.sketch.event.ProgramEvent;
import rogo.sketch.render.shader.uniform.UnsafeUniformMap;

import java.util.function.Function;

@Mixin(GlProgram.class)
public abstract class MixinGlProgram extends GlObject implements ExtraUniform {
    private UnsafeUniformMap unsafeUniformMap;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void onInit(int program, Function interfaceFactory, CallbackInfo ci) {
        unsafeUniformMap = new UnsafeUniformMap(program);
        MinecraftForge.EVENT_BUS.post(new ProgramEvent.Init(this.getUniforms().getProgramId(), this));
    }

    @Inject(method = "bind", at = @At("TAIL"), remap = false)
    private void onBind(CallbackInfo ci) {
        ChunkShaderTracker.lastProgram = this.handle();
        MinecraftForge.EVENT_BUS.post(new ProgramEvent.Bind(this.getUniforms().getProgramId(), this));
    }

    @Override
    public UnsafeUniformMap getUniforms() {
        return unsafeUniformMap;
    }
}
