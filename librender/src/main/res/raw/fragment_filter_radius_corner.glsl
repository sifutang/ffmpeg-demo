precision mediump float;

uniform sampler2D sampler;

uniform float radius;
uniform vec2 texSize;
uniform vec4 bgColor;

varying vec2 vCoordinate;

void main() {
    vec2 imgTex = vCoordinate * texSize; //将纹理坐标系转换为图片坐标系
    float centerX = texSize.x / 2.0;
    float centerY = texSize.y / 2.0;
    vec2 center = vec2(centerX, centerY);

    // 圆形
//    if (distance(imgTex, center) < radius) {
//        gl_FragColor = texture2D(sampler, vCoordinate);
//    } else {
//        gl_FragColor = bgColor;
//    }

    vec2 imgCoord = imgTex - center; // 坐标点为中心点
    if (abs(imgCoord.x) < (centerX - radius) || abs(imgCoord.y) < (centerY - radius)) { // 十字区域
        gl_FragColor = texture2D(sampler, vCoordinate);
    } else if (length(abs(imgCoord) - (center - radius)) < radius) { // 四个角弧形区域
        gl_FragColor = texture2D(sampler, vCoordinate);
    } else {
        gl_FragColor = bgColor;
    }
}
