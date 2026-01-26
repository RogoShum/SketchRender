package rogo.sketch.core.api;

import rogo.sketch.core.util.KeyId;

public interface ShaderResource<T> {
    KeyId id();

    void set(T value);
}