#version 300 es

precision mediump float;

out vec4 fragColor;

in vec2 vCoordinate;

uniform sampler2D sampler;

void main() {
    fragColor = texture(sampler, vCoordinate);
}