package org.vulkanb.eng.impl.lighting;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkViewport;
import org.vulkanb.eng.Settings;
import org.vulkanb.eng.impl.shadows.CascadeShadow;
import org.vulkanb.eng.scene.Light;
import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.vk.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

public class LightPassRenderer {
    private static final String LIGHTING_FRAGMENT_SHADER_FILE_GLSL = "lighting_fragment.glsl";
    private static final String LIGHTING_FRAGMENT_SHADER_FILE_SPV = LIGHTING_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String LIGHTING_VERTEX_SHADER_FILE_GLSL = "lighting_vertex.glsl";
    private static final String LIGHTING_VERTEX_SHADER_FILE_SPV = LIGHTING_VERTEX_SHADER_FILE_GLSL + ".spv";

    private final Vector4f auxVec;
    private final Device device;
    private final LightSettingsUploader lightSettingsUploader;
    private final LightingFrameBuffer lightingFrameBuffer;
    private final Scene scene;

    private AttachmentsDescriptorSet attachmentsDescriptorSet;
    private AttachmentsLayout attachmentsLayout;
    private CommandBuffer[] commandBuffers;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Fence[] fences;
    private VulkanBuffer[] invMatricesBuffers;
    private DescriptorSet.UniformDescriptorSet[] invMatricesDescriptorSets;
    private VulkanBuffer[] lightsBuffers;
    private DescriptorSet.UniformDescriptorSet[] lightsDescriptorSets;
    private Pipeline pipeline;
    private ShaderProgram shaderProgram;
    private VulkanBuffer[] shadowsMatricesBuffers;
    private DescriptorSet.UniformDescriptorSet[] shadowsMatricesDescriptorSets;
    private SwapChain swapChain;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;

    public LightPassRenderer(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache,
                             List<Attachment> attachments, Scene scene) {
        this.swapChain = swapChain;
        this.scene = scene;
        this.device = swapChain.getDevice();
        this.auxVec = new Vector4f();
        this.lightSettingsUploader = new LightSettingsUploader();

        this.lightingFrameBuffer = new LightingFrameBuffer(swapChain);
        var numImages = swapChain.getNumImages();
        createShaders();
        createDescriptorPool(attachments);
        createUniforms(numImages);
        createDescriptorSets(attachments, numImages);
        createPipeline(pipelineCache);
        createCommandBuffers(commandPool, numImages);
    }

    public CommandBuffer beginRecording(List<CascadeShadow> cascadeShadows) {
        var idx = this.swapChain.getCurrentFrame();

        var fence = this.fences[idx];
        var commandBuffer = this.commandBuffers[idx];

        fence.waitForFence();
        fence.reset();

        updateLights(this.scene.getAmbientLight(), this.scene.getLights(), this.scene.getCamera().getViewMatrix(), this.lightsBuffers[idx]);
        updateInvMatrices(this.invMatricesBuffers[idx]);
        updateCascadeShadowMatrices(cascadeShadows, this.shadowsMatricesBuffers[idx]);

        commandBuffer.reset();
        commandBuffer.beginRecording();

        return commandBuffer;
    }

    public void close() {
        this.uniformDescriptorSetLayout.close();
        this.attachmentsDescriptorSet.close();
        this.attachmentsLayout.close();
        this.descriptorPool.close();
        Arrays.stream(this.lightsBuffers).forEach(VulkanBuffer::close);
        this.pipeline.close();
        this.lightSettingsUploader.close();
        Arrays.stream(this.invMatricesBuffers).forEach(VulkanBuffer::close);
        this.lightingFrameBuffer.close();
        Arrays.stream(this.shadowsMatricesBuffers).forEach(VulkanBuffer::close);
        this.shaderProgram.close();
        Arrays.stream(this.commandBuffers).forEach(CommandBuffer::close);
        Arrays.stream(this.fences).forEach(Fence::close);
    }

    private void createCommandBuffers(CommandPool commandPool, int numImages) {
        this.commandBuffers = new CommandBuffer[numImages];
        this.fences = new Fence[numImages];

        for (var i = 0; i < numImages; i++) {
            this.commandBuffers[i] = new CommandBuffer(commandPool, true, false);
            this.fences[i] = new Fence(this.device, true);
        }
    }

