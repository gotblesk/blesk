import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';

// ═══════════════════════════════════════════════════════════════
// SETTINGS SCREEN — sections nav (220px) + content (flex)
// ═══════════════════════════════════════════════════════════════

enum SettingsSection { account, appearance, notifications, chats, calls, privacy, devices, storage, language, help, about }

class SettingsScreen extends StatefulWidget {
  final VoidCallback onBack;
  const SettingsScreen({super.key, required this.onBack});
  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  SettingsSection _section = SettingsSection.account;

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      // Sections nav
      Container(
        width: 220,
        color: const Color(0xFF0c0c0e),
        child: Column(children: [
          // Header
          Container(
            height: 52,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(children: [
              _BackBtn(onTap: widget.onBack),
              const SizedBox(width: 10),
              Text('настройки', style: TextStyle(fontFamily: 'Nekst',
                fontSize: rf(context, 14), fontWeight: FontWeight.w600, color: BColors.textPrimary)),
            ]),
          ),
          const SizedBox(height: 8),
          // Section items
          Expanded(child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Column(children: [
              for (var i = 0; i < SettingsSection.values.length; i++)
                _SectionItem(
                  section: SettingsSection.values[i],
                  active: _section == SettingsSection.values[i],
                  onTap: () => setState(() => _section = SettingsSection.values[i]),
                ).animate(delay: Duration(milliseconds: 30 * i))
                    .fadeIn(duration: 200.ms).slideX(begin: -0.05, duration: 200.ms),
            ]),
          )),
        ]),
      ),
      Container(width: 1, color: BColors.borderLow),
      // Content
      Expanded(
        child: AnimatedSwitcher(
          duration: const Duration(milliseconds: 200),
          transitionBuilder: (child, anim) => FadeTransition(opacity: anim, child: child),
          child: SingleChildScrollView(
            key: ValueKey(_section),
            padding: EdgeInsets.symmetric(horizontal: rs(context, 32), vertical: rs(context, 24)),
            child: _buildSection(context),
          ),
        ),
      ),
    ]);
  }

  Widget _buildSection(BuildContext context) {
    switch (_section) {
      case SettingsSection.account: return _AccountSection();
      case SettingsSection.appearance: return _AppearanceSection();
      case SettingsSection.notifications: return _NotificationsSection();
      case SettingsSection.chats: return _ChatsSection();
      case SettingsSection.calls: return _CallsSection();
      case SettingsSection.privacy: return _PrivacySection();
      case SettingsSection.devices: return _DevicesSection();
      case SettingsSection.storage: return _StorageSection();
      case SettingsSection.language: return _LanguageSection();
      case SettingsSection.help: return _HelpSection();
      case SettingsSection.about: return _AboutSection();
    }
  }
}

// ─── Section Nav Item ─────────────────────────────────────────

const _sectionMeta = <SettingsSection, (IconData, String)>{
  SettingsSection.account: (SolarIconsOutline.user, 'аккаунт'),
  SettingsSection.appearance: (SolarIconsOutline.paletteRound, 'внешний вид'),
  SettingsSection.notifications: (SolarIconsOutline.bell, 'уведомления'),
  SettingsSection.chats: (SolarIconsOutline.chatRound, 'чаты'),
  SettingsSection.calls: (SolarIconsOutline.phone, 'звонки'),
  SettingsSection.privacy: (SolarIconsOutline.lock, 'приватность'),
  SettingsSection.devices: (SolarIconsOutline.devices, 'устройства'),
  SettingsSection.storage: (SolarIconsOutline.database, 'хранилище'),
  SettingsSection.language: (SolarIconsOutline.global, 'язык'),
  SettingsSection.help: (SolarIconsOutline.questionCircle, 'помощь'),
  SettingsSection.about: (SolarIconsOutline.infoCircle, 'о blesk'),
};

class _SectionItem extends StatefulWidget {
  final SettingsSection section;
  final bool active;
  final VoidCallback onTap;
  const _SectionItem({required this.section, required this.active, required this.onTap});
  @override
  State<_SectionItem> createState() => _SectionItemState();
}

