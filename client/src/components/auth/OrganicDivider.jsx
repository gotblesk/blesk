import { useRef, useEffect } from 'react';
import gsap from 'gsap';
import useReducedMotion from '../../hooks/useReducedMotion';

// 3 path variants — x centered around 50, curves between 15-75
const PATHS = [
  'M50 0 C68 40 28 80 55 120 C82 160 22 200 50 240 C78 280 25 320 52 360 C79 400 24 440 50 480 C76 520 32 560 50 588',
  'M50 0 C30 45 75 85 45 125 C15 165 65 205 50 245 C35 285 70 325 48 365 C26 405 68 445 50 485 C32 525 65 565 50 588',
  'M50 0 C65 35 35 75 58 115 C81 155 25 195 48 235 C71 275 30 315 55 355 C80 395 28 435 50 475 C72 515 38 555 50 588',
];

export default function OrganicDivider() {
  const strokeRef = useRef(null);
  const fillRef = useRef(null);
  const reduced = useReducedMotion();

  useEffect(() => {
    if (reduced || !strokeRef.current || !fillRef.current) return;

    const tl = gsap.timeline({ repeat: -1 });

    PATHS.forEach((_, i) => {
      const next = PATHS[(i + 1) % PATHS.length];
      tl.to(strokeRef.current, {
        attr: { d: next },
        duration: 4,
        ease: 'power1.inOut',
      }, i * 4);
      tl.to(fillRef.current, {
        attr: { d: `${next} L-60 588 L-60 0 Z` },
        duration: 4,
        ease: 'power1.inOut',
      }, i * 4);
    });

    return () => tl.kill();
  }, [reduced]);

  return (
    <div className="auth-divider">
      <svg
        viewBox="-60 0 160 588"
        fill="none"
        preserveAspectRatio="none"
        style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}
      >
        {/* Fill left side — extends to -60 to fully cover gap */}
        <path
          ref={fillRef}
          d={`${PATHS[0]} L-60 588 L-60 0 Z`}
          fill="var(--bg, #08060f)"
        />
        {/* Animated glow stroke */}
        <path
          ref={strokeRef}
          d={PATHS[0]}
          stroke="rgba(200,255,0,0.08)"
          strokeWidth="1.5"
          fill="none"
        />
      </svg>
    </div>
  );
}
