import paramiko, sys, time
sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('83.222.16.169', username='root', password='LNwZW8JvMXI%')

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
