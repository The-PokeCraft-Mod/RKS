#version 330 core
#pragma optionNV(strict on)

layout (location = 0) in vec3 inPos;
layout (location = 1) in vec2 inUV;

out VS_OUT {
    out vec3 pos;
    out vec2 uv;
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

    gl_Position = projectionMatrix * viewMatrix * vec4(vsOut.pos, 1.0);
}
