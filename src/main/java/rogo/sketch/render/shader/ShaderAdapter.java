package rogo.sketch.render.shader;

import rogo.sketch.api.ShaderProvider;
import rogo.sketch.render.shader.uniform.UniformHookGroup;
import rogo.sketch.util.Identifier;

import java.util.Map;

/**
 * Simple adapter to make RecompilableShaderWrapper compatible with Shader interface
 * This is a composition-based approach that delegates all calls to the wrapped shader
 */
public class ShaderAdapter implements ShaderProvider {
    
    private final RecompilableShaderWrapper wrapper;
    
    public ShaderAdapter(RecompilableShaderWrapper wrapper) {
        this.wrapper = wrapper;
    }
    
    // Delegate all ShaderProvider methods to the current shader
    public void bind() {
        wrapper.getShader().bind();
    }
    
    public static void unbind() {
        Shader.unbind();
    }
    
    @Override
    public int getHandle() {
        return wrapper.getShader().getHandle();
    }
    
    @Override
    public Identifier getIdentifier() {
        return wrapper.getShader().getIdentifier();
    }
    
    @Override
    public UniformHookGroup getUniformHookGroup() {
        return wrapper.getShader().getUniformHookGroup();
    }
    
    @Override
    public Map<Identifier, Map<Identifier, Integer>> getResourceBindings() {
        return wrapper.getShader().getResourceBindings();
    }
    
    @Override
    public void dispose() {
        wrapper.dispose();
    }
    
    @Override
    public boolean isDisposed() {
        return wrapper.getShader().isDisposed();
    }
    
    /**
     * Get access to recompilation features
     * @return the wrapper with recompilation capabilities
     */
    public RecompilableShaderWrapper getRecompilableWrapper() {
        return wrapper;
    }
    
    /**
     * Get the current shader instance (for type-specific operations)
     * @return the current shader instance
     */
    public Shader getCurrentShader() {
        return wrapper.getShader();
    }
    
    /**
     * Check if this is a recompilable shader
     */
    public boolean isRecompilable() {
        return true;
    }
}
