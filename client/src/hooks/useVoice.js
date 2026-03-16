import { useEffect, useRef, useCallback } from 'react';
import { Device } from 'mediasoup-client';
import { useVoiceStore } from '../store/voiceStore';
import { getCurrentUserId } from '../utils/auth';

// Voice Activity Detection — порог громкости
const VAD_THRESHOLD = 15;
const VAD_INTERVAL = 100;

export function useVoice(socketRef) {
  const deviceRef = useRef(null);
  const sendTransportRef = useRef(null);
  const recvTransportRef = useRef(null);
  const producerRef = useRef(null);
  const consumersRef = useRef(new Map());
  const localStreamRef = useRef(null);
  const audioContextRef = useRef(null);
  const vadIntervalRef = useRef(null);
  const audioElementsRef = useRef(new Map());
  const consumerUserMapRef = useRef(new Map()); // consumerId → userId

  const {
    currentRoomId,
    isMuted,
    isDeafened,
    noiseSuppression,
    echoCancellation,
    inputDeviceId,
    userVolumes,
    setCurrentRoom,
    clearCurrentRoom,
    addParticipant,
    removeParticipant,
    updateParticipant,
    setAudioLevel,
  } = useVoiceStore();

  const currentRoomIdRef = useRef(currentRoomId);
  currentRoomIdRef.current = currentRoomId;

  // Refs для актуальных значений мута/деафена (против stale closure)
  const isMutedRef = useRef(isMuted);
  isMutedRef.current = isMuted;
  const isDeafenedRef = useRef(isDeafened);
  isDeafenedRef.current = isDeafened;

  // ═══ Захват микрофона ═══
  const getLocalStream = useCallback(async () => {
    const constraints = {
      audio: {
        echoCancellation,
        noiseSuppression,
        autoGainControl: true,
        sampleRate: 48000,
        ...(inputDeviceId ? { deviceId: { exact: inputDeviceId } } : {}),
      },
    };

    const stream = await navigator.mediaDevices.getUserMedia(constraints);
    localStreamRef.current = stream;
    return stream;
  }, [echoCancellation, noiseSuppression, inputDeviceId]);

  // ═══ VAD — определение говорящего ═══
  const startVAD = useCallback((stream, userId) => {
    if (audioContextRef.current) audioContextRef.current.close();

    const ctx = new AudioContext();
    audioContextRef.current = ctx;
    const source = ctx.createMediaStreamSource(stream);
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 256;
    source.connect(analyser);

    const data = new Uint8Array(analyser.frequencyBinCount);

    vadIntervalRef.current = setInterval(() => {
      analyser.getByteFrequencyData(data);
      const avg = data.reduce((a, b) => a + b, 0) / data.length;
      const level = Math.min(100, Math.round(avg * 1.5));
      setAudioLevel(userId, level);

      const speaking = avg > VAD_THRESHOLD;
      updateParticipant(userId, { speaking });
    }, VAD_INTERVAL);
  }, [setAudioLevel, updateParticipant]);

  // ═══ Воспроизвести remote audio ═══
  const playRemoteAudio = useCallback((consumerId, track) => {
    const stream = new MediaStream([track]);
    let audio = audioElementsRef.current.get(consumerId);

    if (!audio) {
      audio = new Audio();
      audio.autoplay = true;
      audioElementsRef.current.set(consumerId, audio);
    }

    audio.srcObject = stream;
    audio.play().catch(() => {});
  }, []);

  // ═══ Создать consumer для remote producer ═══
  const consumeProducer = useCallback(async (socket, roomId, producerId, rtpCapabilities, producerUserId) => {
    if (!recvTransportRef.current) return;

    return new Promise((resolve) => {
      socket.emit('voice:consume', {
        roomId,
        producerId,
        rtpCapabilities,
      }, async (response) => {
        if (response.error) {
          console.error('voice:consume error:', response.error);
          resolve(null);
          return;
        }

        const { consumerId, kind, rtpParameters } = response;

        const consumer = await recvTransportRef.current.consume({
          id: consumerId,
          producerId,
          kind,
          rtpParameters,
        });

        consumersRef.current.set(consumerId, consumer);
        if (producerUserId) consumerUserMapRef.current.set(consumerId, producerUserId);
        playRemoteAudio(consumerId, consumer.track);

        // Применить сохранённую громкость
        if (producerUserId) {
          const vol = useVoiceStore.getState().userVolumes[producerUserId];
          if (vol !== undefined) {
            const audio = audioElementsRef.current.get(consumerId);
            if (audio) audio.volume = Math.min(2, vol / 100);
          }
        }

        // Resume на сервере
        socket.emit('voice:resume', { roomId, consumerId }, () => {});

        resolve(consumer);
      });
    });
  }, [playRemoteAudio]);

  // ═══ Войти в голосовую комнату ═══
  const joinRoom = useCallback(async (roomId, roomName) => {
    const socket = socketRef?.current;
    if (!socket) return;

    // Если уже в комнате — сначала выйти
    if (currentRoomIdRef.current) {
      leaveRoom();
    }

    try {
      // Захватить микрофон
      const stream = await getLocalStream();

      // Подключиться к комнате
      socket.emit('voice:join', { roomId }, async (response) => {
        if (response.error) {
          console.error('voice:join error:', response.error);
          stream.getTracks().forEach((t) => t.stop());
          return;
        }

        const { routerRtpCapabilities, peers } = response;

        // Создать mediasoup Device
        const device = new Device();
        await device.load({ routerRtpCapabilities });
        deviceRef.current = device;

        setCurrentRoom(roomId, roomName);

        // Добавить существующих участников
        peers.forEach((p) => {
          addParticipant(p.userId, {
            username: p.username,
            hue: p.hue,
            muted: p.muted,
            deafened: p.deafened,
          });
        });

        // Создать Send Transport
        socket.emit('voice:createTransport', { roomId, direction: 'send' }, async (res) => {
          if (res.error) return;

          const sendTransport = device.createSendTransport(res.params);
          sendTransportRef.current = sendTransport;

          sendTransport.on('connect', ({ dtlsParameters }, callback, errback) => {
            socket.emit('voice:connectTransport', {
              roomId,
              transportId: sendTransport.id,
              dtlsParameters,
            }, (r) => {
              if (r.error) errback(new Error(r.error));
              else callback();
            });
          });

          sendTransport.on('produce', ({ kind, rtpParameters }, callback, errback) => {
            socket.emit('voice:produce', {
              roomId,
              transportId: sendTransport.id,
              kind,
              rtpParameters,
            }, (r) => {
              if (r.error) errback(new Error(r.error));
              else callback({ id: r.producerId });
            });
          });

          // Начать отправку аудио
          const track = stream.getAudioTracks()[0];
          const producer = await sendTransport.produce({ track });
          producerRef.current = producer;

          // VAD для локального пользователя
          const myUserId = getCurrentUserId();
          if (myUserId) startVAD(stream, myUserId);
        });

        // Создать Recv Transport
        socket.emit('voice:createTransport', { roomId, direction: 'recv' }, async (res) => {
          if (res.error) return;

          const recvTransport = device.createRecvTransport(res.params);
          recvTransportRef.current = recvTransport;

          recvTransport.on('connect', ({ dtlsParameters }, callback, errback) => {
            socket.emit('voice:connectTransport', {
              roomId,
              transportId: recvTransport.id,
              dtlsParameters,
            }, (r) => {
              if (r.error) errback(new Error(r.error));
              else callback();
            });
          });

          // Consume существующих producers
          for (const peer of peers) {
            for (const producerId of peer.producers) {
              await consumeProducer(socket, roomId, producerId, device.rtpCapabilities, peer.userId);
            }
          }
        });
      });
    } catch (err) {
      console.error('joinRoom error:', err);
    }
  }, [socketRef, getLocalStream, setCurrentRoom, addParticipant, startVAD, consumeProducer]);

  // ═══ Выйти из комнаты ═══
  const leaveRoom = useCallback(() => {
    const socket = socketRef?.current;
    const roomId = currentRoomIdRef.current;

    if (socket && roomId) {
      socket.emit('voice:leave', { roomId });
    }

    // Остановить VAD
    if (vadIntervalRef.current) {
      clearInterval(vadIntervalRef.current);
      vadIntervalRef.current = null;
    }

    // Закрыть audio context
    if (audioContextRef.current) {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }

    // Остановить локальный stream
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((t) => t.stop());
      localStreamRef.current = null;
    }

    // Закрыть producer
    if (producerRef.current) {
      producerRef.current.close();
      producerRef.current = null;
    }

    // Закрыть consumers
    for (const consumer of consumersRef.current.values()) {
      consumer.close();
    }
    consumersRef.current.clear();

    // Остановить audio элементы
    for (const audio of audioElementsRef.current.values()) {
      audio.pause();
      audio.srcObject = null;
    }
    audioElementsRef.current.clear();

    // Закрыть transports
    if (sendTransportRef.current) {
      sendTransportRef.current.close();
      sendTransportRef.current = null;
    }
    if (recvTransportRef.current) {
      recvTransportRef.current.close();
      recvTransportRef.current = null;
    }

    deviceRef.current = null;
    clearCurrentRoom();
  }, [socketRef, clearCurrentRoom]);

  // ═══ Применить громкость пользователей к аудио элементам ═══
  useEffect(() => {
    for (const [consumerId, uid] of consumerUserMapRef.current) {
      const vol = userVolumes[uid];
      if (vol === undefined) continue;
      const audio = audioElementsRef.current.get(consumerId);
      if (audio) audio.volume = Math.min(2, vol / 100);
    }
  }, [userVolumes]);

  // ═══ Реакция на мут/деафен ═══
  useEffect(() => {
    const socket = socketRef?.current;
    const roomId = currentRoomIdRef.current;
    if (!socket || !roomId) return;

    // Мут: пауза producer
    if (producerRef.current) {
      if (isMuted) {
        producerRef.current.pause();
      } else {
        producerRef.current.resume();
      }
    }

    socket.emit('voice:mute', { roomId, muted: isMuted });
  }, [isMuted, socketRef]);

  useEffect(() => {
    const socket = socketRef?.current;
    const roomId = currentRoomIdRef.current;
    if (!socket || !roomId) return;

    // Деафен: пауза consumers
    for (const consumer of consumersRef.current.values()) {
      if (isDeafened) {
        consumer.pause();
      } else {
        consumer.resume();
      }
    }

    // Мутить аудио элементы
    for (const audio of audioElementsRef.current.values()) {
      audio.muted = isDeafened;
    }

    socket.emit('voice:deafen', { roomId, deafened: isDeafened });
  }, [isDeafened, socketRef]);

  // ═══ Socket-события от сервера ═══
  useEffect(() => {
    const socket = socketRef?.current;
    if (!socket) return;

    const onUserJoined = ({ userId, username, hue }) => {
      addParticipant(userId, { username, hue, muted: false, deafened: false });
    };

    const onUserLeft = ({ userId }) => {
      removeParticipant(userId);
    };

    const onUserMuted = ({ userId, muted }) => {
      updateParticipant(userId, { muted });
    };

    const onUserDeafened = ({ userId, deafened }) => {
      updateParticipant(userId, { deafened });
    };

    const onNewProducer = async ({ userId, producerId, kind }) => {
      if (!deviceRef.current || !recvTransportRef.current) return;
      const roomId = currentRoomIdRef.current;
      if (!roomId) return;

      await consumeProducer(socket, roomId, producerId, deviceRef.current.rtpCapabilities, userId);
    };

    const onConsumerClosed = ({ consumerId }) => {
      const consumer = consumersRef.current.get(consumerId);
      if (consumer) {
        consumer.close();
        consumersRef.current.delete(consumerId);
      }
      const audio = audioElementsRef.current.get(consumerId);
      if (audio) {
        audio.pause();
        audio.srcObject = null;
        audioElementsRef.current.delete(consumerId);
      }
    };

    // Комната удалена владельцем — выйти автоматически
    const onRoomDeleted = ({ roomId }) => {
      if (currentRoomIdRef.current === roomId) {
        leaveRoom();
      }
    };

    socket.on('voice:user-joined', onUserJoined);
    socket.on('voice:user-left', onUserLeft);
    socket.on('voice:user-muted', onUserMuted);
    socket.on('voice:user-deafened', onUserDeafened);
    socket.on('voice:newProducer', onNewProducer);
    socket.on('voice:consumerClosed', onConsumerClosed);
    socket.on('voice:room-deleted', onRoomDeleted);

    return () => {
      socket.off('voice:user-joined', onUserJoined);
      socket.off('voice:user-left', onUserLeft);
      socket.off('voice:user-muted', onUserMuted);
      socket.off('voice:user-deafened', onUserDeafened);
      socket.off('voice:newProducer', onNewProducer);
      socket.off('voice:consumerClosed', onConsumerClosed);
      socket.off('voice:room-deleted', onRoomDeleted);
    };
  }, [socketRef, addParticipant, removeParticipant, updateParticipant, consumeProducer]);

  // ═══ Звонки — обёртки над joinRoom/leaveRoom ═══
  const joinCall = useCallback(async (chatId, chatName) => {
    const voiceRoomId = 'call:' + chatId;
    await joinRoom(voiceRoomId, chatName || 'Звонок');
  }, [joinRoom]);

  const leaveCall = useCallback((chatId) => {
    const socket = socketRef?.current;
    if (socket && chatId) {
      socket.emit('call:end', { chatId });
    }
    leaveRoom();
  }, [socketRef, leaveRoom]);

  return { joinRoom, leaveRoom, joinCall, leaveCall };
}
