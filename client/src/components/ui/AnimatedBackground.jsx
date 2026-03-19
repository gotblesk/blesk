import { useEffect, useRef } from 'react';
import { useSettingsStore } from '../../store/settingsStore';
import './AnimatedBackground.css';

export default function AnimatedBackground({ subtle = false, ambientHue = null }) {
  const animatedBg = useSettingsStore((s) => s.animatedBg);
  const containerRef = useRef(null);

  // Parallax от курсора мыши
  useEffect(() => {
    if (!animatedBg) return;
    const el = containerRef.current;
    if (!el) return;

    const orbs = el.querySelectorAll('.animated-bg__orb');
    const depths = [0.03, 0.02, 0.015];

    let rafId;
    function onMouseMove(e) {
      const cx = window.innerWidth / 2;
      const cy = window.innerHeight / 2;
      const dx = (e.clientX - cx) / cx;
      const dy = (e.clientY - cy) / cy;

      cancelAnimationFrame(rafId);
      rafId = requestAnimationFrame(() => {
        orbs.forEach((orb, i) => {
          const d = depths[i] || 0.02;
          orb.style.setProperty('--parallax-x', `${dx * d * 100}px`);
          orb.style.setProperty('--parallax-y', `${dy * d * 100}px`);
        });
      });
    }

    window.addEventListener('mousemove', onMouseMove, { passive: true });
    return () => {
      window.removeEventListener('mousemove', onMouseMove);
      cancelAnimationFrame(rafId);
    };
  }, [animatedBg]);

  // Ambient Mode: orbs плавно адаптируют цвет к hue собеседника
  useEffect(() => {
    if (!animatedBg || ambientHue === null) return;
    const el = containerRef.current;
    if (!el) return;

    const primary = el.querySelector('.animated-bg__orb--green');
    const secondary = el.querySelector('.animated-bg__orb--cyan');
    if (primary) primary.style.background = `hsl(${ambientHue}, 70%, 55%)`;
    if (secondary) secondary.style.background = `hsl(${(ambientHue + 60) % 360}, 60%, 50%)`;

    return () => {
      // Восстановить дефолтные цвета при размонтировании
      if (primary) primary.style.background = '';
      if (secondary) secondary.style.background = '';
    };
  }, [animatedBg, ambientHue]);

  // Idle breathing: 30 секунд без движения мыши → панели "дышат"
  useEffect(() => {
    if (!animatedBg) return;
    let timer;
    function resetIdle() {
      clearTimeout(timer);
      document.body.classList.remove('app-idle');
      timer = setTimeout(() => {
        document.body.classList.add('app-idle');
      }, 30000);
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
  }, [animatedBg]);

  if (!animatedBg) return null;

  return (
    <div ref={containerRef} className={`animated-bg ${subtle ? 'animated-bg--subtle' : ''}`}>
      <div className="animated-bg__orb animated-bg__orb--green" />
      <div className="animated-bg__orb animated-bg__orb--cyan" />
      <div className="animated-bg__orb animated-bg__orb--pink" />
    </div>
  );
}
