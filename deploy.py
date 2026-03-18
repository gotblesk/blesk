import paramiko, sys, time
sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('83.222.16.169', username='root', password='LNwZW8JvMXI%')

cmds = [
    'cd /var/www/blesk && git stash && git pull origin master',
    'cd /var/www/blesk/server && npm install --production 2>&1 | tail -3',
    'cd /var/www/blesk/server && pm2 restart blesk-server',
    'sleep 2 && pm2 logs blesk-server --lines 8 --nostream',
]

for cmd in cmds:
    print(f'\n>>> {cmd}')
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=60)
    out = stdout.read().decode()
    err = stderr.read().decode()
    if out: print(out.strip())
    if err: print(err.strip())

ssh.close()
print('\n✅ Deploy done!')
