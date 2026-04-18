import 'dart:math';
import 'dart:typed_data';
import 'package:audioplayers/audioplayers.dart';

/// Procedural sound engine for blesk UI.
/// Generates unique acid-chrome-glass tones — no external audio files.
/// All sounds: short, warm, metallic Y2K timbre.
class SoundEngine {
  static final SoundEngine _instance = SoundEngine._();
  factory SoundEngine() => _instance;
  SoundEngine._();

  final _wavs = <String, Uint8List>{};
  bool _enabled = false; // disabled until Windows audio backend fixed
  double _volume = 0.12;

  bool get enabled => _enabled;
  set enabled(bool v) => _enabled = v;

  double get volume => _volume;
  set volume(double v) => _volume = v.clamp(0.0, 1.0);

  /// Pre-generate all sounds into memory
  Future<void> init() async {
    try {
      _wavs['tap'] = _genTap();
      _wavs['hover'] = _genHover();
      _wavs['stepForward'] = _genStepForward();
      _wavs['stepBack'] = _genStepBack();
      _wavs['success'] = _genSuccess();
      _wavs['error'] = _genError();
      _wavs['focus'] = _genFocus();
      _wavs['navClick'] = _genNavClick();
      _wavs['send'] = _genSend();
      _wavs['receive'] = _genReceive();
    } catch (_) {
      _enabled = false;
    }
  }

  /// Play a named sound
  void play(String name) {
    if (!_enabled) return;
    final wav = _wavs[name];
    if (wav == null) return;
    try {
      final player = AudioPlayer();
      player.setVolume(_volume);
      player.play(BytesSource(wav));
      // Auto-dispose after playback
      player.onPlayerComplete.listen((_) => player.dispose());
    } catch (_) {}
  }

  void dispose() {
    _wavs.clear();
  }

  // ═══════════════════════════════════════════════════════════
  // SOUND GENERATORS
  // ═══════════════════════════════════════════════════════════

  static const _rate = 44100;

  Uint8List _genTap() => _synth(
    dur: 0.04, freq: 3200, over: 6400, overAmp: 0.15,
    a: 0.003, d: 0.01, r: 0.027, fDec: 0.02, noise: 0.08,
  );

  Uint8List _genHover() => _synth(
    dur: 0.025, freq: 4000, over: 8000, overAmp: 0.1,
    a: 0.002, d: 0.005, r: 0.018, fDec: 0.01, noise: 0.05,
  );

  Uint8List _genStepForward() {
    final n = (0.13 * _rate).toInt();
    final buf = Float64List(n);
    for (var i = 0; i < n; i++) {
      final t = i / _rate;
      final env = _env(t, 0.005, 0.03, 0.095, 0.13);
      final freq = 600 + 1200 * (t / 0.13);
      buf[i] = (sin(2 * pi * freq * t) * 0.3 + (_prng(i) * 2 - 1) * 0.7) * env * _lpf(t, 0.08);
    }
    return _toWav(buf);
  }

  Uint8List _genStepBack() {
    final n = (0.13 * _rate).toInt();
    final buf = Float64List(n);
    for (var i = 0; i < n; i++) {
      final t = i / _rate;
      final env = _env(t, 0.005, 0.03, 0.095, 0.13);
      final freq = 1800 - 1200 * (t / 0.13);
      buf[i] = (sin(2 * pi * freq * t) * 0.3 + (_prng(i) * 2 - 1) * 0.7) * env * _lpf(t, 0.08);
    }
    return _toWav(buf);
  }

  Uint8List _genSuccess() {
    final n = (0.35 * _rate).toInt();
    final buf = Float64List(n);
    for (var i = 0; i < n; i++) {
      final t = i / _rate;
      final env = _env(t, 0.008, 0.05, 0.15, 0.35);
      final f1 = 880 + 220 * (t / 0.35);
      buf[i] = (sin(2 * pi * f1 * t) * 0.5 + _tri(f1 * 1.5 * t) * 0.25 + sin(2 * pi * f1 * 2 * t) * 0.12) * env;
    }
    return _toWav(buf);
  }

  Uint8List _genError() {
    final n = (0.2 * _rate).toInt();
    final buf = Float64List(n);
    for (var i = 0; i < n; i++) {
      final t = i / _rate;
      final env = _env(t, 0.005, 0.04, 0.08, 0.2);
      final freq = 440 - 120 * (t / 0.2);
      buf[i] = (sin(2 * pi * freq * t) * 0.5 + _tri(freq * 0.5 * t) * 0.3) * env;
    }
    return _toWav(buf);
  }

