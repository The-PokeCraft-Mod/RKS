package com.thepokecraftmod.renderer.scene;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;

import java.io.File;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;
import static com.thepokecraftmod.renderer.EngineUtils.listFloatToArray;
import static com.thepokecraftmod.renderer.EngineUtils.listIntToArray;

@Deprecated
public class ModelLoader {

    public static final int MAX_JOINTS = 150;
    public static final int MAX_WEIGHTS = 4;
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

    private ModelLoader() {
        // Utility class
    }

    private static void buildFrameMatrices(AIAnimation aiAnimation, List<Bone> boneList, ModelData.AnimatedFrame animatedFrame,
                                           int frame, Node node, Matrix4f parentTransformation, Matrix4f globalInverseTransform) {
        var nodeName = node.getName();
        var aiNodeAnim = findAIAnimNode(aiAnimation, nodeName);
        var nodeTransform = node.getNodeTransformation();
        if (aiNodeAnim != null) nodeTransform = buildNodeTransformationMatrix(aiNodeAnim, frame);
        var nodeGlobalTransform = new Matrix4f(parentTransformation).mul(nodeTransform);

        var affectedBones = boneList.stream().filter(b -> b.boneName().equals(nodeName)).toList();
        for (var bone : affectedBones) {
            var boneTransform = new Matrix4f(globalInverseTransform).mul(nodeGlobalTransform).
                    mul(bone.offsetMatrix());
            animatedFrame.jointMatrices()[bone.boneId()] = boneTransform;
        }

        for (var childNode : node.getChildren())
            buildFrameMatrices(aiAnimation, boneList, animatedFrame, frame, childNode, nodeGlobalTransform,
                    globalInverseTransform);
    }

    private static Matrix4f buildNodeTransformationMatrix(AINodeAnim aiNodeAnim, int frame) {
        var positionKeys = aiNodeAnim.mPositionKeys();
        var scalingKeys = aiNodeAnim.mScalingKeys();
        var rotationKeys = aiNodeAnim.mRotationKeys();

        AIVectorKey aiVecKey;
        AIVector3D vec;

        var nodeTransform = new Matrix4f();
        var numPositions = aiNodeAnim.mNumPositionKeys();
        if (numPositions > 0) {
            aiVecKey = Objects.requireNonNull(positionKeys).get(Math.min(numPositions - 1, frame));
            vec = aiVecKey.mValue();
            nodeTransform.translate(vec.x(), vec.y(), vec.z());
        }
        var numRotations = aiNodeAnim.mNumRotationKeys();
        if (numRotations > 0) {
            var quatKey = Objects.requireNonNull(rotationKeys).get(Math.min(numRotations - 1, frame));
            var aiQuat = quatKey.mValue();
            var quat = new Quaternionf(aiQuat.x(), aiQuat.y(), aiQuat.z(), aiQuat.w());
            nodeTransform.rotate(quat);
        }
        var numScalingKeys = aiNodeAnim.mNumScalingKeys();
        if (numScalingKeys > 0) {
            aiVecKey = Objects.requireNonNull(scalingKeys).get(Math.min(numScalingKeys - 1, frame));
            vec = aiVecKey.mValue();
            nodeTransform.scale(vec.x(), vec.y(), vec.z());
        }

        return nodeTransform;
    }

    private static Node buildNodesTree(AINode aiNode, Node parentNode) {
        var nodeName = aiNode.mName().dataString();
        var node = new Node(nodeName, parentNode, toMatrix(aiNode.mTransformation()));

        var numChildren = aiNode.mNumChildren();
        var aiChildren = aiNode.mChildren();
        for (var i = 0; i < numChildren; i++) {
            var aiChildNode = AINode.create(Objects.requireNonNull(aiChildren).get(i));
            var childNode = buildNodesTree(aiChildNode, node);
            node.addChild(childNode);
        }
        return node;
    }

    private static int calcAnimationMaxFrames(AIAnimation aiAnimation) {
        var maxFrames = 0;
        var numNodeAnims = aiAnimation.mNumChannels();
        var aiChannels = aiAnimation.mChannels();
        for (var i = 0; i < numNodeAnims; i++) {
            var aiNodeAnim = AINodeAnim.create(Objects.requireNonNull(aiChannels).get(i));
            var numFrames = Math.max(Math.max(aiNodeAnim.mNumPositionKeys(), aiNodeAnim.mNumScalingKeys()),
                    aiNodeAnim.mNumRotationKeys());
            maxFrames = Math.max(maxFrames, numFrames);
        }

        return maxFrames;
    }

