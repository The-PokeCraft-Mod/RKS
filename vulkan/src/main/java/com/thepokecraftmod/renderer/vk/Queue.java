package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class Queue {
    private static final Logger LOGGER = LoggerFactory.getLogger(Queue.class);
    private final int queueFamilyIndex;
    private final VkQueue vkQueue;

    public Queue(Device device, int queueFamilyIndex, int queueIndex) {
        LOGGER.info("Creating queue");

        this.queueFamilyIndex = queueFamilyIndex;
        try (var stack = MemoryStack.stackPush()) {
            var pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device.vk(), queueFamilyIndex, queueIndex, pQueue);
            var queue = pQueue.get(0);
            this.vkQueue = new VkQueue(queue, device.vk());
        }
    }

    public int getQueueFamilyIndex() {
        return this.queueFamilyIndex;
    }

    public VkQueue getVkQueue() {
        return this.vkQueue;
    }

    public void submit(PointerBuffer commandBuffers, LongBuffer waitSemaphores, IntBuffer dstStageMasks,
                       LongBuffer signalSemaphores, Fence fence) {
        try (var stack = MemoryStack.stackPush()) {
            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(commandBuffers)
                    .pSignalSemaphores(signalSemaphores);
            if (waitSemaphores != null) submitInfo.waitSemaphoreCount(waitSemaphores.capacity())
                    .pWaitSemaphores(waitSemaphores)
                    .pWaitDstStageMask(dstStageMasks);
            else submitInfo.waitSemaphoreCount(0);
            var fenceHandle = fence != null ? fence.getVkFence() : VK_NULL_HANDLE;

            ok(vkQueueSubmit(this.vkQueue, submitInfo, fenceHandle),
                    "Failed to submit command to queue");
        }
    }

    public void waitIdle() {
        vkQueueWaitIdle(this.vkQueue);
    }

    public static class ComputeQueue extends Queue {

        public ComputeQueue(Device device, int queueIndex) {
            super(device, getComputeQueueFamilyIndex(device), queueIndex);
        }

        private static int getComputeQueueFamilyIndex(Device device) {
            var index = -1;
            var physicalDevice = device.getPhysicalDevice();
            var queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
            var numQueuesFamilies = queuePropsBuff.capacity();
            for (var i = 0; i < numQueuesFamilies; i++) {
                var props = queuePropsBuff.get(i);
                var computeQueue = (props.queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0;
                if (computeQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) throw new RuntimeException("Failed to get compute Queue family index");
            return index;
        }
    }

    public static class GraphicsQueue extends Queue {

        public GraphicsQueue(Device device, int queueIndex) {
            super(device, getGraphicsQueueFamilyIndex(device), queueIndex);
        }

        private static int getGraphicsQueueFamilyIndex(Device device) {
            var index = -1;
            var physicalDevice = device.getPhysicalDevice();
            var queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
            var numQueuesFamilies = queuePropsBuff.capacity();
            for (var i = 0; i < numQueuesFamilies; i++) {
                var props = queuePropsBuff.get(i);
                var graphicsQueue = (props.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0;
                if (graphicsQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) throw new RuntimeException("Failed to get graphics Queue family index");
            return index;
        }
    }

    public static class PresentQueue extends Queue {

        public PresentQueue(Device device, Surface surface, int queueIndex) {
            super(device, getPresentQueueFamilyIndex(device, surface), queueIndex);
        }

        private static int getPresentQueueFamilyIndex(Device device, Surface surface) {
            var index = -1;
            try (var stack = MemoryStack.stackPush()) {
                var physicalDevice = device.getPhysicalDevice();
                var queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
                var numQueuesFamilies = queuePropsBuff.capacity();
                var intBuff = stack.mallocInt(1);
                for (var i = 0; i < numQueuesFamilies; i++) {
                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice.vk(),
                            i, surface.getVkSurface(), intBuff);
                    var supportsPresentation = intBuff.get(0) == VK_TRUE;
                    if (supportsPresentation) {
                        index = i;
                        break;
                    }
                }
            }

            if (index < 0) throw new RuntimeException("Failed to get Presentation Queue family index");
            return index;
        }
    }
}
