// Утилиты для быстрого поиска сокетов пользователей
// Используют userSockets Map из index.js для O(1) lookup

let ioInstance = null;
let userSocketsMap = null;

function init(io, userSockets) {
  ioInstance = io;
  userSocketsMap = userSockets;
}

function findUserSocket(userId) {
  if (!userSocketsMap) return null;
  const sockets = userSocketsMap.get(userId);
  if (!sockets || sockets.size === 0) return null;
  return sockets.values().next().value;
}

function findUserSockets(userId) {
  if (!userSocketsMap) return [];
  const sockets = userSocketsMap.get(userId);
  return sockets ? [...sockets] : [];
}

function emitToUser(userId, event, data) {
  const socket = findUserSocket(userId);
  if (socket) socket.emit(event, data);
  return !!socket;
}

function emitToUserAll(userId, event, data) {
  const sockets = findUserSockets(userId);
  for (const s of sockets) s.emit(event, data);
  return sockets.length > 0;
}

function getUserSocketsMap() {
  return userSocketsMap;
}

function getIo() {
  return ioInstance;
}

module.exports = { init, findUserSocket, findUserSockets, emitToUser, emitToUserAll, getUserSocketsMap, getIo };