    private void createDescriptorPool(List<Attachment> attachments) {
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(attachments.size(), VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(this.swapChain.getNumImages() * 3, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        this.descriptorPool = new DescriptorPool(this.device, descriptorTypeCounts);
    }

    private void createDescriptorSets(List<Attachment> attachments, int numImages) {
        this.attachmentsLayout = new AttachmentsLayout(this.device, attachments.size());
        this.uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(this.device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        this.descriptorSetLayouts = new DescriptorSetLayout[]{
                this.attachmentsLayout,
                this.uniformDescriptorSetLayout,
                this.uniformDescriptorSetLayout,
                this.uniformDescriptorSetLayout,
        };

        this.attachmentsDescriptorSet = new AttachmentsDescriptorSet(this.descriptorPool, this.attachmentsLayout,
                attachments, 0);

        this.lightsDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        this.invMatricesDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        this.shadowsMatricesDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        for (var i = 0; i < numImages; i++) {
            this.lightsDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(this.descriptorPool, this.uniformDescriptorSetLayout,
                    this.lightsBuffers[i], 0);
            this.invMatricesDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(this.descriptorPool, this.uniformDescriptorSetLayout,
                    this.invMatricesBuffers[i], 0);
            this.shadowsMatricesDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(this.descriptorPool, this.uniformDescriptorSetLayout,
                    this.shadowsMatricesBuffers[i], 0);
        }
    }

    private void createPipeline(PipelineCache pipelineCache) {
        var pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                this.lightingFrameBuffer.getLightingRenderPass().getVkRenderPass(), this.shaderProgram, 1, false, false, 0,
                new EmptyVertexBufferStructure(), this.descriptorSetLayouts);
        this.pipeline = new Pipeline(pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.close();
    }

    private void createShaders() {
        var settings = Settings.getInstance();
        if (settings.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(LIGHTING_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(LIGHTING_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        this.shaderProgram = new ShaderProgram(this.device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, LIGHTING_VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, LIGHTING_FRAGMENT_SHADER_FILE_SPV,
                                this.lightSettingsUploader.getSpecInfo()),
                });
    }

    private void createUniforms(int numImages) {
        this.lightsBuffers = new VulkanBuffer[numImages];
        this.invMatricesBuffers = new VulkanBuffer[numImages];
        this.shadowsMatricesBuffers = new VulkanBuffer[numImages];
        for (var i = 0; i < numImages; i++) {
            this.lightsBuffers[i] = new VulkanBuffer(this.device, (long)
                    VkConstants.INT_LENGTH * 4 + VkConstants.VEC4_SIZE * 2 * VkConstants.MAX_LIGHTS +
                    VkConstants.VEC4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

            this.invMatricesBuffers[i] = new VulkanBuffer(this.device, (long)
                    VkConstants.MAT4X4_SIZE * 2, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

            this.shadowsMatricesBuffers[i] = new VulkanBuffer(this.device, (long)
                    (VkConstants.MAT4X4_SIZE + VkConstants.VEC4_SIZE) * VkConstants.SHADOW_MAP_CASCADE_COUNT,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        }
    }

    public void endRecording(CommandBuffer commandBuffer) {
        vkCmdEndRenderPass(commandBuffer.getVkCommandBuffer());
        commandBuffer.endRecording();
    }

    public LightingFrameBuffer getLightingFrameBuffer() {
        return this.lightingFrameBuffer;
    }

    public void recordCommandBuffer(CommandBuffer commandBuffer) {
        try (var stack = MemoryStack.stackPush()) {
            var idx = this.swapChain.getCurrentFrame();
            var swapChainExtent = this.swapChain.getSwapChainExtent();
            var width = swapChainExtent.width();
            var height = swapChainExtent.height();

            var frameBuffer = this.lightingFrameBuffer.getFrameBuffers()[idx];

            commandBuffer.reset();
            var clearValues = VkClearValue.calloc(1, stack);
            clearValues.apply(0, v -> v.color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1));

            var renderArea = VkRect2D.calloc(stack);
            renderArea.offset().set(0, 0);
            renderArea.extent().set(width, height);

            var renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(this.lightingFrameBuffer.getLightingRenderPass().getVkRenderPass())
                    .pClearValues(clearValues)
                    .framebuffer(frameBuffer.getVkFrameBuffer())
                    .renderArea(renderArea);

            commandBuffer.beginRecording();
            var cmdHandle = commandBuffer.getVkCommandBuffer();
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline.getVkPipeline());

            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            var scissor = VkRect2D.calloc(1, stack)
                    .extent(it -> it
                            .width(width)
                            .height(height))
                    .offset(it -> it
                            .x(0)
                            .y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            var descriptorSets = stack.mallocLong(4)
                    .put(0, this.attachmentsDescriptorSet.getVkDescriptorSet())
                    .put(1, this.lightsDescriptorSets[idx].getVkDescriptorSet())
                    .put(2, this.invMatricesDescriptorSets[idx].getVkDescriptorSet())
                    .put(3, this.shadowsMatricesDescriptorSets[idx].getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    this.pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            vkCmdDraw(cmdHandle, 3, 1, 0, 0);
        }
    }

    public void resize(SwapChain swapChain, List<Attachment> attachments) {
        this.swapChain = swapChain;
        this.attachmentsDescriptorSet.update(attachments);
        this.lightingFrameBuffer.resize(swapChain);
    }

    public void submit(Queue queue) {
        try (var stack = MemoryStack.stackPush()) {
            var idx = this.swapChain.getCurrentFrame();
            var commandBuffer = this.commandBuffers[idx];
            var currentFence = this.fences[idx];
            var syncSemaphores = this.swapChain.getSyncSemaphoresList()[idx];
            queue.submit(stack.pointers(commandBuffer.getVkCommandBuffer()),
                    stack.longs(syncSemaphores.geometryCompleteSemaphore().getVkSemaphore()),
                    stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.renderCompleteSemaphore().getVkSemaphore()),
                    currentFence);
        }
    }

    private void updateCascadeShadowMatrices(List<CascadeShadow> cascadeShadows, VulkanBuffer shadowsUniformBuffer) {
        var mappedMemory = shadowsUniformBuffer.map();
        var buffer = MemoryUtil.memByteBuffer(mappedMemory, (int) shadowsUniformBuffer.getRequestedSize());
        var offset = 0;
        for (var cascadeShadow : cascadeShadows) {
            cascadeShadow.getProjViewMatrix().get(offset, buffer);
            buffer.putFloat(offset + VkConstants.MAT4X4_SIZE, cascadeShadow.getSplitDistance());
            offset += VkConstants.MAT4X4_SIZE + VkConstants.VEC4_SIZE;
        }
        shadowsUniformBuffer.unMap();
    }

    private void updateInvMatrices(VulkanBuffer invMatricesBuffer) {
        var invProj = new Matrix4f(this.scene.getProjection().getProjectionMatrix()).invert();
        var invView = new Matrix4f(this.scene.getCamera().getViewMatrix()).invert();
        VkUtils.copyMatrixToBuffer(invMatricesBuffer, invProj, 0);
        VkUtils.copyMatrixToBuffer(invMatricesBuffer, invView, VkConstants.MAT4X4_SIZE);
    }

    private void updateLights(Vector4f ambientLight, Light[] lights, Matrix4f viewMatrix,
                              VulkanBuffer lightsBuffer) {
        var mappedMemory = lightsBuffer.map();
        var uniformBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) lightsBuffer.getRequestedSize());

        ambientLight.get(0, uniformBuffer);
        var offset = VkConstants.VEC4_SIZE;
        var numLights = lights != null ? lights.length : 0;
        uniformBuffer.putInt(offset, numLights);
        offset += VkConstants.VEC4_SIZE;
        for (var i = 0; i < numLights; i++) {
            var light = lights[i];
            this.auxVec.set(light.getPosition());
            this.auxVec.mul(viewMatrix);
            this.auxVec.w = light.getPosition().w;
            this.auxVec.get(offset, uniformBuffer);
            offset += VkConstants.VEC4_SIZE;
            light.getColor().get(offset, uniformBuffer);
            offset += VkConstants.VEC4_SIZE;
        }

        lightsBuffer.unMap();
    }
}