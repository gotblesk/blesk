import './DateSeparator.css';

function formatDateLabel(dateStr) {
  const date = new Date(dateStr);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  const msgDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());

  if (msgDate.getTime() === today.getTime()) return 'Сегодня';
  if (msgDate.getTime() === yesterday.getTime()) return 'Вчера';

  const day = date.getDate();
  const months = ['января','февраля','марта','апреля','мая','июня',
                  'июля','августа','сентября','октября','ноября','декабря'];
  const month = months[date.getMonth()];

  if (date.getFullYear() === now.getFullYear()) return `${day} ${month}`;
  return `${day} ${month} ${date.getFullYear()}`;
}

export default function DateSeparator({ date }) {
  return (
    <div className="date-separator">
      <div className="date-separator__pill">{formatDateLabel(date)}</div>
    </div>
  );
}
