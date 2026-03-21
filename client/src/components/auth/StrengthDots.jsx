import { motion } from 'framer-motion';

// Оценка силы пароля → количество точек (0-5)
export function getPasswordScore(pass) {
  if (!pass || pass.length < 8) return 0;
  let score = 1;
  if (/[A-Z]/.test(pass) && /[a-z]/.test(pass)) score++;
  if (/\d/.test(pass)) score++;
  if (/[^a-zA-Z0-9]/.test(pass)) score++;
  if (pass.length >= 12) score++;
  return score;
}

const LABELS = ['', 'Слабый', 'Слабый', 'Средний', 'Сильный', 'Сильный'];

function dotColor(score) {
  if (score <= 2) return '#ef4444';
  if (score === 3) return '#eab308';
  return '#c8ff00';
}

export default function StrengthDots({ password }) {
  const score = getPasswordScore(password);
  if (!password) return null;

  const color = dotColor(score);

  return (
    <div className="strength-dots">
      {[0, 1, 2, 3, 4].map((i) => (
        <motion.div
          key={i}
          className="strength-dot"
          initial={{ scale: 0, opacity: 0 }}
          animate={{
            scale: 1,
            opacity: 1,
            backgroundColor: i < score ? color : 'rgba(255,255,255,0.06)',
            boxShadow: i < score ? `0 0 8px ${color}40` : 'none',
          }}
          transition={{ delay: i * 0.05, duration: 0.2 }}
        />
      ))}
      {score > 0 && (
        <span
          className="strength-label"
          style={{ color: `${color}80` }}
        >
          {LABELS[score]}
        </span>
      )}
    </div>
  );
}
