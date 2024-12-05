package rogo.sketchrender.culling;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
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
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.CullingShader;
import rogo.sketchrender.event.ProgramEvent;
import rogo.sketchrender.mixin.AccessorFrustum;
import rogo.sketchrender.shader.ShaderManager;
import rogo.sketchrender.shader.uniform.UnsafeUniformMap;
import rogo.sketchrender.vertexbuffer.EntityCullingInstanceRenderer;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE18;
import static rogo.sketchrender.gui.ConfigScreen.u;
import static rogo.sketchrender.gui.ConfigScreen.v;

public class CullingRenderEvent {

    public static EntityCullingInstanceRenderer ENTITY_CULLING_INSTANCE_RENDERER;

    static {
        RenderSystem.recordRenderCall(() -> ENTITY_CULLING_INSTANCE_RENDERER = new EntityCullingInstanceRenderer());
    }

    float partialTick;
    int textWidth;

    protected static void updateEntityCullingMap() {
        if (!CullingStateManager.anyCulling() || CullingStateManager.checkCulling)
            return;

        CullingStateManager.callDepthTexture();
        if (Config.doEntityCulling() && CullingStateManager.ENTITY_CULLING_MAP != null && CullingStateManager.ENTITY_CULLING_MAP.needTransferData()) {
            CullingStateManager.ENTITY_CULLING_MAP_TARGET.clear(Minecraft.ON_OSX);
            CullingStateManager.ENTITY_CULLING_MAP_TARGET.bindWrite(false);
            CullingStateManager.ENTITY_CULLING_MAP.getEntityTable().addEntityAttribute(CullingRenderEvent.ENTITY_CULLING_INSTANCE_RENDERER::addInstanceAttrib);
            ENTITY_CULLING_INSTANCE_RENDERER.drawWithShader(ShaderManager.INSTANCED_ENTITY_CULLING_SHADER, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());
        }
        CullingStateManager.bindMainFrameTarget();
    }

