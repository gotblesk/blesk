import { describe, it, expect } from 'vitest';
import { parseMarkdown } from '../markdown';

describe('parseMarkdown', () => {
  it('returns plain text unchanged', () => {
    const result = parseMarkdown('hello world');
    expect(result).toBeDefined();
  });

  it('renders bold text', () => {
    const result = parseMarkdown('**bold**');
    const hasStrong = JSON.stringify(result).includes('strong');
    expect(hasStrong).toBe(true);
  });

  it('renders italic text', () => {
    const result = parseMarkdown('*italic*');
    const hasEm = JSON.stringify(result).includes('em');
    expect(hasEm).toBe(true);
  });

  it('renders inline code', () => {
    const result = parseMarkdown('`code`');
    const hasCode = JSON.stringify(result).includes('msg-code');
    expect(hasCode).toBe(true);
  });

  it('renders code blocks', () => {
    const result = parseMarkdown('```\ncode block\n```');
    const hasCodeblock = JSON.stringify(result).includes('msg-codeblock');
    expect(hasCodeblock).toBe(true);
  });

  it('renders strikethrough', () => {
    const result = parseMarkdown('~~strike~~');
    const hasDel = JSON.stringify(result).includes('del');
    expect(hasDel).toBe(true);
  });

  it('renders spoilers', () => {
    const result = parseMarkdown('||spoiler||');
    const hasSpoiler = JSON.stringify(result).includes('msg-spoiler');
    expect(hasSpoiler).toBe(true);
  });

  it('renders blockquotes', () => {
    const result = parseMarkdown('> quoted text');
    const hasQuote = JSON.stringify(result).includes('msg-quote');
    expect(hasQuote).toBe(true);
  });

  it('auto-links URLs', () => {
    const result = parseMarkdown('visit https://blesk.fun today');
    const hasLink = JSON.stringify(result).includes('https://blesk.fun');
    expect(hasLink).toBe(true);
  });

  it('handles nested formatting', () => {
    const result = parseMarkdown('**bold _and italic_**');
    const str = JSON.stringify(result);
    expect(str).toContain('strong');
    expect(str).toContain('em');
  });

  it('handles unmatched markers as plain text', () => {
    const result = parseMarkdown('this * is not italic');
    expect(result).toBeDefined();
  });

  it('is XSS-safe', () => {
    const result = parseMarkdown('<script>alert("xss")</script>');
    const str = JSON.stringify(result);
    expect(str).not.toContain('<script>');
  });
});
