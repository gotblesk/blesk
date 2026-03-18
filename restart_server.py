import paramiko, sys, time
sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('83.222.16.169', username='root', password='LNwZW8JvMXI%')

stdin, stdout, stderr = ssh.exec_command('cd /var/www/blesk/server && pm2 restart blesk-server && sleep 3 && pm2 logs blesk-server --lines 15 --nostream', timeout=20)
print(stdout.read().decode())
print(stderr.read().decode())
ssh.close()
