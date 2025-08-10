package rogo.sketch.render;

import org.lwjgl.opengl.GL11;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.vertex.VertexResource;
import rogo.sketch.render.vertex.VertexResourceProvider;
import rogo.sketch.render.vertex.VertexResourceType;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.util.Identifier;

import java.util.*;

public class GraphicsPassGroup<C extends RenderContext> {
    private final Identifier stageIdentifier;
    private final Map<RenderSetting, GraphicsPass<C>> groups = new LinkedHashMap<>();
    private final Map<RenderParameter, VertexResource> sharedResources = new LinkedHashMap<>();
    private final Queue<VertexResource> activatedSharedResources = new LinkedList<>();
    private final List<VertexResourceProvider> instanceProviders = new ArrayList<>();

    public GraphicsPassGroup(Identifier stageIdentifier) {
        this.stageIdentifier = stageIdentifier;
    }

    public void addGraphInstance(GraphicsInstance<C> instance, RenderSetting setting) {
        GraphicsPass<C> group = groups.computeIfAbsent(setting, s -> new GraphicsPass<>());
        group.addGraphInstance(instance);
        
        // Track instance resource providers separately
        if (instance.isVertexResourceProvider()) {
            VertexResourceProvider provider = instance.asVertexResourceProvider();
            if (!instanceProviders.contains(provider)) {
                instanceProviders.add(provider);
            }
        }
    }

    public void tick() {

    }

    public void render(RenderStateManager manager, C context) {
        context.preStage(stageIdentifier);
        
        // First, render shared/batched resources
        renderSharedResources(manager, context);
        
        // Then, render instance-owned resources
        renderInstanceResources(manager, context);
        
        context.postStage(stageIdentifier);
    }
    
    private void renderSharedResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> group = entry.getValue();
            
            // Only process shared resources
            if (setting.renderParameter().resourceType() != VertexResourceType.SHARED_DYNAMIC) {
                continue;
            }

            manager.accept(setting, context);
            
            // Create shared VertexResource from RenderParameter
            VertexResource resource = sharedResources.computeIfAbsent(setting.renderParameter(), (parameter) -> {
                return parameter.createVertexResource();
            });
            
            context.shaderProvider().getUniformHookGroup().updateUniforms(context);

            VertexFiller filler = resource.beginFill();
            
            // Configure filler based on RenderParameter
            if (setting.renderParameter().enableIndexBuffer()) {
                filler.enableIndexBuffer();
            }
            if (setting.renderParameter().enableSorting()) {
                filler.enableSorting();
            }
            
            // Only fill from instances that use shared resources
            if (group.fillVertexForShared(filler)) {
                activatedSharedResources.add(resource);
                resource.endFill();
                
                // Render the shared resource
                VertexRenderer.render(resource);
            }
        }
        
        activatedSharedResources.clear();
    }
    
    private void renderInstanceResources(RenderStateManager manager, C context) {
        // Group instance providers by their render settings
        Map<RenderSetting, List<VertexResourceProvider>> providersBySettings = new HashMap<>();
        
        for (VertexResourceProvider provider : instanceProviders) {
            // Find the render setting for this provider
            RenderSetting providerSetting = findRenderSettingForProvider(provider);
            if (providerSetting != null) {
                providersBySettings.computeIfAbsent(providerSetting, k -> new ArrayList<>()).add(provider);
            }
        }
        
        // Render each group of instance providers
        for (Map.Entry<RenderSetting, List<VertexResourceProvider>> entry : providersBySettings.entrySet()) {
            RenderSetting setting = entry.getKey();
            List<VertexResourceProvider> providers = entry.getValue();
            
            manager.accept(setting, context);
            context.shaderProvider().getUniformHookGroup().updateUniforms(context);
            
            for (VertexResourceProvider provider : providers) {
                VertexResource resource = provider.getOrCreateVertexResource();
                
                if (resource != null && provider.needsVertexUpdate()) {
                    // Update vertex data if needed
                    VertexFiller filler = resource.beginFill();
                    
                    // Configure filler based on provider's resource type
                    if (setting.renderParameter().enableIndexBuffer()) {
                        filler.enableIndexBuffer();
                    }
                    if (setting.renderParameter().enableSorting()) {
                        filler.enableSorting();
                    }
                    
                    provider.fillVertexData(filler);
                    resource.endFill();
                }
                
                // Render the instance resource
                if (resource != null) {
                    provider.customRender();
                }
            }
        }
    }
    
    private RenderSetting findRenderSettingForProvider(VertexResourceProvider provider) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            GraphicsPass<C> pass = entry.getValue();
            if (pass.containsProvider(provider)) {
                return entry.getKey();
            }
        }
        return null;
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