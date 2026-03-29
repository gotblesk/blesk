import paramiko, sys, time, os
sys.stdout.reconfigure(encoding='utf-8')

# Credentials из переменных окружения (НЕ hardcode)
VPS_HOST = os.environ.get('BLESK_VPS_HOST', '83.222.16.169')
VPS_USER = os.environ.get('BLESK_VPS_USER', 'root')
VPS_PASSWORD = os.environ.get('BLESK_VPS_PASSWORD')
VPS_KEY_PATH = os.environ.get('BLESK_VPS_KEY', '')

if not VPS_PASSWORD and not VPS_KEY_PATH:
    print('❌ Установи BLESK_VPS_PASSWORD или BLESK_VPS_KEY в переменных окружения')
    print('   set BLESK_VPS_PASSWORD=<пароль>     (Windows)')
    print('   export BLESK_VPS_PASSWORD=<пароль>   (Linux/Mac)')
    sys.exit(1)

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())

if VPS_KEY_PATH:
    ssh.connect(VPS_HOST, username=VPS_USER, key_filename=VPS_KEY_PATH)
else:
    ssh.connect(VPS_HOST, username=VPS_USER, password=VPS_PASSWORD)

cmds = [
    'cd /var/www/blesk && git stash && git pull origin master',
    'cd /var/www/blesk/server && npm install --production 2>&1 | tail -3',
    # Починить владельца файлов после git pull (root) → blesk
    'chown -R blesk:blesk /var/www/blesk',
    # Перезапуск от пользователя blesk
    'sudo -u blesk bash -c "cd /var/www/blesk/server && pm2 restart blesk-server"',
    'sleep 2 && sudo -u blesk pm2 logs blesk-server --lines 8 --nostream',
]

for cmd in cmds:
    print(f'\n>>> {cmd}')
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=60)
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    if out: print(out.strip())
    if err and 'warning' not in err.lower(): print(err.strip())

ssh.close()
print('\n✅ Deploy done!')
