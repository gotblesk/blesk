import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import './OnboardingIntro.css';

const STEPS = [
  {
    title: 'Твой блеск.',
    subtitle: 'Приватный мессенджер нового поколения',
    accent: true,
  },
  {
    title: 'Твои правила.',
    subtitle: 'Шифрование, голос, каналы — всё в одном',
    accent: false,
  },
  {
    title: 'Начни.',
    subtitle: 'Создай аккаунт и присоединяйся',
    accent: true,
    final: true,
  },
];

export default function OnboardingIntro({ onComplete }) {
  const [step, setStep] = useState(0);
  const [alreadyOnboarded] = useState(() => !!localStorage.getItem('blesk_onboarded'));

  // Навигация стрелками
  useEffect(() => {
    const handler = (e) => {
      if (e.key === 'ArrowRight') setStep(s => Math.min(s + 1, STEPS.length - 1));
      if (e.key === 'ArrowLeft') setStep(s => Math.max(s - 1, 0));
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  // Показываем только один раз
  if (alreadyOnboarded) return null;

  const current = STEPS[step];

  const handleNext = () => {
    if (step >= STEPS.length - 1) {
      localStorage.setItem('blesk_onboarded', '1');
      onComplete?.();
    } else {
      setStep(s => s + 1);
    }
  };

  const handleSkip = () => {
    localStorage.setItem('blesk_onboarded', '1');
    onComplete?.();
  };

  return (
    <div className="onboarding">
      {/* Dots */}
      <div className="onboarding__dots">
        {STEPS.map((_, i) => (
          <div
            key={i}
            className={`onboarding__dot${i === step ? ' onboarding__dot--active' : ''}`}
            onClick={() => setStep(i)}
            style={{ cursor: 'pointer' }}
            role="button"
            aria-label={`Шаг ${i + 1}`}
          />
        ))}
      </div>

      {/* Animated content */}
      <AnimatePresence mode="wait">
        <motion.div
          key={step}
          className="onboarding__content"
          initial={{ opacity: 0, x: 60 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -60 }}
          transition={{ type: 'spring', stiffness: 300, damping: 30 }}
        >
          <h1 className={`onboarding__title${current.accent ? ' onboarding__title--accent' : ''}`}>
            {current.title}
          </h1>
          <p className="onboarding__subtitle">{current.subtitle}</p>
        </motion.div>
      </AnimatePresence>

      {/* Actions */}
      <div className="onboarding__actions">
        {!current.final && (
          <button className="onboarding__skip" onClick={handleSkip}>
            Пропустить
          </button>
        )}
        <motion.button
          className={`onboarding__next${current.final ? ' onboarding__next--final' : ''}`}
          onClick={handleNext}
          whileTap={{ scale: 0.95 }}
          whileHover={{ scale: 1.02 }}
          transition={{ type: 'spring', stiffness: 400, damping: 25 }}
        >
          {current.final ? 'Начать' : 'Далее'}
        </motion.button>
      </div>
    </div>
  );
}
