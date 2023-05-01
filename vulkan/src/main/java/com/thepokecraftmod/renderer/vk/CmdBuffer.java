package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.*;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class CmdBuffer implements VkWrapper<VkCommandBuffer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CmdBuffer.class);
    private final CmdPool cmdPool;
    private final boolean oneTimeSubmit;
    private final VkCommandBuffer cmdBuffer;

    public CmdBuffer(CmdPool cmdPool, boolean primary, boolean oneTimeSubmit) {
        LOGGER.debug("Creating command buffer");
        this.cmdPool = cmdPool;
        this.oneTimeSubmit = oneTimeSubmit;
        var vkDevice = cmdPool.getDevice().vk();

        try (var stack = MemoryStack.stackPush()) {
            var cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(cmdPool.vk())
                    .level(primary ? VK_COMMAND_BUFFER_LEVEL_PRIMARY : VK_COMMAND_BUFFER_LEVEL_SECONDARY)
                    .commandBufferCount(1);
            var pb = stack.mallocPointer(1);
            ok(vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb), "Failed to allocate render command buffer");
            this.cmdBuffer = new VkCommandBuffer(pb.get(0), vkDevice);
        }
    }

    public void beginRecording() {
        try (var stack = MemoryStack.stackPush()) {
            var cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default();
            if (this.oneTimeSubmit) cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            ok(vkBeginCommandBuffer(this.cmdBuffer, cmdBufInfo), "Failed to begin command buffer");
        }
    }

    @Override
    public void close() {
        LOGGER.debug("Closing command buffer");
        vkFreeCommandBuffers(this.cmdPool.getDevice().vk(), this.cmdPool.vk(), this.cmdBuffer);
    }

    public void endRecording() {
        ok(vkEndCommandBuffer(this.cmdBuffer), "Failed to end command buffer");
    }

    @Override
    public VkCommandBuffer vk() {
        return this.cmdBuffer;
    }

    public void reset() {
        vkResetCommandBuffer(this.cmdBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT);
    }

    public void submitAndWait(Device device, Queue queue) {
        var fence = new Fence(device, true);
        fence.reset();
        try (var stack = MemoryStack.stackPush()) {
            queue.submit(stack.pointers(this.cmdBuffer), null, null, null, fence);
        }
        fence.waitForFence();
        fence.close();
    }
}
