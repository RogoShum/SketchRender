package rogo.sketch.core.pipeline.flow;

import org.jetbrains.annotations.NotNull;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.information.RasterizationInstanceInfo;

import java.util.List;

public class MeshRenderBatch extends RenderBatch<RasterizationInstanceInfo> {
    private final BakedTypeMesh mesh;

    public MeshRenderBatch(RenderSetting renderSetting, @NotNull BakedTypeMesh mesh, List<RasterizationInstanceInfo> instances) {
        super(renderSetting, instances);
        this.mesh = mesh;
    }

    public BakedTypeMesh mesh() {
        return mesh;
    }
}