package rogo.sketch.core.shader;

import rogo.sketch.core.api.ShaderResource;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.Map;

public interface ProgramReflectionService {
    ProgramReflectionService NO_OP = new ProgramReflectionService() {
        @Override
        public Map<String, ShaderResource<?>> collectUniforms(int program) {
            return Collections.emptyMap();
        }

        @Override
        public Map<KeyId, Map<KeyId, Integer>> discoverResourceBindings(int program) {
            return Collections.emptyMap();
        }

        @Override
        public UniformHookGroup initializeHooks(int program, Map<String, ? extends ShaderResource<?>> uniforms) {
            return new UniformHookGroup();
        }
    };

    Map<String, ShaderResource<?>> collectUniforms(int program);

    Map<KeyId, Map<KeyId, Integer>> discoverResourceBindings(int program);

    UniformHookGroup initializeHooks(int program, Map<String, ? extends ShaderResource<?>> uniforms);
}
