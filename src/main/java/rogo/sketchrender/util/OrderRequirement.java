package rogo.sketchrender.util;

import java.util.ArrayList;
import java.util.List;

public class OrderRequirement<T> {
    private final List<T> mustPrecede;
    private final List<T> mustFollow;
    private final List<T> requiredElements;
    private final boolean followDefault;

    private OrderRequirement(List<T> mustPrecede, List<T> mustFollow, List<T> requiredElements, boolean followDefault) {
        this.mustPrecede = mustPrecede;
        this.mustFollow = mustFollow;
        this.requiredElements = requiredElements;
        this.followDefault = followDefault;
    }

    public List<T> getMustPrecede() {
        return mustPrecede;
    }

    public List<T> getMustFollow() {
        return mustFollow;
    }

    public List<T> getRequiredElements() {
        return requiredElements;
    }

    public boolean isFollowDefault() {
        return followDefault;
    }

    public static class Builder<T> {
        private final List<T> mustPrecede = new ArrayList<>();
        private final List<T> mustFollow = new ArrayList<>();
        private final List<T> requiredElements = new ArrayList<>();
        private boolean followDefault = false;

        public static <T> Builder<T> create() {
            return new Builder<>();
        }

        public Builder<T> mustPrecede(T elem) {
            this.mustPrecede.add(elem);
            return this;
        }

        public Builder<T> mustPrecede(List<T> elems) {
            this.mustPrecede.addAll(elems);
            return this;
        }

        public Builder<T> mustFollow(T elem) {
            this.mustFollow.add(elem);
            return this;
        }

        public Builder<T> mustFollow(List<T> elems) {
            this.mustFollow.addAll(elems);
            return this;
        }

        public Builder<T> require(T elem) {
            this.requiredElements.add(elem);
            return this;
        }

        public Builder<T> require(List<T> elems) {
            this.requiredElements.addAll(elems);
            return this;
        }

        public Builder<T> followDefault(boolean followDefault) {
            this.followDefault = followDefault;
            return this;
        }

        public OrderRequirement<T> build() {
            return new OrderRequirement<>(
                    new ArrayList<>(mustPrecede),
                    new ArrayList<>(mustFollow),
                    new ArrayList<>(requiredElements),
                    followDefault
            );
        }
    }
}