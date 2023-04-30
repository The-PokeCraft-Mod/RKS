package com.thepokecraftmod.renderer.scene;

import com.thepokecraftmod.renderer.Settings;
import com.thepokecraftmod.rks.Pair;
import com.thepokecraftmod.rks.model.Mesh;
import com.thepokecraftmod.rks.model.Model;
import com.thepokecraftmod.rks.model.animation.Animation;
import com.thepokecraftmod.rks.model.material.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

import static com.thepokecraftmod.renderer.EngineUtils.listFloatToArray;
import static com.thepokecraftmod.renderer.EngineUtils.listIntToArray;

/**
 * Handles processing a model loaded from rks modelLoader into a format the renderer understands
 */
public class ModelProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelProcessor.class);
    public static final int MAX_JOINTS = 200;
    public static final int MAX_WEIGHTS = 4;

    public static ModelData loadModel(String modelId, Model model, List<Animation> animations) {
        LOGGER.info("Loading model \"{}\"", modelId);

        // Material Loading
        var materials = new ArrayList<ModelData.Material>();
        var materialArray = new Material[model.materialReferences().length];
        for (var i = 0; i < model.materialReferences().length; i++) {
            materialArray[i] = model.config().materials.get(model.materialReferences()[i]);
            var material = processMaterial(materialArray[i]);
            materials.add(material);
        }


        var meshDataList = new ArrayList<ModelData.MeshData>();
        for (var i = 0; i < model.meshes().length; i++) {
            var meshData = processMesh(model.meshes()[i]);
            meshDataList.add(meshData);
        }

        var modelData = new ModelData(modelId, meshDataList, materials);

        if (animations.size() > 0) {
            LOGGER.info("Processing animations");
            List<ModelData.AnimMeshData> animMeshDataList = new ArrayList<>();
            for (var i = 0; i < model.meshes().length; i++) {
                var animMeshData = processBones(model.meshes()[i], model);
                animMeshDataList.add(animMeshData);
            }

            modelData.setAnimMeshDataList(animMeshDataList);
            modelData.setAnimations(processAnimations(animations));
        }

        LOGGER.info("Loaded model [{}]", modelId);
        return modelData;
    }

    private static List<ModelData.PreComputedAnimation> processAnimations(List<Animation> animations) {
        var processedAnimations = new ArrayList<ModelData.PreComputedAnimation>();
        var ups = Settings.getInstance().getUpdatesPerSecond();
        for (var animation : animations) {
            var framesNeeded = ups * animation.animationDuration;
            System.out.println("ok");
        }

        return processedAnimations;
    }

    private static ModelData.AnimMeshData processBones(Mesh mesh, Model model) {
        var vertBoneWeights = new HashMap<Integer, List<Pair<Integer, Float>>>();
        var boneIds = new ArrayList<Integer>();
        var weights = new ArrayList<Float>();

        for (var bone : mesh.bones()) {
            if (bone.weights.length > MAX_WEIGHTS) throw new RuntimeException("Too many weights");
            for (var i = 0; i < bone.weights.length; i++) {
                var weight = bone.weights[i];
                var map = vertBoneWeights.computeIfAbsent(weight.vertexId, integer -> new ArrayList<>());
                map.add(new Pair<>(model.skeleton().getId(bone), weight.weight));
            }
        }

        var vertexCount = mesh.positions().size();
        for (var i = 0; i < vertexCount; i++) {
            var vertexWeights = vertBoneWeights.get(i);
            var size = vertexWeights != null ? vertexWeights.size() : 0;

            for (var j = 0; j < MAX_WEIGHTS; j++)
                if (j < size) {
                    var vertWeight = vertexWeights.get(j);
                    boneIds.add(vertWeight.a());
                    weights.add(vertWeight.b());
                } else {
                    boneIds.add(0);
                    weights.add(0.0f);
                }
        }

        return new ModelData.AnimMeshData(listFloatToArray(weights), listIntToArray(boneIds));
    }

    private static ModelData.Material processMaterial(Material material) {
        return new ModelData.Material(

        );
    }

    private static ModelData.MeshData processMesh(Mesh mesh) {
        var vertices = processVertices(mesh);
        var normals = processNormals(mesh);
        var tangents = processTangents(mesh, normals);
        var biTangents = processBiTangents(mesh, normals);
        var textCoords = processUvs(mesh);
        var indices = mesh.indices();

        // Texture coordinates may not have been populated. We need at least the empty slots
        if (textCoords.isEmpty()) {
            var numElements = (vertices.size() / 3) * 2;
            for (var i = 0; i < numElements; i++) textCoords.add(0.0f);
        }

        return new ModelData.MeshData(
                listFloatToArray(vertices),
                listFloatToArray(normals),
                listFloatToArray(tangents),
                listFloatToArray(biTangents),
                listFloatToArray(textCoords),
                listIntToArray(indices),
                mesh.material()
        );
    }

    private static List<Float> processNormals(Mesh mesh) {
        return mesh.normals().stream()
                .flatMap(vector3f -> Stream.of(vector3f.x, vector3f.y, vector3f.z))
                .toList();
    }

    private static List<Float> processTangents(Mesh mesh, List<Float> normals) {
        var tangents = mesh.tangents().stream()
                .flatMap(vector3f -> Stream.of(vector3f.x, vector3f.y, vector3f.z))
                .toList();

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (tangents.isEmpty()) tangents = new ArrayList<>(Collections.nCopies(normals.size(), 0.0f));
        return tangents;
    }

    private static List<Float> processBiTangents(Mesh mesh, List<Float> normals) {
        var biTangents = mesh.biTangents().stream()
                .flatMap(vector3f -> Stream.of(vector3f.x, vector3f.y, vector3f.z))
                .toList();

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (biTangents.isEmpty()) biTangents = new ArrayList<>(Collections.nCopies(normals.size(), 0.0f));
        return biTangents;
    }

    private static List<Float> processUvs(Mesh mesh) {
        return mesh.uvs().stream()
                .flatMap(vector3f -> Stream.of(vector3f.x, vector3f.y))
                .toList();
    }

    private static List<Float> processVertices(Mesh mesh) {
        return mesh.positions().stream()
                .flatMap(vector3f -> Stream.of(vector3f.x, vector3f.y, vector3f.z))
                .toList();
    }
}
