import { AnimatePresence, motion } from 'framer-motion';
import SidebarCollapsed from './SidebarCollapsed';
import SidebarNormal from './SidebarNormal';
import './Sidebar.css';

export default function Sidebar({ collapsed, activeTab, activeChatId, onSelectChat, onOpenChat }) {
  return (
    <aside className={`sidebar ${collapsed ? 'sidebar--collapsed' : 'sidebar--normal'}`}>
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
