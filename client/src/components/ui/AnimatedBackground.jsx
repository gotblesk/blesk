import { useSettingsStore } from '../../store/settingsStore';
import './AnimatedBackground.css';

export default function AnimatedBackground({ subtle = false }) {
  const animatedBg = useSettingsStore((s) => s.animatedBg);

  // Если фон выключен — не рендерим
  if (!animatedBg) return null;

  return (
    <div className={`animated-bg ${subtle ? 'animated-bg--subtle' : ''}`}>
      <div className="animated-bg__orb animated-bg__orb--green" />
      <div className="animated-bg__orb animated-bg__orb--cyan" />
      <div className="animated-bg__orb animated-bg__orb--pink" />
    </div>
  );
}
