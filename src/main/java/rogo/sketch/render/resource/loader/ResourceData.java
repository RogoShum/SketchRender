package rogo.sketch.render.resource.loader;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Universal resource data holder.
 * Can hold String data (JSON/Text) or Binary data (InputStream/Bytes).
 */
public class ResourceData {
    private final String stringData;
    private final byte[] binaryData;
    private final InputStream inputStream;

    public ResourceData(String stringData) {
        this.stringData = stringData;
        this.binaryData = null;
        this.inputStream = null;
    }

    public ResourceData(byte[] binaryData) {
        this.stringData = null;
        this.binaryData = binaryData;
        this.inputStream = null;
    }

    public ResourceData(InputStream inputStream) {
        this.stringData = null;
        this.binaryData = null;
        this.inputStream = inputStream;
    }

    public String getString() {
        if (stringData != null) return stringData;
        if (binaryData != null) return new String(binaryData, StandardCharsets.UTF_8);
        if (inputStream != null) {
            try {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read input stream", e);
            }
        }
        return null;
    }

    public BufferedReader getReader() {
        if (stringData != null) {
            return new BufferedReader(new StringReader(stringData));
        }
        if (binaryData != null) {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(binaryData)));
        }
        if (inputStream != null) {
            return new BufferedReader(new InputStreamReader(inputStream));
        }
        return null;
    }

    public InputStream getStream() {
        if (inputStream != null) return inputStream;
        if (binaryData != null) return new ByteArrayInputStream(binaryData);
        if (stringData != null) return new ByteArrayInputStream(stringData.getBytes(StandardCharsets.UTF_8));
        return null;
    }
}

