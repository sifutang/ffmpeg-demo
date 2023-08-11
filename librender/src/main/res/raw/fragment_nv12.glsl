precision mediump float;

varying vec2 vCoordinate;
varying float vProgress;

uniform sampler2D samplerY;
uniform sampler2D samplerUV;

void main() {
    float y,u,v;
    y = texture2D(samplerY, vCoordinate).r;
    u = texture2D(samplerUV, vCoordinate).r - 0.5;
    v = texture2D(samplerUV, vCoordinate).a - 0.5;

    vec3 rgb;
    rgb.r = y + 1.403 * v;
    rgb.g = y - 0.344 * u - 0.714 * v;
    rgb.b = y + 1.770 * u;

    gl_FragColor = vec4(rgb, 1.0);
}