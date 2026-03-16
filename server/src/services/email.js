const nodemailer = require('nodemailer');

// SMTP транспорт — настраивается через env
let transporter = null;

if (process.env.SMTP_HOST) {
  transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: parseInt(process.env.SMTP_PORT) || 465,
    secure: (parseInt(process.env.SMTP_PORT) || 465) === 465,
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
  });
}

// Генерация 6-значного кода
function generateCode() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

// HTML-шаблон письма в стиле blesk
function buildEmailHTML(code) {
  return `
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"></head>
<body style="margin:0;padding:0;background:#08060f;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#08060f;padding:40px 20px;">
    <tr><td align="center">
      <table width="420" cellpadding="0" cellspacing="0" style="background:rgba(255,255,255,0.06);border-radius:24px;border:1px solid rgba(255,255,255,0.08);padding:40px;">
        <tr><td align="center" style="padding-bottom:24px;">
          <span style="font-size:32px;font-weight:800;color:rgba(255,255,255,0.9);letter-spacing:-1px;">
            b<span style="color:#c8ff00;">l</span>
          </span>
          <span style="font-size:18px;font-weight:700;color:rgba(255,255,255,0.7);margin-left:4px;">
            ble<span style="color:#c8ff00;">sk</span>
          </span>
        </td></tr>
        <tr><td align="center" style="padding-bottom:8px;">
          <div style="font-size:16px;font-weight:600;color:rgba(255,255,255,0.85);">
            Код подтверждения
          </div>
        </td></tr>
        <tr><td align="center" style="padding-bottom:24px;">
          <div style="font-size:13px;color:rgba(255,255,255,0.4);">
            Введите этот код в приложении blesk
          </div>
        </td></tr>
        <tr><td align="center" style="padding-bottom:32px;">
          <div style="font-size:36px;font-weight:800;letter-spacing:12px;color:#c8ff00;background:rgba(200,255,0,0.08);border-radius:16px;padding:16px 32px;display:inline-block;">
            ${code}
          </div>
        </td></tr>
        <tr><td align="center">
          <div style="font-size:12px;color:rgba(255,255,255,0.25);">
            Код действует 10 минут. Если вы не регистрировались в blesk — проигнорируйте это письмо.
          </div>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`;
}

// Отправка кода на email
async function sendVerificationCode(email, code) {
  if (!transporter) {
    // Dev-режим: логируем в консоль
    console.log(`\n📧 [DEV] Код верификации для ${email}: ${code}\n`);
    return true;
  }

  try {
    await transporter.sendMail({
      from: process.env.SMTP_FROM || process.env.SMTP_USER,
      to: email,
      subject: `${code} — код подтверждения blesk`,
      html: buildEmailHTML(code),
    });
    return true;
  } catch (err) {
    console.error('Ошибка отправки email:', err.message);
    return false;
  }
}

module.exports = { generateCode, sendVerificationCode };
