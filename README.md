# SketchRender
English | [中文](README.zh.md)

A lightweight rendering utility library for **Minecraft mod development**.
> **Personal Project Note**: This library is a personal hobby project developed out of interest in rendering techniques. The code was primarily generated using AI-assisted development (Vibe coding) and may not be suitable as reference material for learning purposes. Please keep this in mind when exploring the codebase.

## ✨ Current Features

### Entity Culling
- **Function**: Visibility determination based on depth buffer, culling entities that are visually not visible.
- **Requirements**: Requires **OpenGL 4.4** or higher.

### Chunk Culling
- **Function**: Integrates chunk visibility checks into the Embeddium mod rendering pipeline, performing GPU-driven culling of invisible chunks and submitting draw calls.
- **Requirements**: Requires **OpenGL 4.6** and **Embeddium** installed.
- **Use Case**: Significantly improves Embeddium's chunk rendering performance on devices with **relatively weak CPU but strong GPU**.