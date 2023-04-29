package com.thepokecraftmod.renderer.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK11.vkDestroyFramebuffer;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class FrameBuffer {

    private final Device device;
    private final long vkFrameBuffer;

    public FrameBuffer(Device device, int width, int height, LongBuffer pAttachments, long renderPass, int layers) {
        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            var fci = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(pAttachments)
                    .width(width)
                    .height(height)
                    .layers(layers)
                    .renderPass(renderPass);

            var lp = stack.mallocLong(1);
            ok(vkCreateFramebuffer(device.getVkDevice(), fci, null, lp),
                    "Failed to create FrameBuffer");
            this.vkFrameBuffer = lp.get(0);
        }
    }

    public void close() {
        vkDestroyFramebuffer(this.device.getVkDevice(), this.vkFrameBuffer, null);
    }

    public long getVkFrameBuffer() {
        return this.vkFrameBuffer;
    }

}