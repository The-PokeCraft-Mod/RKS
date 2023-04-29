package com.thepokecraftmod.renderer.impl.lighting;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import com.thepokecraftmod.renderer.vk.Device;
import com.thepokecraftmod.renderer.vk.Swapchain;

import static org.lwjgl.vulkan.VK11.*;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class LightingRenderPass {

    private final Device device;
    private final long vkRenderPass;

    public LightingRenderPass(Swapchain swapChain) {
        this.device = swapChain.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            var attachments = VkAttachmentDescription.calloc(1, stack);

            // Color attachment
            attachments.get(0)
                    .format(swapChain.getSurfaceFormat().imageFormat())
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            var colorReference = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            var subPass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(colorReference.remaining())
                    .pColorAttachments(colorReference);

            var subpassDependencies = VkSubpassDependency.calloc(1, stack);
            subpassDependencies.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            var renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subPass)
                    .pDependencies(subpassDependencies);

            var lp = stack.mallocLong(1);
            ok(vkCreateRenderPass(this.device.vk(), renderPassInfo, null, lp),
                    "Failed to create render pass");
            this.vkRenderPass = lp.get(0);
        }
    }

    public void close() {
        vkDestroyRenderPass(this.device.vk(), this.vkRenderPass, null);
    }

    public long getVkRenderPass() {
        return this.vkRenderPass;
    }
}
