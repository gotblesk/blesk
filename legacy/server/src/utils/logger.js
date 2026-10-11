const pino = require('pino');

const isDev = process.env.NODE_ENV !== 'production';

const logger = pino({
  level: process.env.LOG_LEVEL || (isDev ? 'debug' : 'info'),
  transport: isDev
    ? { target: 'pino-pretty', options: { colorize: true, translateTime: 'HH:MM:ss' } }
    : undefined,
  formatters: {
    level(label) { return { level: label }; },
  },
  timestamp: pino.stdTimeFunctions.isoTime,
  base: { service: 'blesk-server' },
});

module.exports = logger;
