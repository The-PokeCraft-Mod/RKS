#version 330 core
#pragma optionNV(strict on)

layout (location = 0) in vec3 inPos;
layout (location = 1) in vec2 inUV;
layout (location = 2) in vec3 inNormal;

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
};

void main() {
    vsOut.pos = vec3(modelMatrix * vec4(inPos, 1.0));
    vsOut.uv = inUV;
    vsOut.normal = mat3(modelMatrix) * inNormal;

    gl_Position = projectionMatrix * viewMatrix * vec4(vsOut.pos, 1.0);
}
