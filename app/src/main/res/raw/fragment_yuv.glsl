precision mediump float;
varying vec2 vCoordinate;
varying float vProgress;
uniform sampler2D samplerY;
uniform sampler2D samplerU;
uniform sampler2D samplerV;

void main() {
    float y,u,v;
    y = texture2D(samplerY, vCoordinate).r;
    u = texture2D(samplerU, vCoordinate).r - 0.5;
    v = texture2D(samplerV, vCoordinate).r - 0.5;
    vec3 rgb;
    rgb.r = y + 1.403 * v;
    rgb.g = y - 0.344 * u - 0.714 * v;
    rgb.b = y + 1.770 * u;

    if (vCoordinate.x > vProgress) {
        gl_FragColor = vec4(rgb, 1.0f);
    } else {
        float h = dot(rgb, vec3(0.3, 0.59, 0.21));
        gl_FragColor = vec4(h, h, h, 1.0f);
    }
}