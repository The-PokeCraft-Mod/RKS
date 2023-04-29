package com.thepokecraftmod.renderer.impl.lighting;

import com.thepokecraftmod.renderer.vk.descriptor.DescriptorPool;
import com.thepokecraftmod.renderer.vk.descriptor.DescriptorSet;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import com.thepokecraftmod.renderer.vk.*;

import java.util.List;

import static org.lwjgl.vulkan.VK11.*;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class AttachmentsDescriptorSet extends DescriptorSet {

    private final int binding;
    private final Device device;
    private final TextureSampler textureSampler;

    public AttachmentsDescriptorSet(DescriptorPool descriptorPool, AttachmentsLayout descriptorSetLayout,
                                    List<Attachment> attachments, int binding) {
        try (var stack = MemoryStack.stackPush()) {
            this.device = descriptorPool.getDevice();
            this.binding = binding;
            var pDescriptorSetLayout = stack.mallocLong(1);
            pDescriptorSetLayout.put(0, descriptorSetLayout.vk());
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(descriptorPool.vk())
                    .pSetLayouts(pDescriptorSetLayout);

            var pDescriptorSet = stack.mallocLong(1);
            ok(vkAllocateDescriptorSets(this.device.getVkDevice(), allocInfo, pDescriptorSet),
                    "Failed to create descriptor set");

            this.vkDescriptorSet = pDescriptorSet.get(0);

            this.textureSampler = new TextureSampler(this.device, 1);

            update(attachments);
        }
    }

    public void close() {
        this.textureSampler.close();
    }

    public void update(List<Attachment> attachments) {
        try (var stack = MemoryStack.stackPush()) {
            var numAttachments = attachments.size();
            var descrBuffer = VkWriteDescriptorSet.calloc(numAttachments, stack);
            for (var i = 0; i < numAttachments; i++) {
                var attachment = attachments.get(i);
                var imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                        .sampler(this.textureSampler.getVkSampler())
                        .imageView(attachment.getImageView().getVkImageView());
                if (attachment.isDepthAttachment())
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);
                else imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

                descrBuffer.get(i)
                        .sType$Default()
                        .dstSet(this.vkDescriptorSet)
                        .dstBinding(this.binding + i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .pImageInfo(imageInfo);
            }

            vkUpdateDescriptorSets(this.device.getVkDevice(), descrBuffer, null);
        }
    }
}