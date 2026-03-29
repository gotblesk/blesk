const nodemailer = require('nodemailer');
const crypto = require('crypto');
const path = require('path');

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
    connectionTimeout: 10000,
    greetingTimeout: 10000,
  });

  // Проверить SMTP при старте
  transporter.verify()
    .then(() => console.log('SMTP connected:', process.env.SMTP_HOST))
    .catch((err) => console.error('SMTP error:', err.message));
} else {
  console.warn('SMTP not configured — codes logged to console');
}

// Путь к логотипу
const LOGO_PATH = path.resolve(__dirname, '../../assets/blesk-email.png');

// Генерация 6-значного кода (криптографически безопасный)
function generateCode() {
  return String(crypto.randomInt(100000, 1000000));
}

// HTML-шаблон письма в стиле blesk (с логотипом через CID)
function buildEmailHTML(code) {
  return `<!DOCTYPE html>
<html>
<head><meta charset="utf-8"></head>
<body style="margin:0;padding:0;background:#08060f;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#08060f;padding:40px 20px;">
    <tr><td align="center">
      <table width="440" cellpadding="0" cellspacing="0" style="background:#111019;border-radius:24px;border:1px solid rgba(255,255,255,0.06);overflow:hidden;">

        <!-- Градиент-полоска сверху -->
        <tr><td style="height:4px;background:linear-gradient(90deg,#c8ff00,#a8e600,#c8ff00);"></td></tr>

        <!-- Логотип -->
        <tr><td align="center" style="padding:32px 40px 20px;">
          <img src="cid:blesk-logo" alt="blesk" width="80" height="80" style="display:block;border-radius:20px;" />
        </td></tr>

        <!-- Заголовок -->
        <tr><td align="center" style="padding:0 40px 6px;">
          <div style="font-size:18px;font-weight:700;color:rgba(255,255,255,0.9);">
            Код подтверждения
          </div>
        </td></tr>

        <tr><td align="center" style="padding:0 40px 24px;">
          <div style="font-size:13px;color:rgba(255,255,255,0.35);">
            Введите этот код в приложении blesk
          </div>
        </td></tr>

        <!-- Код -->
        <tr><td align="center" style="padding:0 40px 28px;">
          <div style="font-size:38px;font-weight:800;letter-spacing:14px;color:#c8ff00;background:rgba(200,255,0,0.06);border:1px solid rgba(200,255,0,0.1);border-radius:16px;padding:18px 36px;display:inline-block;">
            ${code}
          </div>
        </td></tr>

        <!-- Разделитель -->
        <tr><td style="padding:0 40px;"><div style="height:1px;background:rgba(255,255,255,0.05);"></div></td></tr>

        <!-- Подвал -->
        <tr><td align="center" style="padding:20px 40px 28px;">
          <div style="font-size:11px;color:rgba(255,255,255,0.2);line-height:1.6;">
            Код действует 10 минут.<br/>
            Если вы не совершали это действие — проигнорируйте письмо.
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
    console.log(`\n[DEV] Verification code for ${email}: ${code}\n`);
    return true;
  }

  // Попробовать прикрепить логотип как CID, если файл существует
  let attachments = [];
  try {
    const fs = require('fs');
    if (fs.existsSync(LOGO_PATH)) {
      attachments = [{
        filename: 'blesk-logo.png',
        path: LOGO_PATH,
        cid: 'blesk-logo',
      }];
    }
  } catch (err) { console.error('Failed to attach email logo:', err.message); }

  try {
    await transporter.sendMail({
      from: {
        name: 'blesk',
        address: process.env.SMTP_FROM || process.env.SMTP_USER,
      },
      to: email,
      subject: `${code} — blesk`,
      html: buildEmailHTML(code),
      attachments,
    });
    return true;
  } catch (err) {
    console.error('Email send error:', err.message);
    return false;
  }
}

module.exports = { generateCode, sendVerificationCode };
