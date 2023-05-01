package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSpecializationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.vulkan.VK11.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK11.vkDestroyShaderModule;

public class ShaderProgram {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderProgram.class);
    private final Device device;
    private final ShaderModule[] shaderModules;

    public ShaderProgram(Device device, ShaderModuleData[] shaderModuleData) {
        try {
            this.device = device;
            var numModules = shaderModuleData != null ? shaderModuleData.length : 0;
            this.shaderModules = new ShaderModule[numModules];
            for (var i = 0; i < numModules; i++) {
                var moduleContents = Files.readAllBytes(Paths.get("shaders/" + shaderModuleData[i].shaderSpvFile()));
                var moduleHandle = createShaderModule(moduleContents);
                this.shaderModules[i] = new ShaderModule(shaderModuleData[i].shaderStage(), moduleHandle, shaderModuleData[i].specInfo());
            }
        } catch (IOException e) {
            LOGGER.error("Error reading shader files", e);
            throw new RuntimeException(e);
        }
    }

    public void close() {
        for (var shaderModule : this.shaderModules)
            vkDestroyShaderModule(this.device.vk(), shaderModule.handle(), null);
    }

    private long createShaderModule(byte[] code) {
        try (var stack = MemoryStack.stackPush()) {
            var pCode = stack.malloc(code.length).put(0, code);

            var moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(pCode);

            var lp = stack.mallocLong(1);
            VkUtils.ok(vkCreateShaderModule(this.device.vk(), moduleCreateInfo, null, lp), "Failed to create shader module");
            return lp.get(0);
        }
    }

    public ShaderModule[] getShaderModules() {
        return this.shaderModules;
    }

    public record ShaderModule(int shaderStage, long handle, VkSpecializationInfo specInfo) {
    }

    @Deprecated //FIXME: hardcoded to search for paths.
    public record ShaderModuleData(int shaderStage, String shaderSpvFile, VkSpecializationInfo specInfo) {
        public ShaderModuleData(int shaderStage, String shaderSpvFile) {
            this(shaderStage, shaderSpvFile, null);
        }
    }
}
