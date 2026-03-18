import { useRef, useEffect, useState } from 'react';
import { MessageCircle, Mic, Megaphone, Users } from 'lucide-react';
import './NavShelf.css';

const TABS = [
  { id: 'chats', icon: <MessageCircle size={16} strokeWidth={1.5} />, label: 'Чаты' },
  { id: 'voice', icon: <Mic size={16} strokeWidth={1.5} />, label: 'Голос' },
  { id: 'channels', icon: <Megaphone size={16} strokeWidth={1.5} />, label: 'Каналы' },
  { id: 'friends', icon: <Users size={16} strokeWidth={1.5} />, label: 'Друзья' },
];

export default function NavShelf({ activeTab, onTabChange, unread = {} }) {
  const shelfRef = useRef(null);
  const [indicatorStyle, setIndicatorStyle] = useState({});

  useEffect(() => {
    updateIndicator();
    window.addEventListener('resize', updateIndicator);
    return () => window.removeEventListener('resize', updateIndicator);
  }, [activeTab]);

  function updateIndicator() {
    const shelf = shelfRef.current;
    if (!shelf) return;
    const activeBtn = shelf.querySelector('.nav-tab--active');
    if (!activeBtn) return;

    const shelfRect = shelf.getBoundingClientRect();
    const btnRect = activeBtn.getBoundingClientRect();

    setIndicatorStyle({
      left: btnRect.left - shelfRect.left,
      width: btnRect.width,
      top: 4,
      height: shelfRect.height - 8,
    });
  }

  return (
    <div className="nav-shelf__glass" ref={shelfRef}>
      <div className="nav-shelf__indicator" style={indicatorStyle} />
      {TABS.map((tab) => (
        <button
          key={tab.id}
          className={`nav-tab ${activeTab === tab.id ? 'nav-tab--active' : ''}`}
          onClick={() => onTabChange(tab.id)}
        >
          <span className="nav-tab__icon">{tab.icon}</span>
          {tab.label}
          {unread[tab.id] > 0 && (
            <span className="nav-tab__badge">{unread[tab.id]}</span>
          )}
        </button>
      ))}
    </div>
  );
}
