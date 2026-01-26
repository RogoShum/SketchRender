package rogo.sketch.core.util;

import rogo.sketch.core.resource.*;
import rogo.sketch.core.api.Resizable;

import java.util.*;

public class RenderTargetUtil {

    public static void resizeRT(int windowWidth, int windowHeight) {
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
        Map<KeyId, RenderTarget> renderTargets = resourceManager.getResourcesOfType(ResourceTypes.RENDER_TARGET);

        // Maps to track data for validation
        Map<KeyId, List<RenderTarget>> textureRefMap = new HashMap<>(); // TextureId -> List<RenderTarget>
        Map<RenderTarget, Dimension> rtTargetDimensions = new HashMap<>(); // RenderTarget -> TargetSize
        Set<KeyId> blockedTextures = new HashSet<>();
        Set<KeyId> blockedRTs = new HashSet<>();

        // Step 1: Calculate target dimensions for all RenderTargets and map texture
        // usage
        for (RenderTarget rt : renderTargets.values()) {
            if (!(rt instanceof StandardRenderTarget stdRt))
                continue;

            int w, h;
            switch (stdRt.getResolutionMode()) {
                case FIXED -> {
                    w = stdRt.getBaseWidth();
                    h = stdRt.getBaseHeight();
                }
                case SCREEN_SIZE -> {
                    w = windowWidth;
                    h = windowHeight;
                }
                case SCREEN_RELATIVE -> {
                    w = (int) (windowWidth * stdRt.getScaleX());
                    h = (int) (windowHeight * stdRt.getScaleY());
                }
                default -> {
                    w = stdRt.getBaseWidth();
                    h = stdRt.getBaseHeight();
                }
            }
            // Ensure valid dimensions
            w = Math.max(1, w);
            h = Math.max(1, h);

            rtTargetDimensions.put(stdRt, new Dimension(w, h));

            // Collect referenced textures
            List<KeyId> attachments = new ArrayList<>(stdRt.getColorAttachmentIds());
            if (stdRt.getDepthAttachmentId() != null)
                attachments.add(stdRt.getDepthAttachmentId());
            if (stdRt.getStencilAttachmentId() != null)
                attachments.add(stdRt.getStencilAttachmentId());

            for (KeyId texId : attachments) {
                if (texId != null) {
                    textureRefMap.computeIfAbsent(texId, k -> new ArrayList<>()).add(stdRt);
                }
            }
        }

        // Step 2: Validate references
        for (Map.Entry<KeyId, List<RenderTarget>> entry : textureRefMap.entrySet()) {
            KeyId texId = entry.getKey();
            List<RenderTarget> referencingRTs = entry.getValue();

            Optional<Texture> textureOpt = resourceManager.getResource(ResourceTypes.TEXTURE,
                    texId);

            // Check 1: Texture existence and type (Is it an RT texture?)
            if (textureOpt.isEmpty() || !(textureOpt.get() instanceof StandardTexture stdTex)
                    || !stdTex.isRenderTargetAttachment()) {
                // If it's not a StandardTexture or not an attachment, we can't resize it via
                // this mechanism (or it might be a static image).
                // However, if it's referenced by an RT, it SHOULD be an attachment.
                // Exceptions: maybe VanillaTexture? But VanillaTexture isn't Resizable usually.

                // Allow non-Standard textures to simply be skipped / ignored for resize, but
                // log if they are Standard and NOT attachments.
                boolean isInvalid = textureOpt.isEmpty()
                        || (textureOpt.get() instanceof StandardTexture st && !st.isRenderTargetAttachment());

                if (isInvalid) {
                    System.err.println(
                            "Error: Texture [" + texId + "] is not a valid RenderTarget attachment (or not found).");
                    System.err.println("Referenced by RenderTargets:");
                    for (RenderTarget rt : referencingRTs) {
                        System.err.println(" - [" + rt.getIdentifier() + "]");
                        blockedRTs.add(rt.getIdentifier());
                    }
                    System.err.println("Stopping resize for these RenderTargets.");
                    blockedTextures.add(texId);
                    continue;
                }
            }

            // Check 2: Dimension Consistency
            Dimension consistentDim = null;
            boolean conflict = false;

            for (RenderTarget rt : referencingRTs) {
                Dimension rtDim = rtTargetDimensions.get(rt);
                if (consistentDim == null) {
                    consistentDim = rtDim;
                } else if (!consistentDim.equals(rtDim)) {
                    conflict = true;
                }
            }

            if (conflict) {
                System.err.println("Error: Texture [" + texId + "] has conflicting resize dimensions.");
                System.err.println("Dimensions mismatch:");
                for (RenderTarget rt : referencingRTs) {
                    Dimension d = rtTargetDimensions.get(rt);
                    if (d != null) {
                        System.err.println(" - RenderTarget [" + rt.getIdentifier() + "]: " + d.width + "x" + d.height);
                    }
                    blockedRTs.add(rt.getIdentifier());
                }
                System.err.println("Stopping resize for these RenderTargets.");
                blockedTextures.add(texId);
            }
        }

        // Step 3: Execute Resize
        // Resize valid textures first
        for (Map.Entry<KeyId, List<RenderTarget>> entry : textureRefMap.entrySet()) {
            KeyId texId = entry.getKey();
            if (blockedTextures.contains(texId))
                continue;

            List<RenderTarget> rts = entry.getValue();
            if (rts.isEmpty())
                continue;

            // All agree on size, pick first
            Dimension dim = rtTargetDimensions.get(rts.get(0));

            resourceManager.getResource(ResourceTypes.TEXTURE, texId).ifPresent(tex -> {
                if (tex instanceof Resizable r) {
                    r.resize(dim.width, dim.height);
                }
            });
        }

        // Resize valid RenderTargets
        for (RenderTarget rt : renderTargets.values()) {
            KeyId rtId = rt.getIdentifier();
            if (blockedRTs.contains(rtId))
                continue;

            if (rt instanceof Resizable r && rtTargetDimensions.containsKey(rt)) {
                Dimension dim = rtTargetDimensions.get(rt);
                r.resize(dim.width, dim.height);
            }
        }
    }

    private record Dimension(int width, int height) {
    }
}