  Uint8List _genFocus() => _synth(
    dur: 0.05, freq: 2400, over: 4800, overAmp: 0.2,
    a: 0.003, d: 0.012, r: 0.035, fDec: 0.03, noise: 0.06,
  );

  Uint8List _genNavClick() => _synth(
    dur: 0.06, freq: 2800, over: 5600, overAmp: 0.18,
    a: 0.003, d: 0.015, r: 0.042, fDec: 0.025, noise: 0.1,
  );

  Uint8List _genSend() {
    final n = (0.15 * _rate).toInt();
    final buf = Float64List(n);
    for (var i = 0; i < n; i++) {
      final t = i / _rate;
      final env = _env(t, 0.005, 0.03, 0.06, 0.15);
      final freq = 800 + 2000 * (t / 0.15);
      buf[i] = (sin(2 * pi * freq * t) * 0.3 + _tri(freq * 1.5 * t) * 0.15 + (_prng(i) * 2 - 1) * 0.3 * _lpf(t, 0.06)) * env;
    }
    return _toWav(buf);
  }

  Uint8List _genReceive() {
    final n = (0.25 * _rate).toInt();
    final buf = Float64List(n);
    for (var i = 0; i < n; i++) {
      final t = i / _rate;
      final env = _env(t, 0.008, 0.04, 0.10, 0.25);
      final f = t < 0.08 ? 1320.0 : 880.0;
      buf[i] = (sin(2 * pi * f * t) * 0.45 + _tri(f * 1.5 * t) * 0.2 + sin(2 * pi * f * 2 * t) * 0.08) * env;
    }
    return _toWav(buf);
  }

  // ═══════════════════════════════════════════════════════════
  // DSP
  // ═══════════════════════════════════════════════════════════

  Uint8List _synth({
    required double dur, required double freq, required double over,
    required double overAmp, required double a, required double d,
    required double r, required double fDec, required double noise,
  }) {
    final n = (dur * _rate).toInt();
    final buf = Float64List(n);
    for (var i = 0; i < n; i++) {
      final t = i / _rate;
      final env = _env(t, a, d, dur - r, dur);
      buf[i] = (sin(2 * pi * freq * t) * 0.5 + _tri(over * t) * overAmp + (_prng(i) * 2 - 1) * noise * _lpf(t, fDec)) * env;
    }
    return _toWav(buf);
  }

  double _env(double t, double a, double d, double s, double r) {
    if (t < a) return t / a;
    if (t < d) return 1.0 - (t - a) / (d - a) * 0.3;
    if (t < s) return 0.7;
    if (t < r) return 0.7 * (1.0 - (t - s) / (r - s));
    return 0.0;
  }

  double _tri(double phase) {
    final p = phase % 1.0;
    return p < 0.5 ? 4 * p - 1 : 3 - 4 * p;
  }

  double _lpf(double t, double decay) => exp(-t / decay);

  double _prng(int i) {
    var x = i * 1103515245 + 12345;
    x = (x >> 16) & 0x7fff;
    return x / 32767.0;
  }

  Uint8List _toWav(Float64List samples) {
    final numSamples = samples.length;
    final dataSize = numSamples * 2;
    final fileSize = 44 + dataSize;
    final buf = ByteData(fileSize);

    void writeStr(int off, String s) {
      for (var i = 0; i < s.length; i++) {
        buf.setUint8(off + i, s.codeUnitAt(i));
      }
    }

    writeStr(0, 'RIFF');
    buf.setUint32(4, fileSize - 8, Endian.little);
    writeStr(8, 'WAVE');
    writeStr(12, 'fmt ');
    buf.setUint32(16, 16, Endian.little);
    buf.setUint16(20, 1, Endian.little);
    buf.setUint16(22, 1, Endian.little);
    buf.setUint32(24, _rate, Endian.little);
    buf.setUint32(28, _rate * 2, Endian.little);
    buf.setUint16(32, 2, Endian.little);
    buf.setUint16(34, 16, Endian.little);
    writeStr(36, 'data');
    buf.setUint32(40, dataSize, Endian.little);

    for (var i = 0; i < numSamples; i++) {
      buf.setInt16(44 + i * 2, (samples[i].clamp(-1.0, 1.0) * 32767).toInt(), Endian.little);
    }

    return buf.buffer.asUint8List();
  }
}
