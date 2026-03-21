import { useRef, useMemo, useEffect } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import * as THREE from 'three';
import { useSettingsStore } from '../../store/settingsStore';

// 3D Metaball Background — замена CSS orbs на Three.js raymarching shader
// API совместим с AnimatedBackground: subtle, ambientHue

// Храним позицию мыши глобально (без re-render)
const mousePos = { x: 0, y: 0 };

// ═══════ RAYMARCHING SHADER ═══════
const vertexShader = `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position, 1.0);
  }
`;

const fragmentShader = `
  precision highp float;
  uniform float uTime;
  uniform vec2 uResolution;
  uniform vec2 uMouse;
  uniform vec3 uColor1;
  uniform vec3 uColor2;
  uniform vec3 uColor3;
  uniform float uSubtle;
  varying vec2 vUv;

  // Smooth min for metaball blending
  float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
  }

  float sdSphere(vec3 p, vec3 center, float r) {
    return length(p - center) - r;
  }

  float scene(vec3 p) {
    float t = uTime * 0.3;

    // 5 metaball spheres floating
    vec3 c1 = vec3(
      sin(t * 0.7) * 1.5 + uMouse.x * 0.3,
      cos(t * 0.5) * 1.0 + uMouse.y * 0.2,
      sin(t * 0.3) * 0.5
    );
    vec3 c2 = vec3(
      cos(t * 0.6) * 1.8 - uMouse.x * 0.2,
      sin(t * 0.8) * 1.2,
      cos(t * 0.4) * 0.4
    );
    vec3 c3 = vec3(
      sin(t * 0.4 + 2.0) * 1.3,
      cos(t * 0.6 + 1.0) * 1.5 - uMouse.y * 0.15,
      sin(t * 0.5 + 3.0) * 0.3
    );
    vec3 c4 = vec3(
      cos(t * 0.5 + 4.0) * 2.0,
      sin(t * 0.3 + 2.0) * 0.8,
      cos(t * 0.6 + 1.0) * 0.5
    );
    vec3 c5 = vec3(
      sin(t * 0.35 + 1.0) * 1.0,
      cos(t * 0.45 + 3.0) * 1.3,
      sin(t * 0.55) * 0.4
    );

    float d = sdSphere(p, c1, 0.8);
    d = smin(d, sdSphere(p, c2, 0.7), 0.6);
    d = smin(d, sdSphere(p, c3, 0.65), 0.6);
    d = smin(d, sdSphere(p, c4, 0.55), 0.5);
    d = smin(d, sdSphere(p, c5, 0.6), 0.55);

    return d;
  }

  void main() {
    vec2 uv = (gl_FragCoord.xy - uResolution * 0.5) / uResolution.y;

    // Ray setup
    vec3 ro = vec3(0.0, 0.0, 4.5);
    vec3 rd = normalize(vec3(uv, -1.5));

    // Raymarch
    float t = 0.0;
    float d;
    for (int i = 0; i < 40; i++) {
      vec3 p = ro + rd * t;
      d = scene(p);
      if (d < 0.01 || t > 10.0) break;
      t += d * 0.8;
    }

    vec3 col = vec3(0.031, 0.024, 0.059); // #08060f background

    if (d < 0.05) {
      // Hit — compute normal for coloring
      vec3 p = ro + rd * t;
      vec2 e = vec2(0.01, 0.0);
      vec3 n = normalize(vec3(
        scene(p + e.xyy) - scene(p - e.xyy),
        scene(p + e.yxy) - scene(p - e.yxy),
        scene(p + e.yyx) - scene(p - e.yyx)
      ));

      // Color based on position
      float blend1 = smoothstep(-2.0, 2.0, p.x);
      float blend2 = smoothstep(-2.0, 2.0, p.y);
      vec3 blobCol = mix(
        mix(uColor1, uColor2, blend1),
        uColor3,
        blend2 * 0.5
      );

      // Simple lighting
      float diff = max(dot(n, normalize(vec3(1.0, 1.0, 1.0))), 0.0);
      float rim = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);

      col = blobCol * (diff * 0.4 + 0.1) + rim * blobCol * 0.3;
      col *= uSubtle;
    } else {
      // Glow around metaballs (soft falloff)
      float glow = exp(-d * 1.5) * 0.15 * uSubtle;
      float time = uTime * 0.3;
      vec3 glowCol = mix(uColor1, uColor2, sin(time) * 0.5 + 0.5);
      col += glowCol * glow;
    }

    // Vignette
    float vig = 1.0 - length(vUv - 0.5) * 0.8;
    col *= vig;

    // Dithering to prevent color banding
    vec3 dither = vec3(fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453));
    col += (dither - 0.5) / 255.0;

    gl_FragColor = vec4(col, 1.0);
  }
`;