    private static AINodeAnim findAIAnimNode(AIAnimation aiAnimation, String nodeName) {
        AINodeAnim result = null;
        var numAnimNodes = aiAnimation.mNumChannels();
        var aiChannels = aiAnimation.mChannels();
        for (var i = 0; i < numAnimNodes; i++) {
            var aiNodeAnim = AINodeAnim.create(Objects.requireNonNull(aiChannels).get(i));
            if (nodeName.equals(aiNodeAnim.mNodeName().dataString())) {
                result = aiNodeAnim;
                break;
            }
        }
        return result;
    }

    public static ModelData loadModel(String modelId, String modelPath, String texturesDir, boolean animation) {
        return loadModel(modelId, modelPath, texturesDir, aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices |
                aiProcess_Triangulate | aiProcess_FixInfacingNormals | aiProcess_CalcTangentSpace | aiProcess_LimitBoneWeights |
                (animation ? 0 : aiProcess_PreTransformVertices));
    }

    public static ModelData loadModel(String modelId, String modelPath, String texturesDir, int flags) {
        Logger.debug("Loading model data [{}]", modelPath);
        if (!new File(modelPath).exists()) throw new RuntimeException("Model path does not exist [" + modelPath + "]");
        if (!new File(texturesDir).exists())
            throw new RuntimeException("Textures path does not exist [" + texturesDir + "]");

        var aiScene = aiImportFile(modelPath, flags);
        if (aiScene == null) {
            System.out.println(aiGetErrorString());
            throw new RuntimeException("Error loading model [modelPath: " + modelPath + ", texturesDir:" + texturesDir + "]");
        }

        var numMaterials = aiScene.mNumMaterials();
        List<ModelData.Material> materialList = new ArrayList<>();
        for (var i = 0; i < numMaterials; i++) {
            var aiMaterial = AIMaterial.create(Objects.requireNonNull(aiScene.mMaterials()).get(i));
            var material = processMaterial(aiMaterial, texturesDir);
            materialList.add(material);
        }

        var numMeshes = aiScene.mNumMeshes();
        var aiMeshes = aiScene.mMeshes();
        List<ModelData.MeshData> meshDataList = new ArrayList<>();
        for (var i = 0; i < numMeshes; i++) {
            var aiMesh = AIMesh.create(Objects.requireNonNull(aiMeshes).get(i));
            var meshData = processMesh(aiMesh);
            meshDataList.add(meshData);
        }

        var modelData = new ModelData(modelId, meshDataList, materialList);

        var numAnimations = aiScene.mNumAnimations();
        if (numAnimations > 0) {
            Logger.debug("Processing animations");
            List<Bone> boneList = new ArrayList<>();
            List<ModelData.AnimMeshData> animMeshDataList = new ArrayList<>();
            for (var i = 0; i < numMeshes; i++) {
                var aiMesh = AIMesh.create(aiMeshes.get(i));
                var animMeshData = processBones(aiMesh, boneList);
                animMeshDataList.add(animMeshData);
            }
            modelData.setAnimMeshDataList(animMeshDataList);

            var rootNode = buildNodesTree(Objects.requireNonNull(aiScene.mRootNode()), null);
            var globalInverseTransformation = toMatrix(Objects.requireNonNull(aiScene.mRootNode()).mTransformation()).invert();
            var animations = processAnimations(aiScene, boneList, rootNode, globalInverseTransformation);
            modelData.setAnimationsList(animations);
        }

        aiReleaseImport(aiScene);
        Logger.debug("Loaded model [{}]", modelPath);
        return modelData;
    }

    private static List<ModelData.Animation> processAnimations(AIScene aiScene, List<Bone> boneList,
                                                               Node rootNode, Matrix4f globalInverseTransformation) {
        List<ModelData.Animation> animations = new ArrayList<>();

        // Process all animations
        var numAnimations = aiScene.mNumAnimations();
        var aiAnimations = aiScene.mAnimations();
        for (var i = 0; i < numAnimations; i++) {
            var aiAnimation = AIAnimation.create(Objects.requireNonNull(aiAnimations).get(i));
            var maxFrames = calcAnimationMaxFrames(aiAnimation);

            List<ModelData.AnimatedFrame> frames = new ArrayList<>();
            var animation = new ModelData.Animation(aiAnimation.mName().dataString(), aiAnimation.mDuration(), frames);
            animations.add(animation);

            for (var j = 0; j < maxFrames; j++) {
                var jointMatrices = new Matrix4f[MAX_JOINTS];
                Arrays.fill(jointMatrices, IDENTITY_MATRIX);
                var animatedFrame = new ModelData.AnimatedFrame(jointMatrices);
                buildFrameMatrices(aiAnimation, boneList, animatedFrame, j, rootNode,
                        rootNode.getNodeTransformation(), globalInverseTransformation);
                frames.add(animatedFrame);
            }
        }
        return animations;
    }

