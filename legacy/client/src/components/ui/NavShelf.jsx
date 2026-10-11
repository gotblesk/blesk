// NOTE: This component is not currently in use.
// The active tab navigation is handled by TopNav.
// Kept for potential future integration with the NavShelf ARIA improvements.
import { useRef, useEffect, useState, useCallback } from 'react';
import { ChatCircle, Microphone, Megaphone, UsersThree } from '@phosphor-icons/react';
import './NavShelf.css';

const TABS = [
  { id: 'chats', icon: <ChatCircle size={16} weight="regular" />, label: 'Чаты' },
  { id: 'voice', icon: <Microphone size={16} weight="regular" />, label: 'Голос' },
  { id: 'channels', icon: <Megaphone size={16} weight="regular" />, label: 'Каналы' },
  { id: 'friends', icon: <UsersThree size={16} weight="regular" />, label: 'Друзья' },
];

export default function NavShelf({ activeTab, onTabChange, unread = {} }) {
  const shelfRef = useRef(null);
  const [indicatorStyle, setIndicatorStyle] = useState({});

  const updateIndicator = useCallback(() => {
    const shelf = shelfRef.current;
    if (!shelf) return;
    const activeBtn = shelf.querySelector('[role="tab"][aria-selected="true"]');
    if (!activeBtn) return;

    const shelfRect = shelf.getBoundingClientRect();
    const btnRect = activeBtn.getBoundingClientRect();

    setIndicatorStyle({
      left: btnRect.left - shelfRect.left,
      width: btnRect.width,
      top: 4,
      height: shelfRect.height - 8,
    });
  }, []);

  useEffect(() => {
    updateIndicator();
    document.fonts.ready.then(updateIndicator);
    window.addEventListener('resize', updateIndicator);
    return () => window.removeEventListener('resize', updateIndicator);
  }, [activeTab, updateIndicator]);

  const handleTabListKeyDown = useCallback((e) => {
    const tabEls = [...e.currentTarget.querySelectorAll('[role="tab"]')];
    const current = tabEls.indexOf(e.target);
    if (current === -1) return;

    let next;
    if (e.key === 'ArrowRight') {
      next = (current + 1) % tabEls.length;
    } else if (e.key === 'ArrowLeft') {
      next = (current - 1 + tabEls.length) % tabEls.length;
    } else if (e.key === 'Home') {
      next = 0;
    } else if (e.key === 'End') {
      next = tabEls.length - 1;
    } else return;

    e.preventDefault();
    tabEls[next].focus();
    tabEls[next].click();
  }, []);

  return (
    <div className="nav-shelf__glass" ref={shelfRef} role="tablist" onKeyDown={handleTabListKeyDown}>
      <div className="nav-shelf__indicator" style={indicatorStyle} />
      {TABS.map((tab) => {
        const isActive = activeTab === tab.id;
        const count = unread[tab.id] || 0;
        return (
          <button
            key={tab.id}
            className={`nav-tab ${isActive ? 'nav-tab--active' : ''}`}
            onClick={() => onTabChange(tab.id)}
            role="tab"
            aria-selected={isActive}
            tabIndex={isActive ? 0 : -1}
          >
            <span className="nav-tab__icon">{tab.icon}</span>
            {tab.label}
            {count > 0 && (
              <span className="nav-tab__badge" aria-label={`${count} непрочитанных`}>{count}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}
