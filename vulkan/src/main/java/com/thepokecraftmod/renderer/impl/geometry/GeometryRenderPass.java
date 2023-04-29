package com.thepokecraftmod.renderer.impl.geometry;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import com.thepokecraftmod.renderer.vk.Attachment;
import com.thepokecraftmod.renderer.vk.Device;

import java.util.List;

import static org.lwjgl.vulkan.VK11.*;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class GeometryRenderPass {

    private static final int MAX_SAMPLES = 1;

    private final Device device;
    private final long vkRenderPass;

    public GeometryRenderPass(Device device, List<Attachment> attachments) {
        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            var numAttachments = attachments.size();
            var attachmentsDesc = VkAttachmentDescription.calloc(numAttachments, stack);
            var depthAttachmentPos = 0;
            for (var i = 0; i < numAttachments; i++) {
                var attachment = attachments.get(i);
                attachmentsDesc.get(i)
                        .format(attachment.getImage().getFormat())
                        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .samples(MAX_SAMPLES)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
                if (attachment.isDepthAttachment()) {
                    depthAttachmentPos = i;
                    attachmentsDesc.get(i).finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);
                } else attachmentsDesc.get(i).finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }

            var colorReferences = VkAttachmentReference.calloc(GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
                    stack);
            for (var i = 0; i < GeometryAttachments.NUMBER_COLOR_ATTACHMENTS; i++)
                colorReferences.get(i)
                        .attachment(i)
                        .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            var depthReference = VkAttachmentReference.calloc(stack)
                    .attachment(depthAttachmentPos)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            // Render subpass
            var subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .pColorAttachments(colorReferences)
                    .colorAttachmentCount(colorReferences.capacity())
                    .pDepthStencilAttachment(depthReference);

            // Subpass dependencies
            var subpassDependencies = VkSubpassDependency.calloc(2, stack);
            subpassDependencies.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            subpassDependencies.get(1)
                    .srcSubpass(0)
                    .dstSubpass(VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            // Render pass
            var renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachmentsDesc)
                    .pSubpasses(subpass)
                    .pDependencies(subpassDependencies);

            var lp = stack.mallocLong(1);
            ok(vkCreateRenderPass(device.getVkDevice(), renderPassInfo, null, lp),
                    "Failed to create render pass");
            this.vkRenderPass = lp.get(0);
        }
    }

    public void close() {
        vkDestroyRenderPass(this.device.getVkDevice(), this.vkRenderPass, null);
    }

    public long getVkRenderPass() {
        return this.vkRenderPass;
    }
}