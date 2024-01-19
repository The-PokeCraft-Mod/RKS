package com.thepokecraftmod.renderer.impl;

import com.thepokecraftmod.renderer.scene.RksEntity;

import java.util.ArrayList;
import java.util.List;

public class AnimatedEntity {

    public final RksEntity entity;
    public final List<VulkanAnimMesh> meshes;
    public final GpuModel model;

    public AnimatedEntity(RksEntity entity, GpuModel model) {
        this.entity = entity;
        this.model = model;
        this.meshes = new ArrayList<>();
    }

    public record VulkanAnimMesh(
            int meshOffset,
            GpuModel.VulkanMesh vulkanMesh
    ) {}
}
