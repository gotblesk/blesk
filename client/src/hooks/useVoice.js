import { useEffect, useRef, useCallback } from 'react';
import { Device } from 'mediasoup-client';
import { useVoiceStore } from '../store/voiceStore';
import { getCurrentUserId } from '../utils/auth';

// Voice Activity Detection
const VAD_INTERVAL = 100;
const QUALITY_CHECK_INTERVAL = 5000;

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
  const gainNodesRef = useRef(new Map()); // consumerId → { ctx, gain }
  const consumerUserMapRef = useRef(new Map()); // consumerId → userId
  const pendingProducersRef = useRef([]); // Очередь для продюсеров до готовности recvTransport
  const qualityIntervalRef = useRef(null); // Интервал проверки качества соединения

  // Видео refs
  const cameraProducerRef = useRef(null);
  const screenProducerRef = useRef(null);
  const localCameraStreamRef = useRef(null);
  const localScreenStreamRef = useRef(null);

  const {
    currentRoomId,
    isMuted,
    isDeafened,
    noiseSuppression,
    echoCancellation,
    inputDeviceId,
    outputDeviceId,
    userVolumes,
    setCurrentRoom,
    clearCurrentRoom,
    addParticipant,
    removeParticipant,
    updateParticipant,
    setAudioLevel,
    setConnectionQuality,
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

      const threshold = useVoiceStore.getState().vadThreshold;
      const speaking = avg > threshold;
      updateParticipant(userId, { speaking });
    }, VAD_INTERVAL);
  }, [setAudioLevel, updateParticipant]);

  // ═══ Применить громкость к аудио (поддерживает >100% через GainNode) ═══
  const applyVolume = useCallback((audio, consumerId, vol) => {
    try {
      const clamped = Math.max(0, Math.min(200, Number(vol) || 100));
      if (clamped <= 100) {
        // 0-100% — стандартный volume (0.0 — 1.0)
        audio.volume = clamped / 100;
        // Убрать GainNode если был
        const existing = gainNodesRef.current.get(consumerId);
        if (existing) {
          existing.gain.gain.value = 1;
        }
      } else {
        // >100% — volume на макс, усиление через GainNode
        audio.volume = 1;
        let entry = gainNodesRef.current.get(consumerId);
        if (!entry && audio.srcObject) {
          try {
            const ctx = new AudioContext();
            const source = ctx.createMediaStreamSource(audio.srcObject);
            const gain = ctx.createGain();
            source.connect(gain);
            gain.connect(ctx.destination);
            entry = { ctx, gain };
            gainNodesRef.current.set(consumerId, entry);
          } catch {
            return; // AudioContext не доступен
          }
        }
        if (entry) {
          entry.gain.gain.value = clamped / 100; // 1.0 — 2.0
        }
      }
    } catch {
      // Безопасный fallback
      try { audio.volume = 1; } catch {}
    }
  }, []);

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

    // Установить устройство вывода если поддерживается
    const outDeviceId = useVoiceStore.getState().outputDeviceId;
    if (outDeviceId && outDeviceId !== 'default' && typeof audio.setSinkId === 'function') {
      audio.setSinkId(outDeviceId).catch(() => {});
    }

    audio.play().catch(() => {});
  }, []);

  // ═══ Создать consumer для remote producer ═══
  const consumeProducer = useCallback(async (socket, roomId, producerId, rtpCapabilities, producerUserId, producerType) => {
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

        try {
          const consumer = await recvTransportRef.current.consume({
            id: consumerId,
            producerId,
            kind,
            rtpParameters,
          });

          consumersRef.current.set(consumerId, consumer);
          if (producerUserId) consumerUserMapRef.current.set(consumerId, producerUserId);

          if (consumer.kind === 'video') {
            // Видео — сохранить стрим в store
            const stream = new MediaStream([consumer.track]);
            useVoiceStore.getState().setVideoStream(producerUserId, producerType || 'camera', stream);
          } else {
            // Аудио — воспроизвести как раньше
            playRemoteAudio(consumerId, consumer.track);

            // Применить сохранённую громкость (через GainNode для >100%)
            if (producerUserId) {
              const vol = useVoiceStore.getState().userVolumes[producerUserId];
              if (vol !== undefined) {
                const audio = audioElementsRef.current.get(consumerId);
                if (audio) {
                  applyVolume(audio, consumerId, vol);
                }
              }
            }
          }

          // Resume на сервере
          socket.emit('voice:resume', { roomId, consumerId }, () => {});

          resolve(consumer);
        } catch (err) {
          console.error('consumeProducer error:', err);
          resolve(null);
        }
      });
    });
  }, [playRemoteAudio, applyVolume]);

  // ═══ Мониторинг качества соединения ═══
  const startQualityMonitor = useCallback(() => {
    if (qualityIntervalRef.current) clearInterval(qualityIntervalRef.current);

    qualityIntervalRef.current = setInterval(async () => {
      const transport = sendTransportRef.current;
      if (!transport) return;

      try {
        const stats = await transport.getStats();
        let rtt = 0;
        let fractionLost = 0;
        let found = false;

        stats.forEach((report) => {
          // candidate-pair с roundTripTime
          if (report.type === 'candidate-pair' && report.state === 'succeeded') {
            rtt = (report.currentRoundTripTime || 0) * 1000; // секунды → мс
            found = true;
          }
          // outbound-rtp для потерь
          if (report.type === 'outbound-rtp' && report.kind === 'audio') {
            // Для outbound используем retransmittedPacketsSent / packetsSent как proxy
            if (report.fractionLost !== undefined) {
              fractionLost = report.fractionLost * 100;
            }
          }
          // remote-inbound-rtp (более точные данные о потерях и RTT)
          if (report.type === 'remote-inbound-rtp') {
            if (report.roundTripTime !== undefined) {
              rtt = report.roundTripTime * 1000;
              found = true;
            }
            if (report.fractionLost !== undefined) {
              fractionLost = report.fractionLost * 100;
            }
          }
        });

        if (!found) return;

        let quality = 'good';
        if (rtt > 250 || fractionLost > 5) {
          quality = 'poor';
        } else if (rtt > 100 || fractionLost > 2) {
          quality = 'fair';
        }

        useVoiceStore.getState().setConnectionQuality(quality);
      } catch {
        // Stats недоступны
      }
    }, QUALITY_CHECK_INTERVAL);
  }, []);

  // Refs для актуальных функций (против stale closure) — объявлены до joinRoom
  const leaveRoomRef = useRef(null);
  const consumeProducerRef = useRef(consumeProducer);
  consumeProducerRef.current = consumeProducer;

  // ═══ Войти в голосовую комнату ═══
  const joinRoom = useCallback(async (roomId, roomName) => {
    const socket = socketRef?.current;
    if (!socket) return;

    // Если уже в комнате — сначала выйти (через ref, чтобы избежать stale closure)
    if (currentRoomIdRef.current) {
      leaveRoomRef.current();
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
          if (res.error) {
            // Освободить микрофон при ошибке создания транспорта
            if (localStreamRef.current) {
              localStreamRef.current.getTracks().forEach((t) => t.stop());
              localStreamRef.current = null;
            }
            clearCurrentRoom();
            return;
          }

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

          sendTransport.on('produce', ({ kind, rtpParameters, appData }, callback, errback) => {
            socket.emit('voice:produce', {
              roomId,
              transportId: sendTransport.id,
              kind,
              rtpParameters,
              appData: appData || {},
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

          // Мониторинг качества соединения
          startQualityMonitor();
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

          // Consume существующих producers (каждый — объект { producerId, producerType })
          for (const peer of peers) {
            for (const p of peer.producers) {
              const pId = typeof p === 'string' ? p : p.producerId;
              const pType = typeof p === 'string' ? 'audio' : (p.producerType || 'audio');
              await consumeProducer(socket, roomId, pId, device.rtpCapabilities, peer.userId, pType);
            }
          }

          // Обработать буферизированные newProducer события (race condition при звонках)
          if (pendingProducersRef.current.length > 0) {
            for (const pending of pendingProducersRef.current) {
              await consumeProducer(socket, roomId, pending.producerId, device.rtpCapabilities, pending.userId, pending.producerType);
            }
            pendingProducersRef.current = [];
          }
        });
      });
    } catch (err) {
      console.error('joinRoom error:', err);
    }
  }, [socketRef, getLocalStream, setCurrentRoom, addParticipant, startVAD, consumeProducer, startQualityMonitor]);

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

    // Остановить мониторинг качества
    if (qualityIntervalRef.current) {
      clearInterval(qualityIntervalRef.current);
      qualityIntervalRef.current = null;
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

    // Закрыть аудио producer
    if (producerRef.current) {
      producerRef.current.close();
      producerRef.current = null;
    }

    // Закрыть видео producers и стримы
    if (cameraProducerRef.current) {
      cameraProducerRef.current.close();
      cameraProducerRef.current = null;
    }
    if (screenProducerRef.current) {
      screenProducerRef.current.close();
      screenProducerRef.current = null;
    }
    if (localCameraStreamRef.current) {
      localCameraStreamRef.current.getTracks().forEach((t) => t.stop());
      localCameraStreamRef.current = null;
    }
    if (localScreenStreamRef.current) {
      localScreenStreamRef.current.getTracks().forEach((t) => t.stop());
      localScreenStreamRef.current = null;
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

    // Закрыть GainNode контексты
    for (const entry of gainNodesRef.current.values()) {
      try { entry.ctx.close(); } catch {}
    }
    gainNodesRef.current.clear();

    // Очистить маппинг consumer→user (фикс stale volume)
    consumerUserMapRef.current.clear();

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
    pendingProducersRef.current = [];
    clearCurrentRoom();
  }, [socketRef, clearCurrentRoom]);

  // ═══ Применить громкость пользователей к аудио элементам ═══
  useEffect(() => {
    for (const [consumerId, uid] of consumerUserMapRef.current) {
      const vol = userVolumes[uid];
      if (vol === undefined) continue;
      const audio = audioElementsRef.current.get(consumerId);
      if (audio) applyVolume(audio, consumerId, vol);
    }
  }, [userVolumes, applyVolume]);

  // ═══ Обновить устройство вывода на всех аудио элементах ═══
  useEffect(() => {
    for (const audio of audioElementsRef.current.values()) {
      if (typeof audio.setSinkId === 'function') {
        audio.setSinkId(outputDeviceId || 'default').catch(() => {});
      }
    }
  }, [outputDeviceId]);

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

    if (!producerRef.current) return; // продюсер ещё не создан

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

  // Синхронизировать leaveRoomRef после определения leaveRoom
  leaveRoomRef.current = leaveRoom;

  useEffect(() => {
    const socket = socketRef?.current;
    if (!socket) return;

    const onUserJoined = ({ userId, username, hue }) => {
      useVoiceStore.getState().addParticipant(userId, { username, hue, muted: false, deafened: false });
    };

    const onUserLeft = ({ userId }) => {
      useVoiceStore.getState().removeParticipant(userId);
    };

    const onUserMuted = ({ userId, muted }) => {
      useVoiceStore.getState().updateParticipant(userId, { muted });
    };

    const onUserDeafened = ({ userId, deafened }) => {
      useVoiceStore.getState().updateParticipant(userId, { deafened });
    };

    const onNewProducer = async ({ userId, producerId, kind, producerType }) => {
      const roomId = currentRoomIdRef.current;
      if (!roomId) return;

      // Если recvTransport ещё не готов — буферим (race condition при звонках)
      if (!deviceRef.current || !recvTransportRef.current) {
        pendingProducersRef.current.push({ userId, producerId, kind, producerType });
        return;
      }

      await consumeProducerRef.current(socket, roomId, producerId, deviceRef.current.rtpCapabilities, userId, producerType);
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
      // Закрыть GainNode контекст если был
      const gainEntry = gainNodesRef.current.get(consumerId);
      if (gainEntry) {
        try { gainEntry.ctx.close(); } catch {}
        gainNodesRef.current.delete(consumerId);
      }
      consumerUserMapRef.current.delete(consumerId);
    };

    // Комната удалена владельцем — выйти автоматически
    const onRoomDeleted = ({ roomId }) => {
      if (currentRoomIdRef.current === roomId) {
        leaveRoomRef.current();
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
  }, [socketRef]); // минимальные зависимости — всё через refs и getState()

  // ═══ Видео: камера ═══
  const enableCamera = useCallback(async () => {
    try {
      if (cameraProducerRef.current) return;
      if (!sendTransportRef.current) return;
      // Читаем настройки качества
      const { cameraResolution, cameraFps } = (() => {
        try { const s = JSON.parse(localStorage.getItem('blesk-settings') || '{}'); return { cameraResolution: s.cameraResolution || '720p', cameraFps: s.cameraFps || 30 }; }
        catch { return { cameraResolution: '720p', cameraFps: 30 }; }
      })();
      const resMap = { '480p': { w: 640, h: 480 }, '720p': { w: 1280, h: 720 }, '1080p': { w: 1920, h: 1080 }, '1440p': { w: 2560, h: 1440 } };
      const res = resMap[cameraResolution] || resMap['720p'];
      const bitrateMap = { '480p': 800000, '720p': 1500000, '1080p': 3000000, '1440p': 5000000 };
      const bitrate = bitrateMap[cameraResolution] || 1500000;

      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: res.w }, height: { ideal: res.h }, frameRate: { ideal: cameraFps } },
      });
      localCameraStreamRef.current = stream;
      useVoiceStore.getState().setLocalCameraStream(stream);
      const track = stream.getVideoTracks()[0];
      const producer = await sendTransportRef.current.produce({
        track,
        appData: { type: 'camera' },
        encodings: [{ maxBitrate: bitrate }],
        codecOptions: { videoGoogleStartBitrate: 1000 },
      });
      cameraProducerRef.current = producer;
      useVoiceStore.getState().setCameraOn(true);
    } catch (err) {
      console.error('enableCamera error:', err);
    }
  }, []);

  const disableCamera = useCallback(() => {
    try {
      if (cameraProducerRef.current) {
        cameraProducerRef.current.close();
        cameraProducerRef.current = null;
      }
      if (localCameraStreamRef.current) {
        localCameraStreamRef.current.getTracks().forEach((t) => t.stop());
        localCameraStreamRef.current = null;
      }
      useVoiceStore.getState().setLocalCameraStream(null);
      useVoiceStore.getState().setCameraOn(false);
    } catch (err) {
      console.error('disableCamera error:', err);
    }
  }, []);

  // ═══ Видео: демонстрация экрана ═══
  const enableScreenShare = useCallback(async () => {
    try {
      if (screenProducerRef.current) return;
      if (!sendTransportRef.current) return;
      // Читаем настройки качества
      const { screenResolution, screenFps } = (() => {
        try { const s = JSON.parse(localStorage.getItem('blesk-settings') || '{}'); return { screenResolution: s.screenResolution || '1080p', screenFps: s.screenFps || 30 }; }
        catch { return { screenResolution: '1080p', screenFps: 30 }; }
      })();
      const resMap = { '720p': { w: 1280, h: 720 }, '1080p': { w: 1920, h: 1080 }, '1440p': { w: 2560, h: 1440 } };
      const res = resMap[screenResolution] || resMap['1080p'];
      const bitrateMap = { '720p': 1500000, '1080p': 2500000, '1440p': 4000000 };
      const bitrate = bitrateMap[screenResolution] || 2500000;
      // 60fps doubles bitrate
      const finalBitrate = screenFps >= 60 ? bitrate * 1.5 : bitrate;

      let stream;
      if (window.blesk?.screen?.getSources) {
        const sources = await window.blesk.screen.getSources();
        if (!sources || !sources.length) return;
        stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: {
            mandatory: {
              chromeMediaSource: 'desktop',
              chromeMediaSourceId: sources[0].id,
              minWidth: res.w * 0.5,
              maxWidth: res.w,
              minHeight: res.h * 0.5,
              maxHeight: res.h,
              maxFrameRate: screenFps,
            },
          },
        });
      } else {
        stream = await navigator.mediaDevices.getDisplayMedia({
          video: { width: { ideal: res.w }, height: { ideal: res.h }, frameRate: { ideal: screenFps, max: screenFps } },
        });
      }
      localScreenStreamRef.current = stream;
      const track = stream.getVideoTracks()[0];
      track.onended = () => disableScreenShareRef.current();
      const producer = await sendTransportRef.current.produce({
        track,
        appData: { type: 'screen' },
        encodings: [{ maxBitrate: finalBitrate }],
        codecOptions: { videoGoogleStartBitrate: 1000 },
      });
      screenProducerRef.current = producer;
      useVoiceStore.getState().setScreenShareOn(true);
    } catch (err) {
      console.error('enableScreenShare error:', err);
    }
  }, []);

  const disableScreenShare = useCallback(() => {
    try {
      if (screenProducerRef.current) {
        screenProducerRef.current.close();
        screenProducerRef.current = null;
      }
      if (localScreenStreamRef.current) {
        localScreenStreamRef.current.getTracks().forEach((t) => t.stop());
        localScreenStreamRef.current = null;
      }
      useVoiceStore.getState().setScreenShareOn(false);
    } catch (err) {
      console.error('disableScreenShare error:', err);
    }
  }, []);

  // Ref для disableScreenShare (используется в track.onended)
  const disableScreenShareRef = useRef(disableScreenShare);
  disableScreenShareRef.current = disableScreenShare;

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

  // Cleanup voice resources on unmount
  useEffect(() => {
    return () => {
      if (vadIntervalRef.current) { clearInterval(vadIntervalRef.current); vadIntervalRef.current = null; }
      if (qualityIntervalRef.current) { clearInterval(qualityIntervalRef.current); qualityIntervalRef.current = null; }
      if (audioContextRef.current) { try { audioContextRef.current.close(); } catch {} audioContextRef.current = null; }
    };
  }, []);

  return { joinRoom, leaveRoom, joinCall, leaveCall, enableCamera, disableCamera, enableScreenShare, disableScreenShare };
}
