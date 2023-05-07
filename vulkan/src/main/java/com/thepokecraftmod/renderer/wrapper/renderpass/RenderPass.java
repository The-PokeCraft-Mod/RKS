package com.thepokecraftmod.renderer.wrapper.renderpass;

import com.thepokecraftmod.renderer.wrapper.core.VkWrapper;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.List;

import static com.thepokecraftmod.renderer.wrapper.core.VkUtils.ok;
import static org.lwjgl.vulkan.VK10.*;

public class RenderPass implements VkWrapper<Long> {

    private final Device device;
    private final long renderPass;
    public final List<Attachment> attachments;

    public RenderPass(Device device, long renderPass, List<Attachment> attachments) {
        this.device = device;
        this.renderPass = renderPass;
        this.attachments = attachments;
    }

    @Override
    public void close() {
        vkDestroyRenderPass(device.vk(), renderPass, null);
    }

    @Override
    public Long vk() {
        return renderPass;
    }

    public static class Builder {

        private final List<Attachment> colorAttachments = new ArrayList<>();
        private Attachment depthAttachment;

        public Builder colorAttachment(Attachment attachment) {
            colorAttachments.add(attachment);
            return this;
        }

        public Builder depthAttachment(Attachment attachment) {
            depthAttachment = attachment;
            return this;
        }

        public RenderPass build(Device device) {
            try (var stack = MemoryStack.stackPush()) {
                var attachments = new ArrayList<>(colorAttachments);
                attachments.add(depthAttachment);
                var attachmentsDesc = VkAttachmentDescription.calloc(attachments.size(), stack);
                var depthAttachmentPos = 0;

                for (var i = 0; i < attachments.size(); i++) {
                    var attachment = attachments.get(i);
                    attachmentsDesc.get(i)
                            .format(attachment.getImage().format)
                            .loadOp(attachment.loadOp)
                            .storeOp(attachment.storeOp)
                            .stencilLoadOp(attachment.stencilLoadOp)
                            .stencilStoreOp(attachment.stencilStoreOp)
                            .samples(attachment.samples)
                            .initialLayout(attachment.initialLayout)
                            .finalLayout(attachment.finalLayout);
                    if (attachment.finalLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                        depthAttachmentPos = i;
                }

                var colorReferences = VkAttachmentReference.calloc(colorAttachments.size(), stack);
                for (var i = 0; i < colorAttachments.size(); i++)
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

                var pRenderPass = stack.mallocLong(1);
                ok(vkCreateRenderPass(device.vk(), renderPassInfo, null, pRenderPass), "Failed to create RenderPass");
                return new RenderPass(device, pRenderPass.get(0), attachments);
            }
        }
    }
}
