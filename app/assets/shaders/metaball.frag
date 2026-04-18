#version 460 core

#include <flutter/runtime_effect.glsl>

uniform vec2 uResolution;
uniform float uTime;
uniform vec2 uMouse;

out vec4 fragColor;

void main() {
    vec2 pos = FlutterFragCoord().xy;
    float mx = uMouse.x - 0.5;
    float my = uMouse.y - 0.5;
    float w = uResolution.x;
    float h = uResolution.y;

    // 5 orbs: position(%), radius(%), orbit amp, speed, parallax
    float field = 0.0;

    // Orb 1 — main left
    float cx1 = w * 0.25 + sin(uTime * 0.15) * w * 0.03 + mx * 12.0;
    float cy1 = h * 0.45 + cos(uTime * 0.12) * w * 0.03 + my * 12.0;
    float r1 = w * 0.22;
    float d1 = (pos.x - cx1) * (pos.x - cx1) + (pos.y - cy1) * (pos.y - cy1);
    field += (r1 * r1) / (d1 + 1.0);

    // Orb 2 — top right
    float cx2 = w * 0.70 + sin(uTime * 0.20) * w * 0.04 + mx * 8.0;
    float cy2 = h * 0.30 + cos(uTime * 0.18) * w * 0.04 + my * 8.0;
    float r2 = w * 0.16;
    float d2 = (pos.x - cx2) * (pos.x - cx2) + (pos.y - cy2) * (pos.y - cy2);
    field += (r2 * r2) / (d2 + 1.0);

    // Orb 3 — bottom left
    float cx3 = w * 0.18 + sin(uTime * 0.18) * w * 0.03 + mx * 14.0;
    float cy3 = h * 0.72 + cos(uTime * 0.22) * w * 0.03 + my * 14.0;
    float r3 = w * 0.14;
    float d3 = (pos.x - cx3) * (pos.x - cx3) + (pos.y - cy3) * (pos.y - cy3);
    field += (r3 * r3) / (d3 + 1.0);

    // Orb 4 — bottom right
    float cx4 = w * 0.78 + sin(uTime * 0.12) * w * 0.035 + mx * 6.0;
    float cy4 = h * 0.70 + cos(uTime * 0.16) * w * 0.035 + my * 6.0;
    float r4 = w * 0.16;
    float d4 = (pos.x - cx4) * (pos.x - cx4) + (pos.y - cy4) * (pos.y - cy4);
    field += (r4 * r4) / (d4 + 1.0);

    // Orb 5 — center top
    float cx5 = w * 0.48 + sin(uTime * 0.25) * w * 0.02 + mx * 10.0;
    float cy5 = h * 0.25 + cos(uTime * 0.10) * w * 0.02 + my * 10.0;
    float r5 = w * 0.12;
    float d5 = (pos.x - cx5) * (pos.x - cx5) + (pos.y - cy5) * (pos.y - cy5);
    field += (r5 * r5) / (d5 + 1.0);

    // Match CPU metaball look
    float threshold = 1.0;
    if (field > threshold) {
        float intensity = clamp((field - threshold) * 0.4, 0.0, 1.0);
        float alpha = intensity * 0.13;
        fragColor = vec4(0.784 * alpha, 1.0 * alpha, 0.0, alpha);
    } else {
        fragColor = vec4(0.0);
    }
}
