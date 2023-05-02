package com.thepokecraftmod.renderer.impl;

import com.thepokecraftmod.renderer.wrapper.core.Settings;
import com.thepokecraftmod.renderer.scene.ModelData;
import com.thepokecraftmod.renderer.scene.Scene;
import com.thepokecraftmod.renderer.wrapper.cmd.CmdBuffer;
import com.thepokecraftmod.renderer.wrapper.cmd.CmdPool;
import com.thepokecraftmod.renderer.wrapper.core.Queue;
import com.thepokecraftmod.renderer.wrapper.core.VkBuffer;
import com.thepokecraftmod.renderer.wrapper.core.VkConstants;
import com.thepokecraftmod.renderer.wrapper.image.Texture;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import com.thepokecraftmod.renderer.wrapper.vertex.VertexBufferStructure;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkDrawIndexedIndirectCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.lwjgl.vulkan.VK11.*;

//TODO: stage only part which contains new model uploaded. add ability to mark space for removal. GlobalBufferArena?
public class GlobalBuffers {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalBuffers.class);
    public static final int IND_COMMAND_STRIDE = VkDrawIndexedIndirectCommand.SIZEOF;
    // Handle std430 alignment
    private static final int MATERIAL_PADDING = VkConstants.FLOAT_LENGTH * 3;
    private static final int MATERIAL_SIZE = VkConstants.VEC4_SIZE + VkConstants.INT_LENGTH * 3 + VkConstants.FLOAT_LENGTH * 2 + MATERIAL_PADDING;
    private final VkBuffer animJointMatricesBuffer;
    private final VkBuffer animWeightsBuffer;
    private final VkBuffer indicesBuffer;
    private final VkBuffer materialsBuffer;
    private final VkBuffer verticesBuffer;
    private VkBuffer animIndirectBuffer;
    private VkBuffer[] animInstanceDataBuffers;
    private VkBuffer animVerticesBuffer;
    private VkBuffer indirectBuffer;
    private VkBuffer[] instanceDataBuffers;
    private int numAnimIndirectCommands;
    private int numIndirectCommands;
    private List<AnimatedEntity> animatedEntityList;

    public GlobalBuffers(Device device) {
        LOGGER.info("Creating global buffers");
        var settings = Settings.getInstance();
        this.verticesBuffer = new VkBuffer(device, settings.getMaxVerticesBuffer(), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        this.indicesBuffer = new VkBuffer(device, settings.getMaxIndicesBuffer(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        this.materialsBuffer = new VkBuffer(device, (long) settings.getMaxMaterials() * VkConstants.VEC4_SIZE * 9, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        this.animJointMatricesBuffer = new VkBuffer(device, settings.getMaxJointMatricesBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        this.animWeightsBuffer = new VkBuffer(device, settings.getMaxAnimWeightsBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        this.numIndirectCommands = 0;
    }

    public void close() {
        LOGGER.info("Closing global buffers");
        this.verticesBuffer.close();
        this.indicesBuffer.close();
        if (this.indirectBuffer != null) this.indirectBuffer.close();
        if (this.animVerticesBuffer != null) this.animVerticesBuffer.close();
        if (this.animIndirectBuffer != null) this.animIndirectBuffer.close();
        this.materialsBuffer.close();
        this.animJointMatricesBuffer.close();
        this.animWeightsBuffer.close();
        if (this.instanceDataBuffers != null) Arrays.stream(this.instanceDataBuffers).forEach(VkBuffer::close);
        if (this.animInstanceDataBuffers != null)
            Arrays.stream(this.animInstanceDataBuffers).forEach(VkBuffer::close);
    }

    public VkBuffer getAnimIndirectBuffer() {
        return this.animIndirectBuffer;
    }

    public VkBuffer[] getAnimInstanceDataBuffers() {
        return this.animInstanceDataBuffers;
    }

    public VkBuffer getAnimJointMatricesBuffer() {
        return this.animJointMatricesBuffer;
    }

    public VkBuffer getAnimVerticesBuffer() {
        return this.animVerticesBuffer;
    }

    public VkBuffer getAnimWeightsBuffer() {
        return this.animWeightsBuffer;
    }

    public VkBuffer getIndicesBuffer() {
        return this.indicesBuffer;
    }

    public VkBuffer getIndirectBuffer() {
        return this.indirectBuffer;
    }

    public VkBuffer[] getInstanceDataBuffers() {
        return this.instanceDataBuffers;
    }

    public VkBuffer getMaterialsBuffer() {
        return this.materialsBuffer;
    }

    public int getNumAnimIndirectCommands() {
        return this.numAnimIndirectCommands;
    }

    public int getNumIndirectCommands() {
        return this.numIndirectCommands;
    }

    public VkBuffer getVerticesBuffer() {
        return this.verticesBuffer;
    }

    public List<AnimatedEntity> getAnimatedEntities() {
        return this.animatedEntityList;
    }

    private void loadAnimEntities(List<GpuModel> gpuModelList, Scene scene, CmdPool cmdPool, Queue queue, int numSwapChainImages) {
        try (var stack = MemoryStack.stackPush()) {
            this.animatedEntityList = new ArrayList<>();
            this.numAnimIndirectCommands = 0;
            var device = cmdPool.device;
            var cmdBuffer = cmdPool.newBuffer(true, true);

            var bufferOffset = 0;
            var firstInstance = 0;
            var animatedCmdList = new ArrayList<VkDrawIndexedIndirectCommand>();
            for (var vulkanModel : gpuModelList) {
                var entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
                if (entities.isEmpty()) continue;
                for (var entity : entities) {
                    if (!entity.hasAnimation()) continue;
                    var vulkanAnimEntity = new AnimatedEntity(entity, vulkanModel);
                    this.animatedEntityList.add(vulkanAnimEntity);
                    var vulkanAnimMeshList = vulkanAnimEntity.meshes;
                    for (var vulkanMesh : vulkanModel.getVulkanMeshList()) {
                        var cmd = VkDrawIndexedIndirectCommand.calloc(stack);
                        cmd.indexCount(vulkanMesh.numIndices());
                        cmd.firstIndex(vulkanMesh.indicesOffset() / VkConstants.INT_LENGTH);
                        cmd.instanceCount(1);
                        cmd.vertexOffset(bufferOffset / VertexBufferStructure.SIZE_IN_BYTES);
                        cmd.firstInstance(firstInstance);
                        animatedCmdList.add(cmd);

                        vulkanAnimMeshList.add(new AnimatedEntity.VulkanAnimMesh(bufferOffset, vulkanMesh));
                        bufferOffset += vulkanMesh.verticesSize();
                        firstInstance++;
                    }
                }
            }
            this.animVerticesBuffer = new VkBuffer(device, bufferOffset, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

            this.numAnimIndirectCommands = animatedCmdList.size();
            if (this.numAnimIndirectCommands > 0) cmdBuffer.record(queue, true, () -> {
                var indirectStgBuffer = new StagingBuffer(device, (long) IND_COMMAND_STRIDE * this.numAnimIndirectCommands);
                if (this.animIndirectBuffer != null) this.animIndirectBuffer.close();
                this.animIndirectBuffer = new VkBuffer(
                        device,
                        indirectStgBuffer.stgVkBuffer.getRequestedSize(),
                        VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                        0
                );
                var dataBuffer = indirectStgBuffer.mappedMem();
                var indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

                animatedCmdList.forEach(indCommandBuffer::put);

                if (this.animInstanceDataBuffers != null)
                    Arrays.stream(this.animInstanceDataBuffers).forEach(VkBuffer::close);
                this.animInstanceDataBuffers = new VkBuffer[numSwapChainImages];
                for (var i = 0; i < numSwapChainImages; i++)
                    this.animInstanceDataBuffers[i] = new VkBuffer(
                            device,
                            (long) this.numAnimIndirectCommands * (VkConstants.MAT4X4_SIZE + VkConstants.INT_LENGTH),
                            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                            0
                    );

                indirectStgBuffer.recordTransferCommand(cmdBuffer, this.animIndirectBuffer);
                return indirectStgBuffer::close;
            });
        }
    }

    private void loadAnimationData(ModelData modelData, GpuModel gpuModel, StagingBuffer animJointMatricesStagingBuffer) {
        var animationsList = modelData.getAnimations();
        if (!modelData.hasAnimations()) return;
        var dataBuffer = animJointMatricesStagingBuffer.mappedMem();

        for (var animation : animationsList) {
            var animationData = new GpuModel.GpuAnimationData();
            gpuModel.addVulkanAnimationData(animationData);
            var frameList = animation.frames();
            for (var matrices : frameList) {
                animationData.addFrame(new GpuModel.GpuAnimationFrame(dataBuffer.position()));
                for (var matrix : matrices) {
                    matrix.get(dataBuffer);
                    dataBuffer.position(dataBuffer.position() + VkConstants.MAT4X4_SIZE);
                }
            }
        }
    }

    public void loadEntities(List<GpuModel> gpuModelList, Scene scene, CmdPool cmdPool, Queue queue, int numSwapChainImages) {
        loadStaticEntities(gpuModelList, scene, cmdPool, queue, numSwapChainImages);
        loadAnimEntities(gpuModelList, scene, cmdPool, queue, numSwapChainImages);
    }

    public void loadInstanceData(Scene scene, List<GpuModel> gpuModels, int currentSwapChainIdx) {
        if (this.instanceDataBuffers != null) {
            Predicate<GpuModel> excludeAnimatedEntitiesPredicate = GpuModel::hasAnimations;
            loadInstanceData(scene, gpuModels, this.instanceDataBuffers[currentSwapChainIdx], excludeAnimatedEntitiesPredicate);
        }

        if (this.animInstanceDataBuffers != null) {
            Predicate<GpuModel> excludedStaticEntitiesPredicate = v -> !v.hasAnimations();
            loadInstanceData(scene, gpuModels, this.animInstanceDataBuffers[currentSwapChainIdx], excludedStaticEntitiesPredicate);
        }
    }

    private void loadInstanceData(Scene scene, List<GpuModel> gpuModels, VkBuffer instanceBuffer, Predicate<GpuModel> excludedEntitiesPredicate) {
        if (instanceBuffer == null) return;
        var mappedMemory = instanceBuffer.map();
        var dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) instanceBuffer.getRequestedSize());
        var pos = 0;
        for (var vulkanModel : gpuModels) {
            var entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
            if (entities.isEmpty() || excludedEntitiesPredicate.test(vulkanModel)) continue;
            for (var vulkanMesh : vulkanModel.getVulkanMeshList())
                for (var entity : entities) {
                    entity.getModelMatrix().get(pos, dataBuffer);
                    pos += VkConstants.MAT4X4_SIZE;
                    dataBuffer.putInt(pos, vulkanMesh.globalMaterialIdx());
                    pos += VkConstants.INT_LENGTH;
                }
        }
        instanceBuffer.unMap();
    }

    private List<GpuModel.VulkanMaterial> loadMaterials(TextureCache textureCache, StagingBuffer materialsStagingBuffer, List<ModelData.Material> materialList, List<Texture> textureList) {
        var vulkanMaterialList = new ArrayList<GpuModel.VulkanMaterial>();
        for (var material : materialList) {
            var dataBuffer = materialsStagingBuffer.mappedMem();

            var texture = textureCache.getTexture(material.diffuseTexture());
            if (texture != null) textureList.add(texture);
            var textureIdx = textureCache.getPosition(material.diffuseTexture());

            texture = textureCache.getTexture(material.normalTexture());
            if (texture != null) textureList.add(texture);
            var normalMapIdx = textureCache.getPosition(material.normalTexture());

            texture = textureCache.getTexture(material.metalRoughMap());
            if (texture != null) textureList.add(texture);
            var metalRoughMapIdx = textureCache.getPosition(material.metalRoughMap());

            vulkanMaterialList.add(new GpuModel.VulkanMaterial(dataBuffer.position() / MATERIAL_SIZE));
            material.diffuseColor().get(dataBuffer);
            dataBuffer.position(dataBuffer.position() + VkConstants.VEC4_SIZE);
            dataBuffer.putInt(textureIdx);
            dataBuffer.putInt(normalMapIdx);
            dataBuffer.putInt(metalRoughMapIdx);
            dataBuffer.putFloat(material.roughnessFactor());
            dataBuffer.putFloat(material.metallicFactor());
            // Padding due to std430 alignment
            dataBuffer.putFloat(0.0f);
            dataBuffer.putFloat(0.0f);
            dataBuffer.putFloat(0.0f);
        }

        return vulkanMaterialList;
    }

    private void loadMeshes(StagingBuffer verticesStagingBuffer, StagingBuffer indicesStagingBuffer, StagingBuffer animWeightsStagingBuffer, ModelData modelData, GpuModel gpuModel, List<GpuModel.VulkanMaterial> vulkanMaterialList) {
        var verticesBuffer = verticesStagingBuffer.mappedMem();
        var indicesBuffer = indicesStagingBuffer.mappedMem();
        var weightsBuffer = animWeightsStagingBuffer.mappedMem();
        var meshes = modelData.getMeshDataList();
        var meshCount = 0;

        for (var meshData : meshes) {
            var positions = meshData.positions();
            var normals = meshData.normals();
            var tangents = meshData.tangents();
            var biTangents = meshData.biTangents();
            var textCoords = meshData.textCoords();
            if (textCoords == null || textCoords.length == 0) textCoords = new float[(positions.length / 3) * 2];
            var indices = meshData.indices();

            var numElements = positions.length + normals.length + tangents.length + biTangents.length + textCoords.length;
            var verticesSize = numElements * VkConstants.FLOAT_LENGTH;

            var localMaterialIdx = meshData.materialIdx();
            var globalMaterialIdx = 0;
            if (localMaterialIdx >= 0 && localMaterialIdx < vulkanMaterialList.size())
                globalMaterialIdx = vulkanMaterialList.get(localMaterialIdx).globalMaterialIdx();
            gpuModel.addVulkanMesh(new GpuModel.VulkanMesh(verticesSize, indices.length, verticesBuffer.position(), indicesBuffer.position(), globalMaterialIdx, weightsBuffer.position()));

            var rows = positions.length / 3;
            for (var row = 0; row < rows; row++) {
                var startPos = row * 3;
                var startTextCoord = row * 2;
                verticesBuffer.putFloat(positions[startPos])
                        .putFloat(positions[startPos + 1])
                        .putFloat(positions[startPos + 2])
                        .putFloat(normals[startPos])
                        .putFloat(normals[startPos + 1])
                        .putFloat(normals[startPos + 2])
                        .putFloat(tangents[startPos])
                        .putFloat(tangents[startPos + 1])
                        .putFloat(tangents[startPos + 2])
                        .putFloat(biTangents[startPos])
                        .putFloat(biTangents[startPos + 1])
                        .putFloat(biTangents[startPos + 2])
                        .putFloat(textCoords[startTextCoord])
                        .putFloat(textCoords[startTextCoord + 1]);
            }

            Arrays.stream(indices).forEach(indicesBuffer::putInt);

            loadWeightsBuffer(modelData, animWeightsStagingBuffer, meshCount);
            meshCount++;
        }
    }

    public List<GpuModel> loadModels(List<ModelData> models, TextureCache textureCache, CmdPool cmdPool, Queue queue) {
        var gpuModelList = new ArrayList<GpuModel>();
        var textureList = new ArrayList<Texture>();

        var device = cmdPool.device;
        var cmd = cmdPool.newBuffer(true, true);

        var verticesStgBuffer = new StagingBuffer(device, this.verticesBuffer.getRequestedSize());
        var indicesStgBuffer = new StagingBuffer(device, this.indicesBuffer.getRequestedSize());
        var materialsStgBuffer = new StagingBuffer(device, this.materialsBuffer.getRequestedSize());
        var animJointMatricesStgBuffer = new StagingBuffer(device, this.animJointMatricesBuffer.getRequestedSize());
        var animWeightsStgBuffer = new StagingBuffer(device, this.animWeightsBuffer.getRequestedSize());

        cmd.record(queue, true, () -> {
            // Load a default material
            var defaultMaterialList = Collections.singletonList(new ModelData.Material());
            loadMaterials(textureCache, materialsStgBuffer, defaultMaterialList, textureList);

            for (var modelData : models) {
                var vulkanModel = new GpuModel(modelData.getModelId());
                gpuModelList.add(vulkanModel);

                var vulkanMaterialList = loadMaterials(textureCache, materialsStgBuffer, modelData.getMaterialList(), textureList);
                loadMeshes(verticesStgBuffer, indicesStgBuffer, animWeightsStgBuffer, modelData, vulkanModel, vulkanMaterialList);
                loadAnimationData(modelData, vulkanModel, animJointMatricesStgBuffer);
            }

            if (textureList.isEmpty()) throw new RuntimeException("Impossible Scenario. Not a single texture loaded");

            materialsStgBuffer.recordTransferCommand(cmd, this.materialsBuffer);
            verticesStgBuffer.recordTransferCommand(cmd, this.verticesBuffer);
            indicesStgBuffer.recordTransferCommand(cmd, this.indicesBuffer);
            animJointMatricesStgBuffer.recordTransferCommand(cmd, this.animJointMatricesBuffer);
            animWeightsStgBuffer.recordTransferCommand(cmd, this.animWeightsBuffer);
            textureList.forEach(t -> t.recordTextureTransition(cmd));
            return null;
        });

        verticesStgBuffer.close();
        indicesStgBuffer.close();
        materialsStgBuffer.close();
        animJointMatricesStgBuffer.close();
        animWeightsStgBuffer.close();
        textureList.forEach(Texture::closeStaging);

        return gpuModelList;
    }

    private void loadStaticEntities(List<GpuModel> gpuModelList, Scene scene, CmdPool cmdPool, Queue queue, int numSwapChainImages) {
        try (var stack = MemoryStack.stackPush()) {
            var numIndirectCommands = 0;
            var device = cmdPool.device;
            var cmd = cmdPool.newBuffer(true, true);

            var indexedIndirectCommandList = new ArrayList<VkDrawIndexedIndirectCommand>();
            var numInstances = 0;
            var firstInstance = 0;
            for (var vulkanModel : gpuModelList) {
                var entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
                if (entities.isEmpty() || vulkanModel.hasAnimations()) continue;
                for (var vulkanMesh : vulkanModel.getVulkanMeshList()) {
                    var indexedIndirectCommand = VkDrawIndexedIndirectCommand.calloc(stack);
                    indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                    indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / VkConstants.INT_LENGTH);
                    indexedIndirectCommand.instanceCount(entities.size());
                    indexedIndirectCommand.vertexOffset(vulkanMesh.verticesOffset() / VertexBufferStructure.SIZE_IN_BYTES);
                    indexedIndirectCommand.firstInstance(firstInstance);
                    indexedIndirectCommandList.add(indexedIndirectCommand);

                    numIndirectCommands++;
                    firstInstance += entities.size();
                    numInstances += entities.size();
                }
            }

            this.numIndirectCommands = numIndirectCommands;
            if (this.numIndirectCommands > 0) {
                var finalNumInstances = numInstances;
                cmd.record(queue, true, () -> {
                    var indirectStgBuffer = new StagingBuffer(device, (long) IND_COMMAND_STRIDE * this.numIndirectCommands);
                    if (this.indirectBuffer != null) this.indirectBuffer.close();
                    this.indirectBuffer = new VkBuffer(device, indirectStgBuffer.stgVkBuffer.getRequestedSize(),
                            VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
                    var dataBuffer = indirectStgBuffer.mappedMem();
                    var indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

                    indexedIndirectCommandList.forEach(indCommandBuffer::put);

                    if (this.instanceDataBuffers != null) Arrays.stream(this.instanceDataBuffers).forEach(VkBuffer::close);
                    this.instanceDataBuffers = new VkBuffer[numSwapChainImages];
                    for (var i = 0; i < numSwapChainImages; i++)
                        this.instanceDataBuffers[i] = new VkBuffer(device, (long) finalNumInstances * (VkConstants.MAT4X4_SIZE + VkConstants.INT_LENGTH), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

                    indirectStgBuffer.recordTransferCommand(cmd, this.indirectBuffer);

                    return indirectStgBuffer::close;
                });
            }
        }
    }

    private void loadWeightsBuffer(ModelData modelData, StagingBuffer animWeightsBuffer, int meshCount) {
        var animMeshDataList = modelData.getAnimMeshDataList();
        if (animMeshDataList == null || animMeshDataList.isEmpty()) return;
        var animMeshData = animMeshDataList.get(meshCount);
        var weights = animMeshData.weights();
        var boneIds = animMeshData.boneIds();
        var dataBuffer = animWeightsBuffer.mappedMem();

        var rows = weights.length / 4;
        for (var row = 0; row < rows; row++) {
            var startPos = row * 4;
            dataBuffer.putFloat(weights[startPos])
                    .putFloat(weights[startPos + 1])
                    .putFloat(weights[startPos + 2])
                    .putFloat(weights[startPos + 3])
                    .putFloat(boneIds[startPos])
                    .putFloat(boneIds[startPos + 1])
                    .putFloat(boneIds[startPos + 2])
                    .putFloat(boneIds[startPos + 3]);
        }
    }

    private static class StagingBuffer {

        private final ByteBuffer dataBuffer;
        private final VkBuffer stgVkBuffer;

        public StagingBuffer(Device device, long size) {
            this.stgVkBuffer = new VkBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            var mappedMemory = this.stgVkBuffer.map();
            this.dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) this.stgVkBuffer.getRequestedSize());
        }

        public void close() {
            this.stgVkBuffer.unMap();
            this.stgVkBuffer.close();
        }

        public ByteBuffer mappedMem() {
            return this.dataBuffer;
        }

        private void recordTransferCommand(CmdBuffer cmd, VkBuffer dstBuffer) {
            try (var stack = MemoryStack.stackPush()) {
                var copyRegion = VkBufferCopy.calloc(1, stack)
                        .srcOffset(0)
                        .dstOffset(0)
                        .size(this.stgVkBuffer.getRequestedSize());
                vkCmdCopyBuffer(cmd.vk(), this.stgVkBuffer.getBuffer(), dstBuffer.getBuffer(), copyRegion);
            }
        }
    }
}
