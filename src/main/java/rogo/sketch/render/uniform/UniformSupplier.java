package rogo.sketch.render.uniform;

import rogo.sketch.util.Identifier;

public interface UniformSupplier {
    Object getValue(Identifier uniformName);
}