    protected static void updateChunkCullingMap() {
        if (!CullingStateManager.anyCulling() || CullingStateManager.checkCulling)
            return;
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuilder();
        CullingStateManager.callDepthTexture();

        if (Config.getCullChunk()) {
            CullingStateManager.useShader(ShaderManager.CHUNK_CULLING_SHADER);
            CullingStateManager.CHUNK_CULLING_MAP_TARGET.clear(Minecraft.ON_OSX);
            CullingStateManager.CHUNK_CULLING_MAP_TARGET.bindWrite(false);
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            bufferbuilder.vertex(-1.0f, -1.0f, 0.0f).endVertex();
            bufferbuilder.vertex(1.0f, -1.0f, 0.0f).endVertex();
            bufferbuilder.vertex(1.0f, 1.0f, 0.0f).endVertex();
            bufferbuilder.vertex(-1.0f, 1.0f, 0.0f).endVertex();
            tessellator.end();
        }

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
            if (shader == ShaderManager.COPY_DEPTH_SHADER) {
                if (CullingStateManager.DEPTH_INDEX > 0)
                    distance = 2;
                else
                    distance = 0;
            }

            shaderInstance.getRenderDistance().set(distance);
        }
        if (shaderInstance.getDepthSize() != null) {
            float[] array = new float[CullingStateManager.DEPTH_SIZE * 2];
            if (shader == ShaderManager.COPY_DEPTH_SHADER) {
                array[0] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[CullingStateManager.DEPTH_INDEX].width;
                array[1] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[CullingStateManager.DEPTH_INDEX].height;
            } else {
                for (int i = 0; i < CullingStateManager.DEPTH_SIZE; ++i) {
                    int arrayIdx = i * 2;
                    array[arrayIdx] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[i].width;
                    array[arrayIdx + 1] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[i].height;
                }
            }
            shaderInstance.getDepthSize().set(array);
        }
        if (shader == ShaderManager.COPY_DEPTH_SHADER && CullingStateManager.DEPTH_INDEX > 0 && shader.SCREEN_SIZE != null) {
            shader.SCREEN_SIZE.set((float) CullingStateManager.DEPTH_BUFFER_TARGET[CullingStateManager.DEPTH_INDEX - 1].width, (float) CullingStateManager.DEPTH_BUFFER_TARGET[CullingStateManager.DEPTH_INDEX - 1].height);
        }
        if (shaderInstance.getCullingSize() != null) {
            shaderInstance.getCullingSize().set((float) CullingStateManager.CHUNK_CULLING_MAP_TARGET.width, (float) CullingStateManager.CHUNK_CULLING_MAP_TARGET.height);
        }
        if (shaderInstance.getEntityCullingSize() != null) {
            shaderInstance.getEntityCullingSize().set((float) CullingStateManager.ENTITY_CULLING_MAP_TARGET.width, (float) CullingStateManager.ENTITY_CULLING_MAP_TARGET.height);
        }
        if (shaderInstance.getLevelHeightOffset() != null) {
            shaderInstance.getLevelHeightOffset().set(CullingStateManager.LEVEL_SECTION_RANGE);
        }
        if (shaderInstance.getLevelMinSection() != null && Minecraft.getInstance().level != null) {
            int min = Minecraft.getInstance().level.getMinSection();
            shaderInstance.getLevelMinSection().set(min);
        }
    }

    @SubscribeEvent
    public void onBind(ProgramEvent.Init event) {
        GlStateManager._glUseProgram(event.getProgramId());
        event.getExtraUniform().getUniforms().createUniforms(new ResourceLocation(SketchRender.MOD_ID, "terrain")
                , new String[]{
                        "sketch_culling_terrain",
                        "sketch_culling_texture",
                        "sketch_level_min_pos",
                        "sketch_level_pos_range",
                        "sketch_level_section_range",
                        "sketch_render_distance",
                        "sketch_space_partition_size",
                        "sketch_culling_size",
                        "sketch_camera_pos",
                        "sketch_check_culling"
                });
        GlStateManager._glUseProgram(0);
    }

    @SubscribeEvent
    public void onBind(ProgramEvent.Bind event) {
        UnsafeUniformMap uniformMap = event.getExtraUniform().getUniforms();
        if (!Config.getCullChunk() || CullingStateManager.SHADER_LOADER.renderingShaderPass()) {
            uniformMap.setUniform("sketch_culling_terrain", 0);
        } else {
            uniformMap.setUniform("sketch_culling_terrain", 1);
        }

        uniformMap.setUniform("sketch_check_culling", CullingStateManager.checkCulling ? 1 : 0);

        if (CullingStateManager.CHUNK_CULLING_MAP_TARGET != null) {
            uniformMap.setUniform("sketch_culling_texture", 18);
            GL13.glActiveTexture(GL_TEXTURE18);
            GL11.glBindTexture(GL_TEXTURE_2D, CullingStateManager.CHUNK_CULLING_MAP_TARGET.getColorTextureId());
            GL13.glActiveTexture(GL_TEXTURE0);
            uniformMap.setUniform("sketch_culling_size", CullingStateManager.CHUNK_CULLING_MAP_TARGET.width);
        }

        uniformMap.setUniform("sketch_level_min_pos", CullingStateManager.LEVEL_MIN_POS);
        uniformMap.setUniform("sketch_level_pos_range", CullingStateManager.LEVEL_POS_RANGE);
        uniformMap.setUniform("sketch_level_section_range", CullingStateManager.LEVEL_SECTION_RANGE);
        Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BlockPos cameraPos = new BlockPos((int) pos.x >> 4, (int) pos.y >> 4, (int) pos.z >> 4);
        uniformMap.setUniform("sketch_camera_pos", cameraPos);

        uniformMap.setUniform("sketch_render_distance", CullingStateManager.CHUNK_CULLING_MESSAGE.getRenderDistance());
        uniformMap.setUniform("sketch_space_partition_size", CullingStateManager.CHUNK_CULLING_MESSAGE.getSpacePartitionSize());
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
                String Sampler = Component.translatable(SketchRender.MOD_ID + ".sampler").getString() + ": " + String.valueOf((Float.parseFloat(String.format("%.0f", Config.getSampling() * 100.0D))) + "%");
                addString(monitorTexts, Sampler);

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
                    String chunkCullingCount = Component.translatable(SketchRender.MOD_ID + ".chunk_update_count").getString() + ": " + CullingStateManager.CHUNK_CULLING_MESSAGE.lastQueueUpdateCount;
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

            /*
            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);

            height = (int) (minecraft.getWindow().getGuiScaledHeight() * 0.25f);
            width = (int) (minecraft.getWindow().getGuiScaledWidth() * 0.25f);
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferbuilder.vertex(minecraft.getWindow().getGuiScaledWidth() - width, height * 2, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
            bufferbuilder.vertex((double) minecraft.getWindow().getGuiScaledWidth(), height * 2, 0.0D).uv(1, 0.0F).color(255, 255, 255, 255).endVertex();
            bufferbuilder.vertex((double) minecraft.getWindow().getGuiScaledWidth(), height, 0.0D).uv(1, 1).color(255, 255, 255, 255).endVertex();
            bufferbuilder.vertex(minecraft.getWindow().getGuiScaledWidth() - width, height, 0.0D).uv(0.0F, 1).color(255, 255, 255, 255).endVertex();
            RenderSystem.setShaderTexture(0, ModLoader.CULL_TEST_TARGET.getColorTextureId());
            tessellator.end();
             */

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
                RenderSystem.setShaderTexture(0, CullingStateManager.DEPTH_TEXTURE[i]);
                tessellator.end();
                screenScale *= 0.5f;
            }

            if (Config.doEntityCulling()) {
                height = (int) (minecraft.getWindow().getGuiScaledHeight() * 0.25f);
                bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(minecraft.getWindow().getGuiScaledWidth() - height, height, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex((double) minecraft.getWindow().getGuiScaledWidth(), height, 0.0D).uv(1, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex((double) minecraft.getWindow().getGuiScaledWidth(), 0, 0.0D).uv(1, 1).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex(minecraft.getWindow().getGuiScaledWidth() - height, 0, 0.0D).uv(0.0F, 1).color(255, 255, 255, 255).endVertex();
                RenderSystem.setShaderTexture(0, CullingStateManager.ENTITY_CULLING_MAP_TARGET.getColorTextureId());
                tessellator.end();
            }

            if (Config.getCullChunk()) {
                height = (int) (minecraft.getWindow().getGuiScaledHeight() * 0.25f);
                bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(minecraft.getWindow().getGuiScaledWidth() - height, height * 2, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex((double) minecraft.getWindow().getGuiScaledWidth(), height * 2, 0.0D).uv(1, 0.0F).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex((double) minecraft.getWindow().getGuiScaledWidth(), height, 0.0D).uv(1, 1).color(255, 255, 255, 255).endVertex();
                bufferbuilder.vertex(minecraft.getWindow().getGuiScaledWidth() - height, height, 0.0D).uv(0.0F, 1).color(255, 255, 255, 255).endVertex();
                RenderSystem.setShaderTexture(0, CullingStateManager.CHUNK_CULLING_MAP_TARGET.getColorTextureId());
                tessellator.end();
            }
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }
}
