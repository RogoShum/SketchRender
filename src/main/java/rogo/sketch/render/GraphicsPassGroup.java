package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.vertex.DrawMode;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.render.vertex.VertexResource;
import rogo.sketch.render.vertex.VertexResourcePair;
import rogo.sketch.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphicsPassGroup<C extends RenderContext> {
    private final Identifier stageIdentifier;
    private final Map<RenderSetting, GraphicsPass<C>> groups = new LinkedHashMap<>();
    private final Map<RenderParameter, VertexResource> sharedResources = new LinkedHashMap<>();

    public GraphicsPassGroup(Identifier stageIdentifier) {
        this.stageIdentifier = stageIdentifier;
    }

    public void addGraphInstance(GraphicsInstance instance, RenderSetting setting) {
        GraphicsPass<C> group = groups.computeIfAbsent(setting, s -> new GraphicsPass<>());
        group.addGraphInstance(instance);
    }

    public void tick(C context) {
        groups.values().forEach((group) -> {
            group.tick(context);
        });
    }

    public void render(RenderStateManager manager, C context) {
        context.preStage(stageIdentifier);

        renderSharedResources(manager, context);
        renderInstanceResources(manager, context);
        renderCustomResources(manager, context);

        context.postStage(stageIdentifier);
    }

    private void renderSharedResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> pass = entry.getValue();

            if (setting.renderParameter() == RenderParameter.EMPTY || !pass.containsShared()) {
                continue;
            }

            manager.accept(setting, context);

            VertexResource resource = sharedResources.computeIfAbsent(setting.renderParameter(), (parameter) -> {
                return new VertexResource(parameter.dataFormat(), null, DrawMode.NORMAL, parameter.primitiveType(), parameter.usage());
            });

            context.shaderProvider().getUniformHookGroup().updateUniforms(context);

            VertexFiller filler = resource.beginFill();

            if (setting.renderParameter().enableIndexBuffer()) {
                filler.enableIndexBuffer();
            }
            if (setting.renderParameter().enableSorting()) {
                filler.enableSorting();
            }

            if (pass.fillSharedVertex(filler)) {
                resource.endFill();
                VertexRenderer.render(resource);
                pass.endDraw();
            }
        }
    }

    private void renderInstanceResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> pass = entry.getValue();

            if (setting.renderParameter() == RenderParameter.EMPTY || !pass.containsIndependent()) {
                continue;
            }

            manager.accept(setting, context);

            context.shaderProvider().getUniformHookGroup().updateUniforms(context);
            List<VertexResourcePair> pairs = pass.fillIndependentVertex();

            for (VertexResourcePair pair : pairs) {
                pair.drawCommand().execute(pair.resource());
            }
        }
    }

    private void renderCustomResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> pass = entry.getValue();

            if (setting.renderParameter() == RenderParameter.EMPTY || !pass.containsCustom()) {
                continue;
            }

            if (setting.shouldSwitchRenderState()) {
                manager.accept(setting, context);
                context.shaderProvider().getUniformHookGroup().updateUniforms(context);
            }

            pass.endCustom();
        }
    }

    public GraphicsPass<C> getPass(RenderSetting setting) {
        return groups.get(setting);
    }

    public Collection<GraphicsPass<C>> getPasses() {
        return groups.values();
    }

    public void clear() {
        groups.clear();
    }
}