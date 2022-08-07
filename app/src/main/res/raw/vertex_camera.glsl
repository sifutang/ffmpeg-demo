attribute vec4 aPosition;
attribute vec2 aCoordinate;
attribute float progress;

uniform mat4 uMatrix;

varying vec2 vCoordinate;
varying float vProgress;

void main() {
  gl_Position = aPosition * uMatrix;
  vCoordinate = aCoordinate;
  vProgress = progress;
}
