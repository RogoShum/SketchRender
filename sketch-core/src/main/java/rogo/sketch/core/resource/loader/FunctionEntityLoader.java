package rogo.sketch.core.resource.loader;

import com.google.gson.*;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.graphics.ecs.FunctionCommands;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.instance.function.FunctionCommandCodecRegistry;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.flow.ecs.GraphicsContainerHints;
import rogo.sketch.core.pipeline.parmeter.FunctionParameter;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

public class FunctionEntityLoader implements ResourceLoader<GraphicsEntityBlueprint> {
    private final FunctionCommandCodecRegistry codecRegistry;

    public FunctionEntityLoader() {
        this(FunctionCommandCodecRegistry.standard());
    }

    public FunctionEntityLoader(FunctionCommandCodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
    }

    @Override
    public KeyId getResourceType() {
        return ResourceTypes.FUNCTION;
    }

    @Override
    public GraphicsEntityBlueprint load(ResourceLoadContext context) {
        try {
            KeyId keyId = context.getResourceId();
            JsonObject json = context.getJson();
            if (json == null)
                return null;

            String stageId = json.has("stage") ? json.get("stage").getAsString() : null;
            if (stageId == null) {
                SketchDiagnostics.get().warn("function-loader", "FunctionGraphics " + keyId + " missing 'stage' reference");
                return null;
            }

            FunctionCommands.Command[] graphicsCommands;
            List<FunctionCommands.Command> commandList = new ArrayList<>();

            if (json.has("command") && json.get("command").isJsonArray()) {
                JsonArray commands = json.getAsJsonArray("command");
                for (JsonElement cmdElem : commands) {
                    if (!cmdElem.isJsonObject())
                        continue;
                    JsonObject cmdJson = cmdElem.getAsJsonObject();
                    String operate = cmdJson.has("operate") ? cmdJson.get("operate").getAsString() : "";
                    FunctionCommands.Command command = codecRegistry.decode(operate, cmdJson);
                    if (command != null) {
                        commandList.add(command);
                    }
                }
            }

            graphicsCommands = commandList.toArray(new FunctionCommands.Command[0]);
            int priority = json.has("priority") ? json.get("priority").getAsInt() : 100;
            return GraphicsEntityBlueprint.builder()
                    .put(GraphicsBuiltinComponents.IDENTITY, new GraphicsBuiltinComponents.IdentityComponent(keyId))
                    .put(GraphicsBuiltinComponents.LIFECYCLE, new GraphicsBuiltinComponents.LifecycleComponent(true, false))
                    .put(GraphicsBuiltinComponents.RESOURCE_ORIGIN, new GraphicsBuiltinComponents.ResourceOriginComponent(ResourceTypes.FUNCTION))
                    .put(GraphicsBuiltinComponents.STAGE_BINDING, new GraphicsBuiltinComponents.StageBindingComponent(
                            KeyId.of(stageId),
                            PipelineType.FUNCTION,
                            FunctionParameter.FUNCTION_PARAMETER))
                    .put(GraphicsBuiltinComponents.CONTAINER_HINT, new GraphicsBuiltinComponents.ContainerHintComponent(
                            GraphicsContainerHints.PRIORITY,
                            Integer.valueOf(priority),
                            0L,
                            0))
                    .put(GraphicsBuiltinComponents.SUBMISSION_CAPABILITY, new GraphicsBuiltinComponents.SubmissionCapabilityComponent(
                            SubmissionCapability.DIRECT_BATCHABLE))
                    .put(GraphicsBuiltinComponents.FUNCTION_INVOKE, new GraphicsBuiltinComponents.FunctionInvokeComponent(
                            null,
                            graphicsCommands,
                            priority))
                    .build();
        } catch (Exception e) {
            SketchDiagnostics.get().error("function-loader", "Failed to load FunctionGraphics", e);
            return null;
        }
    }
}

