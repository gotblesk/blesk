import { memo } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import Placeholder from './Placeholder';

const fadeVariant = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.15 } },
  exit: { opacity: 0, transition: { duration: 0.1 } },
};

export default memo(function ContentArea({ children, showPlaceholder, onAction }) {
  return (
    <AnimatePresence mode="wait">
      {showPlaceholder ? (
        <motion.div key="placeholder" style={{ flex: 1, display: 'flex' }} {...fadeVariant}>
          <Placeholder onAction={onAction} />
        </motion.div>
      ) : (
        <motion.div key="content" style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }} {...fadeVariant}>
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  );
});
