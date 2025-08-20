package rogo.sketch.render.shader.preprocessor;

import rogo.sketch.util.Identifier;

/**
 * Exception thrown during shader preprocessing
 */
public class ShaderPreprocessorException extends Exception {
    
    private final Identifier shaderIdentifier;
    private final int lineNumber;
    
    public ShaderPreprocessorException(String message) {
        super(message);
        this.shaderIdentifier = null;
        this.lineNumber = -1;
    }
    
    public ShaderPreprocessorException(String message, Throwable cause) {
        super(message, cause);
        this.shaderIdentifier = null;
        this.lineNumber = -1;
    }
    
    public ShaderPreprocessorException(String message, Identifier shaderIdentifier, int lineNumber) {
        super(formatMessage(message, shaderIdentifier, lineNumber));
        this.shaderIdentifier = shaderIdentifier;
        this.lineNumber = lineNumber;
    }
    
    public ShaderPreprocessorException(String message, Identifier shaderIdentifier, int lineNumber, Throwable cause) {
        super(formatMessage(message, shaderIdentifier, lineNumber), cause);
        this.shaderIdentifier = shaderIdentifier;
        this.lineNumber = lineNumber;
    }
    
    private static String formatMessage(String message, Identifier shaderIdentifier, int lineNumber) {
        StringBuilder sb = new StringBuilder();
        if (shaderIdentifier != null) {
            sb.append("In shader '").append(shaderIdentifier).append("'");
            if (lineNumber > 0) {
                sb.append(" at line ").append(lineNumber);
            }
            sb.append(": ");
        }
        sb.append(message);
        return sb.toString();
    }
    
    public Identifier getShaderIdentifier() {
        return shaderIdentifier;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
}
