package org.vulkanb.eng.impl;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkDrawIndexedIndirectCommand;
import org.tinylog.Logger;
import org.vulkanb.eng.Settings;
import org.vulkanb.eng.scene.ModelData;
import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.vk.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.lwjgl.vulkan.VK11.*;

public class GlobalBuffers {
    public static final int IND_COMMAND_STRIDE = VkDrawIndexedIndirectCommand.SIZEOF;
    // Handle std430 alignment
    private static final int MATERIAL_PADDING = GraphConstants.FLOAT_LENGTH * 3;
    private static final int MATERIAL_SIZE = GraphConstants.VEC4_SIZE + GraphConstants.INT_LENGTH * 3 +
            GraphConstants.FLOAT_LENGTH * 2 + MATERIAL_PADDING;
    private final VulkanBuffer animJointMatricesBuffer;
    private final VulkanBuffer animWeightsBuffer;
    private final VulkanBuffer indicesBuffer;
    private final VulkanBuffer materialsBuffer;
    private final VulkanBuffer verticesBuffer;
    private VulkanBuffer animIndirectBuffer;
    private VulkanBuffer[] animInstanceDataBuffers;
    private VulkanBuffer animVerticesBuffer;
    private VulkanBuffer indirectBuffer;
    private VulkanBuffer[] instanceDataBuffers;
    private int numAnimIndirectCommands;
    private int numIndirectCommands;
    private List<VulkanAnimEntity> vulkanAnimEntityList;

