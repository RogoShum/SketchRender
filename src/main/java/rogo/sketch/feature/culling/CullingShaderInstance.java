package rogo.sketch.feature.culling;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.SketchRender;
import rogo.sketch.render.shader.ComputeShaderProgram;
import rogo.sketch.render.shader.GraphicsShaderProgram;
import rogo.sketch.render.shader.Shader;
import rogo.sketch.vanilla.CullingShader;
import rogo.sketch.api.ShaderCollector;

import javax.annotation.Nullable;
import java.io.IOException;

public class CullingShaderInstance extends ShaderInstance implements CullingShader, ShaderCollector {
    @Nullable
    public Uniform CULLING_CAMERA_POS;
    @Nullable
    public Uniform CULLING_CAMERA_DIR;
    @Nullable
    public Uniform BOX_SCALE;
    @Nullable
    public Uniform RENDER_DISTANCE;
    @Nullable
    public Uniform DEPTH_INDEX;
    @Nullable
    public Uniform DEPTH_SIZE;
    @Nullable
    public Uniform CULLING_SIZE;
    @Nullable
    public Uniform ENTITY_CULLING_SIZE;
    @Nullable
    public Uniform LEVEL_HEIGHT_OFFSET;
    @Nullable
    public Uniform LEVEL_MIN_SECTION;
    @Nullable
    public Uniform CULLING_FRUSTUM;
    @Nullable
    public Uniform FRUSTUM_POS;
    @Nullable
    public Uniform TEST_POS;
    @Nullable
    public Uniform TEST_ENTITY_POS;
    @Nullable
    public Uniform TEST_ENTITY_AABB;
    @Nullable
    public Uniform CULLING_VIEW_MAT;
    @Nullable
    public Uniform CULLING_PROJ_MAT;

    public CullingShaderInstance(ResourceProvider p_173336_, ResourceLocation shaderLocation, VertexFormat p_173338_) throws IOException {
        super(p_173336_, shaderLocation, p_173338_);
        this.CULLING_CAMERA_POS = this.getUniform("CullingCameraPos");
        this.CULLING_CAMERA_DIR = this.getUniform("CullingCameraDir");
        this.BOX_SCALE = this.getUniform("BoxScale");
        this.TEST_POS = this.getUniform("TestPos");
        this.TEST_ENTITY_POS = this.getUniform("TestEntityPos");
        this.TEST_ENTITY_AABB = this.getUniform("TestEntityAABB");
        this.DEPTH_INDEX = this.getUniform("DepthIndex");
        this.RENDER_DISTANCE = this.getUniform("RenderDistance");
        this.DEPTH_SIZE = this.getUniform("DepthSize");
        this.CULLING_SIZE = this.getUniform("CullingSize");
        this.LEVEL_HEIGHT_OFFSET = this.getUniform("LevelHeightOffset");
        this.LEVEL_MIN_SECTION = this.getUniform("LevelMinSection");
        this.ENTITY_CULLING_SIZE = this.getUniform("EntityCullingSize");
        this.CULLING_FRUSTUM = this.getUniform("CullingFrustum");
        this.FRUSTUM_POS = this.getUniform("FrustumPos");
        this.CULLING_VIEW_MAT = this.getUniform("CullingViewMat");
        this.CULLING_PROJ_MAT = this.getUniform("CullingProjMat");
        onShadeCreate();
    }

    @Override
    public Uniform getCullingFrustum() {
        return CULLING_FRUSTUM;
    }

    @Override
    public Uniform getCullingCameraPos() {
        return CULLING_CAMERA_POS;
    }

    @Override
    public Uniform getRenderDistance() {
        return RENDER_DISTANCE;
    }

    @Override
    public Uniform getDepthIndex() {
        return DEPTH_INDEX;
    }

    @Override
    public Uniform getDepthSize() {
        return DEPTH_SIZE;
    }

    @Override
    public Uniform getCullingSize() {
        return CULLING_SIZE;
    }

    @Override
    public Uniform getLevelHeightOffset() {
        return LEVEL_HEIGHT_OFFSET;
    }

    @Override
    public Uniform getLevelMinSection() {
        return LEVEL_MIN_SECTION;
    }

    @Override
    public Uniform getEntityCullingSize() {
        return ENTITY_CULLING_SIZE;
    }

    @Override
    public Uniform getFrustumPos() {
        return FRUSTUM_POS;
    }

    @Override
    public Uniform getCullingViewMat() {
        return CULLING_VIEW_MAT;
    }

    @Override
    public Uniform getCullingProjMat() {
        return CULLING_PROJ_MAT;
    }

    @Override
    public Uniform getCullingCameraDir() {
        return CULLING_CAMERA_DIR;
    }

    @Override
    public Uniform getBoxScale() {
        return BOX_SCALE;
    }

    @Override
    public Uniform getTestPos() {
        return TEST_POS;
    }

    @Override
    public Uniform getTestEntityPos() {
        return TEST_ENTITY_POS;
    }

    @Override
    public Uniform getTestEntityAABB() {
        return TEST_ENTITY_AABB;
    }

    @Override
    public void apply() {
        super.apply();
        if (CullingStateManager.updatingDepth)
            ProgramManager.glUseProgram(getId());
    }

    @Override
    public void onShadeCreate() {
        SketchRender.getShaderManager().onShaderLoad(this);
    }
}
