import './TypingBubble.css';
import Avatar from '../ui/Avatar';
import { getHueStyles } from '../../utils/hueIdentity';

export default function TypingBubble({ user, hue }) {
  const hueStyles = getHueStyles(hue);

  return (
    <div className="typing-bubble" style={hueStyles}>
      <Avatar user={user} size={28} />
      <div className="typing-bubble__outer">
        <div className="typing-bubble__inner">
          <div className="typing-bubble__dot" />
          <div className="typing-bubble__dot" />
          <div className="typing-bubble__dot" />
        </div>
      </div>
    </div>
  );
}