    private static List<Float> processBitangents(AIMesh aiMesh, List<Float> normals) {
        List<Float> biTangents = new ArrayList<>();
        var aiBitangents = aiMesh.mBitangents();
        while (aiBitangents != null && aiBitangents.remaining() > 0) {
            var aiBitangent = aiBitangents.get();
            biTangents.add(aiBitangent.x());
            biTangents.add(aiBitangent.y());
            biTangents.add(aiBitangent.z());
        }

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (biTangents.isEmpty()) biTangents = new ArrayList<>(Collections.nCopies(normals.size(), 0.0f));
        return biTangents;
    }

    private static ModelData.AnimMeshData processBones(AIMesh aiMesh, List<Bone> boneList) {
        List<Integer> boneIds = new ArrayList<>();
        List<Float> weights = new ArrayList<>();

        Map<Integer, List<VertexWeight>> weightSet = new HashMap<>();
        var numBones = aiMesh.mNumBones();
        var aiBones = aiMesh.mBones();
        for (var i = 0; i < numBones; i++) {
            var aiBone = AIBone.create(Objects.requireNonNull(aiBones).get(i));
            var id = boneList.size();
            var bone = new Bone(id, aiBone.mName().dataString(), toMatrix(aiBone.mOffsetMatrix()));
            boneList.add(bone);
            var numWeights = aiBone.mNumWeights();
            var aiWeights = aiBone.mWeights();
            for (var j = 0; j < numWeights; j++) {
                var aiWeight = aiWeights.get(j);
                var vw = new VertexWeight(bone.boneId(), aiWeight.mVertexId(),
                        aiWeight.mWeight());
                var vertexWeightList = weightSet.computeIfAbsent(vw.vertexId(), k -> new ArrayList<>());
                vertexWeightList.add(vw);
            }
        }

        var numVertices = aiMesh.mNumVertices();
        for (var i = 0; i < numVertices; i++) {
            var vertexWeightList = weightSet.get(i);
            var size = vertexWeightList != null ? vertexWeightList.size() : 0;
            for (var j = 0; j < MAX_WEIGHTS; j++)
                if (j < size) {
                    var vw = vertexWeightList.get(j);
                    weights.add(vw.weight());
                    boneIds.add(vw.boneId());
                } else {
                    weights.add(0.0f);
                    boneIds.add(0);
                }
        }

        return new ModelData.AnimMeshData(listFloatToArray(weights), listIntToArray(boneIds));
    }

    private static List<Integer> processIndices(AIMesh aiMesh) {
        List<Integer> indices = new ArrayList<>();
        var numFaces = aiMesh.mNumFaces();
        var aiFaces = aiMesh.mFaces();
        for (var i = 0; i < numFaces; i++) {
            var aiFace = aiFaces.get(i);
            var buffer = aiFace.mIndices();
            while (buffer.remaining() > 0) indices.add(buffer.get());
        }
        return indices;
    }

    private static ModelData.Material processMaterial(AIMaterial aiMaterial, String texturesDir) {
        try (var stack = MemoryStack.stackPush()) {
            var colour = AIColor4D.create();

            var diffuse = ModelData.Material.DEFAULT_COLOR;
            var result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0,
                    colour);
            if (result == aiReturn_SUCCESS) diffuse = new Vector4f(colour.r(), colour.g(), colour.b(), colour.a());
            var aiTexturePath = AIString.calloc(stack);
            aiGetMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, 0, aiTexturePath, (IntBuffer) null,
                    null, null, null, null, null);
            var texturePath = aiTexturePath.dataString();
            if (texturePath.length() > 0) {
                texturePath = texturesDir + File.separator + new File(texturePath).getName();
                diffuse = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
            }

            var aiNormalMapPath = AIString.calloc(stack);
            Assimp.aiGetMaterialTexture(aiMaterial, aiTextureType_NORMALS, 0, aiNormalMapPath, (IntBuffer) null,
                    null, null, null, null, null);
            var normalMapPath = aiNormalMapPath.dataString();
            if (normalMapPath.length() > 0)
                normalMapPath = texturesDir + File.separator + new File(normalMapPath).getName();

            var aiMetallicRoughnessPath = AIString.calloc(stack);
            Assimp.aiGetMaterialTexture(aiMaterial, AI_MATKEY_GLTF_PBRMETALLICROUGHNESS_METALLICROUGHNESS_TEXTURE, 0, aiMetallicRoughnessPath, (IntBuffer) null,
                    null, null, null, null, null);
            var metallicRoughnessPath = aiMetallicRoughnessPath.dataString();
            if (metallicRoughnessPath.length() > 0)
                metallicRoughnessPath = texturesDir + File.separator + new File(metallicRoughnessPath).getName();

