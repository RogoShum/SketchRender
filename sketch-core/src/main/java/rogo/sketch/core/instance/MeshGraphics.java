package rogo.sketch.core.instance;

import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.api.graphics.RasterGraphics;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

public abstract class MeshGraphics implements RasterGraphics {
    private final KeyId id;

    public MeshGraphics(KeyId keyId) {
        this.id = keyId;
    }

    @Override
    public KeyId getIdentifier() {
        return id;
    }

    @Override
    public DescriptorStability descriptorStability() {
        return DescriptorStability.STABLE;
    }

    protected final long partialDescriptorVersion(PartialRenderSetting partialRenderSetting) {
        return java.util.Objects.hash(
                descriptorStability(),
                partialRenderSetting != null ? partialRenderSetting : PartialRenderSetting.EMPTY);
    }

    protected final CompiledRenderSetting compilePartialDescriptor(
            RenderParameter renderParameter,
            PartialRenderSetting partialRenderSetting) {
        return RenderSettingCompiler.compile(RenderSetting.fromPartial(
                renderParameter,
                partialRenderSetting != null ? partialRenderSetting : PartialRenderSetting.EMPTY));
    }
}

