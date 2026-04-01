package rogo.sketch.config;

public interface PropertyCodec<T> {
    String encode(T value);

    T decode(String rawValue);
}
