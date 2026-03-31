// Global sound feedback hook for buttons and interactive elements
import { useCallback } from 'react';
import { soundClick, soundHover } from '../utils/sounds';

export function useSoundFeedback() {
  const onSoundHover = useCallback(() => {
    soundHover();
  }, []);

  const onSoundClick = useCallback(() => {
    soundClick();
  }, []);

  // Returns props to spread onto interactive elements
  const soundProps = useCallback(() => ({
    onMouseEnter: onSoundHover,
    onMouseDown: onSoundClick,
  }), [onSoundHover, onSoundClick]);

  return { onSoundHover, onSoundClick, soundProps };
}

export default useSoundFeedback;
