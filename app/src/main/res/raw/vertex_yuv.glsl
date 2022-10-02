attribute vec4 aPosition;
uniform mat4 uMatrix;
attribute vec2 aCoordinate;
attribute float progress;
varying vec2 vCoordinate;
varying float vProgress;
void main() {
  gl_Position = aPosition * uMatrix;
  vCoordinate = aCoordinate;
  vProgress = progress;
}
