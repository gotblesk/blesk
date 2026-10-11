import { Globe } from '@phosphor-icons/react';
import './LinkPreviewCard.css';

export default function LinkPreviewCard({ preview }) {
  if (!preview) return null;
  const { url, title, description, image, domain } = preview;

  const handleClick = () => {
    window.electronAPI?.openExternal?.(url) || window.open(url, '_blank');
  };

  return (
    <div className="link-preview" onClick={handleClick}>
      {image && <img className="link-preview__image" src={image} alt="" loading="lazy" />}
      <div className="link-preview__body">
        {title && <div className="link-preview__title">{title}</div>}
        {description && <div className="link-preview__desc">{description}</div>}
        <div className="link-preview__domain">
          <Globe />
          <span>{domain || new URL(url).hostname}</span>
        </div>
      </div>
    </div>
  );
}
