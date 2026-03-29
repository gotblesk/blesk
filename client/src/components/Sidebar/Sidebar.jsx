import { useRef, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import gsap from 'gsap';
import SidebarCollapsed from './SidebarCollapsed';
import SidebarNormal from './SidebarNormal';
import './Sidebar.css';

export default function Sidebar({ collapsed, activeTab, activeChatId, onSelectChat, onOpenChat }) {
  const sidebarRef = useRef(null);
  const prevCollapsed = useRef(collapsed);

  useEffect(() => {
    if (!sidebarRef.current) return;
    if (prevCollapsed.current === collapsed) return;
    prevCollapsed.current = collapsed;

    gsap.to(sidebarRef.current, {
      width: collapsed ? 72 : 320,
      duration: collapsed ? 0.3 : 0.4,
      ease: collapsed ? 'power2.inOut' : 'back.out(1.2)',
    });
  }, [collapsed]);

  return (
    <aside
      ref={sidebarRef}
      className={`sidebar ${collapsed ? 'sidebar--collapsed' : 'sidebar--normal'}`}
      style={{ width: collapsed ? 72 : 320 }}
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
