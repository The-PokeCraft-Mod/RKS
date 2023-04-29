package com.thepokecraftmod.renderer.impl.shadows;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkViewport;
import com.thepokecraftmod.renderer.Settings;
import com.thepokecraftmod.renderer.impl.GlobalBuffers;
import com.thepokecraftmod.renderer.impl.geometry.GeometryAttachments;
import com.thepokecraftmod.renderer.scene.Scene;
import com.thepokecraftmod.renderer.vk.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

public class ShadowRenderActivity {

    private static final String SHADOW_GEOMETRY_SHADER_FILE_GLSL = "shadow_geometry.glsl";
    private static final String SHADOW_GEOMETRY_SHADER_FILE_SPV = SHADOW_GEOMETRY_SHADER_FILE_GLSL + ".spv";
    private static final String SHADOW_VERTEX_SHADER_FILE_GLSL = "shadow_vertex.glsl";
    private static final String SHADOW_VERTEX_SHADER_FILE_SPV = SHADOW_VERTEX_SHADER_FILE_GLSL + ".spv";

    private final Device device;
    private final Scene scene;
    private final ShadowsFrameBuffer shadowsFrameBuffer;

    private List<CascadeShadow> cascadeShadows;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Pipeline pipeLine;
    private DescriptorSet.UniformDescriptorSet[] projMatrixDescriptorSet;
    private ShaderProgram shaderProgram;
    private VulkanBuffer[] shadowsUniforms;
    private SwapChain swapChain;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;

    public ShadowRenderActivity(SwapChain swapChain, PipelineCache pipelineCache, Scene scene) {
        this.swapChain = swapChain;
        this.scene = scene;
        this.device = swapChain.getDevice();
        var numImages = swapChain.getNumImages();
        this.shadowsFrameBuffer = new ShadowsFrameBuffer(this.device);
        createShaders();
        createDescriptorPool(numImages);
        createDescriptorSets(numImages);
        createPipeline(pipelineCache);
        createShadowCascades();
    }

    public void close() {
        this.pipeLine.close();
        Arrays.stream(this.shadowsUniforms).forEach(VulkanBuffer::close);
        this.uniformDescriptorSetLayout.close();
        this.descriptorPool.close();
        this.shaderProgram.close();
        this.shadowsFrameBuffer.close();
    }

    private void createDescriptorPool(int numImages) {
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(numImages, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        this.descriptorPool = new DescriptorPool(this.device, descriptorTypeCounts);
    }

    private void createDescriptorSets(int numImages) {
        this.uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(this.device, 0, VK_SHADER_STAGE_GEOMETRY_BIT);
        this.descriptorSetLayouts = new DescriptorSetLayout[]{
                this.uniformDescriptorSetLayout,
        };

        this.projMatrixDescriptorSet = new DescriptorSet.UniformDescriptorSet[numImages];
        this.shadowsUniforms = new VulkanBuffer[numImages];
        for (var i = 0; i < numImages; i++) {
            this.shadowsUniforms[i] = new VulkanBuffer(this.device, (long)
                    VkConstants.MAT4X4_SIZE * VkConstants.SHADOW_MAP_CASCADE_COUNT,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            this.projMatrixDescriptorSet[i] = new DescriptorSet.UniformDescriptorSet(this.descriptorPool, this.uniformDescriptorSetLayout,
                    this.shadowsUniforms[i], 0);
        }
    }

    private void createPipeline(PipelineCache pipelineCache) {
        var pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                this.shadowsFrameBuffer.getRenderPass().getVkRenderPass(), this.shaderProgram,
                GeometryAttachments.NUMBER_COLOR_ATTACHMENTS, true, true, 0,
                new InstancedVertexBufferStructure(), this.descriptorSetLayouts);
        this.pipeLine = new Pipeline(pipelineCache, pipeLineCreationInfo);
    }

    private void createShaders() {
        var settings = Settings.getInstance();
        if (settings.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(SHADOW_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(SHADOW_GEOMETRY_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_geometry_shader);
        }
        this.shaderProgram = new ShaderProgram(this.device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, SHADOW_VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_GEOMETRY_BIT, SHADOW_GEOMETRY_SHADER_FILE_SPV),
                });
    }

