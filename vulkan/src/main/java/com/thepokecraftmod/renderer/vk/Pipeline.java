package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.descriptor.DescriptorSetLayout;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import static org.lwjgl.vulkan.VK11.*;

public class Pipeline {

    private final Device device;
    private final long vkPipeline;
    private final long vkPipelineLayout;

    public Pipeline(PipelineCache pipelineCache, PipeLineCreationInfo pipeLineCreationInfo) {
        Logger.debug("Creating pipeline");
        this.device = pipelineCache.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            var lp = stack.mallocLong(1);

            var main = stack.UTF8("main");

            var shaderModules = pipeLineCreationInfo.shaderProgram.getShaderModules();
            var numModules = shaderModules.length;
            var shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack);
            for (var i = 0; i < numModules; i++) {
                var shaderModule = shaderModules[i];
                shaderStages.get(i)
                        .sType$Default()
                        .stage(shaderModule.shaderStage())
                        .module(shaderModule.handle())
                        .pName(main);
                if (shaderModule.specInfo() != null) shaderStages.get(i).pSpecializationInfo(shaderModule.specInfo());
            }

            var vkPipelineInputAssemblyStateCreateInfo =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            var vkPipelineViewportStateCreateInfo =
                    VkPipelineViewportStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .viewportCount(1)
                            .scissorCount(1);

            var vkPipelineRasterizationStateCreateInfo =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_NONE)
                            .frontFace(VK_FRONT_FACE_CLOCKWISE)
                            .lineWidth(1.0f);

            var vkPipelineMultisampleStateCreateInfo =
                    VkPipelineMultisampleStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDepthStencilStateCreateInfo ds = null;
            if (pipeLineCreationInfo.hasDepthAttachment()) ds = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            var blendAttState = VkPipelineColorBlendAttachmentState.calloc(
                    pipeLineCreationInfo.numColorAttachments(), stack);
            for (var i = 0; i < pipeLineCreationInfo.numColorAttachments(); i++) {
                blendAttState.get(i)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                        .blendEnable(pipeLineCreationInfo.useBlend());
                if (pipeLineCreationInfo.useBlend()) blendAttState.get(i).colorBlendOp(VK_BLEND_OP_ADD)
                        .alphaBlendOp(VK_BLEND_OP_ADD)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO);
            }
            var colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pAttachments(blendAttState);

            var vkPipelineDynamicStateCreateInfo =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pDynamicStates(stack.ints(
                                    VK_DYNAMIC_STATE_VIEWPORT,
                                    VK_DYNAMIC_STATE_SCISSOR
                            ));

            VkPushConstantRange.Buffer vpcr = null;
            if (pipeLineCreationInfo.pushConstantsSize() > 0) vpcr = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(pipeLineCreationInfo.pushConstantsSize());

            var descriptorSetLayouts = pipeLineCreationInfo.descriptorSetLayouts();
            var numLayouts = descriptorSetLayouts != null ? descriptorSetLayouts.length : 0;
            var ppLayout = stack.mallocLong(numLayouts);
            for (var i = 0; i < numLayouts; i++) ppLayout.put(i, descriptorSetLayouts[i].vk());

            var pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(ppLayout)
                    .pPushConstantRanges(vpcr);

            VkUtils.ok(vkCreatePipelineLayout(this.device.getVkDevice(), pPipelineLayoutCreateInfo, null, lp),
                    "Failed to create pipeline layout");
            this.vkPipelineLayout = lp.get(0);

            var pipeline = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .pStages(shaderStages)
                    .pVertexInputState(pipeLineCreationInfo.viInputStateInfo().getVi())
                    .pInputAssemblyState(vkPipelineInputAssemblyStateCreateInfo)
                    .pViewportState(vkPipelineViewportStateCreateInfo)
                    .pRasterizationState(vkPipelineRasterizationStateCreateInfo)
                    .pMultisampleState(vkPipelineMultisampleStateCreateInfo)
                    .pColorBlendState(colorBlendState)
                    .pDynamicState(vkPipelineDynamicStateCreateInfo)
                    .layout(this.vkPipelineLayout)
                    .renderPass(pipeLineCreationInfo.vkRenderPass);
            if (ds != null) pipeline.pDepthStencilState(ds);
            VkUtils.ok(vkCreateGraphicsPipelines(this.device.getVkDevice(), pipelineCache.getVkPipelineCache(), pipeline, null, lp),
                    "Error creating graphics pipeline");
            this.vkPipeline = lp.get(0);
        }
    }

    public void close() {
        Logger.debug("Destroying pipeline");
        vkDestroyPipelineLayout(this.device.getVkDevice(), this.vkPipelineLayout, null);
        vkDestroyPipeline(this.device.getVkDevice(), this.vkPipeline, null);
    }

    public long getVkPipeline() {
        return this.vkPipeline;
    }

    public long getVkPipelineLayout() {
        return this.vkPipelineLayout;
    }

    public record PipeLineCreationInfo(long vkRenderPass, ShaderProgram shaderProgram, int numColorAttachments,
                                       boolean hasDepthAttachment, boolean useBlend,
                                       int pushConstantsSize, VertexInputStateInfo viInputStateInfo,
                                       DescriptorSetLayout[] descriptorSetLayouts) {
        public void close() {
            this.viInputStateInfo.close();
        }
    }
}
