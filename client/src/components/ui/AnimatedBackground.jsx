import './AnimatedBackground.css';

export default function AnimatedBackground({ subtle = false }) {
  return (
    <div className={`animated-bg ${subtle ? 'animated-bg--subtle' : ''}`}>
      <div className="animated-bg__orb animated-bg__orb--green" />
      <div className="animated-bg__orb animated-bg__orb--cyan" />
      <div className="animated-bg__orb animated-bg__orb--pink" />
    </div>
  );
}
