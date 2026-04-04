import { useRef, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import SidebarCollapsed from './SidebarCollapsed';
import SidebarNormal from './SidebarNormal';
import { useUIStore } from '../../store/uiStore';
import './Sidebar.css';

// Ширины сайдбара в двух состояниях
const WIDTH_NORMAL = 320;
const WIDTH_COLLAPSED = 72;

export default function Sidebar({ onSelectChat, onOpenChat }) {
  const collapsed = useUIStore(s => s.sidebarCollapsed);
  const activeTab = useUIStore(s => s.activeTab);
  const activeChatId = useUIStore(s => s.activeChatId);
  const sidebarRef = useRef(null);
  const prevCollapsed = useRef(collapsed);

  useEffect(() => {
    if (!sidebarRef.current) return;
    if (prevCollapsed.current === collapsed) return;
    prevCollapsed.current = collapsed;

    const targetWidth = collapsed ? WIDTH_COLLAPSED : WIDTH_NORMAL;

    // Анимация через CSS transition на width заменена на transform: translateX.
    // Сайдбар имеет фиксированную ширину = WIDTH_NORMAL, collapsed — translateX сдвигает
    // внутреннее содержимое, а обёртка сжимается через clip и flex-basis.
    // Используем CSS custom property + transition вместо GSAP width (layout-triggering).
    document.documentElement.style.setProperty('--sidebar-width', `${targetWidth}px`);
    sidebarRef.current.style.setProperty('--sidebar-target-width', `${targetWidth}px`);
  }, [collapsed]);

  return (
    <aside
      ref={sidebarRef}
      className={`sidebar ${collapsed ? 'sidebar--collapsed' : 'sidebar--normal'}`}
      style={{
        '--sidebar-target-width': `${collapsed ? WIDTH_COLLAPSED : WIDTH_NORMAL}px`,
      }}
    >
      <AnimatePresence mode="wait" initial={false}>
        {collapsed ? (
          <motion.div
            key="collapsed"
            className="sidebar__inner"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
          >
            <SidebarCollapsed
              activeTab={activeTab}
              activeChatId={activeChatId}
              onSelectChat={onSelectChat}
            />
          </motion.div>
        ) : (
          <motion.div
            key="normal"
            className="sidebar__inner"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
          >
            <SidebarNormal
              activeTab={activeTab}
              activeChatId={activeChatId}
              onSelectChat={onSelectChat}
              onOpenChat={onOpenChat}
            />
          </motion.div>
        )}
      </AnimatePresence>
    </aside>
  );
}
