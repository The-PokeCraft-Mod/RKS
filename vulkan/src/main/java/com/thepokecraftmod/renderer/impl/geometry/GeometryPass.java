package com.thepokecraftmod.renderer.impl.geometry;

import com.thepokecraftmod.renderer.vk.descriptor.DescriptorPool;
import com.thepokecraftmod.renderer.vk.descriptor.DescriptorSet;
import com.thepokecraftmod.renderer.vk.descriptor.DescriptorSetLayout;
import com.thepokecraftmod.renderer.vk.init.Device;
import com.thepokecraftmod.renderer.vk.manager.PoolManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkViewport;
import com.thepokecraftmod.renderer.Settings;
import com.thepokecraftmod.renderer.impl.GlobalBuffers;
import com.thepokecraftmod.renderer.impl.TextureCache;
import com.thepokecraftmod.renderer.scene.Scene;
import com.thepokecraftmod.renderer.vk.*;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

public class GeometryPass implements Closeable {
    private static final String GEOMETRY_FRAGMENT_SHADER_FILE_GLSL = "geometry_fragment.glsl";
    private static final String GEOMETRY_FRAGMENT_SHADER_FILE_SPV = GEOMETRY_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String GEOMETRY_VERTEX_SHADER_FILE_GLSL = "geometry_vertex.glsl";
    private static final String GEOMETRY_VERTEX_SHADER_FILE_SPV = GEOMETRY_VERTEX_SHADER_FILE_GLSL + ".spv";

    private final Device device;
    private final GeometryFrameBuffer geometryFrameBuffer;
    private final GeometrySpecConstants geometrySpecConstants;
    private final MemoryBarrier memoryBarrier;
    private final PipelineCache pipelineCache;
    private final Scene scene;

    private PoolManager pools;
    private DescriptorSetLayout[] geometryDescriptorSetLayouts;
    private DescriptorSetLayout.DynUniformDescriptorSetLayout materialDescriptorSetLayout;
    private DescriptorSet.StorageDescriptorSet materialsDescriptorSet;
    private Pipeline pipeLine;
    private DescriptorSet.UniformDescriptorSet projMatrixDescriptorSet;
    private VulkanBuffer projMatrixUniform;
    private ShaderProgram shaderProgram;
    private DescriptorSetLayout.StorageDescriptorSetLayout storageDescriptorSetLayout;
    private Swapchain swapChain;
    private TextureDescriptorSet textureDescriptorSet;
    private DescriptorSetLayout.SamplerDescriptorSetLayout textureDescriptorSetLayout;
    private TextureSampler textureSampler;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;
    private VulkanBuffer[] viewMatricesBuffer;
    private DescriptorSet.UniformDescriptorSet[] viewMatricesDescriptorSets;

    public GeometryPass(Swapchain swapChain, PipelineCache pipelineCache, Scene scene, GlobalBuffers globalBuffers) {
        this.swapChain = swapChain;
        this.pipelineCache = pipelineCache;
        this.scene = scene;
        this.device = swapChain.getDevice();
        this.geometrySpecConstants = new GeometrySpecConstants();

        this.geometryFrameBuffer = new GeometryFrameBuffer(swapChain);
        var numImages = swapChain.getNumImages();
        createShaders();
        createDescriptorPool();
        createDescriptorSets(numImages, globalBuffers);
        createPipeline();
        VkUtils.copyMatrixToBuffer(this.projMatrixUniform, scene.getProjection().getProjectionMatrix());
        this.memoryBarrier = new MemoryBarrier(VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT);
    }

    public void close() {
        this.pipeLine.close();
        this.geometrySpecConstants.close();
        Arrays.stream(this.viewMatricesBuffer).forEach(VulkanBuffer::close);
        this.projMatrixUniform.close();
        this.textureSampler.close();
        this.materialDescriptorSetLayout.close();
        this.textureDescriptorSetLayout.close();
        this.uniformDescriptorSetLayout.close();
        this.storageDescriptorSetLayout.close();
        this.pools.close();
        this.shaderProgram.close();
        this.geometryFrameBuffer.close();
    }

