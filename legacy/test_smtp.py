import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('83.222.16.169', username='root', password='LNwZW8JvMXI%')

script = r"""
import smtplib, ssl
ctx = ssl.create_default_context()
try:
    s = smtplib.SMTP_SSL('smtp.beget.com', 465, context=ctx, timeout=15)
    s.login('noreply@blesk.fun', '2dLvpvoOI7h%')
    print('LOGIN OK')
    c, m = s.mail('noreply@blesk.fun')
    print('MAIL FROM:', c, m)
    if c == 250:
        c2, m2 = s.rcpt('noreply@blesk.fun')
        print('RCPT TO:', c2, m2)
        s.quit()
        print('SUCCESS')
    else:
        print('MAIL FROM REJECTED')
        s.quit()
except Exception as e:
    print('ERROR:', e)
"""

# Write script to VPS and run
ssh.exec_command('cat > /tmp/test_smtp.py << \'PYEOF\'\n' + script + '\nPYEOF')
import time
time.sleep(1)
stdin, stdout, stderr = ssh.exec_command('python3 /tmp/test_smtp.py', timeout=30)
out = stdout.read().decode()
err = stderr.read().decode()
print(out)
if err:
    print('STDERR:', err)
ssh.close()
