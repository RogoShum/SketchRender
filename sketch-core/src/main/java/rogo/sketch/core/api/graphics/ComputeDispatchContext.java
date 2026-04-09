package rogo.sketch.core.api.graphics;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.uniform.UniformHookGroup;

public interface ComputeDispatchContext {
    RenderContext renderContext();

    ShaderProgramHandle programHandle();

    UniformHookGroup uniformHookGroup();

    void dispatch(int numGroupsX, int numGroupsY, int numGroupsZ);

    void memoryBarrier(int barriers);

    void shaderStorageBarrier();

    void allBarriers();
}

