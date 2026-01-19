package rogo.sketch.api;

import rogo.sketch.util.KeyId;

public interface ShaderResource<T> {
    KeyId id();

    void set(T value);
}