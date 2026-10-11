describe('Auth validation', () => {
  function checkPasswordComplexity(password) {
    if (password.length < 8) return false;
    let classes = 0;
    if (/[a-z]/.test(password)) classes++;
    if (/[A-Z]/.test(password)) classes++;
    if (/[0-9]/.test(password)) classes++;
    if (/[^a-zA-Z0-9]/.test(password)) classes++;
    return classes >= 2;
  }

  it('rejects password shorter than 8 chars', () => {
    expect(checkPasswordComplexity('Ab1')).toBe(false);
  });

  it('rejects single-class password', () => {
    expect(checkPasswordComplexity('abcdefgh')).toBe(false);
    expect(checkPasswordComplexity('12345678')).toBe(false);
  });

  it('accepts 2-class password', () => {
    expect(checkPasswordComplexity('abcdef12')).toBe(true);
    expect(checkPasswordComplexity('ABCDabcd')).toBe(true);
  });

  it('accepts strong password', () => {
    expect(checkPasswordComplexity('MyP@ss123')).toBe(true);
  });

  function validateUsername(username) {
    if (!username || username.length < 3 || username.length > 24) return false;
    return /^[a-zA-Z0-9_]+$/.test(username);
  }

  it('rejects short username', () => {
    expect(validateUsername('ab')).toBe(false);
  });

  it('rejects long username', () => {
    expect(validateUsername('a'.repeat(25))).toBe(false);
  });

  it('rejects special chars in username', () => {
    expect(validateUsername('user@name')).toBe(false);
    expect(validateUsername('user name')).toBe(false);
  });

  it('accepts valid username', () => {
    expect(validateUsername('john_doe')).toBe(true);
    expect(validateUsername('User123')).toBe(true);
  });
});
