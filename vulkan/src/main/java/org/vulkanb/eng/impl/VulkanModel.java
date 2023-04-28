package org.vulkanb.eng.impl;

import java.util.ArrayList;
import java.util.List;

public class VulkanModel {

    private final String modelId;
    private final List<VulkanAnimationData> vulkanAnimationDataList;
    private final List<VulkanMesh> vulkanMeshList;

    public VulkanModel(String modelId) {
        this.modelId = modelId;
        this.vulkanMeshList = new ArrayList<>();
        this.vulkanAnimationDataList = new ArrayList<>();
    }

    public void addVulkanAnimationData(VulkanAnimationData vulkanAnimationData) {
        this.vulkanAnimationDataList.add(vulkanAnimationData);
    }

    public void addVulkanMesh(VulkanMesh vulkanMesh) {
        this.vulkanMeshList.add(vulkanMesh);
    }

    public String getModelId() {
        return this.modelId;
    }

    public List<VulkanAnimationData> getVulkanAnimationDataList() {
        return this.vulkanAnimationDataList;
    }

    public List<VulkanMesh> getVulkanMeshList() {
        return this.vulkanMeshList;
    }

    public boolean hasAnimations() {
        return !this.vulkanAnimationDataList.isEmpty();
    }

    public static class VulkanAnimationData {
        private final List<VulkanAnimationFrame> vulkanAnimationFrameList;

        public VulkanAnimationData() {
            this.vulkanAnimationFrameList = new ArrayList<>();
        }

        public void addVulkanAnimationFrame(VulkanAnimationFrame vulkanAnimationFrame) {
            this.vulkanAnimationFrameList.add(vulkanAnimationFrame);
        }

        public List<VulkanAnimationFrame> getVulkanAnimationFrameList() {
            return this.vulkanAnimationFrameList;
        }
    }

    public record VulkanAnimationFrame(int jointMatricesOffset) {

    }

    public record VulkanMaterial(int globalMaterialIdx) {
    }

    public record VulkanMesh(int verticesSize, int numIndices, int verticesOffset, int indicesOffset,
                             int globalMaterialIdx, int weightsOffset) {
    }
}