package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.vkCreatePipelineCache;
import static org.lwjgl.vulkan.VK11.vkDestroyPipelineCache;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class PipelineCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineCache.class);
    private final Device device;
    private final long vkPipelineCache;

    public PipelineCache(Device device) {
        LOGGER.info("Creating pipeline cache");
        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            var createInfo = VkPipelineCacheCreateInfo.calloc(stack)
                    .sType$Default();

            var lp = stack.mallocLong(1);
            ok(vkCreatePipelineCache(device.vk(), createInfo, null, lp),
                    "Error creating pipeline cache");
            this.vkPipelineCache = lp.get(0);
        }
    }

    public void close() {
        LOGGER.info("Closing pipeline cache");
        vkDestroyPipelineCache(this.device.vk(), this.vkPipelineCache, null);
    }

    public Device getDevice() {
        return this.device;
    }

    public long getVkPipelineCache() {
        return this.vkPipelineCache;
    }
}