class _SectionItemState extends State<_SectionItem> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final meta = _sectionMeta[widget.section]!;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          height: 38,
          margin: const EdgeInsets.only(bottom: 2),
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            color: widget.active ? Colors.white.withValues(alpha: 0.06) : _h ? Colors.white.withValues(alpha: 0.03) : Colors.transparent,
          ),
          child: Row(children: [
            Icon(meta.$1, size: 16, color: widget.active ? BColors.textPrimary : BColors.textMuted),
            const SizedBox(width: 12),
            Text(meta.$2, style: TextStyle(fontFamily: 'Onest', fontSize: 13,
              color: widget.active ? BColors.textPrimary : BColors.textSecondary)),
          ]),
        ),
      ),
    );
  }
}

class _BackBtn extends StatefulWidget {
  final VoidCallback onTap;
  const _BackBtn({required this.onTap});
  @override
  State<_BackBtn> createState() => _BackBtnState();
}

class _BackBtnState extends State<_BackBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: Icon(SolarIconsOutline.altArrowLeft, size: 14,
          color: _h ? BColors.textSecondary : BColors.textMuted),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// SHARED SETTING WIDGETS
// ═══════════════════════════════════════════════════════════════

class _SectionTitle extends StatelessWidget {
  final String title;
  const _SectionTitle(this.title);
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 24),
    child: Text(title, style: TextStyle(fontFamily: 'Nekst', fontSize: rf(context, 24),
      fontWeight: FontWeight.w700, color: BColors.textPrimary)),
  ).animate().fadeIn(duration: 300.ms).slideY(begin: 0.05);
}

class _GroupLabel extends StatelessWidget {
  final String label;
  const _GroupLabel(this.label);
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 28, bottom: 12),
    child: Text(label.toUpperCase(), style: const TextStyle(fontFamily: 'Onest', fontSize: 11,
      fontWeight: FontWeight.w600, color: BColors.textMuted, letterSpacing: 1)),
  );
}

class _SettingRow extends StatefulWidget {
  final String label;
  final String? value;
  final String? action;
  final bool destructive;
  final VoidCallback? onTap;
  const _SettingRow({required this.label, this.value, this.action, this.destructive = false, this.onTap});
  @override
  State<_SettingRow> createState() => _SettingRowState();
}

class _SettingRowState extends State<_SettingRow> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final color = widget.destructive ? const Color(0xCCFF5C5C) : BColors.textPrimary;
    return MouseRegion(
      cursor: widget.onTap != null ? SystemMouseCursors.click : SystemMouseCursors.basic,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: Container(
          height: 52,
          padding: const EdgeInsets.symmetric(horizontal: 16),
          decoration: BoxDecoration(
            border: Border(bottom: BorderSide(color: BColors.borderLow)),
            color: _h ? Colors.white.withValues(alpha: 0.02) : Colors.transparent,
          ),
          child: Row(children: [
            Text(widget.label, style: TextStyle(fontFamily: 'Onest', fontSize: 14, color: color)),
            const Spacer(),
            if (widget.value != null) Text(widget.value!, style: const TextStyle(fontFamily: 'Onest', fontSize: 13, color: BColors.textSecondary)),
            if (widget.action != null) ...[
              const SizedBox(width: 8),
              Text(widget.action!, style: TextStyle(fontFamily: 'Onest', fontSize: 12,
                color: widget.destructive ? const Color(0xCCFF5C5C) : BColors.accent.withValues(alpha: 0.7))),
            ],
          ]),
        ),
      ),
    );
  }
}

class _ToggleRow2 extends StatefulWidget {
  final String label;
  final bool value;
  const _ToggleRow2({required this.label, required this.value});
  @override
  State<_ToggleRow2> createState() => _ToggleRow2State();
}

