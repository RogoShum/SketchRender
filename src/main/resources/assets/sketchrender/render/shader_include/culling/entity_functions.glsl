// Entity-specific functions for culling

// Entity-specific getSampler with slightly different calculation
int getSamplerEntity(float xLength, float yLength) {
    for (int i = 0; i < sketch_depthSize.length(); ++i) {
        float xStep = 2.1 / sketch_depthSize[i].x;
        float yStep = 2.1 / sketch_depthSize[i].y;
        if (xStep > xLength && yStep > yLength) {
            return i;
        }
    }
    return sketch_depthSize.length() - 1;
}
