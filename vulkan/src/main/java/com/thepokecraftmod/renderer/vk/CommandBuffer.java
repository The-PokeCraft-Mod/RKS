package com.thepokecraftmod.renderer.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.tinylog.Logger;

import static org.lwjgl.vulkan.VK11.*;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class CommandBuffer {
    private final CommandPool commandPool;
    private final boolean oneTimeSubmit;
    private final VkCommandBuffer vkCommandBuffer;

    public CommandBuffer(CommandPool commandPool, boolean primary, boolean oneTimeSubmit) {
        Logger.trace("Creating command buffer");
        this.commandPool = commandPool;
        this.oneTimeSubmit = oneTimeSubmit;
        var vkDevice = commandPool.getDevice().getVkDevice();

        try (var stack = MemoryStack.stackPush()) {
            var cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool.getVkCommandPool())
                    .level(primary ? VK_COMMAND_BUFFER_LEVEL_PRIMARY : VK_COMMAND_BUFFER_LEVEL_SECONDARY)
                    .commandBufferCount(1);
            var pb = stack.mallocPointer(1);
            ok(vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb),
                    "Failed to allocate render command buffer");

            this.vkCommandBuffer = new VkCommandBuffer(pb.get(0), vkDevice);
        }
    }

    public void beginRecording() {
        try (var stack = MemoryStack.stackPush()) {
            var cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default();
            if (this.oneTimeSubmit) cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            ok(vkBeginCommandBuffer(this.vkCommandBuffer, cmdBufInfo), "Failed to begin command buffer");
        }
    }

    public void close() {
        Logger.trace("Destroying command buffer");
        vkFreeCommandBuffers(this.commandPool.getDevice().getVkDevice(), this.commandPool.getVkCommandPool(),
                this.vkCommandBuffer);
    }

    public void endRecording() {
        ok(vkEndCommandBuffer(this.vkCommandBuffer), "Failed to end command buffer");
    }

    public VkCommandBuffer getVkCommandBuffer() {
        return this.vkCommandBuffer;
    }

    public void reset() {
        vkResetCommandBuffer(this.vkCommandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT);
    }

    public void submitAndWait(Device device, Queue queue) {
        var fence = new Fence(device, true);
        fence.reset();
        try (var stack = MemoryStack.stackPush()) {
            queue.submit(stack.pointers(this.vkCommandBuffer), null, null, null, fence);
        }
        fence.waitForFence();
        fence.close();
    }
}
