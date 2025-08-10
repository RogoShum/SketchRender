package rogo.sketch.api;

import rogo.sketch.render.shader.UniformHookGroup;
import rogo.sketch.util.Identifier;

import java.util.Map;

public interface ShaderProvider extends ResourceObject {
    Identifier getIdentifier();

    UniformHookGroup getUniformHookGroup();
    
    /**
     * Get SSBO bindings discovered from this shader
     */
    Map<String, Integer> getSSBOBindings();
    
    /**
     * Get UBO bindings discovered from this shader
     */
    Map<String, Integer> getUBOBindings();
    
    /**
     * Get texture bindings discovered from this shader
     */
    Map<String, Integer> getTextureBindings();
    
    /**
     * Get image bindings discovered from this shader
     */
    Map<String, Integer> getImageBindings();
    
    /**
     * Get atomic counter bindings discovered from this shader
     */
    Map<String, Integer> getAtomicCounterBindings();
}