package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.descriptor.DescriptorSetLayout;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.tinylog.Logger;

import static org.lwjgl.vulkan.VK11.*;

public class ComputePipeline {

    private final Device device;
    private final long vkPipeline;
    private final long vkPipelineLayout;

    public ComputePipeline(PipelineCache pipelineCache, PipeLineCreationInfo pipeLineCreationInfo) {
        Logger.debug("Creating compute pipeline");
        this.device = pipelineCache.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            var lp = stack.callocLong(1);
            var main = stack.UTF8("main");

            var shaderModules = pipeLineCreationInfo.shaderProgram.getShaderModules();
            var numModules = shaderModules != null ? shaderModules.length : 0;
            if (numModules != 1) throw new RuntimeException("Compute pipelines can have only one shader");
            var shaderModule = shaderModules[0];
            var shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(shaderModule.shaderStage())
                    .module(shaderModule.handle())
                    .pName(main);

            VkPushConstantRange.Buffer pushConstantRanges = null;
            if (pipeLineCreationInfo.pushConstantsSize() > 0) pushConstantRanges = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(pipeLineCreationInfo.pushConstantsSize());

            var descriptorSetLayouts = pipeLineCreationInfo.descriptorSetLayouts();
            var numLayouts = descriptorSetLayouts != null ? descriptorSetLayouts.length : 0;
            var ppLayout = stack.mallocLong(numLayouts);
            for (var i = 0; i < numLayouts; i++) ppLayout.put(i, descriptorSetLayouts[i].vk());
            var pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(ppLayout)
                    .pPushConstantRanges(pushConstantRanges);
            VkUtils.ok(vkCreatePipelineLayout(this.device.getVkDevice(), pPipelineLayoutCreateInfo, null, lp),
                    "Failed to create pipeline layout");
            this.vkPipelineLayout = lp.get(0);

            var computePipelineCreateInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .stage(shaderStage)
                    .layout(this.vkPipelineLayout);
            VkUtils.ok(vkCreateComputePipelines(this.device.getVkDevice(), pipelineCache.getVkPipelineCache(), computePipelineCreateInfo, null, lp), "Error creating compute pipeline");
            this.vkPipeline = lp.get(0);
        }
    }

    public void close() {
        Logger.debug("Destroying compute pipeline");
        vkDestroyPipelineLayout(this.device.getVkDevice(), this.vkPipelineLayout, null);
        vkDestroyPipeline(this.device.getVkDevice(), this.vkPipeline, null);
    }

    public long getVkPipeline() {
        return this.vkPipeline;
    }

    public long getVkPipelineLayout() {
        return this.vkPipelineLayout;
    }

    public record PipeLineCreationInfo(ShaderProgram shaderProgram, DescriptorSetLayout[] descriptorSetLayouts,
                                       int pushConstantsSize) {
    }
}
