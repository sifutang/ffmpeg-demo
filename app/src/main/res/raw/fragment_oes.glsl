#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES samplerOES;

varying vec2 vCoordinate;

void main() {
    gl_FragColor = texture2D(samplerOES, vCoordinate);
}
