#version 300 es
#extension GL_OES_EGL_image_external : require
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

out vec4 fragColor;

in vec2 vCoordinate;

uniform samplerExternalOES samplerOES;

void main() {
    fragColor = texture(samplerOES, vCoordinate);
}