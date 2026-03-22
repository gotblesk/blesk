import { useState, useEffect, useRef, useCallback } from 'react';
import { useSettingsStore } from '../../store/settingsStore';
import { useChatStore } from '../../store/chatStore';
import { useVoiceStore } from '../../store/voiceStore';
import { Search, Users, Mic, Radio, Settings, X } from 'lucide-react';
import Avatar from './Avatar';
import './DynamicIsland.css';

// ═══════ DYNAMIC ISLAND — навигация blesk ═══════
export default function DynamicIsland({
  user,
  onNavigate,
  onOpenProfile,
  onOpenSearch,
  onStatusChange,
}) {
  const [expanded, setExpanded] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [statusMenuOpen, setStatusMenuOpen] = useState(false);
  const [statusMenuClosing, setStatusMenuClosing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const islandRef = useRef(null);
  const wrapRef = useRef(null);
  const searchInputRef = useRef(null);
  const isHovered = useRef(false);

  const chats = useChatStore(s => s.chats);
  const onlineUsers = useChatStore(s => s.onlineUsers);
  const voiceRoomId = useVoiceStore(s => s.currentRoomId);

  // Unread count
  const totalUnread = chats.reduce((sum, c) => sum + (c.unreadCount || 0), 0);

  // ═══════ MAGNETIC PROXIMITY ═══════
  useEffect(() => {
    const RANGE = 200, STRENGTH = 8;

    function onMove(e) {
      if (isHovered.current || !wrapRef.current) return;
      const rect = wrapRef.current.getBoundingClientRect();
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height / 2;
      const dx = e.clientX - cx;
      const dy = e.clientY - cy;
      const dist = Math.hypot(dx, dy);

      if (dist < RANGE && dist > 30) {
        const force = 1 - dist / RANGE;
        const mx = (dx / dist) * force * STRENGTH;
        const my = (dy / dist) * force * STRENGTH;
        wrapRef.current.style.transition = 'transform .1s ease-out';
        wrapRef.current.style.transform = `translateX(calc(-50% + ${mx}px)) translateY(${my}px)`;
      } else {
        wrapRef.current.style.transition = 'transform .3s ease-out';
        wrapRef.current.style.transform = 'translateX(-50%)';
      }
    }

    window.addEventListener('mousemove', onMove, { passive: true });
    return () => window.removeEventListener('mousemove', onMove);
  }, []);

  // Reset magnetic on hover
  const handleMouseEnter = useCallback(() => {
    isHovered.current = true;
    if (wrapRef.current) {
      wrapRef.current.style.transition = 'transform .4s cubic-bezier(.34,1.56,.64,1)';
      wrapRef.current.style.transform = 'translateX(-50%)';
    }
  }, []);

  const handleMouseLeave = useCallback(() => {
    isHovered.current = false;
    if (wrapRef.current) {
      wrapRef.current.style.transition = 'transform .4s cubic-bezier(.34,1.56,.64,1)';
      wrapRef.current.style.transform = 'translateX(-50%)';
    }
  }, []);

  // ═══════ SEARCH ═══════
  const toggleSearch = useCallback((e) => {
    if (e) e.stopPropagation();
    setSearchOpen(prev => {
      if (!prev) {
        setTimeout(() => searchInputRef.current?.focus(), 350);
      } else {
        setSearchQuery('');
      }
      return !prev;
    });
  }, []);

  // Search submit
  const handleSearchSubmit = useCallback(() => {
    if (searchQuery.trim() && onOpenSearch) {
      onOpenSearch(searchQuery.trim());
      setSearchOpen(false);
      setSearchQuery('');
    }
  }, [searchQuery, onOpenSearch]);

  // Status — fallback to 'online' if undefined/null/empty
  const rawStatus = user?.status;
  const userStatus = (rawStatus === 'dnd' || rawStatus === 'invisible') ? rawStatus : 'online';

  const closeStatusMenu = useCallback(() => {
    setStatusMenuClosing(true);
    setTimeout(() => {
      setStatusMenuOpen(false);
      setStatusMenuClosing(false);
    }, 250);
  }, []);

  // Close menus on outside click
  useEffect(() => {
    function onClick(e) {
      if (statusMenuOpen && !statusMenuClosing && !e.target.closest('.island-status-menu') && !e.target.closest('.island__status')) {
        closeStatusMenu();
      }
      if (searchOpen && !e.target.closest('.island-search')) {
        setSearchOpen(false);
        setSearchQuery('');
      }
    }
    document.addEventListener('click', onClick);
    return () => document.removeEventListener('click', onClick);
  }, [statusMenuOpen, statusMenuClosing, searchOpen, closeStatusMenu]);

  const handleStatusSelect = (status) => {
    closeStatusMenu();
    if (onStatusChange) onStatusChange(status);
  };

  // Nav click with ripple
  const handleNav = (e, section) => {
    // Ripple
    const btn = e.currentTarget;
    const rect = btn.getBoundingClientRect();
    const ripple = document.createElement('div');
    ripple.className = 'island-ripple';
    ripple.style.width = ripple.style.height = Math.max(rect.width, rect.height) + 'px';
    ripple.style.left = (e.clientX - rect.left - Math.max(rect.width, rect.height) / 2) + 'px';
    ripple.style.top = (e.clientY - rect.top - Math.max(rect.width, rect.height) / 2) + 'px';
    btn.appendChild(ripple);
    setTimeout(() => ripple.remove(), 450);

    if (onNavigate) onNavigate(section);
  };

  const username = user?.username || 'blesk';
  const displayName = username.length > 8 ? username.slice(0, 8) + '...' : username;

  return (
    <>
      <div className="island-row" ref={wrapRef}>
        {/* ═══════ MAIN ISLAND ═══════ */}
        <div
          className="island"
          ref={islandRef}
          onMouseEnter={handleMouseEnter}
          onMouseLeave={handleMouseLeave}
        >
          {/* Shimmer */}
          <div className="island-shimmer" />

          {/* Collapsed */}
          <div className="island__collapsed">
            <div className={`island__dot island__dot--${userStatus}`} />
            <span className="island__nick">{displayName}</span>
          </div>

          {/* Expanded */}
          <div className="island__expanded">
            {/* Status dot */}
            <button
              className="island__status"
              onClick={(e) => { e.stopPropagation(); if (statusMenuOpen) closeStatusMenu(); else setStatusMenuOpen(true); }}
              title="Сменить статус"
            >
              <div className={`island__status-dot island__status-dot--${userStatus}`} />
            </button>

            <div className="island__sep" />

            {/* Nav buttons */}
            <div className="island__nav">
              <button className="island__btn" onClick={(e) => handleNav(e, 'friends')}>
                <div className="island__btn-icon"><Users size={14} /></div>
                <span className="island__btn-label">Друзья</span>
              </button>

              <button className="island__btn" onClick={(e) => handleNav(e, 'voice')}>
                <div className="island__btn-icon island__btn-icon--voice"><Mic size={14} /></div>
                <span className="island__btn-label">Голос</span>
                {voiceRoomId && <span className="island__btn-badge">●</span>}
              </button>

              <button className="island__btn" onClick={(e) => handleNav(e, 'channels')}>
                <div className="island__btn-icon island__btn-icon--channels"><Radio size={14} /></div>
                <span className="island__btn-label">Каналы</span>
              </button>

              <button className="island__btn" onClick={(e) => handleNav(e, 'settings')}>
                <div className="island__btn-icon island__btn-icon--settings"><Settings size={14} /></div>
              </button>
            </div>

            <div className="island__sep" />

            {/* Profile */}
            <button
              className="island__profile"
              onClick={(e) => { e.stopPropagation(); if (onOpenProfile) onOpenProfile(); }}
              title="Мой профиль"
            >
              <Avatar username={username} avatarUrl={user?.avatar} size={30} />
            </button>
          </div>
        </div>

        {/* ═══════ SEARCH BUTTON ═══════ */}
        <div
          className={`island-search ${searchOpen ? 'island-search--open' : ''}`}
          onClick={!searchOpen ? toggleSearch : undefined}
        >
          <div className="island-search__icon">
            <Search size={13} />
          </div>
          <div className="island-search__expanded">
            <input
              ref={searchInputRef}
              className="island-search__input"
              type="text"
              placeholder="Поиск..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onClick={(e) => e.stopPropagation()}
              onKeyDown={(e) => e.key === 'Enter' && handleSearchSubmit()}
            />
            <button className="island-search__go" onClick={handleSearchSubmit}>→</button>
          </div>
        </div>
      </div>

      {/* ═══════ STATUS MENU ═══════ */}
      {(statusMenuOpen || statusMenuClosing) && (
        <div className={`island-status-menu ${statusMenuClosing ? 'island-status-menu--closing' : ''}`}>
          <button className="island-status-opt" onClick={() => handleStatusSelect('online')}>
            <div className="island-status-opt__dot island-status-opt__dot--online" />
            В сети
          </button>
          <button className="island-status-opt" onClick={() => handleStatusSelect('dnd')}>
            <div className="island-status-opt__dot island-status-opt__dot--dnd" />
            Не беспокоить
          </button>
          <button className="island-status-opt" onClick={() => handleStatusSelect('invisible')}>
            <div className="island-status-opt__dot island-status-opt__dot--invisible" />
            Невидимый
          </button>
        </div>
      )}
    </>
  );
}
