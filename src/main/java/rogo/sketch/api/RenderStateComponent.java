package rogo.sketch.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.KeyId;

public interface RenderStateComponent {
    KeyId getIdentifier();

    boolean equals(Object other);

    void apply(RenderContext context);

    int hashCode();
    
    /**
     * Deserialize from JSON data
     * This method should modify the component's internal state based on the JSON
     */
    void deserializeFromJson(JsonObject json, Gson gson);
    
    /**
     * Create a new instance of this component type
     * This should return a new instance with default values
     */
    RenderStateComponent createInstance();
}