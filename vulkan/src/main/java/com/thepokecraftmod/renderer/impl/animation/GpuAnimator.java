package com.thepokecraftmod.renderer.impl.animation;

import com.thepokecraftmod.renderer.vk.descriptor.DescriptorPool;
import com.thepokecraftmod.renderer.vk.descriptor.DescriptorSet;
import com.thepokecraftmod.renderer.vk.descriptor.DescriptorSetLayout;
import com.thepokecraftmod.renderer.vk.init.Device;
import com.thepokecraftmod.renderer.vk.manager.PoolManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import com.thepokecraftmod.renderer.Settings;
import com.thepokecraftmod.renderer.impl.GlobalBuffers;
import com.thepokecraftmod.renderer.vk.*;

import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

public class GpuAnimator {
    private static final String ANIM_COMPUTE_SHADER_FILE_GLSL = "animations_comp.glsl";
    private static final String ANIM_COMPUTE_SHADER_FILE_SPV = ANIM_COMPUTE_SHADER_FILE_GLSL + ".spv";
    private static final int LOCAL_SIZE_X = 32;
    private static final int PUSH_CONSTANTS_SIZE = VkConstants.INT_LENGTH * 5;

    private final Queue.ComputeQueue computeQueue;
    private final Device device;
    private final MemoryBarrier memoryBarrier;

    private CmdBuffer cmdBuffer;
    private ComputePipeline computePipeline;
    private PoolManager pools;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private DescriptorSet.StorageDescriptorSet dstVerticesDescriptorSet;
    private Fence fence;
    private DescriptorSet.StorageDescriptorSet jointMatricesDescriptorSet;
    private ShaderProgram shaderProgram;
    private DescriptorSet.StorageDescriptorSet srcVerticesDescriptorSet;
    private DescriptorSetLayout.StorageDescriptorSetLayout storageDescriptorSetLayout;
    private DescriptorSet.StorageDescriptorSet weightsDescriptorSet;

    public GpuAnimator(CmdPool cmdPool, PipelineCache pipelineCache) {
        this.device = pipelineCache.getDevice();
        this.computeQueue = new Queue.ComputeQueue(this.device, 0);
        createDescriptorPool();
        createDescriptorSets();
        createShaders();
        createPipeline(pipelineCache);
        createCommandBuffers(cmdPool);
        this.memoryBarrier = new MemoryBarrier(0, VK_ACCESS_SHADER_WRITE_BIT);
    }

    public void close() {
        computePipeline.close();
        shaderProgram.close();
        cmdBuffer.close();
        pools.close();
        storageDescriptorSetLayout.close();
        fence.close();
    }

    private void createCommandBuffers(CmdPool cmdPool) {
        this.cmdBuffer = cmdPool.newBuffer(true, false);
        this.fence = new Fence(this.device, true);
    }

    private void createDescriptorPool() {
        this.pools = new PoolManager(this.device, List.of(new DescriptorPool.DescriptorTypeCount(4, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)));
    }

    private void createDescriptorSets() {
        this.storageDescriptorSetLayout = new DescriptorSetLayout.StorageDescriptorSetLayout(this.device, 0, VK_SHADER_STAGE_COMPUTE_BIT);
        this.descriptorSetLayouts = new DescriptorSetLayout[]{
                this.storageDescriptorSetLayout,
                this.storageDescriptorSetLayout,
                this.storageDescriptorSetLayout,
                this.storageDescriptorSetLayout,
        };
    }

    private void createPipeline(PipelineCache pipelineCache) {
        var pipeLineCreationInfo = new ComputePipeline.PipeLineCreationInfo(this.shaderProgram, this.descriptorSetLayouts, PUSH_CONSTANTS_SIZE);
        this.computePipeline = new ComputePipeline(pipelineCache, pipeLineCreationInfo);
    }