class _ToggleRow2State extends State<_ToggleRow2> {
  late bool _on = widget.value;
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => setState(() => _on = !_on),
      child: Container(
        height: 52,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        decoration: BoxDecoration(border: Border(bottom: BorderSide(color: BColors.borderLow))),
        child: Row(children: [
          Text(widget.label, style: const TextStyle(fontFamily: 'Onest', fontSize: 14, color: BColors.textPrimary)),
          const Spacer(),
          _Toggle2(on: _on),
        ]),
      ),
    );
  }
}

class _Toggle2 extends StatelessWidget {
  final bool on;
  const _Toggle2({required this.on});
  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200), curve: Curves.easeOutCubic,
      width: 36, height: 20,
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(10),
        color: on ? BColors.accent : Colors.white.withValues(alpha: 0.06)),
      child: AnimatedAlign(
        duration: const Duration(milliseconds: 200), curve: Curves.easeOutCubic,
        alignment: on ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(width: 16, height: 16, margin: const EdgeInsets.symmetric(horizontal: 2),
          decoration: BoxDecoration(shape: BoxShape.circle,
            color: on ? BColors.bg : Colors.white.withValues(alpha: 0.3))),
      ),
    );
  }
}

class _DropdownRow extends StatelessWidget {
  final String label;
  final String value;
  const _DropdownRow({required this.label, required this.value});
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 52, padding: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(border: Border(bottom: BorderSide(color: BColors.borderLow))),
      child: Row(children: [
        Text(label, style: const TextStyle(fontFamily: 'Onest', fontSize: 14, color: BColors.textPrimary)),
        const Spacer(),
        Text(value, style: const TextStyle(fontFamily: 'Onest', fontSize: 13, color: BColors.textSecondary)),
        const SizedBox(width: 4),
        const Icon(SolarIconsOutline.altArrowRight, size: 16, color: BColors.textMuted),
      ]),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// SECTIONS
// ═══════════════════════════════════════════════════════════════

class _AccountSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('аккаунт'),
    const _GroupLabel('основное'),
    _SettingRow(label: 'email', value: 'g@blesk.fun', action: 'изменить'),
    _SettingRow(label: 'телефон', value: 'не указан', action: 'добавить'),
    _SettingRow(label: 'пароль', value: '••••••••••', action: 'изменить'),
    const _GroupLabel('безопасность'),
    const _ToggleRow2(label: 'двухфакторная аутентификация', value: false),
    _SettingRow(label: 'активные сессии', value: '3 устройства', action: '→', onTap: () {}),
    _SettingRow(label: 'история входов', action: '→', onTap: () {}),
    const _GroupLabel('удаление'),
    _SettingRow(label: 'экспорт данных', action: 'скачать', onTap: () {}),
    _SettingRow(label: 'удалить аккаунт', action: 'удалить', destructive: true, onTap: () {}),
  ]);
}

class _AppearanceSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('внешний вид'),
    const _GroupLabel('тема'),
    Row(children: [
      for (final (name, color) in [('тёмная', const Color(0xFF0a0a0a)), ('светлая', const Color(0xFFf0f2f5)), ('системная', const Color(0xFF1a1a24))])
        Expanded(child: Container(
          height: 64, margin: const EdgeInsets.only(right: 8),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(10),
            color: color, border: Border.all(color: name == 'тёмная' ? BColors.accent : BColors.borderLow, width: name == 'тёмная' ? 2 : 1)),
          child: Center(child: Text(name, style: TextStyle(fontFamily: 'Onest', fontSize: 11,
            color: name == 'светлая' ? Colors.black54 : BColors.textSecondary))),
        )),
    ]).animate().fadeIn(duration: 300.ms),
    const _GroupLabel('акцентный цвет'),
    Row(children: [
      for (final c in [0xFFC8FF00, 0xFF3b82f6, 0xFFa78bfa, 0xFFf472b6, 0xFFfb923c, 0xFFef4444, 0xFF22c55e])
        Container(
          width: 32, height: 32, margin: const EdgeInsets.only(right: 10),
          decoration: BoxDecoration(shape: BoxShape.circle, color: Color(c),
            border: c == 0xFFC8FF00 ? Border.all(color: Colors.white.withValues(alpha: 0.3), width: 2) : null),
        ),
    ]).animate(delay: 100.ms).fadeIn(duration: 300.ms),
    const SizedBox(height: 4),
    const Text('лайм accent', style: TextStyle(fontFamily: 'Onest', fontSize: 12, color: BColors.textMuted)),
    const _GroupLabel('анимации'),
    const _ToggleRow2(label: 'анимации интерфейса', value: true),
    const _ToggleRow2(label: 'liquid glass эффекты', value: true),
    const _ToggleRow2(label: 'metaball фон', value: true),
  ]);
}

