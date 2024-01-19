package com.thepokecraftmod.renderer.impl.lighting;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import com.thepokecraftmod.renderer.wrapper.vertex.VertexInputStateInfo;

public class EmptyVertexBufferStructure extends VertexInputStateInfo {

    public EmptyVertexBufferStructure() {
        this.vi = VkPipelineVertexInputStateCreateInfo.calloc();
        this.vi.sType$Default()
                .pVertexBindingDescriptions(null)
                .pVertexAttributeDescriptions(null);
    }
}