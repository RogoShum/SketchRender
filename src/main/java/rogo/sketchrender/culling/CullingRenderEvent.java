package rogo.sketchrender.culling;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;
import org.joml.Vector4f;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.CullingShader;
import rogo.sketchrender.compat.sodium.MeshUniform;
import rogo.sketchrender.event.ProgramEvent;
import rogo.sketchrender.mixin.AccessorFrustum;
import rogo.sketchrender.shader.ShaderManager;
import rogo.sketchrender.shader.uniform.UnsafeUniformMap;

import java.util.ArrayList;
import java.util.List;

import static rogo.sketchrender.gui.ConfigScreen.u;
import static rogo.sketchrender.gui.ConfigScreen.v;

public class CullingRenderEvent {
    float partialTick;
    int textWidth;

    protected static void updateEntityCullingMap() {
        if (!CullingStateManager.anyCulling() || CullingStateManager.checkCulling)
            return;

        if (SketchRender.CULL_TEST_TARGET.width != Minecraft.getInstance().getWindow().getWidth() || SketchRender.CULL_TEST_TARGET.height != Minecraft.getInstance().getWindow().getHeight()) {
            SketchRender.CULL_TEST_TARGET.resize(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight(), Minecraft.ON_OSX);
        }

        if (CullingStateManager.DEBUG > 0) {
            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tessellator.getBuilder();
            CullingStateManager.useShader(ShaderManager.CULL_TEST_SHADER);
            SketchRender.CULL_TEST_TARGET.clear(Minecraft.ON_OSX);
            SketchRender.CULL_TEST_TARGET.bindWrite(false);
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            bufferbuilder.vertex(-1.0f, -1.0f, 0.0f).endVertex();
            bufferbuilder.vertex(1.0f, -1.0f, 0.0f).endVertex();
            bufferbuilder.vertex(1.0f, 1.0f, 0.0f).endVertex();
            bufferbuilder.vertex(-1.0f, 1.0f, 0.0f).endVertex();
            CullingStateManager.callDepthTexture();
            tessellator.end();
        }

        CullingStateManager.callDepthTexture();

        if (Config.doEntityCulling() && CullingStateManager.ENTITY_CULLING_MASK != null) {
            CullingStateManager.ENTITY_CULLING_MASK.updateEntityData();
            CullingStateManager.computeEntityCulling();
        }
        CullingStateManager.bindMainFrameTarget();
    }

    protected static void updateChunkCullingMap() {
        if (!CullingStateManager.anyCulling() || CullingStateManager.checkCulling)
            return;

        CullingStateManager.bindMainFrameTarget();
    }

    public void addString(List<String> list, String text) {
        list.add(text);
    }

