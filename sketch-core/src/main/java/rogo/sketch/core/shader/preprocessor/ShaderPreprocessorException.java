package rogo.sketch.core.shader.preprocessor;

import rogo.sketch.core.util.KeyId;

/**
 * Exception thrown during shader preprocessing
 */
public class ShaderPreprocessorException extends Exception {
    
    private final KeyId shaderKeyId;
    private final int lineNumber;
    
    public ShaderPreprocessorException(String message) {
        super(message);
        this.shaderKeyId = null;
        this.lineNumber = -1;
    }
    
    public ShaderPreprocessorException(String message, Throwable cause) {
        super(message, cause);
        this.shaderKeyId = null;
        this.lineNumber = -1;
    }
    
    public ShaderPreprocessorException(String message, KeyId shaderKeyId, int lineNumber) {
        super(formatMessage(message, shaderKeyId, lineNumber));
        this.shaderKeyId = shaderKeyId;
        this.lineNumber = lineNumber;
    }
    
    public ShaderPreprocessorException(String message, KeyId shaderKeyId, int lineNumber, Throwable cause) {
        super(formatMessage(message, shaderKeyId, lineNumber), cause);
        this.shaderKeyId = shaderKeyId;
        this.lineNumber = lineNumber;
    }
    
    private static String formatMessage(String message, KeyId shaderKeyId, int lineNumber) {
        StringBuilder sb = new StringBuilder();
        if (shaderKeyId != null) {
            sb.append("In shader '").append(shaderKeyId).append("'");
            if (lineNumber > 0) {
                sb.append(" at line ").append(lineNumber);
            }
            sb.append(": ");
        }
        sb.append(message);
        return sb.toString();
    }
    
    public KeyId getShaderIdentifier() {
        return shaderKeyId;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
}
