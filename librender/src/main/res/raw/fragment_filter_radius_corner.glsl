#version 300 es

precision mediump float;

out vec4 fragColor;

in vec2 vCoordinate;

uniform sampler2D sampler;

uniform float radius;
uniform vec2 texSize;
uniform vec4 bgColor;

void main() {
    vec2 imgTex = vCoordinate * texSize; //将纹理坐标系转换为图片坐标系
    float centerX = texSize.x / 2.0;
    float centerY = texSize.y / 2.0;
    vec2 center = vec2(centerX, centerY);

    vec2 imgCoord = imgTex - center; // 坐标点为中心点
    if (abs(imgCoord.x) < (centerX - radius) || abs(imgCoord.y) < (centerY - radius)) { // 十字区域
        fragColor = texture(sampler, vCoordinate);
    } else if (length(abs(imgCoord) - (center - radius)) < radius) { // 四个角弧形区域
        fragColor = texture(sampler, vCoordinate);
    } else {
        fragColor = bgColor;
    }
}
