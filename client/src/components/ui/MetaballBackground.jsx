import { useRef, useMemo, useEffect, lazy, Suspense } from 'react';
import { useSettingsStore } from '../../store/settingsStore';

// 3D Metaball Background — замена CSS orbs на Three.js raymarching shader
// API совместим с AnimatedBackground: subtle, ambientHue

// Храним позицию мыши глобально (без re-render)
const mousePos = { x: 0, y: 0 };

// Three.js imports are deferred until actually needed
let Canvas, useFrame, useThree, THREE;
let threeLoaded = false;

function loadThreeDeps() {
  if (threeLoaded) return Promise.resolve();
  return Promise.all([
    import('@react-three/fiber'),
    import('three'),
  ]).then(([fiber, three]) => {
    Canvas = fiber.Canvas;
    useFrame = fiber.useFrame;
    useThree = fiber.useThree;
    THREE = three;
    threeLoaded = true;
  });
}

// Lazy-loaded scene wrapper
const LazyMetaballCanvas = lazy(() =>
  loadThreeDeps().then(() => ({
    default: function MetaballCanvas({ subtle, ambientHue, contentActive, theme }) {
      const effectiveSubtle = contentActive ? true : subtle;
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
            gl={{ antialias: false, alpha: false, powerPreference: 'default' }}
            style={{ width: '100%', height: '100%' }}
          >
            <MetaballScene ambientHue={ambientHue} subtle={effectiveSubtle} contentActive={contentActive} theme={theme} />
          </Canvas>
        </div>
      );
    }
  }))
);

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
  uniform float uEventPulse;
  uniform float uIdle;
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
    // Скорость применяется на стороне JS (shaderTimeRef), здесь используем напрямую
    float t = uTime;

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
    for (int i = 0; i < 26; i++) {
      vec3 p = ro + rd * t;
      d = scene(p);
      if (d < 0.01 || t > 8.0) break;
      t += d * 0.85;
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
      // Смешать с фоном — в светлой теме шары невидимые, только glow
      float bgLum = dot(uBgColor, vec3(0.299, 0.587, 0.114));
      float blobAlpha = bgLum > 0.5 ? 0.0 : 1.0;
      col = mix(uBgColor, surface, blobAlpha);
    } else {
      // Glow around metaballs (soft falloff)
      float bgLum2 = dot(uBgColor, vec3(0.299, 0.587, 0.114));
      float glowMult = bgLum2 > 0.5 ? 0.035 : 0.15;
      float glow = exp(-d * 1.5) * glowMult * uSubtle;
      vec3 glowCol = mix(uColor1, uColor2, sin(uTime) * 0.5 + 0.5);
      col += glowCol * glow;
    }

    // Chromatic aberration at edges
    if (d < 0.15) {
      float aberration = (0.15 - d) * 0.015;
      col.r *= 1.0 + aberration * 3.0;
      col.b *= 1.0 - aberration * 2.0;
    }

    // Event-reactive pulse
    col *= 1.0 + uEventPulse * 0.4;

    // Vignette — почти отключена в светлой теме
    float bgLumV = dot(uBgColor, vec3(0.299, 0.587, 0.114));
    float vigStrength = bgLumV > 0.5 ? 0.03 : 0.8;
    float vig = 1.0 - length(vUv - 0.5) * vigStrength;
    col *= vig;

    // Dithering to prevent color banding
    vec3 dither = vec3(fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453));
    col += (dither - 0.5) / 255.0;

    gl_FragColor = vec4(col, 1.0);
  }