class _NotificationsSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('уведомления'),
    const _GroupLabel('общее'),
    const _ToggleRow2(label: 'показывать уведомления', value: true),
    const _ToggleRow2(label: 'звук уведомлений', value: true),
    const _ToggleRow2(label: 'показывать превью', value: true),
    const _ToggleRow2(label: 'показывать имя отправителя', value: true),
    const _GroupLabel('тихие часы'),
    const _ToggleRow2(label: 'тихие часы', value: false),
    const _DropdownRow(label: 'с', value: '22:00'),
    const _DropdownRow(label: 'до', value: '08:00'),
  ]);
}

class _ChatsSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('чаты'),
    const _GroupLabel('сообщения'),
    const _ToggleRow2(label: 'показывать аватары', value: true),
    const _ToggleRow2(label: 'группировать сообщения', value: true),
    const _ToggleRow2(label: 'показывать секунды', value: false),
    const _GroupLabel('ввод'),
    const _ToggleRow2(label: 'Enter для отправки', value: true),
    const _ToggleRow2(label: 'проверка орфографии', value: true),
    const _ToggleRow2(label: 'автоисправление', value: false),
    const _GroupLabel('медиа'),
    const _ToggleRow2(label: 'автозагрузка фото', value: true),
    const _ToggleRow2(label: 'автозагрузка видео', value: false),
    const _ToggleRow2(label: 'автозагрузка файлов', value: false),
    const _GroupLabel('история'),
    _SettingRow(label: 'очистить кэш', value: '248 MB', action: 'очистить', onTap: () {}),
  ]);
}

class _CallsSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('звонки'),
    const _GroupLabel('качество'),
    const _DropdownRow(label: 'качество видео', value: 'HD'),
    const _DropdownRow(label: 'качество звука', value: 'высокое'),
    const _ToggleRow2(label: 'шумоподавление', value: true),
    const _ToggleRow2(label: 'эхоподавление', value: true),
    const _GroupLabel('устройства'),
    const _DropdownRow(label: 'микрофон', value: 'Default'),
    const _DropdownRow(label: 'динамик', value: 'Default'),
    const _DropdownRow(label: 'камера', value: 'Default'),
    const _GroupLabel('поведение'),
    const _ToggleRow2(label: 'видео по умолчанию', value: false),
    const _ToggleRow2(label: 'демонстрация курсора', value: true),
  ]);
}

class _PrivacySection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('приватность'),
    const _GroupLabel('кто может'),
    const _DropdownRow(label: 'видеть мой статус', value: 'все'),
    const _DropdownRow(label: 'видеть последний раз', value: 'контакты'),
    const _DropdownRow(label: 'звонить мне', value: 'контакты'),
    const _DropdownRow(label: 'добавлять в группы', value: 'все'),
    const _GroupLabel('чтение'),
    const _ToggleRow2(label: 'показывать прочтения', value: true),
    const _ToggleRow2(label: 'показывать "печатает..."', value: true),
    const _GroupLabel('peek'),
    const _ToggleRow2(label: 'скрывать превью в peek', value: false),
    const _GroupLabel('блокировка'),
    _SettingRow(label: 'заблокированные', value: '3 человека', action: '→', onTap: () {}),
    _SettingRow(label: 'скрытые чаты', value: '0', action: '→', onTap: () {}),
    const _GroupLabel('безопасность экрана'),
    const _ToggleRow2(label: 'блокировка приложения', value: false),
    const _ToggleRow2(label: 'скрывать в task switcher', value: true),
  ]);
}

