#version 330 core
#pragma optionNV(strict on)

in VS_OUT {
    vec3 pos;
    vec2 uv;
} fsIn;

out vec4 outColor;

uniform sampler2D diffuse;

void main() {
    outColor = texture(diffuse, fsIn.uv);
}