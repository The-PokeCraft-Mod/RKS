package com.thepokecraftmod.renderer.wrapper.pipeline;

import com.thepokecraftmod.renderer.wrapper.core.VkUtils;
import com.thepokecraftmod.renderer.wrapper.core.VkWrapper;
import com.thepokecraftmod.renderer.wrapper.descriptor.DescriptorSetLayout;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.*;

public class ComputePipeline implements VkWrapper<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Device.class);
    public final Device device;
    private final long pipeline;
    public final long layout;

    public ComputePipeline(PipelineCache pipelineCache, PipelineCreationInfo creationInfo) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.info("Creating compute pipeline");
            this.device = pipelineCache.getDevice();
            var lp = stack.callocLong(1);
            var main = stack.UTF8("main");

            var shaderModules = creationInfo.shaderProgram.shaderModules;
            var moduleCount = shaderModules != null ? shaderModules.length : 0;
            if (moduleCount != 1) throw new RuntimeException("Compute pipelines can have only one shader");
            var shaderModule = shaderModules[0];
            var shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(shaderModule.shaderStage())
                    .module(shaderModule.handle())
                    .pName(main);

            VkPushConstantRange.Buffer pushConstantRanges = null;
            if (creationInfo.pushConstantsSize() > 0) pushConstantRanges = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(creationInfo.pushConstantsSize());

            var descriptorSetLayouts = creationInfo.descriptorSetLayouts();
            var numLayouts = descriptorSetLayouts != null ? descriptorSetLayouts.length : 0;
            var ppLayout = stack.mallocLong(numLayouts);
            for (var i = 0; i < numLayouts; i++) ppLayout.put(i, descriptorSetLayouts[i].vk());
            var pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(ppLayout)
                    .pPushConstantRanges(pushConstantRanges);
            VkUtils.ok(vkCreatePipelineLayout(this.device.vk(), pPipelineLayoutCreateInfo, null, lp), "Failed to create pipeline layout");
            this.layout = lp.get(0);

            var computeCreateInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .stage(shaderStage)
                    .layout(this.layout);
            VkUtils.ok(vkCreateComputePipelines(this.device.vk(), pipelineCache.vk(), computeCreateInfo, null, lp), "Error creating compute pipeline");
            this.pipeline = lp.get(0);
        }
    }

    @Override
    public void close() {
        LOGGER.info("Closing");
        vkDestroyPipelineLayout(device.vk(), layout, null);
        vkDestroyPipeline(device.vk(), pipeline, null);
    }

    @Override
    public Long vk() {
        return pipeline;
    }

    public record PipelineCreationInfo(
            ShaderProgram shaderProgram,
            DescriptorSetLayout[] descriptorSetLayouts,
            int pushConstantsSize
    ) {
    }
}