class _DevicesSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('устройства'),
    const _GroupLabel('активные сессии'),
    _DeviceCard(icon: SolarIconsOutline.monitor, name: 'Windows PC (этот)', location: 'Хабаровск · Windows 11', current: true)
        .animate().fadeIn(duration: 300.ms).slideX(begin: 0.05),
    const SizedBox(height: 8),
    _DeviceCard(icon: SolarIconsOutline.smartphone, name: 'iPhone 15 Pro', location: 'Москва · iOS 18.1', current: false)
        .animate(delay: 80.ms).fadeIn(duration: 300.ms).slideX(begin: 0.05),
    const SizedBox(height: 8),
    _DeviceCard(icon: SolarIconsOutline.laptop, name: 'MacBook Pro', location: 'Москва · macOS 15.1', current: false)
        .animate(delay: 160.ms).fadeIn(duration: 300.ms).slideX(begin: 0.05),
    const SizedBox(height: 20),
    _SettingRow(label: 'завершить все остальные', destructive: true, onTap: () {}),
  ]);
}

class _DeviceCard extends StatelessWidget {
  final IconData icon;
  final String name, location;
  final bool current;
  const _DeviceCard({required this.icon, required this.name, required this.location, required this.current});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: Colors.white.withValues(alpha: 0.03),
        border: Border.all(color: BColors.borderLow),
      ),
      child: Row(children: [
        Icon(icon, size: 28, color: BColors.textSecondary),
        const SizedBox(width: 14),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(name, style: const TextStyle(fontFamily: 'Onest', fontSize: 14, fontWeight: FontWeight.w500, color: BColors.textPrimary)),
          const SizedBox(height: 2),
          Text(location, style: const TextStyle(fontFamily: 'Onest', fontSize: 12, color: BColors.textSecondary)),
        ])),
        if (current) Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(6), color: BColors.accent.withValues(alpha: 0.12)),
          child: const Text('текущая', style: TextStyle(fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w600, color: BColors.accent)),
        )
        else Text('завершить', style: TextStyle(fontFamily: 'Onest', fontSize: 12, color: const Color(0xCCFF5C5C))),
      ]),
    );
  }
}

class _StorageSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('хранилище'),
    const _GroupLabel('использовано'),
    // Progress bar
    Container(
      height: 8, width: double.infinity,
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(4), color: Colors.white.withValues(alpha: 0.06)),
      child: FractionallySizedBox(
        alignment: Alignment.centerLeft, widthFactor: 0.12,
        child: Container(decoration: BoxDecoration(borderRadius: BorderRadius.circular(4),
          gradient: LinearGradient(colors: [BColors.accent, BColors.accent.withValues(alpha: 0.6)]))),
      ),
    ).animate().fadeIn(duration: 400.ms),
    const SizedBox(height: 8),
    const Text('1.2 GB / 10 GB — 12% использовано', style: TextStyle(fontFamily: 'Onest', fontSize: 12, color: BColors.textSecondary)),
    const SizedBox(height: 16),
    _SettingRow(label: 'фото', value: '567 MB', action: 'просмотр', onTap: () {}),
    _SettingRow(label: 'видео', value: '312 MB', action: 'просмотр', onTap: () {}),
    _SettingRow(label: 'голосовые', value: '98 MB', action: 'просмотр', onTap: () {}),
    _SettingRow(label: 'документы', value: '201 MB', action: 'просмотр', onTap: () {}),
    _SettingRow(label: 'кэш', value: '48 MB', action: 'очистить', onTap: () {}),
    const _GroupLabel('очистка'),
    const _DropdownRow(label: 'автоочистка медиа', value: 'через 30 дней'),
    _SettingRow(label: 'экспорт всех данных', action: 'скачать', onTap: () {}),
  ]);
}

