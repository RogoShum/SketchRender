package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RasterPipelineKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketKind;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Arrays;

public record StageExecutionPlan(
        KeyId stageId,
        PipelineExecutionSlice[] pipelineSlices,
        Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> packets,
        SnapshotScope stageSnapshotScope,
        StageResourceFootprint stageResourceFootprint,
        int planHash,
        boolean empty
) {
    public StageExecutionPlan {
        packets = packets != null ? normalizePackets(packets) : Map.of();
        pipelineSlices = pipelineSlices != null ? pipelineSlices.clone() : buildPipelineSlices(packets);
        stageSnapshotScope = stageSnapshotScope != null ? stageSnapshotScope : SnapshotScope.empty();
        stageResourceFootprint = stageResourceFootprint != null
                ? stageResourceFootprint
                : StageResourceFootprint.fromPackets(packets, stageSnapshotScope);
        planHash = planHash != 0 ? planHash : derivePlanHash(stageId, pipelineSlices);
        empty = empty || packets.isEmpty();
    }

    public static StageExecutionPlan empty(KeyId stageId) {
        return new StageExecutionPlan(stageId, new PipelineExecutionSlice[0], Map.of(), SnapshotScope.empty(), StageResourceFootprint.empty(), 1, true);
    }

    public static StageExecutionPlan fromPackets(
            KeyId stageId,
            Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> packets) {
        SnapshotScope snapshotScope = deriveSnapshotScope(packets);
        return new StageExecutionPlan(
                stageId,
                null,
                packets,
                snapshotScope,
                StageResourceFootprint.fromPackets(packets, snapshotScope),
                0,
                packets == null || packets.isEmpty());
    }

    public boolean isEmpty() {
        return empty;
    }

    public PipelineExecutionSlice pipelineSlice(PipelineType pipelineType) {
        if (pipelineType == null || pipelineSlices.length == 0) {
            return null;
        }
        for (PipelineExecutionSlice pipelineSlice : pipelineSlices) {
            if (pipelineSlice != null && pipelineType.equals(pipelineSlice.pipelineType())) {
                return pipelineSlice;
            }
        }
        return null;
    }

    private static Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> normalizePackets(
            Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> packets) {
        Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> normalized = new LinkedHashMap<>();
        for (Map.Entry<PipelineType, Map<ExecutionKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
            if (pipelineEntry.getKey() == null || pipelineEntry.getValue() == null || pipelineEntry.getValue().isEmpty()) {
                continue;
            }
            Map<ExecutionKey, List<RenderPacket>> states = new LinkedHashMap<>();
            for (Map.Entry<ExecutionKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                if (stateEntry.getKey() == null || stateEntry.getValue() == null || stateEntry.getValue().isEmpty()) {
                    continue;
                }
                states.put(stateEntry.getKey(), List.copyOf(stateEntry.getValue()));
            }
            if (!states.isEmpty()) {
                normalized.put(pipelineEntry.getKey(), Collections.unmodifiableMap(states));
            }
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static PipelineExecutionSlice[] buildPipelineSlices(
            Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> packets) {
        if (packets == null || packets.isEmpty()) {
            return new PipelineExecutionSlice[0];
        }
        PipelineExecutionSlice[] slices = new PipelineExecutionSlice[packets.size()];
        int index = 0;
        for (Map.Entry<PipelineType, Map<ExecutionKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
            PacketGroup[] groups = new PacketGroup[pipelineEntry.getValue().size()];
            int groupIndex = 0;
            int pipelineHash = 1;
            for (Map.Entry<ExecutionKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                PacketGroup group = new PacketGroup(
                        stateEntry.getKey(),
                        stateEntry.getValue().toArray(new RenderPacket[0]));
                groups[groupIndex++] = group;
                pipelineHash = 31 * pipelineHash + group.groupHash();
            }
            slices[index++] = new PipelineExecutionSlice(
                    pipelineEntry.getKey(),
                    groups,
                    pipelineEntry.getValue(),
                    pipelineHash);
        }
        return slices;
    }

    private static int derivePlanHash(KeyId stageId, PipelineExecutionSlice[] pipelineSlices) {
        int hash = Objects.hashCode(stageId);
        for (PipelineExecutionSlice slice : pipelineSlices) {
            hash = 31 * hash + (slice != null ? slice.pipelineHash() : 0);
        }
        return hash;
    }

    private static SnapshotScope deriveSnapshotScope(
            Map<PipelineType, Map<ExecutionKey, List<RenderPacket>>> packets) {
        if (packets == null || packets.isEmpty()) {
            return SnapshotScope.empty();
        }

        SnapshotScope.Builder builder = SnapshotScope.builder();
        Map<ShaderBindingCacheKey, Map<KeyId, Map<KeyId, Integer>>> resolvedBindingsCache = new LinkedHashMap<>();
        Set<BindingPlanScopeCacheKey> appliedBindingScopes = new LinkedHashSet<>();
        boolean hasPackets = false;
        boolean touchesFramebuffer = false;
        boolean touchesVertexArrays = false;

        for (Map.Entry<PipelineType, Map<ExecutionKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
            PipelineType pipelineType = pipelineEntry.getKey();
            for (Map.Entry<ExecutionKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                ExecutionKey stateKey = stateEntry.getKey();
                if (stateKey != null) {
                    if (stateKey instanceof RasterPipelineKey rasterPipelineKey && rasterPipelineKey.renderState() != null) {
                        builder.addStatesFromRenderStatePatch(rasterPipelineKey.renderState());
                    }
                    if (stateKey.shaderId() != null) {
                        builder.addState(SnapshotScope.StateType.PROGRAM);
                    }
                    if (stateKey.bindingPlan() != null && !stateKey.bindingPlan().isEmpty()) {
                        addBindingPlanScope(builder, stateKey, stateKey.bindingPlan(), resolvedBindingsCache, appliedBindingScopes);
                    }
                }

                for (RenderPacket packet : stateEntry.getValue()) {
                    if (packet == null) {
                        continue;
                    }
                    hasPackets = true;
                    if (packet.bindingPlan() != null
                            && !packet.bindingPlan().isEmpty()
                            && !packet.bindingPlan().equals(stateKey != null ? stateKey.bindingPlan() : null)) {
                        addBindingPlanScope(builder, packet.stateKey(), packet.bindingPlan(), resolvedBindingsCache, appliedBindingScopes);
                    }
                    if (packet.packetKind() == RenderPacketKind.DRAW) {
                        touchesFramebuffer = true;
                        touchesVertexArrays = true;
                        continue;
                    }
                    if (packet.packetKind() == RenderPacketKind.CLEAR && packet instanceof rogo.sketch.core.packet.ClearPacket clearPacket) {
                        touchesFramebuffer = true;
                        if (clearPacket.colorMask() != null && clearPacket.colorMask().length >= 4) {
                            builder.addState(SnapshotScope.StateType.PIPELINE_RASTER);
                        }
                        if (clearPacket.clearDepth()) {
                            builder.addState(SnapshotScope.StateType.PIPELINE_RASTER);
                        }
                        continue;
                    }
                    if (packet.packetKind() == RenderPacketKind.DISPATCH || PipelineType.COMPUTE.equals(pipelineType)) {
                        builder.addState(SnapshotScope.StateType.PROGRAM);
                        continue;
                    }
                    if (packet.packetKind() == RenderPacketKind.GENERATE_MIPMAP) {
                        builder.addState(SnapshotScope.StateType.PROGRAM);
                    }
                }
            }
        }

        if (touchesFramebuffer || hasPackets) {
            builder.addState(SnapshotScope.StateType.FBO);
        }
        if (touchesVertexArrays) {
            builder.addState(SnapshotScope.StateType.VAO);
        }
        return builder.build();
    }

    private static void addBindingPlanScope(
            SnapshotScope.Builder builder,
            ExecutionKey stateKey,
            ResourceBindingPlan bindingPlan,
            Map<ShaderBindingCacheKey, Map<KeyId, Map<KeyId, Integer>>> resolvedBindingsCache,
            Set<BindingPlanScopeCacheKey> appliedBindingScopes) {
        builder.addState(SnapshotScope.StateType.PROGRAM);
        builder.addState(SnapshotScope.StateType.PASS_BINDINGS);
        if (bindingPlan == null || bindingPlan.isEmpty() || stateKey == null || stateKey.shaderId() == null) {
            return;
        }

        BindingPlanScopeCacheKey cacheKey = new BindingPlanScopeCacheKey(
                stateKey.shaderId(),
                stateKey.shaderVariantKey() != null ? stateKey.shaderVariantKey() : ShaderVariantKey.EMPTY,
                bindingPlan.layoutKey());
        if (appliedBindingScopes != null && !appliedBindingScopes.add(cacheKey)) {
            return;
        }

        Map<KeyId, Map<KeyId, Integer>> resolvedBindings = resolveShaderBindings(
                stateKey.shaderId(),
                stateKey.shaderVariantKey(),
                resolvedBindingsCache);
        if (resolvedBindings.isEmpty()) {
            return;
        }

        for (ResourceBindingPlan.BindingEntry entry : bindingPlan.entries()) {
            if (entry == null || entry.bindingName() == null || entry.resourceType() == null) {
                continue;
            }
            Integer bindingIndex = resolveBindingIndex(resolvedBindings, entry);
            if (bindingIndex == null || bindingIndex < 0) {
                continue;
            }
            KeyId normalizedType = ResourceTypes.normalize(entry.resourceType());
            if (ResourceTypes.TEXTURE.equals(normalizedType)) {
                builder.addTextureUnit(bindingIndex);
            } else if (ResourceTypes.IMAGE.equals(normalizedType)) {
                builder.addImageBinding(bindingIndex);
            } else if (ResourceTypes.STORAGE_BUFFER.equals(normalizedType)) {
                builder.addSSBOBinding(bindingIndex);
            } else if (ResourceTypes.UNIFORM_BUFFER.equals(normalizedType)) {
                builder.addUBOBinding(bindingIndex);
            }
        }
    }

    private static Map<KeyId, Map<KeyId, Integer>> resolveShaderBindings(
            KeyId shaderId,
            ShaderVariantKey shaderVariantKey,
            Map<ShaderBindingCacheKey, Map<KeyId, Map<KeyId, Integer>>> resolvedBindingsCache) {
        if (shaderId == null) {
            return Map.of();
        }
        ShaderBindingCacheKey cacheKey = new ShaderBindingCacheKey(
                shaderId,
                shaderVariantKey != null ? shaderVariantKey : ShaderVariantKey.EMPTY);
        if (resolvedBindingsCache != null) {
            Map<KeyId, Map<KeyId, Integer>> cached = resolvedBindingsCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        Object resource = GraphicsResourceManager.getInstance()
                .getResource(ResourceTypes.SHADER_TEMPLATE, shaderId);
        if (!(resource instanceof ShaderTemplate shaderTemplate)) {
            return Map.of();
        }
        try {
            Map<KeyId, Map<KeyId, Integer>> resolved = shaderTemplate.resolveResourceBindings(
                    shaderVariantKey != null ? shaderVariantKey : ShaderVariantKey.EMPTY);
            if (resolvedBindingsCache != null) {
                resolvedBindingsCache.put(cacheKey, resolved);
            }
            return resolved;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Integer resolveBindingIndex(
            Map<KeyId, Map<KeyId, Integer>> resolvedBindings,
            ResourceBindingPlan.BindingEntry entry) {
        for (KeyId searchType : ResourceTypes.getSearchOrder(entry.resourceType())) {
            Map<KeyId, Integer> typeBindings = resolvedBindings.get(ResourceTypes.normalize(searchType));
            if (typeBindings == null || typeBindings.isEmpty()) {
                continue;
            }
            Integer binding = typeBindings.get(entry.bindingName());
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private record BindingPlanScopeCacheKey(
            KeyId shaderId,
            ShaderVariantKey shaderVariantKey,
            KeyId resourceLayoutKey
    ) {
    }

    private record ShaderBindingCacheKey(
            KeyId shaderId,
            ShaderVariantKey shaderVariantKey
    ) {
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StageExecutionPlan other)) {
            return false;
        }
        if (planHash != other.planHash || empty != other.empty) {
            return false;
        }
        if (!Objects.equals(stageId, other.stageId)) {
            return false;
        }
        return Arrays.equals(pipelineSlices, other.pipelineSlices);
    }

    @Override
    public int hashCode() {
        return planHash;
    }
}

