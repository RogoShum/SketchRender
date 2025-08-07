package rogo.sketchrender.api;

import rogo.sketchrender.util.Identifier;

public interface ShaderResource<T> {
    Identifier id();

    void set(T value);
}