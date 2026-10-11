import './TypingBubble.css';
import Avatar from '../ui/Avatar';
import TypingWaveform from './TypingWaveform';
import { getHueStyles } from '../../utils/hueIdentity';

export default function TypingBubble({ user, hue, username }) {
  const hueStyles = getHueStyles(hue);

  return (
    <div className="typing-bubble" style={hueStyles}>
      <Avatar user={user} size={28} />
      <div className="typing-bubble__outer">
        <div className="typing-bubble__inner">
          <TypingWaveform username={username} />
        </div>
      </div>
    </div>
  );
}
