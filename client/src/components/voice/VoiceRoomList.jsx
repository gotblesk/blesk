import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, X, Trash, UserPlus, Microphone, Sparkle, ArrowRight, Radio, UsersThree, Crown } from '@phosphor-icons/react';
import { useVoiceStore } from '../../store/voiceStore';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import { getCurrentUserId } from '../../utils/auth';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import './VoiceRoomList.css';

const cardV = {
  hidden: { opacity: 0, y: 20, scale: 0.95 },
  visible: (i) => ({
    opacity: 1, y: 0, scale: 1,
    transition: { delay: i * 0.07, duration: 0.5, ease: [0.16, 1, 0.3, 1] },
  }),
};

const inviteRowV = {
  hidden: { opacity: 0, x: -12 },
  visible: (i) => ({
    opacity: 1, x: 0,
    transition: { delay: 0.05 + i * 0.04, duration: 0.3, ease: [0.16, 1, 0.3, 1] },
  }),
};

export default function VoiceRoomList({ onJoinRoom }) {
  const rooms = useVoiceStore((s) => s.rooms);
  const loading = useVoiceStore((s) => s.loading);
  const loadRooms = useVoiceStore((s) => s.loadRooms);
  const createRoom = useVoiceStore((s) => s.createRoom);
  const deleteRoom = useVoiceStore((s) => s.deleteRoom);
  const inviteToRoom = useVoiceStore((s) => s.inviteToRoom);
  const kickFromRoom = useVoiceStore((s) => s.kickFromRoom);
  const currentRoomId = useVoiceStore((s) => s.currentRoomId);
  // [CRIT-5] Реактивная подписка на participants для speaking indicators
  const voiceParticipants = useVoiceStore((s) => s.participants);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newName, setNewName] = useState('');
  const [newLimit, setNewLimit] = useState(10);
  const [error, setError] = useState('');
  const [deleting, setDeleting] = useState(null);
  const [inviting, setInviting] = useState(null);
  const [friends, setFriends] = useState([]);
  const [friendsLoading, setFriendsLoading] = useState(false);

  const currentUserId = useMemo(getCurrentUserId, []);
  const deleteTimerRef = useRef(null);
  const friendsFetchedRef = useRef(0);

  useEffect(() => () => clearTimeout(deleteTimerRef.current), []);

  useEffect(() => {
    loadRooms();
    const iv = setInterval(() => {
      if (!document.hidden) loadRooms();
    }, 10000);
    return () => clearInterval(iv);
  }, [loadRooms]);

  useEffect(() => {
    if (!inviting) return;
    // Кэш друзей — не перезапрашивать чаще чем раз в 30 секунд
    if (friends.length > 0 && Date.now() - friendsFetchedRef.current < 30000) return;
    setFriendsLoading(true);
    fetch(`${API_URL}/api/friends`, { headers: { ...getAuthHeaders() }, credentials: 'include' })
      .then(r => r.json())
      .then(data => { if (Array.isArray(data)) { setFriends(data); friendsFetchedRef.current = Date.now(); } })
      .catch(err => console.error('VoiceRoomList loadFriends:', err?.message || err))
      .finally(() => setFriendsLoading(false));
  }, [inviting]); // eslint-disable-line react-hooks/exhaustive-deps

  const [isCreating, setIsCreating] = useState(false);
  const handleCreate = async () => {
    if (!newName.trim() || isCreating) return;
    setError('');
    setIsCreating(true);
    const result = await createRoom(newName.trim(), newLimit);
    setIsCreating(false);
    if (result?.room) { setNewName(''); setNewLimit(10); setShowCreateModal(false); }
    else if (result?.error) setError(result.error);
  };

  const handleDelete = async (e, roomId) => {
    e.stopPropagation();
    if (deleting === roomId) {
      const result = await deleteRoom(roomId);
      if (result?.error) setError(result.error);
      setDeleting(null);
    } else {
      setDeleting(roomId);
      deleteTimerRef.current = setTimeout(() => setDeleting(p => p === roomId ? null : p), 3000);
    }
  };

  const handleInvite = async (roomId, userId) => {
    const result = await inviteToRoom(roomId, userId);
    if (result?.error) { setError(result.error); setTimeout(() => setError(''), 3000); }
  };

  const handleKick = async (e, roomId, userId) => {
    e.stopPropagation();
    await kickFromRoom(roomId, userId);
  };

  const isInvited = (roomId, userId) => {
    const room = rooms.find(r => r.id === roomId);
    return room?.invited?.some(i => i.userId === userId) || false;
  };

  return (
    <div className="vrl">
      {/* Header */}
      <div className="vrl__head">
        <h2 className="vrl__title">Голосовые комнаты</h2>
        <motion.button
          className="vrl__create"
          onClick={() => setShowCreateModal(true)}
          whileHover={{ scale: 1.06 }}
          whileTap={{ scale: 0.9 }}
        >
          <span className="vrl__create-bg" />
          <span className="vrl__create-ray" />
          <span className="vrl__create-icon">
            <Plus size={13} weight="bold" />
          </span>
          <span className="vrl__create-text">Создать</span>
          <span className="vrl__create-ring" />
        </motion.button>
      </div>

      {error && <motion.div className="vrl__error" initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>{error}</motion.div>}

      {/* Create modal */}
      <AnimatePresence>
        {showCreateModal && (
          <CreateRoomModal
            newName={newName}
            setNewName={setNewName}
            newLimit={newLimit}
            setNewLimit={setNewLimit}
            onClose={() => setShowCreateModal(false)}
            onCreate={handleCreate}
          />
        )}
      </AnimatePresence>

      {/* Empty state */}
      {loading && rooms.length === 0 && <div className="vrl__empty">Загрузка...</div>}
      {!loading && rooms.length === 0 && (
        <motion.div className="vrl__empty" initial={{ opacity: 0 }} animate={{ opacity: 1 }} onClick={() => setShowCreateModal(true)} style={{ cursor: 'pointer' }}>
          <div className="vrl__empty-icon">
            <Radio size={28} weight="regular" />
          </div>
          <span>Нет голосовых комнат</span>
          <span className="vrl__empty-hint">Создай первую!</span>
        </motion.div>
      )}

      {/* Grid */}
      <div className="vrl__grid">
        {rooms.map((room, i) => {
          const isOwner = currentUserId && room.ownerId === currentUserId;
          const isHere = currentRoomId === room.id;
          const isLive = room.participantCount > 0;
          const participants = room.participants || [];
          const showInvitePanel = inviting === room.id;

          return (
            <motion.div
              key={room.id}
              className={`vrl__card ${isHere ? 'vrl__card--here' : ''} ${!isLive && !isHere ? 'vrl__card--empty' : ''} ${isLive ? 'vrl__card--live vrl__card--active' : ''}`}
              custom={i}
              variants={cardV}
              initial="hidden"
              animate="visible"
              onClick={() => !isHere && onJoinRoom(room.id, room.name)}
              whileHover={{ y: -5, transition: { duration: 0.2, ease: [0.16, 1, 0.3, 1] } }}
            >
              {/* Animated border gradient */}
              {isLive && !isHere && <div className="vrl__card-border-anim" />}

              {/* Accent line */}
              {(isLive || isHere) && (
                <div className="vrl__card-accent" style={{
                  background: `linear-gradient(90deg, transparent, ${isHere ? 'var(--accent)' : 'var(--online)'}, transparent)`
                }} />
              )}

              {/* Ambient glow */}
              {isLive && (
                <div className="vrl__card-glow" style={{
                  background: `radial-gradient(circle at 50% 20%, ${getAvatarColor(getAvatarHue(participants[0] || { username: room.name }))}40, transparent 70%)`
                }} />
              )}

              {/* Sound waves for live rooms */}
              {isLive && !isHere && (
                <div className="vrl__card-waves">
                  <div className="vrl__card-wave" />
                  <div className="vrl__card-wave" />
                  <div className="vrl__card-wave" />
                </div>
              )}

              {/* Top — avatars */}
              <div className="vrl__card-top">
                {participants.length > 0 ? (
                  <div className="vrl__card-avatars">
                    {participants.slice(0, 5).map((p, pi) => {
                      // [CRIT-5] Реактивный speaking через подписку
                      const voiceP = voiceParticipants[p.userId];
                      const speaking = voiceP?.speaking && !voiceP?.muted;
                      return (
                        <motion.div
                          key={p.userId}
                          className={`vrl__card-ava ${speaking ? 'vrl__card-ava--speaking' : ''}`}
                          style={{
                            background: getAvatarColor(getAvatarHue(p)),
                            zIndex: 10 - pi,
                            marginLeft: pi > 0 ? '-8px' : '0',
                          }}
                          title={p.username}
                          initial={{ scale: 0, opacity: 0 }}
                          animate={{ scale: 1, opacity: 1 }}
                          transition={{ delay: pi * 0.06, type: 'spring', stiffness: 400, damping: 15 }}
                        >
                          {p.username[0].toUpperCase()}
                        </motion.div>
                      );
                    })}
                    {participants.length > 5 && (
                      <div className="vrl__card-ava vrl__card-ava--more" style={{ marginLeft: '-8px', zIndex: 4 }}>
                        +{participants.length - 5}
                      </div>
                    )}
                  </div>
                ) : isHere ? (
                  <div className="vrl__card-you">
                    <span className="vrl__card-you-dot" />
                    Вы здесь
                  </div>
                ) : (
                  <div className="vrl__card-mic">
                    <Microphone size={20} weight="regular" />
                  </div>
                )}
              </div>

              {/* Bottom — name + status + actions */}
              <div className="vrl__card-bottom">
                <div className="vrl__card-info">
                  <div className="vrl__card-name-row">
                    <span className="vrl__card-name">{room.name}</span>
                    {isOwner && (
                      <span className="vrl__card-own" title="Моя комната">
                        <Crown size={10} weight="bold" />
                      </span>
                    )}
                  </div>
                  <div className="vrl__card-meta">
                    {isHere ? (
                      <span className="vrl__card-status-here">В комнате</span>
                    ) : isLive ? (
                      <span className="vrl__card-status-live">
                        <span className="vrl__card-dot" />
                        {/* Используем реальное количество из массива, не серверный счётчик */}
                        {participants.length || room.participantCount} {(participants.length || room.participantCount) === 1 ? 'участник' : 'в голосе'}
                      </span>
                    ) : (
                      <span className="vrl__card-status-empty vrl__empty-label">Пусто</span>
                    )}
                  </div>
                </div>

                {/* Owner actions */}
                {isOwner && (
                  <div className="vrl__card-acts" onClick={e => e.stopPropagation()}>
                    <motion.button
                      className={`vrl__card-act vrl__card-act--invite ${showInvitePanel ? 'vrl__card-act--active' : ''}`}
                      onClick={() => setInviting(showInvitePanel ? null : room.id)}
                      whileHover={{ scale: 1.1 }}
                      whileTap={{ scale: 0.85 }}
                      title="Пригласить"
                    >
                      <UserPlus size={13} weight="bold" />
                    </motion.button>
                    <motion.button
                      className={`vrl__card-act vrl__card-act--delete ${deleting === room.id ? 'vrl__card-act--danger' : ''}`}
                      onClick={e => handleDelete(e, room.id)}
                      whileHover={{ scale: 1.1 }}
                      whileTap={{ scale: 0.85 }}
                      title={deleting === room.id ? 'Нажмите ещё раз для удаления' : 'Удалить'}
                    >
                      {deleting === room.id ? (
                        <>
                          <Trash size={13} weight="bold" />
                          <span className="vrl__card-act-confirm">Удалить?</span>
                        </>
                      ) : (
                        <Trash size={13} weight="bold" />
                      )}
                    </motion.button>
                  </div>
                )}

                {/* Join / Here indicator */}
                {isHere ? (
                  <span className="vrl__card-here-label">●</span>
                ) : (
                  <motion.span className="vrl__card-join" whileHover={{ x: 3 }}>
                    <ArrowRight size={16} weight="bold" />
                  </motion.span>
                )}
              </div>

              {/* Invite panel */}
              <AnimatePresence>
                {showInvitePanel && (
                  <motion.div
                    className="vrl__invite"
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
                    onClick={e => e.stopPropagation()}
                  >
                    <div className="vrl__invite-inner">
                      {room.invited?.length > 0 && (
                        <div className="vrl__invite-sec">
                          <span className="vrl__invite-label">
                            <UsersThree size={10} weight="bold" />
                            Приглашены
                          </span>
                          {room.invited.map((inv, ii) => (
                            <motion.div key={inv.userId} className="vrl__invite-row" custom={ii} variants={inviteRowV} initial="hidden" animate="visible">
                              <div className="vrl__invite-ava" style={{ background: getAvatarColor(getAvatarHue({ username: inv.username })) }}>
                                {inv.username[0].toUpperCase()}
                              </div>
                              <span className="vrl__invite-name">{inv.username}</span>
                              <motion.button
                                className="vrl__invite-btn vrl__invite-btn--kick"
                                onClick={e => handleKick(e, room.id, inv.userId)}
                                whileHover={{ scale: 1.15 }}
                                whileTap={{ scale: 0.85 }}
                              >
                                <X size={10} weight="bold" />
                              </motion.button>
                            </motion.div>
                          ))}
                        </div>
                      )}
                      <div className="vrl__invite-sec">
                        <span className="vrl__invite-label">
                          <UserPlus size={10} weight="bold" />
                          Друзья
                        </span>
                        {friendsLoading && <span className="vrl__invite-load">Загрузка...</span>}
                        {friends.filter(f => !isInvited(room.id, f.id) && !participants.some(p => p.userId === f.id)).map((f, fi) => (
                          <motion.div key={f.id} className="vrl__invite-row" custom={fi} variants={inviteRowV} initial="hidden" animate="visible">
                            <div className="vrl__invite-ava" style={{ background: getAvatarColor(getAvatarHue(f)) }}>
                              {f.username[0].toUpperCase()}
                            </div>
                            <span className="vrl__invite-name">{f.username}</span>
                            <motion.button
                              className="vrl__invite-btn vrl__invite-btn--add"
                              onClick={() => handleInvite(room.id, f.id)}
                              whileHover={{ scale: 1.15 }}
                              whileTap={{ scale: 0.85 }}
                            >
                              <Plus size={11} weight="bold" />
                            </motion.button>
                          </motion.div>
                        ))}
                        {!friendsLoading && friends.filter(f => !isInvited(room.id, f.id) && !participants.some(p => p.userId === f.id)).length === 0 && (
                          <span className="vrl__invite-load">Все друзья уже приглашены</span>
                        )}
                      </div>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          );
        })}
      </div>
    </div>
  );
}

/* ═══════ Create Room Modal — Double Glass + 3D Tilt ═══════ */
function CreateRoomModal({ newName, setNewName, newLimit, setNewLimit, onClose, onCreate }) {
  const modalRef = useRef(null);
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const [glare, setGlare] = useState({ x: 50, y: 50 });

  const handleMouseMove = useCallback((e) => {
    const el = modalRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    const px = (e.clientX - cx) / (rect.width / 2);
    const py = (e.clientY - cy) / (rect.height / 2);
    setTilt({ x: py * -6, y: px * 6 });
    setGlare({ x: 50 + px * 30, y: 50 + py * 30 });
  }, []);

  const handleMouseLeave = useCallback(() => {
    setTilt({ x: 0, y: 0 });
    setGlare({ x: 50, y: 50 });
  }, []);

  const childV = {
    hidden: { opacity: 0, y: 12 },
    visible: (i) => ({
      opacity: 1, y: 0,
      transition: { delay: 0.15 + i * 0.06, duration: 0.4, ease: [0.16, 1, 0.3, 1] },
    }),
  };

  return (
    <>
      <motion.div className="vrl__modal-bg" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
      <div className="vrl__modal-wrap">
        <motion.div
          ref={modalRef}
          className="vrl__modal"
          initial={{ opacity: 0, scale: 0.88, y: 30, rotateX: 0, rotateY: 0 }}
          animate={{ opacity: 1, scale: 1, y: 0, rotateX: tilt.x, rotateY: tilt.y }}
          exit={{ opacity: 0, scale: 0.92, y: 20 }}
          transition={{ type: 'spring', damping: 22, stiffness: 300, rotateX: { duration: 0.1, ease: 'linear' }, rotateY: { duration: 0.1, ease: 'linear' } }}
          onMouseMove={handleMouseMove}
          onMouseLeave={handleMouseLeave}
        >
          <div className="vrl__modal-glare" style={{ background: `radial-gradient(ellipse at ${glare.x}% ${glare.y}%, var(--glare-color) 0%, transparent 60%)` }} />
          <div className="vrl__modal-inner-glass">
            <motion.div className="vrl__modal-head" custom={0} variants={childV} initial="hidden" animate="visible">
              <div className="vrl__modal-title-row">
                <Sparkle size={14} className="vrl__modal-sparkle" />
                <span className="vrl__modal-title">Новая комната</span>
              </div>
              <motion.button className="vrl__modal-close" onClick={onClose} whileHover={{ rotate: 90 }} whileTap={{ scale: 0.8 }} transition={{ duration: 0.2 }}>
                <X size={14} weight="regular" />
              </motion.button>
            </motion.div>
            <div className="vrl__modal-body">
              <motion.label className="vrl__modal-label" custom={1} variants={childV} initial="hidden" animate="visible">Название</motion.label>
              <motion.div custom={2} variants={childV} initial="hidden" animate="visible">
                <input className="vrl__modal-input" value={newName} onChange={e => setNewName(e.target.value.slice(0, 50))} onKeyDown={e => { if (e.key === 'Enter') onCreate(); if (e.key === 'Escape') onClose(); }} placeholder="Например: Музыка, Игры..." autoFocus maxLength={50} />
              </motion.div>
              <motion.label className="vrl__modal-label" custom={3} variants={childV} initial="hidden" animate="visible">Лимит участников</motion.label>
              <motion.div className="vrl__modal-limit" custom={4} variants={childV} initial="hidden" animate="visible">
                <input type="range" className="vrl__modal-range" min={2} max={25} value={newLimit} onChange={e => setNewLimit(+e.target.value)} />
                <motion.span className="vrl__modal-limit-val" key={newLimit} initial={{ scale: 1.3, opacity: 0.5 }} animate={{ scale: 1, opacity: 1 }} transition={{ duration: 0.2 }}>
                  {newLimit}
                </motion.span>
              </motion.div>
              <motion.div custom={5} variants={childV} initial="hidden" animate="visible">
                <motion.button className="vrl__modal-submit" onClick={onCreate} disabled={!newName.trim()} whileHover={newName.trim() ? { scale: 1.02, boxShadow: '0 0 24px rgba(200,255,0,0.25)' } : {}} whileTap={newName.trim() ? { scale: 0.97 } : {}}>
                  <span className="vrl__modal-submit-shimmer" />
                  Создать комнату
                </motion.button>
              </motion.div>
            </div>
          </div>
          <div className="vrl__modal-edge" />
        </motion.div>
      </div>
    </>
  );
}
