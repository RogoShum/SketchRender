package rogo.sketch.core.api;

import rogo.sketch.core.util.KeyId;

public interface BindingResource {
    void bind(KeyId resourceType, int binding);
}