package org.vulkanb.eng.impl.gui;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.vulkanb.eng.vk.GraphConstants;
import org.vulkanb.eng.vk.VertexInputStateInfo;

import static org.lwjgl.vulkan.VK10.*;

public class ImGuiVertexBufferStructure extends VertexInputStateInfo {

    public static final int VERTEX_SIZE = GraphConstants.FLOAT_LENGTH * 5;
    private static final int NUMBER_OF_ATTRIBUTES = 3;
    private final VkVertexInputAttributeDescription.Buffer viAttrs;
    private final VkVertexInputBindingDescription.Buffer viBindings;

    public ImGuiVertexBufferStructure() {
        this.viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        this.viBindings = VkVertexInputBindingDescription.calloc(1);
        this.vi = VkPipelineVertexInputStateCreateInfo.calloc();

        var i = 0;
        // Position
        this.viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(0);

        // Texture coordinates
        i++;
        this.viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(GraphConstants.FLOAT_LENGTH * 2);

        // Color
        i++;
        this.viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R8G8B8A8_UNORM)
                .offset(GraphConstants.FLOAT_LENGTH * 4);

        this.viBindings.get(0)
                .binding(0)
                .stride(VERTEX_SIZE)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        this.vi
                .sType$Default()
                .pVertexBindingDescriptions(this.viBindings)
                .pVertexAttributeDescriptions(this.viAttrs);
    }

    @Override
    public void close() {
        super.close();
        this.viBindings.free();
        this.viAttrs.free();
    }
}
