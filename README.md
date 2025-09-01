# SketchRender
English | [中文](README.zh.md)

A lightweight rendering utility library for **Minecraft mod development**.

## ✨ Current Features

### Entity Culling
- **Function**: Visibility determination based on depth buffer, culling entities that are visually not visible.
- **Requirements**: Requires **OpenGL 4.4** or higher.

### Chunk Culling
- **Function**: Integrates chunk visibility checks into the Embeddium mod rendering pipeline, performing GPU-driven culling of invisible chunks and submitting draw calls.
- **Requirements**: Requires **OpenGL 4.6** and **Embeddium** installed.
- **Use Case**: Significantly improves Embeddium's chunk rendering performance on devices with **relatively weak CPU but strong GPU**.