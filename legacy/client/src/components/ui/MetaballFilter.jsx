import { useEffect } from 'react';

// SVG goo filter для metaball эффекта
// Рендерится ОДИН раз в App.jsx
// Использование: style={{ filter: 'url(#goo)' }} на контейнере с ТОЛЬКО формами (без текста!)
// Текст/иконки рендерить СНАРУЖИ filtered контейнера

let injected = false;

export const GOO_FILTER = 'url(#goo)';

export default function MetaballFilter({ blur = 10, contrast = 20, id = 'goo' }) {
  useEffect(() => {
    if (injected) return;
    injected = true;

    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('aria-hidden', 'true');
    svg.style.cssText = 'position:absolute;width:0;height:0;pointer-events:none;overflow:hidden';

    // alpha channel contrast: row 4 of matrix = [0 0 0 contrast -offset]
    const offset = Math.round(contrast / 2);
    svg.innerHTML = `<defs>
      <filter id="${id}">
        <feGaussianBlur in="SourceGraphic" stdDeviation="${blur}" result="blur"/>
        <feColorMatrix in="blur" type="matrix"
          values="1 0 0 0 0
                  0 1 0 0 0
                  0 0 1 0 0
                  0 0 0 ${contrast} -${offset}"/>
      </filter>
    </defs>`;

    document.body.appendChild(svg);

    return () => {
      injected = false;
      svg.remove();
    };
  }, [blur, contrast, id]);

  return null;
}
