package com.thepokecraftmod.renderer.wrapper.pipeline;

import com.thepokecraftmod.renderer.wrapper.core.VkUtils;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.Closeable;

import static org.lwjgl.vulkan.VK11.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK11.vkDestroyShaderModule;

public class ShaderProgram implements Closeable {

    private final Device device;
    public final ShaderModule[] shaderModules;

    public ShaderProgram(Device device, ShaderData[] shaderData) {
        this.device = device;
        var moduleCount = shaderData != null ? shaderData.length : 0;

        this.shaderModules = new ShaderModule[moduleCount];
        for (var i = 0; i < moduleCount; i++)
            this.shaderModules[i] = new ShaderModule(shaderData[i].stage(), createModule(shaderData[i].spvBytes), shaderData[i].constants());
    }

    @Override
    public void close() {
        for (var module : this.shaderModules) vkDestroyShaderModule(device.vk(), module.handle(), null);
    }

    private long createModule(byte[] code) {
        try (var stack = MemoryStack.stackPush()) {
            var createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(stack.malloc(code.length).put(0, code));

            var pModule = stack.mallocLong(1);
            VkUtils.ok(vkCreateShaderModule(device.vk(), createInfo, null, pModule), "Failed to create shader module");
            return pModule.get(0);
        }
    }

    public record ShaderModule(
            int shaderStage,
            long handle,
            ShaderConstants constants
    ) {}

    public record ShaderData(
            int stage,
            byte[] spvBytes,
            ShaderConstants constants
    ) {
        public ShaderData(int stage, byte[] spvBytes) {
            this(stage, spvBytes, null);
        }
    }
}
