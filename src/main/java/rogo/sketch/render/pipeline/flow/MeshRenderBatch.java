package rogo.sketch.render.pipeline.flow;

import rogo.sketch.api.model.BakedTypeMesh;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.pipeline.information.RasterizationInstanceInfo;

import javax.annotation.Nonnull;
import java.util.List;

public class MeshRenderBatch extends RenderBatch<RasterizationInstanceInfo> {
    private final BakedTypeMesh mesh;

    public MeshRenderBatch(RenderSetting renderSetting, @Nonnull BakedTypeMesh mesh, List<RasterizationInstanceInfo> instances) {
        super(renderSetting, instances);
        this.mesh = mesh;
    }

    public BakedTypeMesh mesh() {
        return mesh;
    }
}