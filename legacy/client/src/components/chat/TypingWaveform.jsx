import { motion } from 'framer-motion';
import './TypingWaveform.css';

const barVariants = {
  animate: (i) => ({
    height: [4, 14, 6, 12, 4],
    transition: {
      duration: 1.2,
      repeat: Infinity,
      delay: i * 0.12,
      ease: 'easeInOut',
    },
  }),
};

export default function TypingWaveform({ username }) {
  return (
    <div className="typing-waveform">
      <div className="typing-waveform__bars">
        {[0, 1, 2, 3, 4].map((i) => (
          <motion.div
            key={i}
            className="typing-waveform__bar"
            custom={i}
            animate="animate"
            variants={barVariants}
          />
        ))}
      </div>
      {username && (
        <span className="typing-waveform__text">{username} печатает</span>
      )}
    </div>
  );
}