    private void createShadowCascades() {
        this.cascadeShadows = new ArrayList<>();
        for (var i = 0; i < VkConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            var cascadeShadow = new CascadeShadow();
            this.cascadeShadows.add(cascadeShadow);
        }
    }

    public Attachment getDepthAttachment() {
        return this.shadowsFrameBuffer.getDepthAttachment();
    }

    public List<CascadeShadow> getShadowCascades() {
        return this.cascadeShadows;
    }

    public void recordCommandBuffer(CommandBuffer commandBuffer, GlobalBuffers globalBuffers, int idx) {
        try (var stack = MemoryStack.stackPush()) {
            var clearValues = VkClearValue.calloc(1, stack);
            clearValues.apply(0, v -> v.depthStencil().depth(1.0f));

            var settings = Settings.getInstance();
            var shadowMapSize = settings.getShadowMapSize();

            var cmdHandle = commandBuffer.getVkCommandBuffer();

            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(shadowMapSize)
                    .height(-shadowMapSize)
                    .width(shadowMapSize)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            var scissor = VkRect2D.calloc(1, stack)
                    .extent(it -> it
                            .width(shadowMapSize)
                            .height(shadowMapSize))
                    .offset(it -> it
                            .x(0)
                            .y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            var frameBuffer = this.shadowsFrameBuffer.getFrameBuffer();

            var renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(this.shadowsFrameBuffer.getRenderPass().getVkRenderPass())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(shadowMapSize, shadowMapSize))
                    .framebuffer(frameBuffer.getVkFrameBuffer());

            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeLine.getVkPipeline());

            var descriptorSets = stack.mallocLong(1)
                    .put(0, this.projMatrixDescriptorSet[idx].vk());

            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    this.pipeLine.getVkPipelineLayout(), 0, descriptorSets, null);

            var vertexBuffer = stack.mallocLong(1);
            var instanceBuffer = stack.mallocLong(1);
            var offsets = stack.mallocLong(1).put(0, 0L);

            // Draw commands for non-animated models
            if (globalBuffers.getNumIndirectCommands() > 0) {
                vertexBuffer.put(0, globalBuffers.getVerticesBuffer().getBuffer());
                instanceBuffer.put(0, globalBuffers.getInstanceDataBuffers()[idx].getBuffer());

                vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                vkCmdBindVertexBuffers(cmdHandle, 1, instanceBuffer, offsets);
                vkCmdBindIndexBuffer(cmdHandle, globalBuffers.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);

                var indirectBuffer = globalBuffers.getIndirectBuffer();
                vkCmdDrawIndexedIndirect(cmdHandle, indirectBuffer.getBuffer(), 0, globalBuffers.getNumIndirectCommands(),
                        GlobalBuffers.IND_COMMAND_STRIDE);
            }

            if (globalBuffers.getNumAnimIndirectCommands() > 0) {
                // Draw commands for  animated models
                vertexBuffer.put(0, globalBuffers.getAnimVerticesBuffer().getBuffer());
                instanceBuffer.put(0, globalBuffers.getAnimInstanceDataBuffers()[idx].getBuffer());

                vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                vkCmdBindVertexBuffers(cmdHandle, 1, instanceBuffer, offsets);
                vkCmdBindIndexBuffer(cmdHandle, globalBuffers.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
                var animIndirectBuffer = globalBuffers.getAnimIndirectBuffer();
                vkCmdDrawIndexedIndirect(cmdHandle, animIndirectBuffer.getBuffer(), 0, globalBuffers.getNumAnimIndirectCommands(),
                        GlobalBuffers.IND_COMMAND_STRIDE);
            }

            vkCmdEndRenderPass(cmdHandle);
        }
    }

    public void render() {
        if (this.scene.isLightChanged() || this.scene.getCamera().isHasMoved())
            CascadeShadow.updateCascadeShadows(this.cascadeShadows, this.scene);

        var idx = this.swapChain.getCurrentFrame();
        var offset = 0;
        for (var cascadeShadow : this.cascadeShadows) {
            VkUtils.copyMatrixToBuffer(this.shadowsUniforms[idx], cascadeShadow.getProjViewMatrix(), offset);
            offset += VkConstants.MAT4X4_SIZE;
        }
    }

    public void resize(SwapChain swapChain) {
        this.swapChain = swapChain;
        CascadeShadow.updateCascadeShadows(this.cascadeShadows, this.scene);
    }
}