package org.vulkanb.eng.impl.animation;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.vulkanb.eng.Settings;
import org.vulkanb.eng.impl.GlobalBuffers;
import org.vulkanb.eng.vk.*;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

public class AnimationComputeActivity {

    private static final String ANIM_COMPUTE_SHADER_FILE_GLSL = "animations_comp.glsl";
    private static final String ANIM_COMPUTE_SHADER_FILE_SPV = ANIM_COMPUTE_SHADER_FILE_GLSL + ".spv";
    private static final int LOCAL_SIZE_X = 32;
    private static final int PUSH_CONSTANTS_SIZE = GraphConstants.INT_LENGTH * 5;

    private final Queue.ComputeQueue computeQueue;
    private final Device device;
    private final MemoryBarrier memoryBarrier;

    private CommandBuffer commandBuffer;
    private ComputePipeline computePipeline;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private DescriptorSet.StorageDescriptorSet dstVerticesDescriptorSet;
    private Fence fence;
    private DescriptorSet.StorageDescriptorSet jointMatricesDescriptorSet;
    private ShaderProgram shaderProgram;
    private DescriptorSet.StorageDescriptorSet srcVerticesDescriptorSet;
    private DescriptorSetLayout.StorageDescriptorSetLayout storageDescriptorSetLayout;
    private DescriptorSet.StorageDescriptorSet weightsDescriptorSet;

    public AnimationComputeActivity(CommandPool commandPool, PipelineCache pipelineCache) {
        this.device = pipelineCache.getDevice();
        this.computeQueue = new Queue.ComputeQueue(this.device, 0);
        createDescriptorPool();
        createDescriptorSets();
        createShaders();
        createPipeline(pipelineCache);
        createCommandBuffers(commandPool);
        this.memoryBarrier = new MemoryBarrier(0, VK_ACCESS_SHADER_WRITE_BIT);
    }

    public void close() {
        this.computePipeline.close();
        this.shaderProgram.close();
        this.commandBuffer.close();
        this.descriptorPool.close();
        this.storageDescriptorSetLayout.close();
        this.fence.close();
    }

    private void createCommandBuffers(CommandPool commandPool) {
        this.commandBuffer = new CommandBuffer(commandPool, true, false);
        this.fence = new Fence(this.device, true);
    }

    private void createDescriptorPool() {
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(4, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER));
        this.descriptorPool = new DescriptorPool(this.device, descriptorTypeCounts);
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
        var pipeLineCreationInfo = new ComputePipeline.PipeLineCreationInfo(this.shaderProgram,
                this.descriptorSetLayouts, PUSH_CONSTANTS_SIZE);
        this.computePipeline = new ComputePipeline(pipelineCache, pipeLineCreationInfo);
    }

    private void createShaders() {
        var settings = Settings.getInstance();
        if (settings.isShaderRecompilation())
            ShaderCompiler.compileShaderIfChanged(ANIM_COMPUTE_SHADER_FILE_GLSL, Shaderc.shaderc_compute_shader);
        this.shaderProgram = new ShaderProgram(this.device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_COMPUTE_BIT, ANIM_COMPUTE_SHADER_FILE_SPV),
                });
    }

    public void onAnimatedEntitiesLoaded(GlobalBuffers globalBuffers) {
        this.srcVerticesDescriptorSet = new DescriptorSet.StorageDescriptorSet(this.descriptorPool,
                this.storageDescriptorSetLayout, globalBuffers.getVerticesBuffer(), 0);
        this.weightsDescriptorSet = new DescriptorSet.StorageDescriptorSet(this.descriptorPool,
                this.storageDescriptorSetLayout, globalBuffers.getAnimWeightsBuffer(), 0);
        this.dstVerticesDescriptorSet = new DescriptorSet.StorageDescriptorSet(this.descriptorPool,
                this.storageDescriptorSetLayout, globalBuffers.getAnimVerticesBuffer(), 0);
        this.jointMatricesDescriptorSet = new DescriptorSet.StorageDescriptorSet(this.descriptorPool,
                this.storageDescriptorSetLayout, globalBuffers.getAnimJointMatricesBuffer(), 0);
    }

    public void recordCommandBuffer(GlobalBuffers globalBuffers) {
        this.fence.fenceWait();
        this.fence.reset();

        this.commandBuffer.reset();
        this.commandBuffer.beginRecording();

        try (var stack = MemoryStack.stackPush()) {
            var cmdHandle = this.commandBuffer.getVkCommandBuffer();

            vkCmdPipelineBarrier(cmdHandle, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, this.memoryBarrier.getVkMemoryBarrier(), null, null);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, this.computePipeline.getVkPipeline());

            var descriptorSets = stack.mallocLong(4);

            descriptorSets.put(this.srcVerticesDescriptorSet.getVkDescriptorSet());
            descriptorSets.put(this.weightsDescriptorSet.getVkDescriptorSet());
            descriptorSets.put(this.dstVerticesDescriptorSet.getVkDescriptorSet());
            descriptorSets.put(this.jointMatricesDescriptorSet.getVkDescriptorSet());
            descriptorSets.flip();
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE,
                    this.computePipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            var vulkanAnimEntityList = globalBuffers.getVulkanAnimEntityList();
            for (var vulkanAnimEntity : vulkanAnimEntityList) {
                var entity = vulkanAnimEntity.getEntity();
                var entityAnimation = entity.getEntityAnimation();
                if (!entityAnimation.isStarted()) continue;

                var vulkanModel = vulkanAnimEntity.getVulkanModel();
                var animationIdx = entity.getEntityAnimation().getAnimationIdx();
                var currentFrame = entity.getEntityAnimation().getCurrentFrame();
                var jointMatricesOffset = vulkanModel.getVulkanAnimationDataList().get(animationIdx).getVulkanAnimationFrameList().get(currentFrame).jointMatricesOffset();

                for (var vulkanAnimMesh : vulkanAnimEntity.getVulkanAnimMeshList()) {
                    var mesh = vulkanAnimMesh.vulkanMesh();

                    var groupSize = (int) Math.ceil((mesh.verticesSize() / (float) InstancedVertexBufferStructure.SIZE_IN_BYTES) / LOCAL_SIZE_X);

                    // Push constants
                    var pushConstantBuffer = stack.malloc(PUSH_CONSTANTS_SIZE);
                    pushConstantBuffer.putInt(mesh.verticesOffset() / GraphConstants.FLOAT_LENGTH);
                    pushConstantBuffer.putInt(mesh.verticesSize() / GraphConstants.FLOAT_LENGTH);
                    pushConstantBuffer.putInt(mesh.weightsOffset() / GraphConstants.FLOAT_LENGTH);
                    pushConstantBuffer.putInt(jointMatricesOffset / GraphConstants.MAT4X4_SIZE);
                    pushConstantBuffer.putInt(vulkanAnimMesh.meshOffset() / GraphConstants.FLOAT_LENGTH);
                    pushConstantBuffer.flip();
                    vkCmdPushConstants(cmdHandle, this.computePipeline.getVkPipelineLayout(),
                            VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstantBuffer);

                    vkCmdDispatch(cmdHandle, groupSize, 1, 1);
                }
            }
        }
        this.commandBuffer.endRecording();
    }

    public void submit() {
        try (var stack = MemoryStack.stackPush()) {
            this.computeQueue.submit(stack.pointers(this.commandBuffer.getVkCommandBuffer()),
                    null,
                    null,
                    null,
                    this.fence);
        }
    }
}
