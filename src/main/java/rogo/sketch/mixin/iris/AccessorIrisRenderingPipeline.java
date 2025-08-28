package rogo.sketch.mixin.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.SodiumTerrainPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IrisRenderingPipeline.class)
public interface AccessorIrisRenderingPipeline {

    @Accessor("sodiumTerrainPipeline")
    SodiumTerrainPipeline sodiumTerrainPipeline();
}