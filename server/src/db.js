// Единственный экземпляр PrismaClient для всего сервера
const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient({
  // Логирование медленных запросов
  log: process.env.NODE_ENV === 'production'
    ? [{ level: 'warn', emit: 'stdout' }]
    : ['query', 'warn', 'error'],
});

// Graceful disconnect при завершении
process.on('beforeExit', async () => {
  await prisma.$disconnect();
});

module.exports = prisma;
