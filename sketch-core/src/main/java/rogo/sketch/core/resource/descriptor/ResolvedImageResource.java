package rogo.sketch.core.resource.descriptor;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class ResolvedImageResource {
    private final KeyId identifier;
    private final int width;
    private final int height;
    private final int mipLevels;
    private final ImageFormat format;
    private final EnumSet<ImageUsage> usages;
    private final SamplerFilter minFilter;
    private final SamplerFilter magFilter;
    @Nullable
    private final SamplerFilter mipmapFilter;
    private final SamplerWrap wrapS;
    private final SamplerWrap wrapT;
    @Nullable
    private final String sourcePath;
    private final boolean usagesExplicitlyDeclared;

    public ResolvedImageResource(
            KeyId identifier,
            int width,
            int height,
            int mipLevels,
            ImageFormat format,
            Set<ImageUsage> usages,
            SamplerFilter minFilter,
            SamplerFilter magFilter,
            @Nullable SamplerFilter mipmapFilter,
            SamplerWrap wrapS,
            SamplerWrap wrapT,
            @Nullable String sourcePath) {
        this(
                identifier,
                width,
                height,
                mipLevels,
                format,
                usages,
                minFilter,
                magFilter,
                mipmapFilter,
                wrapS,
                wrapT,
                sourcePath,
                true);
    }

    public ResolvedImageResource(
            KeyId identifier,
            int width,
            int height,
            int mipLevels,
            ImageFormat format,
            Set<ImageUsage> usages,
            SamplerFilter minFilter,
            SamplerFilter magFilter,
            @Nullable SamplerFilter mipmapFilter,
            SamplerWrap wrapS,
            SamplerWrap wrapT,
            @Nullable String sourcePath,
            boolean usagesExplicitlyDeclared) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.mipLevels = Math.max(1, mipLevels);
        this.format = Objects.requireNonNull(format, "format");
        if (usages == null || usages.isEmpty()) {
            throw new IllegalArgumentException("Image usages must not be empty for " + identifier);
        }
        this.usages = EnumSet.copyOf(usages);
        this.minFilter = Objects.requireNonNull(minFilter, "minFilter");
        this.magFilter = Objects.requireNonNull(magFilter, "magFilter");
        this.mipmapFilter = mipmapFilter;
        this.wrapS = Objects.requireNonNull(wrapS, "wrapS");
        this.wrapT = Objects.requireNonNull(wrapT, "wrapT");
        this.sourcePath = sourcePath;
        this.usagesExplicitlyDeclared = usagesExplicitlyDeclared;
        validate();
    }

    private void validate() {
        if (format.isDepthFormat() && usages.contains(ImageUsage.COLOR_ATTACHMENT)) {
            throw new IllegalArgumentException("Depth image cannot be used as color attachment: " + identifier);
        }
        if (!format.isDepthFormat() && usages.contains(ImageUsage.DEPTH_ATTACHMENT)) {
            throw new IllegalArgumentException("Color image cannot be used as depth attachment: " + identifier);
        }
    }

    public KeyId identifier() {
        return identifier;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int mipLevels() {
        return mipLevels;
    }

    public ImageFormat format() {
        return format;
    }

    public Set<ImageUsage> usages() {
        return Collections.unmodifiableSet(usages);
    }

    public SamplerFilter minFilter() {
        return minFilter;
    }

    public SamplerFilter magFilter() {
        return magFilter;
    }

    @Nullable
    public SamplerFilter mipmapFilter() {
        return mipmapFilter;
    }

    public SamplerWrap wrapS() {
        return wrapS;
    }

    public SamplerWrap wrapT() {
        return wrapT;
    }

    @Nullable
    public String sourcePath() {
        return sourcePath;
    }

    public boolean usesMipmaps() {
        return mipLevels > 1 || (mipmapFilter != null && mipmapFilter.usesMipmaps()) || minFilter.usesMipmaps();
    }

    public boolean supports(ImageUsage usage) {
        return usages.contains(usage);
    }

    public boolean usagesExplicitlyDeclared() {
        return usagesExplicitlyDeclared;
    }

    public boolean isRenderTargetAttachment() {
        return supports(ImageUsage.COLOR_ATTACHMENT) || supports(ImageUsage.DEPTH_ATTACHMENT);
    }
}

