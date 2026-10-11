describe('File validation', () => {
  const BLOCKED_EXTS = ['.exe', '.bat', '.cmd', '.sh', '.ps1', '.msi', '.dll', '.sys', '.com', '.vbs', '.js', '.ws', '.wsf', '.scr', '.pif', '.hta', '.cpl', '.inf', '.reg'];

  function isBlockedExtension(filename) {
    const ext = filename.toLowerCase().split('.').pop();
    return BLOCKED_EXTS.includes('.' + ext);
  }

  it('blocks executable files', () => {
    expect(isBlockedExtension('virus.exe')).toBe(true);
    expect(isBlockedExtension('script.bat')).toBe(true);
    expect(isBlockedExtension('PAYLOAD.PS1')).toBe(true);
  });

  it('allows safe files', () => {
    expect(isBlockedExtension('photo.jpg')).toBe(false);
    expect(isBlockedExtension('document.pdf')).toBe(false);
    expect(isBlockedExtension('music.mp3')).toBe(false);
  });

  const LIMITS = { video: 50 * 1024 * 1024, audio: 20 * 1024 * 1024, image: 10 * 1024 * 1024, other: 10 * 1024 * 1024 };

  function getMaxSize(mimeType) {
    if (mimeType.startsWith('video/')) return LIMITS.video;
    if (mimeType.startsWith('audio/')) return LIMITS.audio;
    if (mimeType.startsWith('image/')) return LIMITS.image;
    return LIMITS.other;
  }

  it('allows 50MB for video', () => {
    expect(getMaxSize('video/mp4')).toBe(50 * 1024 * 1024);
  });

  it('allows 20MB for audio', () => {
    expect(getMaxSize('audio/ogg')).toBe(20 * 1024 * 1024);
  });

  it('allows 10MB for images', () => {
    expect(getMaxSize('image/png')).toBe(10 * 1024 * 1024);
  });
});
