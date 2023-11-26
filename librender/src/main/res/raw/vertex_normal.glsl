#version 300 es

uniform mat4 uMatrix;

layout (location = 0) in vec4 aPosition;
layout (location = 1) in vec2 aCoordinate;

out vec2 vCoordinate;

void main() {
  gl_Position = uMatrix * aPosition;
  vCoordinate = aCoordinate;
}
