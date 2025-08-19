package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.api.IndependentVertexProvider;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.SharedVertexProvider;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.render.vertex.VertexResource;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.render.vertex.VertexResourcePair;

import java.util.List;

public class RenderHelper {
    private final GraphicsPipeline<?> pipeline;

    public RenderHelper(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }

    public void renderInstanceImmediately(GraphicsInstance instance, RenderSetting setting) {
        RenderContext context = pipeline.currentContext();
        pipeline.renderStateManager().accept(setting, context);
        ShaderProvider shader = context.shaderProvider();
        shader.getUniformHookGroup().updateUniforms(context);

        if (instance.shouldTick()) {
            instance.tick(context);
        }

        if (instance instanceof SharedVertexProvider sharedInstance) {
            VertexResource resource = VertexResourceManager.getInstance().getOrCreateVertexResource(setting);
            VertexFiller filler = resource.beginFill();

            if (setting.renderParameter().enableIndexBuffer()) {
                filler.enableIndexBuffer();
            }
            if (setting.renderParameter().enableSorting()) {
                filler.enableSorting();
            }

            sharedInstance.fillVertexData(filler);
            resource.endFill();
            VertexRenderer.render(resource);
            instance.afterDraw(context);
        } else if (instance instanceof IndependentVertexProvider independentInstance) {
            independentInstance.fillVertexData();
            List<VertexResourcePair> pairs = independentInstance.getVertexResources();
            for (VertexResourcePair pair : pairs) {
                pair.drawCommand().execute(pair.resource());
            }

            instance.afterDraw(context);
        } else {
            instance.afterDraw(context);
        }
    }

    public void addGraphicsInstance(GraphicsInstance instance, RenderSetting setting) {
        pipeline.addGraphInstance(instance.getIdentifier(), instance, setting);
    }

    public GraphicsPipeline<?> pipeline() {
        return pipeline;
    }
}