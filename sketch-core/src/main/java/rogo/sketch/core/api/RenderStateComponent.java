package rogo.sketch.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.util.KeyId;

public interface RenderStateComponent {
    KeyId getIdentifier();

    boolean equals(Object other);

    int hashCode();
    
    /**
     * Deserialize from JSON data
     * This method should modify the component's internal state based on the JSON
     */
    void deserializeFromJson(JsonObject json, Gson gson, @Nullable GraphicsResourceManager resourceManager);
    
    /**
     * Create a new instance of this component type
     * This should return a new instance with default values
     */
    RenderStateComponent createInstance();
}
