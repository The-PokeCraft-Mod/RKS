package com.thepokecraftmod.renderer.impl.lighting;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import com.thepokecraftmod.renderer.vk.descriptor.DescriptorSetLayout;
import com.thepokecraftmod.renderer.vk.init.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.*;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class AttachmentsLayout extends DescriptorSetLayout {
    private static final Logger LOGGER = LoggerFactory.getLogger(Device.class);

    public AttachmentsLayout(Device device, int numAttachments) {
        super(device);

        LOGGER.info("Creating Attachments Layout");
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
            ok(vkCreateDescriptorSetLayout(device.vk(), layoutInfo, null, lp),
                    "Failed to create descriptor set layout");
            super.layout = lp.get(0);
        }
    }
}
