package com.thepokecraftmod.renderer.scene;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

public class ModelData {
    private final List<Material> materialList;
    private final List<MeshData> meshDataList;
    private final String modelId;
    private List<AnimMeshData> animMeshDataList;
    private List<Animation> animationsList;

    public ModelData(String modelId, List<MeshData> meshDataList, List<Material> materialList) {
        this.modelId = modelId;
        this.meshDataList = meshDataList;
        this.materialList = materialList;
    }

    public List<AnimMeshData> getAnimMeshDataList() {
        return this.animMeshDataList;
    }

    public void setAnimMeshDataList(List<AnimMeshData> animMeshDataList) {
        this.animMeshDataList = animMeshDataList;
    }

    public List<Animation> getAnimationsList() {
        return this.animationsList;
    }

    public void setAnimationsList(List<Animation> animationsList) {
        this.animationsList = animationsList;
    }

    public List<Material> getMaterialList() {
        return this.materialList;
    }

    public List<MeshData> getMeshDataList() {
        return this.meshDataList;
    }

    public String getModelId() {
        return this.modelId;
    }

    public boolean hasAnimations() {
        return this.animationsList != null && !this.animationsList.isEmpty();
    }

    public record AnimMeshData(float[] weights, int[] boneIds) {
    }

    public record AnimatedFrame(Matrix4f[] jointMatrices) {
    }

    public record Animation(String name, double duration, List<AnimatedFrame> frames) {
    }

    public record Material(String texturePath, String normalMapPath, String metalRoughMap, Vector4f diffuseColor,
                           float roughnessFactor, float metallicFactor) {
        public static final Vector4f DEFAULT_COLOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

        public Material() {
            this(null, null, null, DEFAULT_COLOR, 0.0f, 0.0f);
        }
    }

    public record MeshData(float[] positions, float[] normals, float[] tangents, float[] biTangents,
                           float[] textCoords, int[] indices, int materialIdx) {

    }
}