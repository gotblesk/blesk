import { useState, useCallback } from 'react';
import { ZoomIn } from 'lucide-react';
import Cropper from 'react-easy-crop';
import './AvatarCropModal.css';

// Хелпер: вырезать область из изображения → JPEG blob
function getCroppedBlob(imageSrc, pixelCrop) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const canvas = document.createElement('canvas');
      const size = Math.min(pixelCrop.width, pixelCrop.height);
      canvas.width = size;
      canvas.height = size;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(
        img,
        pixelCrop.x, pixelCrop.y, pixelCrop.width, pixelCrop.height,
        0, 0, size, size
      );
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('Canvas toBlob failed'))),
        'image/jpeg',
        0.9
      );
    };
    img.onerror = reject;
    img.src = imageSrc;
  });
}

export default function AvatarCropModal({ imageSrc, onClose, onSave }) {
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [croppedArea, setCroppedArea] = useState(null);
  const [saving, setSaving] = useState(false);

  const onCropComplete = useCallback((_, croppedPixels) => {
    setCroppedArea(croppedPixels);
  }, []);

  const handleSave = async () => {
    if (!croppedArea) return;
    setSaving(true);
    try {
      const blob = await getCroppedBlob(imageSrc, croppedArea);
      await onSave(blob);
    } catch {
      // ошибка кропа
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="avatar-crop-overlay">
      <div className="avatar-crop-card" onClick={(e) => e.stopPropagation()}>
        <div className="avatar-crop-card__shine" />

        <div className="avatar-crop__title">Обрезать аватар</div>

        <div className="avatar-crop__area">
          <Cropper
            image={imageSrc}
            crop={crop}
            zoom={zoom}
            aspect={1}
            cropShape="round"
            showGrid={false}
            onCropChange={setCrop}
            onZoomChange={setZoom}
            onCropComplete={onCropComplete}
          />
        </div>

        <div className="avatar-crop__zoom">
          <span className="avatar-crop__zoom-label"><ZoomIn size={14} strokeWidth={1.5} /></span>
          <input
            type="range"
            min={1}
            max={3}
            step={0.05}
            value={zoom}
            onChange={(e) => setZoom(Number(e.target.value))}
            className="avatar-crop__zoom-slider"
          />
          <span className="avatar-crop__zoom-value">{zoom.toFixed(1)}×</span>
        </div>

        <div className="avatar-crop__actions">
          <button className="avatar-crop__btn avatar-crop__btn--cancel" onClick={onClose}>
            Отмена
          </button>
          <button
            className="avatar-crop__btn avatar-crop__btn--save"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? 'Сохраняю...' : 'Сохранить'}
          </button>
        </div>
      </div>
    </div>
  );
}