            var metallicArr = new float[]{0.0f};
            var pMax = new int[]{1};
            result = aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_METALLIC_FACTOR, aiTextureType_NONE, 0, metallicArr, pMax);
            if (result != aiReturn_SUCCESS) metallicArr[0] = 1.0f;

            var roughnessArr = new float[]{0.0f};
            result = aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_ROUGHNESS_FACTOR, aiTextureType_NONE, 0, roughnessArr, pMax);
            if (result != aiReturn_SUCCESS) roughnessArr[0] = 1.0f;

            return new ModelData.Material(texturePath, normalMapPath, metallicRoughnessPath, diffuse,
                    roughnessArr[0], metallicArr[0]);
        }
    }

    private static ModelData.MeshData processMesh(AIMesh aiMesh) {
        var vertices = processVertices(aiMesh);
        var normals = processNormals(aiMesh);
        var tangents = processTangents(aiMesh, normals);
        var biTangents = processBitangents(aiMesh, normals);
        var textCoords = processTextCoords(aiMesh);
        var indices = processIndices(aiMesh);

        // Texture coordinates may not have been populated. We need at least the empty slots
        if (textCoords.isEmpty()) {
            var numElements = (vertices.size() / 3) * 2;
            for (var i = 0; i < numElements; i++) textCoords.add(0.0f);
        }

        var materialIdx = aiMesh.mMaterialIndex();
        return new ModelData.MeshData(listFloatToArray(vertices), listFloatToArray(normals), listFloatToArray(tangents),
                listFloatToArray(biTangents), listFloatToArray(textCoords), listIntToArray(indices), materialIdx);
    }

    private static List<Float> processNormals(AIMesh aiMesh) {
        List<Float> normals = new ArrayList<>();

        var aiNormals = aiMesh.mNormals();
        while (aiNormals != null && aiNormals.remaining() > 0) {
            var aiNormal = aiNormals.get();
            normals.add(aiNormal.x());
            normals.add(aiNormal.y());
            normals.add(aiNormal.z());
        }
        return normals;
    }

    private static List<Float> processTangents(AIMesh aiMesh, List<Float> normals) {
        List<Float> tangents = new ArrayList<>();
        var aiTangents = aiMesh.mTangents();
        while (aiTangents != null && aiTangents.remaining() > 0) {
            var aiTangent = aiTangents.get();
            tangents.add(aiTangent.x());
            tangents.add(aiTangent.y());
            tangents.add(aiTangent.z());
        }

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (tangents.isEmpty()) tangents = new ArrayList<>(Collections.nCopies(normals.size(), 0.0f));
        return tangents;
    }

    private static List<Float> processTextCoords(AIMesh aiMesh) {
        List<Float> textCoords = new ArrayList<>();
        var aiTextCoords = aiMesh.mTextureCoords(0);
        var numTextCoords = aiTextCoords != null ? aiTextCoords.remaining() : 0;
        for (var i = 0; i < numTextCoords; i++) {
            var textCoord = aiTextCoords.get();
            textCoords.add(textCoord.x());
            textCoords.add(1 - textCoord.y());
        }
        return textCoords;
    }

    private static List<Float> processVertices(AIMesh aiMesh) {
        List<Float> vertices = new ArrayList<>();
        var aiVertices = aiMesh.mVertices();
        while (aiVertices.remaining() > 0) {
            var aiVertex = aiVertices.get();
            vertices.add(aiVertex.x());
            vertices.add(aiVertex.y());
            vertices.add(aiVertex.z());
        }
        return vertices;
    }

    private static Matrix4f toMatrix(AIMatrix4x4 aiMatrix4x4) {
        var result = new Matrix4f();
        result.m00(aiMatrix4x4.a1());
        result.m10(aiMatrix4x4.a2());
        result.m20(aiMatrix4x4.a3());
        result.m30(aiMatrix4x4.a4());
        result.m01(aiMatrix4x4.b1());
        result.m11(aiMatrix4x4.b2());
        result.m21(aiMatrix4x4.b3());
        result.m31(aiMatrix4x4.b4());
        result.m02(aiMatrix4x4.c1());
        result.m12(aiMatrix4x4.c2());
        result.m22(aiMatrix4x4.c3());
        result.m32(aiMatrix4x4.c4());
        result.m03(aiMatrix4x4.d1());
        result.m13(aiMatrix4x4.d2());
        result.m23(aiMatrix4x4.d3());
        result.m33(aiMatrix4x4.d4());

        return result;
    }

    private record Bone(int boneId, String boneName, Matrix4f offsetMatrix) {
    }

    private record VertexWeight(int boneId, int vertexId, float weight) {
    }
}
