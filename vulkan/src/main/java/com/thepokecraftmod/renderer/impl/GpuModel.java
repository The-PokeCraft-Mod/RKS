package com.thepokecraftmod.renderer.impl;

import java.util.ArrayList;
import java.util.List;

public class GpuModel {

    private final String modelId;
    private final List<GpuAnimationData> gpuAnimationDataList;
    private final List<VulkanMesh> vulkanMeshList;

    public GpuModel(String modelId) {
        this.modelId = modelId;
        this.vulkanMeshList = new ArrayList<>();
        this.gpuAnimationDataList = new ArrayList<>();
    }

    public void addVulkanAnimationData(GpuAnimationData gpuAnimationData) {
        this.gpuAnimationDataList.add(gpuAnimationData);
}

    public void addVulkanMesh(VulkanMesh vulkanMesh) {
        this.vulkanMeshList.add(vulkanMesh);
    }

    public String getModelId() {
        return this.modelId;
    }

    public List<GpuAnimationData> getAnimationData() {
        return this.gpuAnimationDataList;
    }

    public List<VulkanMesh> getVulkanMeshList() {
        return this.vulkanMeshList;
    }

    public boolean hasAnimations() {
        return !this.gpuAnimationDataList.isEmpty();
    }

    public static class GpuAnimationData {

        private final List<GpuAnimationFrame> frameList;

        public GpuAnimationData() {
            this.frameList = new ArrayList<>();
        }

        public void addFrame(GpuAnimationFrame gpuAnimationFrame) {
            this.frameList.add(gpuAnimationFrame);
        }

        public List<GpuAnimationFrame> getFrameList() {
            return this.frameList;
        }
    }

    public record GpuAnimationFrame(int jointOffset) {}

    public record VulkanMaterial(int globalMaterialIdx) {}

    public record VulkanMesh(
            int verticesSize,
            int numIndices,
            int verticesOffset,
            int indicesOffset,
            int globalMaterialIdx,
            int weightsOffset
    ) {}
}