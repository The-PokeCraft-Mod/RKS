#version 330 core
#pragma optionNV(strict on)
#define BONE_COUNT 220

layout (location = 0) in vec3 inPos;
layout (location = 1) in vec2 inUV;
layout (location = 2) in vec3 inNormal;
layout (location = 3) in vec4 inJoints;
layout (location = 4) in vec4 inWeights;

out VS_OUT {
    vec3 pos;
    vec2 uv;
    vec3 normal;
} vsOut;

layout (std140) uniform SharedInfo {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

layout (std140) uniform InstanceInfo {
    mat4 modelMatrix;
    mat4 boneTransforms[BONE_COUNT];
};

mat4 getBoneTransform() {
    return boneTransforms[uint(inJoints.x)] * inWeights.x +// Bone 1 Transform (Bone Transform * Weight)
    boneTransforms[uint(inJoints.y)] * inWeights.y +// Bone 2 Transform (Bone Transform * Weight)
    boneTransforms[uint(inJoints.z)] * inWeights.z +// Bone 3 Transform (Bone Transform * Weight)
    boneTransforms[uint(inJoints.w)] * inWeights.w;// Bone 4 Transform (Bone Transform * Weight)
}

void main() {
    mat4 localMatrix = modelMatrix * getBoneTransform();
    vsOut.pos = vec3(localMatrix * vec4(inPos, 1.0));
    vsOut.uv = inUV;
    vsOut.normal = mat3(localMatrix) * inNormal;

    gl_Position = projectionMatrix * viewMatrix * vec4(vsOut.pos, 1.0);
}
