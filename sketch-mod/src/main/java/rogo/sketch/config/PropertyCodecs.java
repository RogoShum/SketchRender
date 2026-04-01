package rogo.sketch.config;

import java.util.ArrayList;
import java.util.List;

public final class PropertyCodecs {
    public static final PropertyCodec<String> STRING = new PropertyCodec<>() {
        @Override
        public String encode(String value) {
            return value != null ? value : "";
        }

        @Override
        public String decode(String rawValue) {
            return rawValue != null ? rawValue : "";
        }
    };

    public static final PropertyCodec<Boolean> BOOLEAN = new PropertyCodec<>() {
        @Override
        public String encode(Boolean value) {
            return Boolean.toString(Boolean.TRUE.equals(value));
        }

        @Override
        public Boolean decode(String rawValue) {
            return Boolean.parseBoolean(rawValue);
        }
    };

    public static final PropertyCodec<Integer> INTEGER = new PropertyCodec<>() {
        @Override
        public String encode(Integer value) {
            return Integer.toString(value);
        }

        @Override
        public Integer decode(String rawValue) {
            return Integer.parseInt(rawValue.trim());
        }
    };

    public static final PropertyCodec<Float> FLOAT = new PropertyCodec<>() {
        @Override
        public String encode(Float value) {
            return Float.toString(value);
        }

        @Override
        public Float decode(String rawValue) {
            return Float.parseFloat(rawValue.trim());
        }
    };

    public static final PropertyCodec<Double> DOUBLE = new PropertyCodec<>() {
        @Override
        public String encode(Double value) {
            return Double.toString(value);
        }

        @Override
        public Double decode(String rawValue) {
            return Double.parseDouble(rawValue.trim());
        }
    };

    public static final PropertyCodec<List<String>> STRING_LIST = new PropertyCodec<>() {
        @Override
        public String encode(List<String> value) {
            return String.join(",", value);
        }

        @Override
        public List<String> decode(String rawValue) {
            List<String> values = new ArrayList<>();
            if (rawValue == null || rawValue.isBlank()) {
                return values;
            }
            for (String token : rawValue.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            return values;
        }
    };

    private PropertyCodecs() {
    }
}
