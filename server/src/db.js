// Единственный экземпляр PrismaClient для всего сервера
const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient();

module.exports = prisma;
