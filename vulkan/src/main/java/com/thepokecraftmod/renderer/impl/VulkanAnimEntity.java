package com.thepokecraftmod.renderer.impl;

import com.thepokecraftmod.renderer.scene.Entity;

import java.util.ArrayList;
import java.util.List;

public class VulkanAnimEntity {
    private final Entity entity;
    private final List<VulkanAnimMesh> vulkanAnimMeshList;
    private final VulkanModel vulkanModel;

    public VulkanAnimEntity(Entity entity, VulkanModel vulkanModel) {
        this.entity = entity;
        this.vulkanModel = vulkanModel;
        this.vulkanAnimMeshList = new ArrayList<>();
    }

    public Entity getEntity() {
        return this.entity;
    }

    public List<VulkanAnimMesh> getAnimatedMeshes() {
        return this.vulkanAnimMeshList;
    }

    public VulkanModel getVulkanModel() {
        return this.vulkanModel;
    }

    public record VulkanAnimMesh(int meshOffset, VulkanModel.VulkanMesh vulkanMesh) {
    }
}
