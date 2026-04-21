package rogo.sketch.core.driver.state;

public record CompiledRasterState(
        RenderStatePatch patch,
        PipelineRasterState pipelineRasterState,
        DynamicRenderState dynamicRenderState,
        AttachmentBindingState attachmentBindingState,
        ShaderBindingState shaderBindingState
) {
    public static CompiledRasterState empty() {
        return RasterStateCompiler.compile(RenderStatePatch.empty());
    }
}
