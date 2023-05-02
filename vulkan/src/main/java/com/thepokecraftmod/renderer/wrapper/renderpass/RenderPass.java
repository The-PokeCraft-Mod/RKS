package com.thepokecraftmod.renderer.wrapper.renderpass;

import com.thepokecraftmod.renderer.wrapper.core.VkWrapper;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;

import java.util.ArrayList;
import java.util.List;

import static com.thepokecraftmod.renderer.wrapper.core.VkUtils.ok;
import static org.lwjgl.vulkan.VK10.vkCreateRenderPass;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;

public class RenderPass implements VkWrapper<Long> {

    private final Device device;
    private final long renderPass;

    public RenderPass(Device device, long renderPass) {
        this.device = device;
        this.renderPass = renderPass;
    }

    @Override
    public void close() {
        vkDestroyRenderPass(device.vk(), this.renderPass, null);
    }

    @Override
    public Long vk() {
        return renderPass;
    }

    public static class Builder {

        private final List<Attachment> attachments = new ArrayList<>();

        public Builder attachment(Attachment attachment) {
            attachments.add(attachment);
            return this;
        }

        /*public RenderPass build(Device device) {
            try (var stack = MemoryStack.stackPush()) {
                var createInfo = VkRenderPassCreateInfo.calloc(stack)
                        .sType$Default()
                        .pAttachments(attachmentsDesc)
                        .pSubpasses(subpass)
                        .pDependencies(subpassDependencies);

                var pRenderPass = stack.mallocLong(1);
                ok(vkCreateRenderPass(device.vk(), createInfo, null, pRenderPass), "Failed to create RenderPass");
                var renderPass = pRenderPass.get(0);
            }
            return null;
        }*/
    }
}
