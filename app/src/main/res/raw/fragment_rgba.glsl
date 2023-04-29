precision mediump float;

uniform sampler2D sampler;

varying vec2 vCoordinate;
varying float vProgress;

void main() {
    gl_FragColor = texture2D(sampler, vCoordinate);
}
