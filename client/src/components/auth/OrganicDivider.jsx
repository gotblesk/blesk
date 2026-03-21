import { useRef, useEffect } from 'react';
import gsap from 'gsap';
import useReducedMotion from '../../hooks/useReducedMotion';

// 3 path variants for morphing
const PATHS = [
  'M40 0 C58 40 18 80 45 120 C72 160 12 200 40 240 C68 280 15 320 42 360 C69 400 14 440 40 480 C66 520 22 560 40 588',
  'M40 0 C20 45 65 85 35 125 C5 165 55 205 40 245 C25 285 60 325 38 365 C16 405 58 445 40 485 C22 525 55 565 40 588',
  'M40 0 C55 35 25 75 48 115 C71 155 15 195 38 235 C61 275 20 315 45 355 C70 395 18 435 40 475 C62 515 28 555 40 588',
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
        attr: { d: `${next} L0 588 L0 0 Z` },
        duration: 4,
        ease: 'power1.inOut',
      }, i * 4);
    });

    return () => tl.kill();
  }, [reduced]);

  return (
    <div className="auth-divider">
      <svg
        width="80"
        height="100%"
        viewBox="0 0 80 588"
        fill="none"
        preserveAspectRatio="none"
        style={{ height: '100%', width: '100%' }}
      >
        {/* Fill left side to cover background gap */}
        <path
          ref={fillRef}
          d={`${PATHS[0]} L0 588 L0 0 Z`}
          fill="var(--bg, #08060f)"
        />
        {/* Animated stroke line */}
        <path
          ref={strokeRef}
          d={PATHS[0]}
          stroke="rgba(200,255,0,0.06)"
          strokeWidth="1"
          fill="none"
        />
      </svg>
    </div>
  );
}
