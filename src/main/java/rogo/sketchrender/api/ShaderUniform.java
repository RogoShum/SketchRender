package rogo.sketchrender.api;

import rogo.sketchrender.util.Identifier;

public interface ShaderUniform<T> {
    Identifier id();

    void set(T value);
}