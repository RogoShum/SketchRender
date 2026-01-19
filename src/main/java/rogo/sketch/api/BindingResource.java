package rogo.sketch.api;

import rogo.sketch.util.KeyId;

public interface BindingResource {
    void bind(KeyId resourceType, int binding);
}