// ═══════ FULLSCREEN QUAD ═══════
function MetaballScene({ ambientHue, subtle }) {
  const meshRef = useRef();
  const { size } = useThree();

  const uniforms = useMemo(() => ({
    uTime: { value: 0 },
    uResolution: { value: new THREE.Vector2(size.width, size.height) },
    uMouse: { value: new THREE.Vector2(0, 0) },
    uColor1: { value: new THREE.Color('#c8ff00').multiplyScalar(0.5) }, // green
    uColor2: { value: new THREE.Color('#00d4ff').multiplyScalar(0.4) }, // cyan
    uColor3: { value: new THREE.Color('#ff006a').multiplyScalar(0.4) }, // pink
    uSubtle: { value: subtle ? 0.5 : 0.8 },
  }), []);

  // Mouse tracking
  useEffect(() => {
    function onMove(e) {
      mousePos.x = (e.clientX / window.innerWidth) * 2 - 1;
      mousePos.y = -(e.clientY / window.innerHeight) * 2 + 1;
    }
    window.addEventListener('mousemove', onMove, { passive: true });
    return () => window.removeEventListener('mousemove', onMove);
  }, []);

  // Update ambient hue
  useEffect(() => {
    if (ambientHue !== null && ambientHue !== undefined && meshRef.current) {
      const mat = meshRef.current.material;
      const hsl1 = { h: ambientHue / 360, s: 0.7, l: 0.45 };
      const hsl2 = { h: ((ambientHue + 60) % 360) / 360, s: 0.6, l: 0.4 };
      mat.uniforms.uColor1.value.setHSL(hsl1.h, hsl1.s, hsl1.l);
      mat.uniforms.uColor2.value.setHSL(hsl2.h, hsl2.s, hsl2.l);
    }
  }, [ambientHue]);

  // Resolution update
  useEffect(() => {
    if (meshRef.current) {
      meshRef.current.material.uniforms.uResolution.value.set(size.width, size.height);
    }
  }, [size]);

  useFrame((_, delta) => {
    if (!meshRef.current) return;
    const mat = meshRef.current.material;
    mat.uniforms.uTime.value += delta;
    // Smooth mouse lerp
    mat.uniforms.uMouse.value.x += (mousePos.x - mat.uniforms.uMouse.value.x) * 0.05;
    mat.uniforms.uMouse.value.y += (mousePos.y - mat.uniforms.uMouse.value.y) * 0.05;
  });

  return (
    <mesh ref={meshRef}>
      <planeGeometry args={[2, 2]} />
      <shaderMaterial
        vertexShader={vertexShader}
        fragmentShader={fragmentShader}
        uniforms={uniforms}
      />
    </mesh>
  );
}

// ═══════ IDLE BREATHING (same as AnimatedBackground) ═══════
function useIdleBreathing(enabled) {
  useEffect(() => {
    if (!enabled) return;
    let timer;
    function resetIdle() {
      clearTimeout(timer);
      document.body.classList.remove('app-idle');
      timer = setTimeout(() => document.body.classList.add('app-idle'), 30000);
    }
    window.addEventListener('mousemove', resetIdle, { passive: true });
    window.addEventListener('keydown', resetIdle, { passive: true });
    resetIdle();
    return () => {
      clearTimeout(timer);
      document.body.classList.remove('app-idle');
      window.removeEventListener('mousemove', resetIdle);
      window.removeEventListener('keydown', resetIdle);
    };
  }, [enabled]);
}

// ═══════ MAIN COMPONENT ═══════
export default function MetaballBackground({ subtle = false, ambientHue = null }) {
  const animatedBg = useSettingsStore((s) => s.animatedBg);

  useIdleBreathing(animatedBg);

  if (!animatedBg) return null;

  return (
    <div style={{
      position: 'absolute',
      inset: 0,
      zIndex: 0,
      pointerEvents: 'none',
      overflow: 'hidden',
    }}>
      <Canvas
        dpr={[1, 1.5]}
        gl={{ antialias: false, alpha: false, powerPreference: 'high-performance' }}
        style={{ width: '100%', height: '100%' }}
      >
        <MetaballScene ambientHue={ambientHue} subtle={subtle} />
      </Canvas>
    </div>
  );
}
