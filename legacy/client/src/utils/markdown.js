import React from 'react';

/**
 * Легковесный markdown-парсер для чат-сообщений.
 * Возвращает массив React-элементов (без dangerouslySetInnerHTML).
 * Поддерживает: **bold**, *italic*, ~~strike~~, `code`, ```codeblock```,
 * ||spoiler||, > quote, авто-ссылки (http/https).
 */

const LINK_RE = /https?:\/\/[^\s<>"')\]]+/g;

let keyCounter = 0;
function nextKey() {
  return `md-${++keyCounter}`;
}

// Сброс счётчика между рендерами
function resetKeys() {
  keyCounter = 0;
}

/**
 * Компонент-спойлер: скрыт до клика
 */
function Spoiler({ children }) {
  const [revealed, setRevealed] = React.useState(false);
  return React.createElement(
    'span',
    {
      className: revealed ? 'msg-spoiler msg-spoiler--revealed' : 'msg-spoiler',
      onClick: () => setRevealed((v) => !v),
      role: 'button',
      tabIndex: 0,
      onKeyDown: (e) => {
        if (e.key === 'Enter' || e.key === ' ') setRevealed((v) => !v);
      },
      'aria-label': revealed ? 'Скрыть спойлер' : 'Показать спойлер',
    },
    children,
  );
}

/**
 * Парсит inline-форматирование (bold, italic, strike, code, spoiler, ссылки).
 * Рекурсивно обрабатывает вложенность (кроме code и spoiler).
 */
function parseInline(text) {
  if (!text) return [];

  const tokens = [];
  let remaining = text;

  // Паттерны в порядке приоритета
  // inline code — не рекурсивный
  // spoiler — не рекурсивный
  // bold — рекурсивный
  // italic — рекурсивный
  // strikethrough — рекурсивный
  // ссылки
  const patterns = [
    {
      // inline code: `text`
      re: /`([^`]+?)`/,
      render: (match) =>
        React.createElement('code', { className: 'msg-code', key: nextKey() }, match[1]),
      recursive: false,
    },
    {
      // spoiler: ||text||
      re: /\|\|(.+?)\|\|/,
      render: (match) =>
        React.createElement(Spoiler, { key: nextKey() }, match[1]),
      recursive: false,
    },
    {
      // bold: **text** or __text__
      re: /(\*\*|__)(.+?)\1/,
      render: (match) =>
        React.createElement('strong', { key: nextKey() }, parseInline(match[2])),
      recursive: true,
    },
    {
      // italic: *text* or _text_ (не жадный, не захватывает **)
      re: /(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)/,
      render: (match) =>
        React.createElement('em', { key: nextKey() }, parseInline(match[1] || match[2])),
      recursive: true,
    },
    {
      // strikethrough: ~~text~~
      re: /~~(.+?)~~/,
      render: (match) =>
        React.createElement('del', { key: nextKey() }, parseInline(match[1])),
      recursive: true,
    },
    {
      // URL авто-ссылка
      re: LINK_RE,
      render: (match) =>
        React.createElement(
          'a',
          {
            href: match[0],
            key: nextKey(),
            className: 'msg-link',
            target: '_blank',
            rel: 'noopener noreferrer',
          },
          match[0],
        ),
      recursive: false,
    },
  ];

  while (remaining.length > 0) {
    let earliestMatch = null;
    let earliestIndex = Infinity;
    let matchedPattern = null;

    for (const pattern of patterns) {
      // Создаём новый RegExp без глобального флага для поиска первого вхождения
      const re = new RegExp(pattern.re.source, pattern.re.flags.replace('g', ''));
      const m = remaining.match(re);
      if (m && m.index < earliestIndex) {
        earliestIndex = m.index;
        earliestMatch = m;
        matchedPattern = pattern;
      }
    }

    if (!earliestMatch) {
      // Ничего не найдено — остаток как текст
      tokens.push(remaining);
      break;
    }

    // Текст до совпадения
    if (earliestIndex > 0) {
      tokens.push(remaining.slice(0, earliestIndex));
    }

    // Отрисовка совпадения
    tokens.push(matchedPattern.render(earliestMatch));

    // Двигаемся дальше
    remaining = remaining.slice(earliestIndex + earliestMatch[0].length);
  }

  return tokens;
}

/**
 * Основная функция — парсит текст сообщения в массив React-элементов.
 * Обрабатывает блочные конструкции (codeblock, blockquote) и inline.
 */
export function parseMarkdown(text) {
  if (!text || typeof text !== 'string') return text || '';

  resetKeys();

  const result = [];

  // Разбиваем на блоки по тройным бэктикам
  const codeBlockRe = /```(\w*)\n?([\s\S]*?)```/g;
  let lastIndex = 0;
  let match;

  while ((match = codeBlockRe.exec(text)) !== null) {
    // Текст до code block
    const before = text.slice(lastIndex, match.index);
    if (before) {
      result.push(...parseBlockContent(before));
    }

    // Code block
    const lang = match[1] || '';
    const code = match[2].replace(/\n$/, ''); // убираем trailing newline
    result.push(
      React.createElement(
        'pre',
        { className: 'msg-codeblock', key: nextKey(), 'data-lang': lang || undefined },
        React.createElement('code', null, code),
      ),
    );

    lastIndex = match.index + match[0].length;
  }

  // Оставшийся текст после последнего code block
  const tail = text.slice(lastIndex);
  if (tail) {
    result.push(...parseBlockContent(tail));
  }

  return result;
}

/**
 * Парсит текст вне code-блоков: blockquotes и inline.
 */
function parseBlockContent(text) {
  const lines = text.split('\n');
  const result = [];
  let quoteBuffer = [];

  function flushQuote() {
    if (quoteBuffer.length === 0) return;
    const quoteText = quoteBuffer.join('\n');
    result.push(
      React.createElement(
        'blockquote',
        { className: 'msg-quote', key: nextKey() },
        parseInline(quoteText),
      ),
    );
    quoteBuffer = [];
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Blockquote: строка начинается с "> "
    if (/^>\s?/.test(line)) {
      quoteBuffer.push(line.replace(/^>\s?/, ''));
      continue;
    }

    // Если был буфер цитат — сбрасываем
    flushQuote();

    // Обычная строка → inline-парсинг
    const inlineElements = parseInline(line);

    if (i < lines.length - 1) {
      // Добавляем перенос строки между строками (кроме последней)
      result.push(
        React.createElement(React.Fragment, { key: nextKey() }, ...inlineElements, React.createElement('br')),
      );
    } else if (inlineElements.length > 0) {
      if (inlineElements.length === 1 && typeof inlineElements[0] === 'string') {
        result.push(inlineElements[0]);
      } else {
        result.push(
          React.createElement(React.Fragment, { key: nextKey() }, ...inlineElements),
        );
      }
    }
  }

  // Финальный сброс цитат
  flushQuote();

  return result;
}