    private void createDescriptorPool() {
        var engineProps = Settings.getInstance();
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(this.swapChain.getNumImages() + 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(engineProps.getMaxMaterials() * 3, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER));
        this.pools = new PoolManager(this.device, descriptorTypeCounts);
    }

    private void createDescriptorSets(int numImages, GlobalBuffers globalBuffers) {
        var settings = Settings.getInstance();
        this.uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(this.device, 0, VK_SHADER_STAGE_VERTEX_BIT);
        this.textureDescriptorSetLayout = new DescriptorSetLayout.SamplerDescriptorSetLayout(this.device, settings.getMaxTextures(), 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        this.materialDescriptorSetLayout = new DescriptorSetLayout.DynUniformDescriptorSetLayout(this.device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        this.storageDescriptorSetLayout = new DescriptorSetLayout.StorageDescriptorSetLayout(this.device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        this.geometryDescriptorSetLayouts = new DescriptorSetLayout[]{this.uniformDescriptorSetLayout, this.uniformDescriptorSetLayout, this.storageDescriptorSetLayout, this.textureDescriptorSetLayout,};

        this.textureSampler = new TextureSampler(this.device, 1);
        this.projMatrixUniform = new VulkanBuffer(this.device, VkConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        this.projMatrixDescriptorSet = new DescriptorSet.UniformDescriptorSet(this.pools.getPool(), this.uniformDescriptorSetLayout, this.projMatrixUniform, 0);
        this.materialsDescriptorSet = new DescriptorSet.StorageDescriptorSet(this.pools.getPool(), this.storageDescriptorSetLayout, globalBuffers.getMaterialsBuffer(), 0);

        this.viewMatricesDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        this.viewMatricesBuffer = new VulkanBuffer[numImages];
        for (var i = 0; i < numImages; i++) {
            this.viewMatricesBuffer[i] = new VulkanBuffer(this.device, VkConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            this.viewMatricesDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(this.pools.getPool(), this.uniformDescriptorSetLayout, this.viewMatricesBuffer[i], 0);
        }
    }

    private void createPipeline() {
        var pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(this.geometryFrameBuffer.getRenderPass().getVkRenderPass(), this.shaderProgram, GeometryAttachments.NUMBER_COLOR_ATTACHMENTS, true, true, 0, new InstancedVertexBufferStructure(), this.geometryDescriptorSetLayouts);
        this.pipeLine = new Pipeline(this.pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.close();
    }

    private void createShaders() {
        var settings = Settings.getInstance();
        if (settings.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(GEOMETRY_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(GEOMETRY_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        this.shaderProgram = new ShaderProgram(this.device, new ShaderProgram.ShaderModuleData[]{new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, GEOMETRY_VERTEX_SHADER_FILE_SPV), new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, GEOMETRY_FRAGMENT_SHADER_FILE_SPV, this.geometrySpecConstants.getSpecInfo()),});
    }

    public List<Attachment> getAttachments() {
        return this.geometryFrameBuffer.geometryAttachments().getAttachments();
    }

    public void loadModels(TextureCache textureCache) {
        this.device.waitIdle();
        // Size of the descriptor is set up in the layout, we need to fill up the texture list
        // up to the number defined in the layout (reusing last texture)
        var textureCacheList = textureCache.getAll();
        var textureCacheSize = textureCacheList.size();
        var textureList = new ArrayList<>(textureCacheList);
        var settings = Settings.getInstance();
        var maxTextures = settings.getMaxTextures();
        for (var i = 0; i < maxTextures - textureCacheSize; i++) textureList.add(textureCacheList.get(textureCacheSize - 1));
        this.textureDescriptorSet = new TextureDescriptorSet(this.pools.getPool(), this.textureDescriptorSetLayout, textureList, this.textureSampler, 0);
    }

    public void recordCommandBuffer(CmdBuffer cmdBuffer, GlobalBuffers globalBuffers, int idx) {
        try (var stack = MemoryStack.stackPush()) {
            var swapChainExtent = this.swapChain.getSwapChainExtent();
            var width = swapChainExtent.width();
            var height = swapChainExtent.height();

            var frameBuffer = this.geometryFrameBuffer.getFrameBuffer();
            var attachments = this.geometryFrameBuffer.geometryAttachments().getAttachments();
            var clearValues = VkClearValue.calloc(attachments.size(), stack);
            for (var attachment : attachments)
                if (attachment.isDepthAttachment()) clearValues.apply(v -> v.depthStencil().depth(1.0f));
                else
                    clearValues.apply(v -> v.color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1));
            clearValues.flip();

            var renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack).sType$Default().renderPass(this.geometryFrameBuffer.getRenderPass().getVkRenderPass()).pClearValues(clearValues).renderArea(a -> a.extent().set(width, height)).framebuffer(frameBuffer.vk());
            var cmdHandle = cmdBuffer.vk();

            vkCmdPipelineBarrier(cmdHandle, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, 0, this.memoryBarrier.getVkMemoryBarrier(), null, null);
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeLine.getVkPipeline());

            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            var scissor = VkRect2D.calloc(1, stack).extent(it -> it
                    .width(width)
                    .height(height)
            ).offset(it -> it
                    .x(0)
                    .y(0)
            );
            vkCmdSetScissor(cmdHandle, 0, scissor);

            var descriptorSets = stack.mallocLong(4)
                    .put(0, this.projMatrixDescriptorSet.vk())
                    .put(1, this.viewMatricesDescriptorSets[idx].vk())
                    .put(2, this.materialsDescriptorSet.vk())
                    .put(3, this.textureDescriptorSet.vk());

            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeLine.getVkPipelineLayout(), 0, descriptorSets, null);

            var vertexBuffer = stack.mallocLong(1);
            var instanceBuffer = stack.mallocLong(1);
            var offsets = stack.mallocLong(1).put(0, 0L);

            // Draw commands for non-animated entities
            if (globalBuffers.getNumIndirectCommands() > 0) {
                vertexBuffer.put(0, globalBuffers.getVerticesBuffer().getBuffer());
                instanceBuffer.put(0, globalBuffers.getInstanceDataBuffers()[idx].getBuffer());

                vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                vkCmdBindVertexBuffers(cmdHandle, 1, instanceBuffer, offsets);
                vkCmdBindIndexBuffer(cmdHandle, globalBuffers.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
                var staticIndirectBuffer = globalBuffers.getIndirectBuffer();
                vkCmdDrawIndexedIndirect(cmdHandle, staticIndirectBuffer.getBuffer(), 0, globalBuffers.getNumIndirectCommands(), GlobalBuffers.IND_COMMAND_STRIDE);
            }

            // Draw commands for animated entities
            if (globalBuffers.getNumAnimIndirectCommands() > 0) {
                vertexBuffer.put(0, globalBuffers.getAnimVerticesBuffer().getBuffer());
                instanceBuffer.put(0, globalBuffers.getAnimInstanceDataBuffers()[idx].getBuffer());

                vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                vkCmdBindVertexBuffers(cmdHandle, 1, instanceBuffer, offsets);
                vkCmdBindIndexBuffer(cmdHandle, globalBuffers.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
                var animIndirectBuffer = globalBuffers.getAnimIndirectBuffer();
                vkCmdDrawIndexedIndirect(cmdHandle, animIndirectBuffer.getBuffer(), 0, globalBuffers.getNumAnimIndirectCommands(), GlobalBuffers.IND_COMMAND_STRIDE);
            }

            vkCmdEndRenderPass(cmdHandle);
        }
    }

    public void render() {
        var idx = this.swapChain.getCurrentFrame();
        VkUtils.copyMatrixToBuffer(this.projMatrixUniform, this.scene.getProjection().getProjectionMatrix());
        VkUtils.copyMatrixToBuffer(this.viewMatricesBuffer[idx], this.scene.getCamera().getViewMatrix());
    }

    public void resize(Swapchain swapChain) {
        this.swapChain = swapChain;
        this.geometryFrameBuffer.resize(swapChain);
    }
}