package rogo.sketchrender.render.sketch.uniform;

import rogo.sketchrender.util.Identifier;

public interface UniformSupplier {
    Object getValue(Identifier uniformName);
}