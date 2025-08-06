package rogo.sketchrender.minecraft;

import com.mojang.blaze3d.shaders.Uniform;

public interface CullingShader {
    Uniform getRenderDistance();
    Uniform getDepthIndex();
    Uniform getCullingCameraPos();
    Uniform getCullingCameraDir();
    Uniform getBoxScale();
    Uniform getDepthSize();
    Uniform getCullingSize();
    Uniform getLevelHeightOffset();
    Uniform getLevelMinSection();
    Uniform getEntityCullingSize();
    Uniform getCullingFrustum();
    Uniform getFrustumPos();
    Uniform getCullingViewMat();
    Uniform getCullingProjMat();
    Uniform getTestPos();
    Uniform getTestEntityPos();
    Uniform getTestEntityAABB();
}