package rogo.sketch.util;

import com.mojang.blaze3d.pipeline.RenderTarget;

public record DepthContext(RenderTarget frame, int index, int lastTexture) {}
