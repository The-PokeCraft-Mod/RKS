package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSpecializationInfo;

import static org.lwjgl.vulkan.VK11.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK11.vkDestroyShaderModule;

public class ShaderProgram {

    private final Device device;
    public final ShaderModule[] shaderModules;

    public ShaderProgram(Device device, ShaderData[] shaderData) {
        this.device = device;
        var moduleCount = shaderData != null ? shaderData.length : 0;

        this.shaderModules = new ShaderModule[moduleCount];
        for (var i = 0; i < moduleCount; i++)
            this.shaderModules[i] = new ShaderModule(shaderData[i].stage(), createShaderModule(shaderData[i].spvBytes), shaderData[i].specInfo());
    }

    public void close() {
        for (var shaderModule : this.shaderModules)
            vkDestroyShaderModule(device.vk(), shaderModule.handle(), null);
    }

    private long createShaderModule(byte[] code) {
        try (var stack = MemoryStack.stackPush()) {
            var pCode = stack.malloc(code.length).put(0, code);

            var moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(pCode);

            var lp = stack.mallocLong(1);
            VkUtils.ok(vkCreateShaderModule(device.vk(), moduleCreateInfo, null, lp), "Failed to create shader module");
            return lp.get(0);
        }
    }

    public record ShaderModule(
            int shaderStage,
            long handle,
            VkSpecializationInfo specInfo
    ) {}

    public record ShaderData(
            int stage,
            byte[] spvBytes,
            VkSpecializationInfo specInfo
    ) {
        public ShaderData(int stage, byte[] spvBytes) {
            this(stage, spvBytes, null);
        }
    }
}
