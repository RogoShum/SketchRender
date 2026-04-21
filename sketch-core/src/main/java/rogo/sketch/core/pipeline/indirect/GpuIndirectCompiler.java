package rogo.sketch.core.pipeline.indirect;

public interface GpuIndirectCompiler {
    GpuIndirectCompiler NO_OP = input -> GpuIndirectCompileResult.unhandled("gpu_indirect_compiler_unavailable");

    GpuIndirectCompileResult compile(GpuIndirectCompileInput input);
}