`;

// ═══════ FULLSCREEN QUAD ═══════
// Дефолтные цвета (иммутабельные константы)
const DEFAULT_C1_H = 0.22, DEFAULT_C1_S = 0.7, DEFAULT_C1_L = 0.45;
const DEFAULT_C2_H = 0.52, DEFAULT_C2_S = 0.6, DEFAULT_C2_L = 0.4;

function MetaballScene({ ambientHue, subtle, contentActive, theme }) {
  const meshRef = useRef();
  // Целевые цвета — per-instance refs (не module-level, чтобы избежать shared state)
  const targetColor1Ref = useRef(new THREE.Color().setHSL(DEFAULT_C1_H, DEFAULT_C1_S, DEFAULT_C1_L));
  const targetColor2Ref = useRef(new THREE.Color().setHSL(DEFAULT_C2_H, DEFAULT_C2_S, DEFAULT_C2_L));
  const lightC1Ref = useRef(new THREE.Color('#a78bfa'));
  const lightC2Ref = useRef(new THREE.Color('#818cf8'));
  const { size } = useThree();

  // Инициализируем цвета под текущую тему сразу
  const initTheme = document.documentElement.getAttribute('data-theme') || 'dark';
  const initIsLight = initTheme === 'light';

  const eventPulseRef = useRef(0);
  const idleRef = useRef(0);
  const shaderTimeRef = useRef(0);

  const uniforms = useMemo(() => ({
    uTime: { value: 0 },
    uResolution: { value: new THREE.Vector2(size.width, size.height) },
    uMouse: { value: new THREE.Vector2(0, 0) },
    uColor1: { value: initIsLight ? new THREE.Color('#a78bfa') : new THREE.Color('#c8ff00').multiplyScalar(0.5) },
    uColor2: { value: initIsLight ? new THREE.Color('#818cf8') : new THREE.Color('#00d4ff').multiplyScalar(0.4) },
    uColor3: { value: initIsLight ? new THREE.Color('#c084fc') : new THREE.Color('#ff006a').multiplyScalar(0.4) },
    // subtle: 0.35 (было 0.5) — мягче на фоне; contentActive: 0.25 — ещё тише
    uSubtle: { value: contentActive ? 0.25 : subtle ? 0.35 : 0.7 },
    uBgColor: { value: initIsLight ? new THREE.Color(0.941, 0.949, 0.961) : new THREE.Color(0.031, 0.024, 0.059) },
    uEventPulse: { value: 0 },
    uIdle: { value: 0 },
  }), []);

  // Dispose geometry and material on unmount
  useEffect(() => {
    return () => {
      if (meshRef.current) {
        meshRef.current.geometry?.dispose();
        meshRef.current.material?.dispose();
      }
    };
  }, []);

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
      targetColor1Ref.current.setHSL(ambientHue / 360, 0.7, 0.45);
      targetColor2Ref.current.setHSL(((ambientHue + 60) % 360) / 360, 0.6, 0.4);
    } else {
      targetColor1Ref.current.setHSL(DEFAULT_C1_H, DEFAULT_C1_S, DEFAULT_C1_L);
      targetColor2Ref.current.setHSL(DEFAULT_C2_H, DEFAULT_C2_S, DEFAULT_C2_L);
    }
  }, [ambientHue]);

  // Resolution update
  useEffect(() => {
    if (meshRef.current) {
      meshRef.current.material.uniforms.uResolution.value.set(size.width, size.height);
    }
  }, [size]);

  // Кэшируем subtle/contentActive в ref для плавного lerp в useFrame
  const subtleRef = useRef(subtle);
  const contentActiveRef = useRef(contentActive);
  useEffect(() => { subtleRef.current = subtle; }, [subtle]);
  useEffect(() => { contentActiveRef.current = contentActive; }, [contentActive]);

  // Кэшируем тему в ref — не читаем DOM каждый кадр
  const themeRef = useRef(document.documentElement.getAttribute('data-theme') || 'dark');
  const prevThemeRef = useRef(themeRef.current);

  useEffect(() => {
    const observer = new MutationObserver(() => {
      themeRef.current = document.documentElement.getAttribute('data-theme') || 'dark';
    });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => observer.disconnect();
  }, []);

  // Expose global pulse trigger
  useEffect(() => {
    window.__bleskBgPulse = () => { eventPulseRef.current = 1.0; };
    return () => { delete window.__bleskBgPulse; };
  }, []);

  useFrame((_, delta) => {
    if (!meshRef.current) return;
    const mat = meshRef.current.material;

    // Event pulse decay
    eventPulseRef.current *= 0.95;
    mat.uniforms.uEventPulse.value = eventPulseRef.current;

    // Idle breathing — slow down metaballs when idle
    const isIdle = document.body.classList.contains('app-idle');
    idleRef.current += ((isIdle ? 1.0 : 0.0) - idleRef.current) * 0.02;
    mat.uniforms.uIdle.value = idleRef.current;

    // Накапливаем время с учётом скорости — предотвращает резкий прыжок при idle
    // Базовая скорость снижена с 0.3 до 0.2 — метаболы движутся медленнее и мягче
    const currentSpeed = 0.2 * (1.0 - idleRef.current * 0.7);
    shaderTimeRef.current += delta * currentSpeed;
    mat.uniforms.uTime.value = shaderTimeRef.current;

    // Плавный lerp uSubtle при смене contentActive/subtle
    const targetSubtle = contentActiveRef.current ? 0.25 : subtleRef.current ? 0.35 : 0.7;
    mat.uniforms.uSubtle.value += (targetSubtle - mat.uniforms.uSubtle.value) * 0.04;

    // Переход цвета фона при смене темы
    const curThemeNow = themeRef.current;
    const targetBg = curThemeNow === 'light'
      ? { r: 0.941, g: 0.949, b: 0.961 } // #f0f2f5
      : { r: 0.031, g: 0.024, b: 0.059 }; // #08060f
    const bg = mat.uniforms.uBgColor.value;
    const bgDist = Math.abs(bg.r - targetBg.r) + Math.abs(bg.g - targetBg.g) + Math.abs(bg.b - targetBg.b);
    if (bgDist > 0.3) {
      // Большая разница = тема только что сменилась — быстрый переход
      bg.r += (targetBg.r - bg.r) * 0.5;
      bg.g += (targetBg.g - bg.g) * 0.5;
      bg.b += (targetBg.b - bg.b) * 0.5;
    } else {
      bg.r += (targetBg.r - bg.r) * 0.15;
      bg.g += (targetBg.g - bg.g) * 0.15;
      bg.b += (targetBg.b - bg.b) * 0.15;
    }
    // Smooth mouse lerp
    mat.uniforms.uMouse.value.x += (mousePos.x - mat.uniforms.uMouse.value.x) * 0.05;
    mat.uniforms.uMouse.value.y += (mousePos.y - mat.uniforms.uMouse.value.y) * 0.05;
    // Цвета metaballs — пастельные для светлой темы
    const curTheme = themeRef.current;
    const isLight = curTheme === 'light';
    const themeJustChanged = curTheme !== prevThemeRef.current;
    if (themeJustChanged) {
      prevThemeRef.current = curTheme;
      // Мгновенный переход цветов при смене темы
      if (isLight) {
        mat.uniforms.uColor1.value.set('#a78bfa');
        mat.uniforms.uColor2.value.set('#818cf8');
        mat.uniforms.uColor3.value.set('#c084fc');
      } else {
        mat.uniforms.uColor1.value.copy(targetColor1Ref.current);
        mat.uniforms.uColor2.value.copy(targetColor2Ref.current);
      }
    } else {
      // [IMP-1] Hoisted constants — не аллоцировать в useFrame
      if (isLight) {
        mat.uniforms.uColor1.value.lerp(lightC1Ref.current, 0.02);
        mat.uniforms.uColor2.value.lerp(lightC2Ref.current, 0.02);
      } else {
        mat.uniforms.uColor1.value.lerp(targetColor1Ref.current, 0.02);
        mat.uniforms.uColor2.value.lerp(targetColor2Ref.current, 0.02);
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

// CSS gradient fallback (no Three.js needed)
function MetaballFallback() {
  return (
    <div className="metaball-fallback" style={{
      position: 'fixed',
      inset: 0,
      zIndex: 0,
      background: `radial-gradient(ellipse at 30% 50%, rgba(200,255,0,0.03) 0%, transparent 50%),
                    radial-gradient(ellipse at 70% 30%, rgba(200,255,0,0.02) 0%, transparent 50%),
                    var(--bg, #0a0a0f)`,
      pointerEvents: 'none',
    }} />
  );
}

// ═══════ MAIN COMPONENT ═══════
export default function MetaballBackground({ subtle = false, ambientHue = null, contentActive = false }) {
  const animatedBg = useSettingsStore((s) => s.animatedBg);
  const theme = useSettingsStore((s) => s.theme);

  useIdleBreathing(animatedBg);

  if (!animatedBg) return null;

  // Eco-mode: skip WebGL when user prefers reduced motion or eco performance
  const reducedMotion = useSettingsStore((s) => s.reducedMotion);
  const performanceMode = useMemo(() => {
    try {
      const s = JSON.parse(localStorage.getItem('blesk-settings') || '{}');
      return s.performanceMode || 'auto';
    } catch { return 'auto'; }
  }, []);

  const useShader = performanceMode === 'high' ||
    (performanceMode === 'auto' && !reducedMotion);

  if (!useShader) {
    return <MetaballFallback />;
  }

  return (
    <Suspense fallback={<MetaballFallback />}>
      <LazyMetaballCanvas subtle={subtle} ambientHue={ambientHue} contentActive={contentActive} theme={theme} />
    </Suspense>
  );
}
