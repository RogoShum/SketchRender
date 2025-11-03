package rogo.sketch.render.command;

public record VertexDataShard(long maybePointerButNotUsedYet, long vertexOffset, int indexCount, long indicesOffset) {
}