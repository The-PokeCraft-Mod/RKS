package com.thepokecraftmod.renderer.wrapper.vertex;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

public abstract class VertexInputStateInfo {

    protected VkPipelineVertexInputStateCreateInfo vi;

    public void close() {
        this.vi.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return this.vi;
    }
}
