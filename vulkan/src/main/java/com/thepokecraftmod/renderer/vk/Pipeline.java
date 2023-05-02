package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.descriptor.DescriptorSetLayout;
import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.*;

public class Pipeline implements VkWrapper<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Pipeline.class);
    public final Device device;
    private final long pipeline;
    public final long layout;

    public Pipeline(PipelineCache pipelineCache, PipeLineCreationInfo pipeLineCreationInfo) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.info("Creating pipeline");
            this.device = pipelineCache.getDevice();
            var lp = stack.mallocLong(1);

            var main = stack.UTF8("main");

            var shaderModules = pipeLineCreationInfo.shaderProgram.shaderModules;
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

            VkUtils.ok(vkCreatePipelineLayout(this.device.vk(), pPipelineLayoutCreateInfo, null, lp),
                    "Failed to create pipeline layout");
            this.layout = lp.get(0);

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
                    .layout(this.layout)
                    .renderPass(pipeLineCreationInfo.vkRenderPass);
            if (ds != null) pipeline.pDepthStencilState(ds);
            VkUtils.ok(vkCreateGraphicsPipelines(this.device.vk(), pipelineCache.vk(), pipeline, null, lp),
                    "Error creating graphics pipeline");
            this.pipeline = lp.get(0);
        }
    }

    public void close() {
        LOGGER.info("Closing pipeline");
        vkDestroyPipelineLayout(this.device.vk(), this.layout, null);
        vkDestroyPipeline(this.device.vk(), this.pipeline, null);
    }

    @Override
    public Long vk() {
        return pipeline;
    }

    // TODO: Builder
    public record PipeLineCreationInfo(long vkRenderPass, ShaderProgram shaderProgram, int numColorAttachments,
                                       boolean hasDepthAttachment, boolean useBlend,
                                       int pushConstantsSize, VertexInputStateInfo viInputStateInfo,
                                       DescriptorSetLayout[] descriptorSetLayouts) {
        public void close() {
            this.viInputStateInfo.close();
        }
    }
}
