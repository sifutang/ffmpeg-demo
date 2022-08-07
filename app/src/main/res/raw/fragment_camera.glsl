#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uTexture;

varying vec2 vCoordinate;
varying float vProgress;

void main() {
    vec4 rgba = texture2D(uTexture, vCoordinate);
    if (vCoordinate.x > vProgress) {
        gl_FragColor = rgba;
    } else {
        float h = dot(rgba.xyz, vec3(0.3, 0.59, 0.21));
        gl_FragColor = vec4(h, h, h, 1);
    }
}