    public void renderText(GuiGraphics guiGraphics, List<String> list, int width, int height) {
        textWidth = 0;
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < list.size(); ++i) {
            String text = list.get(i);
            textWidth = Math.max(font.width(text), textWidth);
            guiGraphics.drawString(font, text, (int) (width - (font.width(text) / 2f)), height + font.lineHeight * i, 16777215);
        }
    }

    public static void setUniform(ShaderInstance shader) {
        if (!(shader instanceof CullingShader)) {
            return;
        }
        CullingShader shaderInstance = (CullingShader) shader;
        if (shaderInstance.getCullingCameraPos() != null) {
            Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            float[] array = new float[]{(float) pos.x, (float) pos.y, (float) pos.z};
            shaderInstance.getCullingCameraPos().set(array);
        }
        if (shaderInstance.getCullingCameraDir() != null) {
            Vector3f pos = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
            float[] array = new float[]{pos.x, pos.y, pos.z};
            shaderInstance.getCullingCameraDir().set(array);
        }
        if (shaderInstance.getBoxScale() != null) {
            shaderInstance.getBoxScale().set(4.0f);
        }
        if (shaderInstance.getTestPos() != null) {
            float[] array = new float[]{SketchRender.testPos.getX(), SketchRender.testPos.getY(), SketchRender.testPos.getZ()};
            shaderInstance.getTestPos().set(array);
        }
        if (shaderInstance.getFrustumPos() != null && CullingStateManager.FRUSTUM != null) {
            Vec3 pos = new Vec3(
                    ((AccessorFrustum) CullingStateManager.FRUSTUM).camX(),
                    ((AccessorFrustum) CullingStateManager.FRUSTUM).camY(),
                    ((AccessorFrustum) CullingStateManager.FRUSTUM).camZ());

            float[] array = new float[]{(float) pos.x, (float) pos.y, (float) pos.z};
            shaderInstance.getFrustumPos().set(array);
        }
        if (shaderInstance.getCullingViewMat() != null) {
            shaderInstance.getCullingViewMat().set(CullingStateManager.VIEW_MATRIX);
        }
        if (shaderInstance.getCullingProjMat() != null) {
            shaderInstance.getCullingProjMat().set(CullingStateManager.PROJECTION_MATRIX);
        }
        if (shaderInstance.getCullingFrustum() != null) {
            Vector4f[] frustumData = SketchRender.getFrustumPlanes(((AccessorFrustum) CullingStateManager.FRUSTUM).frustumIntersection());
            List<Float> data = new ArrayList<>();
            for (Vector4f frustumDatum : frustumData) {
                data.add(frustumDatum.x());
                data.add(frustumDatum.y());
                data.add(frustumDatum.z());
                data.add(frustumDatum.w());
            }
            float[] array = new float[data.size()];
            for (int i = 0; i < data.size(); i++) {
                array[i] = data.get(i);
            }
            shaderInstance.getCullingFrustum().set(array);
        }
        if (shaderInstance.getRenderDistance() != null) {
            float distance = Minecraft.getInstance().options.getEffectiveRenderDistance();
            shaderInstance.getRenderDistance().set(distance);
        }

        if (shaderInstance.getDepthSize() != null) {
            float[] array = new float[CullingStateManager.DEPTH_SIZE * 2];
            for (int i = 0; i < CullingStateManager.DEPTH_SIZE; ++i) {
                int arrayIdx = i * 2;
                array[arrayIdx] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[i].width;
                array[arrayIdx + 1] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[i].height;
            }
            shaderInstance.getDepthSize().set(array);
        }

        if (shaderInstance.getLevelHeightOffset() != null) {
            shaderInstance.getLevelHeightOffset().set(CullingStateManager.LEVEL_SECTION_RANGE);
        }

        if (shaderInstance.getLevelMinSection() != null && Minecraft.getInstance().level != null) {
            int min = Minecraft.getInstance().level.getMinSection();
            shaderInstance.getLevelMinSection().set(min);
        }
    }

    public static final ResourceLocation culling_terrain = new ResourceLocation(SketchRender.MOD_ID, "terrain");
    public static final ResourceLocation collect_chunk = new ResourceLocation(SketchRender.MOD_ID, "collect_chunk");
    public static final ResourceLocation copy_depth = new ResourceLocation(SketchRender.MOD_ID, "copy_depth");
    public static final ResourceLocation culling_chunk = new ResourceLocation(SketchRender.MOD_ID, "culling_chunk");

    @SubscribeEvent
    public void onBind(ProgramEvent.Init event) {
        GlStateManager._glUseProgram(event.getProgramId());
        event.getExtraUniform().getUniforms().tryInsertUniform("sketch_render_distance", () -> {
            event.getExtraUniform().getUniforms().createUniforms(culling_terrain
                    , new String[]{
                            "sketch_culling_texture",
                            "sketch_level_min_pos",
                            "sketch_level_pos_range",
                            "sketch_level_section_range",
                            "sketch_render_distance",
                            "sketch_space_partition_size",
                            "sketch_culling_size",
                            "sketch_camera_offset",
                            "sketch_check_culling",
                            "sketch_camera_pos",
                            "sketch_entity_count"
                    });
        });

        event.getExtraUniform().getUniforms().tryInsertUniform("sketch_cull_facing", () -> {
            event.getExtraUniform().getUniforms().createUniforms(collect_chunk
                    , new String[]{
                            "sketch_cull_facing",
                            "sketch_translucent_sort"
                    });
        });

        event.getExtraUniform().getUniforms().tryInsertUniform("sketch_screen_size", () -> {
            event.getExtraUniform().getUniforms().createUniforms(copy_depth
                    , new String[]{
                            "sketch_depth_size",
                            "sketch_sampler_texture_0",
                            "sketch_sampler_texture_1",
                            "sketch_sampler_texture_2",
                            "sketch_sampler_texture_3",
                            "sketch_sampler_texture_4",
                            "sketch_sampler_texture_5",
                            "sketch_screen_size",
                            "sketch_render_distance",
                            "sketch_depth_level"
                    });
        });

        event.getExtraUniform().getUniforms().tryInsertUniform("sketch_culling_view_mat", () -> {
            event.getExtraUniform().getUniforms().createUniforms(culling_chunk
                    , new String[]{
                            "sketch_culling_view_mat",
                            "sketch_culling_proj_mat",
                            "sketch_culling_camera_pos",
                            "sketch_culling_camera_dir",
                            "sketch_frustum_pos",
                            "sketch_culling_frustum",
                            "sketch_depth_size",

                            "Sampler0",
                            "Sampler1",
                            "Sampler2",
                            "Sampler3",
                            "Sampler4",
                            "Sampler5"
                    });
        });
        GlStateManager._glUseProgram(0);
    }

    @SubscribeEvent
    public void onBind(ProgramEvent.Bind event) {
        UnsafeUniformMap uniformMap = event.getExtraUniform().getUniforms();
        if (uniformMap.containsOperate(collect_chunk)) {
            boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;
            boolean useTranslucentFaceSorting = SodiumClientMod.options().performance.useTranslucentFaceSorting;

            uniformMap.setUniform("sketch_cull_facing", useBlockFaceCulling ? 1 : 0);
            uniformMap.setUniform("sketch_translucent_sort", useTranslucentFaceSorting ? 1 : 0);
        }

        if (uniformMap.containsOperate(culling_terrain)) {
            if (CullingStateManager.ENTITY_CULLING_MASK != null) {
                uniformMap.setUniform("sketch_entity_count", CullingStateManager.ENTITY_CULLING_MASK.getEntityTable().size());
            }

            if (!Config.getCullChunk() || CullingStateManager.SHADER_LOADER.renderingShaderPass()) {
                uniformMap.setUniform("sketch_culling_terrain", 0);
            } else {
                uniformMap.setUniform("sketch_culling_terrain", 1);
            }

            uniformMap.setUniform("sketch_check_culling", CullingStateManager.checkCulling ? 1 : 0);
            uniformMap.setUniform("sketch_level_min_pos", CullingStateManager.LEVEL_MIN_POS);
            uniformMap.setUniform("sketch_level_pos_range", CullingStateManager.LEVEL_POS_RANGE);
            uniformMap.setUniform("sketch_level_section_range", CullingStateManager.LEVEL_SECTION_RANGE);
            Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            BlockPos cameraPos = new BlockPos((int) pos.x >> 4, CullingStateManager.LEVEL_MIN_POS >> 4, (int) pos.z >> 4);
            uniformMap.setUniform("sketch_camera_offset", cameraPos);
            cameraPos = new BlockPos((int) pos.x, (int) pos.y, (int) pos.z);
            uniformMap.setUniform("sketch_camera_pos", cameraPos);
            uniformMap.setUniform("sketch_render_distance", MeshUniform.getRenderDistance());
            uniformMap.setUniform("sketch_space_partition_size", MeshUniform.getSpacePartitionSize());
        }

        if (uniformMap.containsOperate(culling_chunk)) {
            uniformMap.setUniform("sketch_culling_view_mat", CullingStateManager.VIEW_MATRIX);
            uniformMap.setUniform("sketch_culling_proj_mat", CullingStateManager.PROJECTION_MATRIX);
            Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vector3f dir = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
            uniformMap.setUniform("sketch_culling_camera_pos", pos);
            uniformMap.setUniform("sketch_culling_camera_dir", dir);
            pos = new Vec3(
                    ((AccessorFrustum) CullingStateManager.FRUSTUM).camX(),
                    ((AccessorFrustum) CullingStateManager.FRUSTUM).camY(),
                    ((AccessorFrustum) CullingStateManager.FRUSTUM).camZ());

            uniformMap.setUniform("sketch_frustum_pos", pos);
            Vector4f[] frustumData = SketchRender.getFrustumPlanes(((AccessorFrustum) CullingStateManager.FRUSTUM).frustumIntersection());
            List<Float> data = new ArrayList<>();
            for (Vector4f frustumDatum : frustumData) {
                data.add(frustumDatum.x());
                data.add(frustumDatum.y());
                data.add(frustumDatum.z());
                data.add(frustumDatum.w());
            }
            float[] array = new float[data.size()];
            for (int i = 0; i < data.size(); i++) {
                array[i] = data.get(i);
            }
            uniformMap.setUniform("sketch_culling_frustum", array);

            array = new float[CullingStateManager.DEPTH_SIZE * 2];
            for (int i = 0; i < CullingStateManager.DEPTH_SIZE; ++i) {
                int arrayIdx = i * 2;
                array[arrayIdx] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[i].width;
                array[arrayIdx + 1] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[i].height;
            }
            uniformMap.setUniform("sketch_depth_size", array);

            uniformMap.setUniform("Sampler0", 0);
            uniformMap.setUniform("Sampler1", 1);
            uniformMap.setUniform("Sampler2", 2);
            uniformMap.setUniform("Sampler3", 3);
            uniformMap.setUniform("Sampler4", 4);
            uniformMap.setUniform("Sampler5", 5);
        }
    }

    @SubscribeEvent
    public void onLevelStage(RenderLevelStageEvent event) {
        SketchRender.COMMAND_TIMER.end("after_sky");
        SketchRender.COMMAND_TIMER.end("after_entities");
        SketchRender.COMMAND_TIMER.end("after_block_entities");

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            SketchRender.COMMAND_TIMER.start("after_sky");
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            SketchRender.COMMAND_TIMER.start("after_entities");
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            SketchRender.COMMAND_TIMER.start("after_block_entities");
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {

        }
    }

    @SubscribeEvent
    public void onOverlayRender(RenderGuiOverlayEvent event) {
        if (Minecraft.getInstance().player == null) {
            return;
        }

        if (event.getOverlay().id().equals(VanillaGuiOverlay.HELMET.id()) && partialTick != event.getPartialTick()) {
            partialTick = event.getPartialTick();
        } else {
            return;
        }

        if (CullingStateManager.DEBUG > 0 && event.getOverlay().id().equals(VanillaGuiOverlay.HELMET.id())) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            Minecraft minecraft = Minecraft.getInstance();
            int width = minecraft.getWindow().getGuiScaledWidth() / 2;
            int height = 20;
            int widthScale = Math.max(80, textWidth / 2 + 20);

            List<String> monitorTexts = new ArrayList<>();

            if (CullingStateManager.fps == 0) {
                CullingStateManager.fps++;
            }

            if (CullingStateManager.cullingInitCount == 0) {
                CullingStateManager.cullingInitCount++;
            }

            int index = Minecraft.getInstance().fpsString.indexOf("fps");
            if (index != -1) {
                String extractedString = Minecraft.getInstance().fpsString.substring(0, index + 3);
                String fps = "FPS: " + extractedString;
                addString(monitorTexts, fps);
            }

            String cull = Component.translatable(SketchRender.MOD_ID + ".cull_entity").getString() + ": "
                    + (Config.getCullEntity() ? Component.translatable(SketchRender.MOD_ID + ".enable").getString() : Component.translatable(SketchRender.MOD_ID + ".disable").getString());
            addString(monitorTexts, cull);

            String cull_block_entity = Component.translatable(SketchRender.MOD_ID + ".cull_block_entity").getString() + ": "
                    + (Config.getCullBlockEntity() ? Component.translatable(SketchRender.MOD_ID + ".enable").getString() : Component.translatable(SketchRender.MOD_ID + ".disable").getString());
            addString(monitorTexts, cull_block_entity);

            String cull_chunk = Component.translatable(SketchRender.MOD_ID + ".cull_chunk").getString() + ": "
                    + (Config.getCullChunk() ? Component.translatable(SketchRender.MOD_ID + ".enable").getString() : Component.translatable(SketchRender.MOD_ID + ".disable").getString());
            addString(monitorTexts, cull_chunk);

            if (CullingStateManager.DEBUG > 1) {
                if (Config.doEntityCulling()) {
                    String blockCullingTime = Component.translatable(SketchRender.MOD_ID + ".block_culling_time").getString() + ": " + (CullingStateManager.blockCullingTime / 1000 / CullingStateManager.fps) + " μs";
                    addString(monitorTexts, blockCullingTime);

                    String blockCulling = Component.translatable(SketchRender.MOD_ID + ".block_culling").getString() + ": " + CullingStateManager.blockCulling + " / " + CullingStateManager.blockCount;
                    addString(monitorTexts, blockCulling);

                    String entityCullingTime = Component.translatable(SketchRender.MOD_ID + ".entity_culling_time").getString() + ": " + (CullingStateManager.entityCullingTime / 1000 / CullingStateManager.fps) + " μs";
                    addString(monitorTexts, entityCullingTime);

                    String entityCulling = Component.translatable(SketchRender.MOD_ID + ".entity_culling").getString() + ": " + CullingStateManager.entityCulling + " / " + CullingStateManager.entityCount;
                    addString(monitorTexts, entityCulling);

                    String initTime = Component.translatable(SketchRender.MOD_ID + ".entity_culling_init").getString() + ": " + (CullingStateManager.entityCullingInitTime / CullingStateManager.cullingInitCount / CullingStateManager.fps) + " ns";
                    addString(monitorTexts, initTime);
                }

                if (Config.getCullChunk()) {
                    String chunkCullingCount = Component.translatable(SketchRender.MOD_ID + ".chunk_update_count").getString() + ": " + MeshUniform.lastQueueUpdateCount;
                    addString(monitorTexts, chunkCullingCount);
                }
            }

            int heightOffset = minecraft.font.lineHeight * monitorTexts.size();
            int top = height;
            int bottom = height + heightOffset;
            int left = width + widthScale;
            int right = width - widthScale;

            float bgColor = 1.0f;
            float bgAlpha = 0.3f;
            BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            bufferbuilder.vertex(right - 1, bottom + 1, 0.0D)
                    .color(bgColor, bgColor, bgColor, bgAlpha)
                    .uv(u(right - 1), v(bottom + 1)).endVertex();
            bufferbuilder.vertex(left + 1, bottom + 1, 0.0D)
                    .color(bgColor, bgColor, bgColor, bgAlpha)
                    .uv(u(left + 1), v(bottom + 1)).endVertex();
            bufferbuilder.vertex(left + 1, top - 1, 0.0D)
                    .color(bgColor, bgColor, bgColor, bgAlpha)
                    .uv(u(left + 1), v(top - 1)).endVertex();
            bufferbuilder.vertex(right - 1, top - 1, 0.0D)
                    .color(bgColor, bgColor, bgColor, bgAlpha)
                    .uv(u(right - 1), v(top - 1)).endVertex();
            RenderSystem.setShaderTexture(0, Minecraft.getInstance().getMainRenderTarget().getColorTextureId());
            CullingStateManager.useShader(ShaderManager.REMOVE_COLOR_SHADER);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.1f);
            RenderSystem.disableBlend();
            RenderSystem.getModelViewStack().pushPose();
            RenderSystem.getModelViewStack().translate(0, 0, -1);
            RenderSystem.applyModelViewMatrix();
            BufferUploader.drawWithShader(bufferbuilder.end());
            RenderSystem.getModelViewStack().popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            bufferbuilder.vertex(right, bottom, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            bufferbuilder.vertex(left, bottom, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            bufferbuilder.vertex(left, top, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            bufferbuilder.vertex(right, top, 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(bufferbuilder.end());
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            renderText(guiGraphics, monitorTexts, width, top);

            Tesselator tessellator = Tesselator.getInstance();


            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);

            if (true) {
                height = (int) (minecraft.getWindow().getGuiScaledHeight() * 0.25f);
                width = (int) (minecraft.getWindow().getGuiScaledWidth() * 0.25f);
                bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(minecraft.getWindow().getGuiScaledWidth() - width, height * 2, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex((double) minecraft.getWindow().getGuiScaledWidth(), height * 2, 0.0D).uv(1, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex((double) minecraft.getWindow().getGuiScaledWidth(), height, 0.0D).uv(1, 1).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex(minecraft.getWindow().getGuiScaledWidth() - width, height, 0.0D).uv(0.0F, 1).color(255, 255, 255, 255).endVertex();
                RenderSystem.setShaderTexture(0, SketchRender.CULL_TEST_TARGET.getColorTextureId());
                tessellator.end();
            }

            if (!CullingStateManager.checkTexture)
                return;

            float screenScale = 1.0f;
            double windowScale = 0.4;
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();
            for (int i = 0; i < CullingStateManager.DEPTH_BUFFER_TARGET.length; ++i) {
                int scaledHeight = (int) (minecraft.getWindow().getGuiScaledHeight() * windowScale * screenScale);
                int scaledWidth = (int) (minecraft.getWindow().getGuiScaledWidth() * windowScale * screenScale);
                int offsetHeight = (int) ((1 - screenScale) * 2 * minecraft.getWindow().getGuiScaledHeight() * windowScale);
                bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(0.0D, minecraft.getWindow().getGuiScaledHeight() - offsetHeight, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex(scaledWidth, minecraft.getWindow().getGuiScaledHeight() - offsetHeight, 0.0D).uv(1, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex(scaledWidth, minecraft.getWindow().getGuiScaledHeight() - scaledHeight - offsetHeight, 0.0D).uv(1, 1).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex(0.0D, minecraft.getWindow().getGuiScaledHeight() - scaledHeight - offsetHeight, 0.0D).uv(0.0F, 1).color(255, 255, 255, 255).endVertex();
                RenderSystem.setShaderTexture(0, CullingStateManager.DEPTH_BUFFER_TARGET[i].getColorTextureId());
                tessellator.end();
                screenScale *= 0.5f;
            }

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }
}
