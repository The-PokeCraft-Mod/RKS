package org.vulkanb.eng.impl.lighting;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.vulkanb.eng.vk.VertexInputStateInfo;

public class EmptyVertexBufferStructure extends VertexInputStateInfo {

    public EmptyVertexBufferStructure() {
        this.vi = VkPipelineVertexInputStateCreateInfo.calloc();
        this.vi.sType$Default()
                .pVertexBindingDescriptions(null)
                .pVertexAttributeDescriptions(null);
    }
}