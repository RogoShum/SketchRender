package rogo.sketchrender.minecraft.uniform;

import com.mojang.blaze3d.shaders.Uniform;
import rogo.sketchrender.api.ShaderUniform;
import rogo.sketchrender.util.Identifier;

public class McUniformWrapper<T> implements ShaderUniform<T> {
    private final Identifier id;
    private final Uniform mcUniform;
    private final McUniformWrapperFactory.UniformApplier<T> setter;

    public McUniformWrapper(Identifier id, Uniform mcUniform, McUniformWrapperFactory.UniformApplier<T> setter) {
        this.id = id;
        this.mcUniform = mcUniform;
        this.setter = setter;
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public void set(T value) {
        setter.apply(mcUniform, value);
    }
}