class _LanguageSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('язык'),
    const _GroupLabel('интерфейс'),
    for (final (code, name, selected) in [('ru', 'Русский', true), ('en', 'English', false), ('uk', 'Українська', false)])
      _RadioRow(label: name, selected: selected),
    const _GroupLabel('форматы'),
    const _DropdownRow(label: 'формат даты', value: 'DD.MM.YYYY'),
    const _DropdownRow(label: 'формат времени', value: '24 часа'),
    const _DropdownRow(label: 'первый день недели', value: 'Понедельник'),
  ]);
}

class _RadioRow extends StatelessWidget {
  final String label;
  final bool selected;
  const _RadioRow({required this.label, required this.selected});
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 44, padding: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(border: Border(bottom: BorderSide(color: BColors.borderLow))),
      child: Row(children: [
        AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          width: 16, height: 16,
          decoration: BoxDecoration(shape: BoxShape.circle,
            color: selected ? BColors.accent : Colors.transparent,
            border: Border.all(color: selected ? BColors.accent : Colors.white.withValues(alpha: 0.2), width: selected ? 4 : 1.5)),
        ),
        const SizedBox(width: 12),
        Text(label, style: const TextStyle(fontFamily: 'Onest', fontSize: 14, color: BColors.textPrimary)),
      ]),
    );
  }
}

class _HelpSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const _SectionTitle('помощь'),
    const _GroupLabel('поддержка'),
    _SettingRow(label: 'вопросы и ответы', action: '→', onTap: () {}),
    _SettingRow(label: 'связаться с поддержкой', action: '→', onTap: () {}),
    _SettingRow(label: 'сообщить о проблеме', action: '→', onTap: () {}),
    const _GroupLabel('сообщество'),
    _SettingRow(label: 'чат blesk users', action: '→', onTap: () {}),
    _SettingRow(label: 'канал новостей', action: '→', onTap: () {}),
    _SettingRow(label: 'github', action: '→', onTap: () {}),
    const _GroupLabel('правовое'),
    _SettingRow(label: 'условия использования', action: '→', onTap: () {}),
    _SettingRow(label: 'политика конфиденциальности', action: '→', onTap: () {}),
    _SettingRow(label: 'лицензии open source', action: '→', onTap: () {}),
  ]);
}

class _AboutSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.center, children: [
    const SizedBox(height: 40),
    Image.asset('assets/logo/blesk-logo.png', width: 120, fit: BoxFit.contain)
        .animate().fadeIn(duration: 500.ms).scale(begin: const Offset(0.9, 0.9), duration: 500.ms, curve: Curves.easeOutCubic),
    const SizedBox(height: 20),
    Text('версия 0.1.0 alpha', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 13), color: BColors.textSecondary))
        .animate(delay: 200.ms).fadeIn(duration: 300.ms),
    const SizedBox(height: 4),
    Text('сборка 2026.04.17', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 12), color: BColors.textMuted))
        .animate(delay: 250.ms).fadeIn(duration: 300.ms),
    const SizedBox(height: 32),
    Text('сделано в хабаровске,', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 14), color: BColors.textSecondary, height: 1.6))
        .animate(delay: 350.ms).fadeIn(duration: 300.ms),
    Text('с лаймом и любовью.', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 14), color: BColors.textSecondary))
        .animate(delay: 400.ms).fadeIn(duration: 300.ms),
    const SizedBox(height: 24),
    Text('ваш ник: @gotblesk', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 12), color: BColors.textMuted))
        .animate(delay: 500.ms).fadeIn(duration: 300.ms),
    Text('ID: 7384619', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 12), color: BColors.textMuted))
        .animate(delay: 550.ms).fadeIn(duration: 300.ms),
    const SizedBox(height: 40),
    Text('✦ blesk — твой блеск, твои правила.', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 13),
      fontStyle: FontStyle.italic, color: BColors.accent.withValues(alpha: 0.5)))
        .animate(delay: 650.ms).fadeIn(duration: 400.ms),
    const SizedBox(height: 40),
  ]);
}