    public GlobalBuffers(Device device) {
        Logger.debug("Creating global buffers");
        var engProps = Settings.getInstance();
        this.verticesBuffer = new VulkanBuffer(device, engProps.getMaxVerticesBuffer(), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        this.indicesBuffer = new VulkanBuffer(device, engProps.getMaxIndicesBuffer(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        var maxMaterials = engProps.getMaxMaterials();
        this.materialsBuffer = new VulkanBuffer(device, (long) maxMaterials * GraphConstants.VEC4_SIZE * 9, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        this.animJointMatricesBuffer = new VulkanBuffer(device, engProps.getMaxJointMatricesBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        this.animWeightsBuffer = new VulkanBuffer(device, engProps.getMaxAnimWeightsBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        this.numIndirectCommands = 0;
    }

    public void close() {
        Logger.debug("Destroying global buffers");
        this.verticesBuffer.close();
        this.indicesBuffer.close();
        if (this.indirectBuffer != null) this.indirectBuffer.close();
        if (this.animVerticesBuffer != null) this.animVerticesBuffer.close();
        if (this.animIndirectBuffer != null) this.animIndirectBuffer.close();
        this.materialsBuffer.close();
        this.animJointMatricesBuffer.close();
        this.animWeightsBuffer.close();
        if (this.instanceDataBuffers != null) Arrays.stream(this.instanceDataBuffers).forEach(VulkanBuffer::close);
        if (this.animInstanceDataBuffers != null)
            Arrays.stream(this.animInstanceDataBuffers).forEach(VulkanBuffer::close);
    }

    public VulkanBuffer getAnimIndirectBuffer() {
        return this.animIndirectBuffer;
    }

    public VulkanBuffer[] getAnimInstanceDataBuffers() {
        return this.animInstanceDataBuffers;
    }

    public VulkanBuffer getAnimJointMatricesBuffer() {
        return this.animJointMatricesBuffer;
    }

    public VulkanBuffer getAnimVerticesBuffer() {
        return this.animVerticesBuffer;
    }

    public VulkanBuffer getAnimWeightsBuffer() {
        return this.animWeightsBuffer;
    }

    public VulkanBuffer getIndicesBuffer() {
        return this.indicesBuffer;
    }

    public VulkanBuffer getIndirectBuffer() {
        return this.indirectBuffer;
    }

    public VulkanBuffer[] getInstanceDataBuffers() {
        return this.instanceDataBuffers;
    }

    public VulkanBuffer getMaterialsBuffer() {
        return this.materialsBuffer;
    }

    public int getNumAnimIndirectCommands() {
        return this.numAnimIndirectCommands;
    }

    public int getNumIndirectCommands() {
        return this.numIndirectCommands;
    }

    public VulkanBuffer getVerticesBuffer() {
        return this.verticesBuffer;
    }

    public List<VulkanAnimEntity> getVulkanAnimEntityList() {
        return this.vulkanAnimEntityList;
    }

    private void loadAnimEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool,
                                  Queue queue, int numSwapChainImages) {
        this.vulkanAnimEntityList = new ArrayList<>();
        this.numAnimIndirectCommands = 0;
        try (var stack = MemoryStack.stackPush()) {
            var device = commandPool.getDevice();
            var cmd = new CommandBuffer(commandPool, true, true);

            var bufferOffset = 0;
            var firstInstance = 0;
            List<VkDrawIndexedIndirectCommand> indexedIndirectCommandList = new ArrayList<>();
            for (var vulkanModel : vulkanModelList) {
                var entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
                if (entities.isEmpty()) continue;
                for (var entity : entities) {
                    if (!entity.hasAnimation()) continue;
                    var vulkanAnimEntity = new VulkanAnimEntity(entity, vulkanModel);
                    this.vulkanAnimEntityList.add(vulkanAnimEntity);
                    var vulkanAnimMeshList = vulkanAnimEntity.getVulkanAnimMeshList();
                    for (var vulkanMesh : vulkanModel.getVulkanMeshList()) {
                        var indexedIndirectCommand = VkDrawIndexedIndirectCommand.calloc(stack);
                        indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                        indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / GraphConstants.INT_LENGTH);
                        indexedIndirectCommand.instanceCount(1);
                        indexedIndirectCommand.vertexOffset(bufferOffset / VertexBufferStructure.SIZE_IN_BYTES);
                        indexedIndirectCommand.firstInstance(firstInstance);
                        indexedIndirectCommandList.add(indexedIndirectCommand);

                        vulkanAnimMeshList.add(new VulkanAnimEntity.VulkanAnimMesh(bufferOffset, vulkanMesh));
                        bufferOffset += vulkanMesh.verticesSize();
                        firstInstance++;
                    }
                }
            }
            this.animVerticesBuffer = new VulkanBuffer(device, bufferOffset, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

            this.numAnimIndirectCommands = indexedIndirectCommandList.size();
            if (this.numAnimIndirectCommands > 0) {
                cmd.beginRecording();

                var indirectStgBuffer = new StgBuffer(device, (long) IND_COMMAND_STRIDE * this.numAnimIndirectCommands);
                if (this.animIndirectBuffer != null) this.animIndirectBuffer.close();
                this.animIndirectBuffer = new VulkanBuffer(device, indirectStgBuffer.stgVulkanBuffer.getRequestedSize(),
                        VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
                var dataBuffer = indirectStgBuffer.getDataBuffer();
                var indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

                indexedIndirectCommandList.forEach(indCommandBuffer::put);

                if (this.animInstanceDataBuffers != null)
                    Arrays.stream(this.animInstanceDataBuffers).forEach(VulkanBuffer::close);
                this.animInstanceDataBuffers = new VulkanBuffer[numSwapChainImages];
                for (var i = 0; i < numSwapChainImages; i++)
                    this.animInstanceDataBuffers[i] = new VulkanBuffer(device,
                            (long) this.numAnimIndirectCommands * (GraphConstants.MAT4X4_SIZE + GraphConstants.INT_LENGTH),
                            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

                indirectStgBuffer.recordTransferCommand(cmd, this.animIndirectBuffer);

                cmd.endRecording();
                cmd.submitAndWait(device, queue);
                cmd.close();
                indirectStgBuffer.close();
            }
        }
    }

    private void loadAnimationData(ModelData modelData, VulkanModel vulkanModel, StgBuffer animJointMatricesStgBuffer) {
        var animationsList = modelData.getAnimationsList();
        if (!modelData.hasAnimations()) return;
        var dataBuffer = animJointMatricesStgBuffer.getDataBuffer();
        for (var animation : animationsList) {
            var vulkanAnimationData = new VulkanModel.VulkanAnimationData();
            vulkanModel.addVulkanAnimationData(vulkanAnimationData);
            var frameList = animation.frames();
            for (var frame : frameList) {
                vulkanAnimationData.addVulkanAnimationFrame(new VulkanModel.VulkanAnimationFrame(dataBuffer.position()));
                var matrices = frame.jointMatrices();
                for (var matrix : matrices) {
                    matrix.get(dataBuffer);
                    dataBuffer.position(dataBuffer.position() + GraphConstants.MAT4X4_SIZE);
                }
            }
        }
    }

    public void loadEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool,
                             Queue queue, int numSwapChainImages) {
        loadStaticEntities(vulkanModelList, scene, commandPool, queue, numSwapChainImages);
        loadAnimEntities(vulkanModelList, scene, commandPool, queue, numSwapChainImages);
    }

    public void loadInstanceData(Scene scene, List<VulkanModel> vulkanModels, int currentSwapChainIdx) {
        if (this.instanceDataBuffers != null) {
            Predicate<VulkanModel> excludeAnimatedEntitiesPredicate = VulkanModel::hasAnimations;
            loadInstanceData(scene, vulkanModels, this.instanceDataBuffers[currentSwapChainIdx], excludeAnimatedEntitiesPredicate);
        }

        if (this.animInstanceDataBuffers != null) {
            Predicate<VulkanModel> excludedStaticEntitiesPredicate = v -> !v.hasAnimations();
            loadInstanceData(scene, vulkanModels, this.animInstanceDataBuffers[currentSwapChainIdx], excludedStaticEntitiesPredicate);
        }
    }

    private void loadInstanceData(Scene scene, List<VulkanModel> vulkanModels, VulkanBuffer instanceBuffer,
                                  Predicate<VulkanModel> excludedEntitiesPredicate) {
        if (instanceBuffer == null) return;
        var mappedMemory = instanceBuffer.map();
        var dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) instanceBuffer.getRequestedSize());
        var pos = 0;
        for (var vulkanModel : vulkanModels) {
            var entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
            if (entities.isEmpty() || excludedEntitiesPredicate.test(vulkanModel)) continue;
            for (var vulkanMesh : vulkanModel.getVulkanMeshList())
                for (var entity : entities) {
                    entity.getModelMatrix().get(pos, dataBuffer);
                    pos += GraphConstants.MAT4X4_SIZE;
                    dataBuffer.putInt(pos, vulkanMesh.globalMaterialIdx());
                    pos += GraphConstants.INT_LENGTH;
                }
        }
        instanceBuffer.unMap();
    }

    private List<VulkanModel.VulkanMaterial> loadMaterials(Device device, TextureCache textureCache, StgBuffer
            materialsStgBuffer,
                                                           List<ModelData.Material> materialList, List<Texture> textureList) {
        List<VulkanModel.VulkanMaterial> vulkanMaterialList = new ArrayList<>();
        for (var material : materialList) {
            var dataBuffer = materialsStgBuffer.getDataBuffer();

            var texture = textureCache.createTexture(device, material.texturePath(), VK_FORMAT_R8G8B8A8_SRGB);
            if (texture != null) textureList.add(texture);
            var textureIdx = textureCache.getPosition(material.texturePath());

            texture = textureCache.createTexture(device, material.normalMapPath(), VK_FORMAT_R8G8B8A8_UNORM);
            if (texture != null) textureList.add(texture);
            var normalMapIdx = textureCache.getPosition(material.normalMapPath());

            texture = textureCache.createTexture(device, material.metalRoughMap(), VK_FORMAT_R8G8B8A8_UNORM);
            if (texture != null) textureList.add(texture);
            var metalRoughMapIdx = textureCache.getPosition(material.metalRoughMap());

            vulkanMaterialList.add(new VulkanModel.VulkanMaterial(dataBuffer.position() / MATERIAL_SIZE));
            material.diffuseColor().get(dataBuffer);
            dataBuffer.position(dataBuffer.position() + GraphConstants.VEC4_SIZE);
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

    private void loadMeshes(StgBuffer verticesStgBuffer, StgBuffer indicesStgBuffer, StgBuffer animWeightsStgBuffer,
                            ModelData modelData, VulkanModel vulkanModel, List<VulkanModel.VulkanMaterial> vulkanMaterialList) {
        var verticesData = verticesStgBuffer.getDataBuffer();
        var indicesData = indicesStgBuffer.getDataBuffer();
        var meshDataList = modelData.getMeshDataList();
        var meshCount = 0;
        for (var meshData : meshDataList) {
            var positions = meshData.positions();
            var normals = meshData.normals();
            var tangents = meshData.tangents();
            var biTangents = meshData.biTangents();
            var textCoords = meshData.textCoords();
            if (textCoords == null || textCoords.length == 0) textCoords = new float[(positions.length / 3) * 2];
            var indices = meshData.indices();

            var numElements = positions.length + normals.length + tangents.length + biTangents.length + textCoords.length;
            var verticesSize = numElements * GraphConstants.FLOAT_LENGTH;

            var localMaterialIdx = meshData.materialIdx();
            var globalMaterialIdx = 0;
            if (localMaterialIdx >= 0 && localMaterialIdx < vulkanMaterialList.size())
                globalMaterialIdx = vulkanMaterialList.get(localMaterialIdx).globalMaterialIdx();
            vulkanModel.addVulkanMesh(new VulkanModel.VulkanMesh(verticesSize, indices.length,
                    verticesData.position(), indicesData.position(), globalMaterialIdx, animWeightsStgBuffer.getDataBuffer().position()));

            var rows = positions.length / 3;
            for (var row = 0; row < rows; row++) {
                var startPos = row * 3;
                var startTextCoord = row * 2;
                verticesData.putFloat(positions[startPos]);
                verticesData.putFloat(positions[startPos + 1]);
                verticesData.putFloat(positions[startPos + 2]);
                verticesData.putFloat(normals[startPos]);
                verticesData.putFloat(normals[startPos + 1]);
                verticesData.putFloat(normals[startPos + 2]);
                verticesData.putFloat(tangents[startPos]);
                verticesData.putFloat(tangents[startPos + 1]);
                verticesData.putFloat(tangents[startPos + 2]);
                verticesData.putFloat(biTangents[startPos]);
                verticesData.putFloat(biTangents[startPos + 1]);
                verticesData.putFloat(biTangents[startPos + 2]);
                verticesData.putFloat(textCoords[startTextCoord]);
                verticesData.putFloat(textCoords[startTextCoord + 1]);
            }

            Arrays.stream(indices).forEach(indicesData::putInt);

            loadWeightsBuffer(modelData, animWeightsStgBuffer, meshCount);
            meshCount++;
        }
    }

    public List<VulkanModel> loadModels(List<ModelData> modelDataList, TextureCache textureCache, CommandPool
            commandPool, Queue queue) {
        List<VulkanModel> vulkanModelList = new ArrayList<>();
        List<Texture> textureList = new ArrayList<>();

        var device = commandPool.getDevice();
        var cmd = new CommandBuffer(commandPool, true, true);

        var verticesStgBuffer = new StgBuffer(device, this.verticesBuffer.getRequestedSize());
        var indicesStgBuffer = new StgBuffer(device, this.indicesBuffer.getRequestedSize());
        var materialsStgBuffer = new StgBuffer(device, this.materialsBuffer.getRequestedSize());
        var animJointMatricesStgBuffer = new StgBuffer(device, this.animJointMatricesBuffer.getRequestedSize());
        var animWeightsStgBuffer = new StgBuffer(device, this.animWeightsBuffer.getRequestedSize());

        cmd.beginRecording();

        // Load a default material
        var defaultMaterialList = Collections.singletonList(new ModelData.Material());
        loadMaterials(device, textureCache, materialsStgBuffer, defaultMaterialList, textureList);

        for (var modelData : modelDataList) {
            var vulkanModel = new VulkanModel(modelData.getModelId());
            vulkanModelList.add(vulkanModel);

            var vulkanMaterialList = loadMaterials(device, textureCache, materialsStgBuffer,
                    modelData.getMaterialList(), textureList);
            loadMeshes(verticesStgBuffer, indicesStgBuffer, animWeightsStgBuffer, modelData, vulkanModel, vulkanMaterialList);
            loadAnimationData(modelData, vulkanModel, animJointMatricesStgBuffer);
        }

        // We need to ensure that at least we have one texture
        if (textureList.isEmpty()) {
            var settings = Settings.getInstance();
            var defaultTexture = textureCache.createTexture(device, settings.getDefaultTexturePath(),
                    VK_FORMAT_R8G8B8A8_SRGB);
            textureList.add(defaultTexture);
        }

        materialsStgBuffer.recordTransferCommand(cmd, this.materialsBuffer);
        verticesStgBuffer.recordTransferCommand(cmd, this.verticesBuffer);
        indicesStgBuffer.recordTransferCommand(cmd, this.indicesBuffer);
        animJointMatricesStgBuffer.recordTransferCommand(cmd, this.animJointMatricesBuffer);
        animWeightsStgBuffer.recordTransferCommand(cmd, this.animWeightsBuffer);
        textureList.forEach(t -> t.recordTextureTransition(cmd));
        cmd.endRecording();

        cmd.submitAndWait(device, queue);
        cmd.close();

        verticesStgBuffer.close();
        indicesStgBuffer.close();
        materialsStgBuffer.close();
        animJointMatricesStgBuffer.close();
        animWeightsStgBuffer.close();
        textureList.forEach(Texture::closeStaging);

        return vulkanModelList;
    }

    private void loadStaticEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool, Queue queue, int numSwapChainImages) {
        this.numIndirectCommands = 0;
        try (var stack = MemoryStack.stackPush()) {
            var device = commandPool.getDevice();
            var cmd = new CommandBuffer(commandPool, true, true);

            List<VkDrawIndexedIndirectCommand> indexedIndirectCommandList = new ArrayList<>();
            var numInstances = 0;
            var firstInstance = 0;
            for (var vulkanModel : vulkanModelList) {
                var entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
                if (entities.isEmpty() || vulkanModel.hasAnimations()) continue;
                for (var vulkanMesh : vulkanModel.getVulkanMeshList()) {
                    var indexedIndirectCommand = VkDrawIndexedIndirectCommand.calloc(stack);
                    indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                    indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / GraphConstants.INT_LENGTH);
                    indexedIndirectCommand.instanceCount(entities.size());
                    indexedIndirectCommand.vertexOffset(vulkanMesh.verticesOffset() / VertexBufferStructure.SIZE_IN_BYTES);
                    indexedIndirectCommand.firstInstance(firstInstance);
                    indexedIndirectCommandList.add(indexedIndirectCommand);

                    this.numIndirectCommands++;
                    firstInstance += entities.size();
                    numInstances += entities.size();
                }
            }
            if (this.numIndirectCommands > 0) {
                cmd.beginRecording();

                var indirectStgBuffer = new StgBuffer(device, (long) IND_COMMAND_STRIDE * this.numIndirectCommands);
                if (this.indirectBuffer != null) this.indirectBuffer.close();
                this.indirectBuffer = new VulkanBuffer(device, indirectStgBuffer.stgVulkanBuffer.getRequestedSize(),
                        VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
                var dataBuffer = indirectStgBuffer.getDataBuffer();
                var indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

                indexedIndirectCommandList.forEach(indCommandBuffer::put);

                if (this.instanceDataBuffers != null)
                    Arrays.stream(this.instanceDataBuffers).forEach(VulkanBuffer::close);
                this.instanceDataBuffers = new VulkanBuffer[numSwapChainImages];
                for (var i = 0; i < numSwapChainImages; i++)
                    this.instanceDataBuffers[i] = new VulkanBuffer(device, (long) numInstances * (GraphConstants.MAT4X4_SIZE + GraphConstants.INT_LENGTH),
                            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

                indirectStgBuffer.recordTransferCommand(cmd, this.indirectBuffer);

                cmd.endRecording();
                cmd.submitAndWait(device, queue);
                cmd.close();
                indirectStgBuffer.close();
            }
        }
    }

    private void loadWeightsBuffer(ModelData modelData, StgBuffer animWeightsBuffer, int meshCount) {
        var animMeshDataList = modelData.getAnimMeshDataList();
        if (animMeshDataList == null || animMeshDataList.isEmpty()) return;

        var animMeshData = animMeshDataList.get(meshCount);
        var weights = animMeshData.weights();
        var boneIds = animMeshData.boneIds();

        var dataBuffer = animWeightsBuffer.getDataBuffer();

        var rows = weights.length / 4;
        for (var row = 0; row < rows; row++) {
            var startPos = row * 4;
            dataBuffer.putFloat(weights[startPos]);
            dataBuffer.putFloat(weights[startPos + 1]);
            dataBuffer.putFloat(weights[startPos + 2]);
            dataBuffer.putFloat(weights[startPos + 3]);
            dataBuffer.putFloat(boneIds[startPos]);
            dataBuffer.putFloat(boneIds[startPos + 1]);
            dataBuffer.putFloat(boneIds[startPos + 2]);
            dataBuffer.putFloat(boneIds[startPos + 3]);
        }
    }

    private static class StgBuffer {
        private final ByteBuffer dataBuffer;
        private final VulkanBuffer stgVulkanBuffer;

        public StgBuffer(Device device, long size) {
            this.stgVulkanBuffer = new VulkanBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            var mappedMemory = this.stgVulkanBuffer.map();
            this.dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) this.stgVulkanBuffer.getRequestedSize());
        }

        public void close() {
            this.stgVulkanBuffer.unMap();
            this.stgVulkanBuffer.close();
        }

        public ByteBuffer getDataBuffer() {
            return this.dataBuffer;
        }

        private void recordTransferCommand(CommandBuffer cmd, VulkanBuffer dstBuffer) {
            try (var stack = MemoryStack.stackPush()) {
                var copyRegion = VkBufferCopy.calloc(1, stack)
                        .srcOffset(0).dstOffset(0).size(this.stgVulkanBuffer.getRequestedSize());
                vkCmdCopyBuffer(cmd.getVkCommandBuffer(), this.stgVulkanBuffer.getBuffer(), dstBuffer.getBuffer(), copyRegion);
            }
        }
    }
}
