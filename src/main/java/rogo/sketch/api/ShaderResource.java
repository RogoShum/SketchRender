package rogo.sketch.api;

import rogo.sketch.util.Identifier;

public interface ShaderResource<T> {
    Identifier id();

    void set(T value);
}