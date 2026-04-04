import { useState, useEffect, useRef, useCallback } from 'react';
import { MagnifyingGlass, X } from '@phosphor-icons/react';
import { Skeleton } from '../ui/GlassSkeleton';
import './GifPicker.css';

const TENOR_KEY = 'AIzaSyBqwDKjwi9kGJGUUBpxJ7v0Qi9HPVVn8hg';
const TENOR_BASE = 'https://tenor.googleapis.com/v2';
const LIMIT = 20;

export default function GifPicker({ onSelect, onClose }) {
  const [query, setQuery] = useState('');
  const [gifs, setGifs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const containerRef = useRef(null);
  const inputRef = useRef(null);
  const debounceRef = useRef(null);

  // Фокус на поле поиска при открытии
  useEffect(() => {
    setTimeout(() => inputRef.current?.focus(), 100);
  }, []);

  // Загрузить трендовые при первом открытии
  useEffect(() => {
    fetchFeatured();
  }, []);

  // Закрытие при клике вне
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target) &&
          !e.target.closest('.chat-input__tool-btn')) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [onClose]);

  // Закрытие по Escape
  useEffect(() => {
    const handleEsc = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [onClose]);

  const fetchFeatured = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(
        `${TENOR_BASE}/featured?key=${TENOR_KEY}&limit=${LIMIT}&media_filter=tinygif,gif`
      );
      if (!res.ok) throw new Error('Tenor API error');
      const data = await res.json();
      setGifs(extractGifs(data.results));
    } catch {
      setError('Не удалось загрузить GIF');
    } finally {
      setLoading(false);
    }
  };

  const searchGifs = useCallback(async (q) => {
    if (!q.trim()) {
      fetchFeatured();
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(
        `${TENOR_BASE}/search?q=${encodeURIComponent(q)}&key=${TENOR_KEY}&limit=${LIMIT}&media_filter=tinygif,gif`
      );
      if (!res.ok) throw new Error('Tenor API error');
      const data = await res.json();
      setGifs(extractGifs(data.results));
    } catch {
      setError('Ошибка поиска');
    } finally {
      setLoading(false);
    }
  }, []);

  // Извлечь URL из ответа Tenor
  const extractGifs = (results) => {
    if (!Array.isArray(results)) return [];
    return results.map((item) => {
      const media = item.media_formats || {};
      const tiny = media.tinygif || {};
      const full = media.gif || {};
      return {
        id: item.id,
        preview: tiny.url || full.url || '',
        full: full.url || tiny.url || '',
        width: tiny.dims?.[0] || 200,
        height: tiny.dims?.[1] || 150,
      };
    }).filter((g) => g.preview);
  };

  const handleInputChange = (e) => {
    const val = e.target.value;
    setQuery(val);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => searchGifs(val), 300);
  };

  const handleClear = () => {
    setQuery('');
    fetchFeatured();
    inputRef.current?.focus();
  };

  const handleGifClick = (gif) => {
    onSelect(gif.full);
  };

  return (
    <div className="gif-picker" ref={containerRef}>
      <div className="gif-picker__header">
        <div className="gif-picker__search">
          <MagnifyingGlass size={14} className="gif-picker__search-icon" />
          <input
            ref={inputRef}
            className="gif-picker__search-input"
            type="text"
            placeholder="Поиск GIF..."
            value={query}
            onChange={handleInputChange}
            aria-label="Поиск GIF"
          />
          {query && (
            <button
              className="gif-picker__search-clear"
              onClick={handleClear}
              aria-label="Очистить поиск"
            >
              <X size={12} />
            </button>
          )}
        </div>
      </div>

      <div className="gif-picker__grid-wrap">
        {loading && (
          <div className="gif-picker__grid">
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton.Block
                key={i}
                height={80 + Math.floor(Math.random() * 40)}
                borderRadius={8}
                className="gif-picker__skeleton"
              />
            ))}
          </div>
        )}

        {!loading && error && (
          <div className="gif-picker__empty">{error}</div>
        )}

        {!loading && !error && gifs.length === 0 && (
          <div className="gif-picker__empty">
            {query ? 'Ничего не найдено' : 'Нет доступных GIF'}
          </div>
        )}

        {!loading && !error && gifs.length > 0 && (
          <div className="gif-picker__grid">
            {gifs.map((gif) => (
              <button
                key={gif.id}
                className="gif-picker__item"
                onClick={() => handleGifClick(gif)}
                aria-label="Выбрать GIF"
              >
                <img
                  src={gif.preview}
                  alt=""
                  loading="lazy"
                  draggable={false}
                />
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="gif-picker__footer">
        <span className="gif-picker__powered">Tenor</span>
      </div>
    </div>
  );
}
