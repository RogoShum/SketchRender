package rogo.sketch.backend.opengl.resource.descriptor;

public record OpenGLImageFormatMapping(
        int internalFormat,
        int uploadFormat,
        int uploadType,
        int imageUnitFormat
) {
}

