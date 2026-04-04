import { useState, useEffect } from 'react';

const FALLBACK = '1.2.1-beta';
let cached = null;

export default function useAppVersion() {
  const [version, setVersion] = useState(cached || FALLBACK);

  useEffect(() => {
    if (cached) return;
    window.blesk?.getVersion?.().then((v) => {
      cached = v;
      setVersion(v);
    }).catch(err => console.error('useAppVersion getVersion:', err?.message || err));
  }, []);

  return version;
}
