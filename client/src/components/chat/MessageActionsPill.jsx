import { CornerUpLeft, SmilePlus, Pencil, Trash2 } from 'lucide-react';
import './MessageActionsPill.css';

export default function MessageActionsPill({ isOwn, onReply, onReact, onEdit, onDelete }) {
  return (
    <div className="msg-actions-pill">
      <button className="msg-actions-pill__btn" onClick={onReply} title="Ответить">
        <CornerUpLeft />
      </button>
      <button className="msg-actions-pill__btn" onClick={onReact} title="Реакция">
        <SmilePlus />
      </button>
      {isOwn && (
        <>
          <button className="msg-actions-pill__btn" onClick={onEdit} title="Редактировать">
            <Pencil />
          </button>
          <button className="msg-actions-pill__btn" onClick={onDelete} title="Удалить">
            <Trash2 />
          </button>
        </>
      )}
    </div>
  );
}
