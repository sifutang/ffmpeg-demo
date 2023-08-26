precision mediump float;

uniform sampler2D sampler;
uniform float progress;

varying vec2 vCoordinate;

void main() {
    vec4 rgba = texture2D(sampler, vCoordinate);
    if (vCoordinate.x > progress) {
        gl_FragColor = rgba;
    } else {
        float h = dot(rgba.xyz, vec3(0.3, 0.59, 0.21));
        gl_FragColor = vec4(h, h, h, 1);
    }
}

