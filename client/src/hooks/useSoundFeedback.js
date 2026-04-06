// Global sound feedback hook for buttons and interactive elements
import { useCallback, useMemo } from 'react';
import { soundClick, soundHover } from '../utils/sounds';

let lastHoverTime = 0;

export function useSoundFeedback() {
  const onSoundHover = useCallback(() => {
    const now = performance.now();
    if (now - lastHoverTime < 50) return;
    lastHoverTime = now;
    soundHover();
  }, []);

  const onSoundClick = useCallback(() => {
    soundClick();
  }, []);

  // Memoized props object — stable reference, safe to spread onto elements
  const soundProps = useMemo(() => ({
    onMouseEnter: onSoundHover,
    onMouseDown: onSoundClick,
  }), [onSoundHover, onSoundClick]);

  return { onSoundHover, onSoundClick, soundProps };
}

export default useSoundFeedback;
