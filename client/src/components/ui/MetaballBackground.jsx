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
  uniform vec3 uBgColor;
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

    vec3 col = uBgColor;

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

      vec3 surface = blobCol * (diff * 0.4 + 0.1) + rim * blobCol * 0.3;
      surface *= uSubtle;
      // Смешать с фоном — в светлой теме шары полупрозрачные
      float bgLum = dot(uBgColor, vec3(0.299, 0.587, 0.114));
      float blobAlpha = bgLum > 0.5 ? 0.3 : 1.0;
      col = mix(uBgColor, surface, blobAlpha);
    } else {
      // Glow around metaballs (soft falloff)
      float bgLum2 = dot(uBgColor, vec3(0.299, 0.587, 0.114));
      float glowMult = bgLum2 > 0.5 ? 0.05 : 0.15; // Слабее в светлой теме
      float glow = exp(-d * 1.5) * glowMult * uSubtle;
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
// Целевые цвета для плавной интерполяции (мутабельные)
const targetColor1 = new THREE.Color().setHSL(0.22, 0.7, 0.45);
const targetColor2 = new THREE.Color().setHSL(0.52, 0.6, 0.4);
// Дефолтные цвета (иммутабельные копии)
const DEFAULT_C1_H = 0.22, DEFAULT_C1_S = 0.7, DEFAULT_C1_L = 0.45;
const DEFAULT_C2_H = 0.52, DEFAULT_C2_S = 0.6, DEFAULT_C2_L = 0.4;

function MetaballScene({ ambientHue, subtle, theme }) {
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
    uBgColor: { value: new THREE.Color(0.031, 0.024, 0.059) },
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

  // Update target colors for smooth lerp (not instant)
  useEffect(() => {
    if (ambientHue !== null && ambientHue !== undefined) {
      targetColor1.setHSL(ambientHue / 360, 0.7, 0.45);
      targetColor2.setHSL(((ambientHue + 60) % 360) / 360, 0.6, 0.4);
    } else {
      targetColor1.setHSL(DEFAULT_C1_H, DEFAULT_C1_S, DEFAULT_C1_L);
      targetColor2.setHSL(DEFAULT_C2_H, DEFAULT_C2_S, DEFAULT_C2_L);
    }
  }, [ambientHue]);

  // Resolution update
  useEffect(() => {
    if (meshRef.current) {
      meshRef.current.material.uniforms.uResolution.value.set(size.width, size.height);
    }
  }, [size]);

  // Обновить фон при смене темы — читаем напрямую из DOM
  const getTheme = () => document.documentElement.getAttribute('data-theme') || 'dark';
  const prevThemeRef = useRef(getTheme());

  useFrame((_, delta) => {
    if (!meshRef.current) return;
    const mat = meshRef.current.material;
    mat.uniforms.uTime.value += delta;
    // Плавный переход цвета фона при смене темы
    const targetBg = getTheme() === 'light'
      ? { r: 0.929, g: 0.918, b: 0.953 } // #edeaf3
      : { r: 0.031, g: 0.024, b: 0.059 }; // #08060f
    const bg = mat.uniforms.uBgColor.value;
    bg.r += (targetBg.r - bg.r) * 0.15;
    bg.g += (targetBg.g - bg.g) * 0.15;
    bg.b += (targetBg.b - bg.b) * 0.15;
    // Smooth mouse lerp
    mat.uniforms.uMouse.value.x += (mousePos.x - mat.uniforms.uMouse.value.x) * 0.05;
    mat.uniforms.uMouse.value.y += (mousePos.y - mat.uniforms.uMouse.value.y) * 0.05;
    // Цвета metaballs — пастельные для светлой темы
    const curTheme = getTheme();
    const isLight = curTheme === 'light';
    const themeJustChanged = curTheme !== prevThemeRef.current;
    if (themeJustChanged) {
      prevThemeRef.current = curTheme;
      // Мгновенный переход цветов при смене темы
      if (isLight) {
        mat.uniforms.uColor1.value.set('#b8e060');
        mat.uniforms.uColor2.value.set('#7ec8e3');
        mat.uniforms.uColor3.value.set('#d4a0e0');
      } else {
        mat.uniforms.uColor1.value.copy(targetColor1);
        mat.uniforms.uColor2.value.copy(targetColor2);
      }
    } else {
      // Обычный медленный lerp между ambient hue цветами
      if (isLight) {
        mat.uniforms.uColor1.value.lerp(new THREE.Color('#b8e060'), 0.02);
        mat.uniforms.uColor2.value.lerp(new THREE.Color('#7ec8e3'), 0.02);
      } else {
        mat.uniforms.uColor1.value.lerp(targetColor1, 0.02);
        mat.uniforms.uColor2.value.lerp(targetColor2, 0.02);
      }
    }
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
  const theme = useSettingsStore((s) => s.theme);

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
        <MetaballScene ambientHue={ambientHue} subtle={subtle} theme={theme} />
      </Canvas>
    </div>
  );
}
