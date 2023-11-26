#version 300 es

precision mediump float;

out vec4 fragColor;

in vec2 vCoordinate;

uniform sampler2D sampler;
uniform float progress;

void main() {
    vec4 rgba = texture(sampler, vCoordinate);
    if (vCoordinate.x > progress) {
        fragColor = rgba;
    } else {
        float h = dot(rgba.xyz, vec3(0.3, 0.59, 0.21));
        fragColor = vec4(h, h, h, 1);
    }
}