    private void createShaders() {
        var settings = Settings.getInstance();
        if (settings.isShaderRecompilation())
            ShaderCompiler.compileShaderIfChanged(ANIM_COMPUTE_SHADER_FILE_GLSL, Shaderc.shaderc_compute_shader);
        this.shaderProgram = new ShaderProgram(this.device, new ShaderProgram.ShaderModuleData[]{new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_COMPUTE_BIT, ANIM_COMPUTE_SHADER_FILE_SPV)});
    }

    public void onAnimatedEntitiesLoaded(GlobalBuffers globalBuffers) {
        this.srcVerticesDescriptorSet = new DescriptorSet.StorageDescriptorSet(pools.getPool(), storageDescriptorSetLayout, globalBuffers.getVerticesBuffer(), 0);
        this.weightsDescriptorSet = new DescriptorSet.StorageDescriptorSet(pools.getPool(), storageDescriptorSetLayout, globalBuffers.getAnimWeightsBuffer(), 0);
        this.dstVerticesDescriptorSet = new DescriptorSet.StorageDescriptorSet(pools.getPool(), storageDescriptorSetLayout, globalBuffers.getAnimVerticesBuffer(), 0);
        this.jointMatricesDescriptorSet = new DescriptorSet.StorageDescriptorSet(pools.getPool(), storageDescriptorSetLayout, globalBuffers.getAnimJointMatricesBuffer(), 0);
    }

    public void recordCommandBuffer(GlobalBuffers globalBuffers) {
        try (var stack = MemoryStack.stackPush()) {
            this.fence.waitForFence();
            this.fence.reset();

            this.cmdBuffer.reset();
            this.cmdBuffer.record(null, false, () -> {
                vkCmdPipelineBarrier(cmdBuffer.vk(), VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, this.memoryBarrier.getVkMemoryBarrier(), null, null);
                vkCmdBindPipeline(cmdBuffer.vk(), VK_PIPELINE_BIND_POINT_COMPUTE, this.computePipeline.getVkPipeline());

                var descriptorSets = stack.mallocLong(4)
                        .put(this.srcVerticesDescriptorSet.vk())
                        .put(this.weightsDescriptorSet.vk())
                        .put(this.dstVerticesDescriptorSet.vk())
                        .put(this.jointMatricesDescriptorSet.vk())
                        .flip();
                vkCmdBindDescriptorSets(cmdBuffer.vk(), VK_PIPELINE_BIND_POINT_COMPUTE, this.computePipeline.getVkPipelineLayout(), 0, descriptorSets, null);

                var entities = globalBuffers.getAnimatedEntities();
                for (var animatedMesh : entities) {
                    var entity = animatedMesh.entity;
                    var entityAnimation = entity.getAnimation();
                    if (!entityAnimation.playing) continue;
                    var model = animatedMesh.model;
                    var animIdx = entity.getAnimation().animationIdx;
                    var currentFrame = entity.getAnimation().currentFrame;
                    var jointOffset = model.getAnimationData().get(animIdx).getFrameList().get(currentFrame).jointOffset();

                    for (var vulkanAnimMesh : animatedMesh.meshes) {
                        var mesh = vulkanAnimMesh.vulkanMesh();
                        var groupSize = (int) Math.ceil((mesh.verticesSize() / (float) InstancedVertexBufferStructure.SIZE_IN_BYTES) / LOCAL_SIZE_X);

                        // Push constants
                        var pushConstantBuffer = stack.malloc(PUSH_CONSTANTS_SIZE)
                                .putInt(mesh.verticesOffset() / VkConstants.FLOAT_LENGTH)
                                .putInt(mesh.verticesSize() / VkConstants.FLOAT_LENGTH)
                                .putInt(mesh.weightsOffset() / VkConstants.FLOAT_LENGTH)
                                .putInt(jointOffset / VkConstants.MAT4X4_SIZE)
                                .putInt(vulkanAnimMesh.meshOffset() / VkConstants.FLOAT_LENGTH)
                                .flip();
                        vkCmdPushConstants(cmdBuffer.vk(), this.computePipeline.getVkPipelineLayout(), VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstantBuffer);
                        vkCmdDispatch(cmdBuffer.vk(), groupSize, 1, 1);
                    }
                }

                return null;
            });
        }
    }

    public void submit() {
        try (var stack = MemoryStack.stackPush()) {
            this.computeQueue.submit(
                    stack.pointers(this.cmdBuffer.vk()),
                    null,
                    null,
                    null,
                    this.fence
            );
        }
    }
}
