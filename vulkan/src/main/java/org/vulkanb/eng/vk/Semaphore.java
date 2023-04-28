package org.vulkanb.eng.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import static org.lwjgl.vulkan.VK11.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK11.vkDestroySemaphore;

public class Semaphore {

    private final Device device;
    private final long vkSemaphore;

    public Semaphore(Device device) {
        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            var semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType$Default();

            var lp = stack.mallocLong(1);
            VkUtils.ok(vkCreateSemaphore(device.getVkDevice(), semaphoreCreateInfo, null, lp),
                    "Failed to create semaphore");
            this.vkSemaphore = lp.get(0);
        }
    }

    public void close() {
        vkDestroySemaphore(this.device.getVkDevice(), this.vkSemaphore, null);
    }

    public long getVkSemaphore() {
        return this.vkSemaphore;
    }
}