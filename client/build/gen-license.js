const fs = require('fs');

const lines = [
  'blesk — приватный мессенджер',
  'Версия 0.1.0-alpha',
  '',
  'Лицензия: AGPL-3.0',
  '',
  'Это ПО распространяется на условиях лицензии AGPL-3.0.',
  'Вы можете свободно использовать, изменять и распространять',
  'эту программу при соблюдении условий лицензии.',
  '',
  'https://www.gnu.org/licenses/agpl-3.0.html',
  'https://github.com/gotblesk/blesk',
  '',
  '© 2026 gotblesk',
];

const bom = Buffer.from([0xEF, 0xBB, 0xBF]);
const text = Buffer.from(lines.join('\r\n'), 'utf8');
fs.writeFileSync('build/license.txt', Buffer.concat([bom, text]));
console.log('license.txt created with UTF-8 BOM');
