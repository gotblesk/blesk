import { useCallback } from 'react';
import gsap from 'gsap';

export default function useRipple() {
  const handleRipple = useCallback((e) => {
    const btn = e.currentTarget;
    const rect = btn.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const ripple = document.createElement('div');
    ripple.style.cssText = `
      position: absolute; border-radius: 50%;
      background: radial-gradient(circle, rgba(255,255,255,0.25), transparent);
      width: 0; height: 0; left: ${x}px; top: ${y}px;
      transform: translate(-50%, -50%); pointer-events: none;
    `;
    btn.appendChild(ripple);

    gsap.to(ripple, {
      width: rect.width * 2.5,
      height: rect.width * 2.5,
      opacity: 0,
      duration: 0.5,
      ease: 'power2.out',
      onComplete: () => ripple.remove(),
    });
  }, []);

  return handleRipple;
}
