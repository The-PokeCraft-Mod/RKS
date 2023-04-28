package org.vulkanb.eng.impl.lighting;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.tinylog.Logger;
import org.vulkanb.eng.vk.DescriptorSetLayout;
import org.vulkanb.eng.vk.Device;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.vk.VkUtils.ok;

public class AttachmentsLayout extends DescriptorSetLayout {

    public AttachmentsLayout(Device device, int numAttachments) {
        super(device);

        Logger.debug("Creating Attachments Layout");
        try (var stack = MemoryStack.stackPush()) {
            var layoutBindings = VkDescriptorSetLayoutBinding.calloc(numAttachments, stack);
            for (var i = 0; i < numAttachments; i++)
                layoutBindings.get(i)
                        .binding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            var layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(layoutBindings);

            var lp = stack.mallocLong(1);
            ok(vkCreateDescriptorSetLayout(device.getVkDevice(), layoutInfo, null, lp),
                    "Failed to create descriptor set layout");
            super.vkDescriptorLayout = lp.get(0);
        }
    }
}
