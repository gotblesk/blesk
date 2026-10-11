import { create } from 'zustand';

export const useUIStore = create((set, get) => ({
  // ═══════ NAVIGATION ═══════
  activeTab: 'chats',
  setActiveTab: (tab) => set({ activeTab: tab }),

  sidebarCollapsed: false,
  setSidebarCollapsed: (v) => set({ sidebarCollapsed: v }),
  toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),

  activeChatId: null,
  setActiveChatId: (id) => set({ activeChatId: id }),

  activeChannelId: null,
  setActiveChannelId: (id) => set({ activeChannelId: id }),

  // ═══════ PANELS ═══════
  orbitOpen: false,
  setOrbitOpen: (v) => set({ orbitOpen: v }),

  vibeOpen: false,
  setVibeOpen: (v) => set({ vibeOpen: v }),

  // ═══════ MODALS ═══════
  settingsOpen: false,
  setSettingsOpen: (v) => set({ settingsOpen: v }),

  aboutOpen: false,
  setAboutOpen: (v) => set({ aboutOpen: v }),

  feedbackOpen: false,
  setFeedbackOpen: (v) => set({ feedbackOpen: v }),

  editorOpen: false,
  setEditorOpen: (v) => set({ editorOpen: v }),

  profilePopover: { open: false, userId: null, anchorRef: null },
  setProfilePopover: (v) => set({ profilePopover: v }),

  // ═══════ SPOTLIGHT ═══════
  spotlightOpen: false,
  setSpotlightOpen: (v) => set({ spotlightOpen: v }),

  // ═══════ VOICE UI ═══════
  voiceExpanded: false,
  setVoiceExpanded: (v) => set({ voiceExpanded: v }),

  // ═══════ FOCUS MODE ═══════
  focusMode: false,
  setFocusMode: (v) => set({ focusMode: v }),
  toggleFocusMode: () => set((s) => ({ focusMode: !s.focusMode })),
}));
