package com.thepokecraftmod.renderer.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.tinylog.Logger;

import static org.lwjgl.vulkan.VK11.vkCreatePipelineCache;
import static org.lwjgl.vulkan.VK11.vkDestroyPipelineCache;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class PipelineCache {

    private final Device device;
    private final long vkPipelineCache;

    public PipelineCache(Device device) {
        Logger.debug("Creating pipeline cache");
        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            var createInfo = VkPipelineCacheCreateInfo.calloc(stack)
                    .sType$Default();

            var lp = stack.mallocLong(1);
            ok(vkCreatePipelineCache(device.getVkDevice(), createInfo, null, lp),
                    "Error creating pipeline cache");
            this.vkPipelineCache = lp.get(0);
        }
    }

    public void close() {
        Logger.debug("Destroying pipeline cache");
        vkDestroyPipelineCache(this.device.getVkDevice(), this.vkPipelineCache, null);
    }

    public Device getDevice() {
        return this.device;
    }

    public long getVkPipelineCache() {
        return this.vkPipelineCache;
    }
}
