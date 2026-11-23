#!/bin/bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

if [ ! -x /usr/local/jdk-25/bin/java ]; then
  curl -fsSL -o /tmp/jdk25.tar.gz "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
  mkdir -p /usr/local/jdk-25
  tar -xzf /tmp/jdk25.tar.gz -C /usr/local/jdk-25 --strip-components=1
fi

apt-get update -qq
apt-get install -y -qq postgresql

if ! id crm >/dev/null 2>&1; then
  useradd --system --home /opt/mehanika-crm --shell /usr/sbin/nologin crm
fi
install -d -o crm -g crm /opt/mehanika-crm

if [ ! -f /etc/mehanika-crm.env ]; then
  DB_PASSWORD="$(openssl rand -base64 24 | tr -d '\n')"
  ADMIN_PASSWORD="$(openssl rand -base64 18 | tr -d '\n')"
  LEAD_TOKEN="$(openssl rand -base64 24 | tr -d '\n')"
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c "CREATE ROLE crm LOGIN PASSWORD '${DB_PASSWORD}'"
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE mehanika OWNER crm"
  umask 077
  cat > /etc/mehanika-crm.env << EOF
DB_URL=jdbc:postgresql://127.0.0.1:5432/mehanika
DB_USER=crm
DB_PASSWORD=${DB_PASSWORD}
ADMIN_PASSWORD=${ADMIN_PASSWORD}
LEAD_TOKEN=${LEAD_TOKEN}
EOF
  chmod 600 /etc/mehanika-crm.env
  printf '%s\n' "${ADMIN_PASSWORD}" > /root/crm-admin-password
  chmod 600 /root/crm-admin-password
  touch /root/crm-lead-token
  printf '%s\n' "${LEAD_TOKEN}" > /root/crm-lead-token
  chmod 600 /root/crm-lead-token
fi

install -o crm -g crm -m 644 /tmp/mehanika-upload/app.jar /opt/mehanika-crm/app.jar
install -m 644 /tmp/mehanika-upload/mehanika-crm.service /etc/systemd/system/mehanika-crm.service
systemctl daemon-reload
systemctl enable mehanika-crm.service
systemctl restart mehanika-crm.service
ready=0
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  code="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/session || true)"
  if [ "$code" = "401" ] || [ "$code" = "204" ]; then
    ready=1
    break
  fi
  sleep 1
done
if [ "$ready" != 1 ]; then
  journalctl -u mehanika-crm.service -n 40 --no-pager
  exit 1
fi

python3 - << 'PY'
from pathlib import Path
path = Path("/etc/nginx/sites-available/mehanika")
text = path.read_text()
block = """
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
"""
if "location /api/" not in text:
    text = text.replace("    location / {", block + "\n    location / {")
    path.write_text(text)
PY
nginx -t
systemctl reload nginx

install -m 644 /tmp/mehanika-upload/crm_client.py /opt/tgbotmehanikakomforta/crm_client.py
chown mehanika:mehanika /opt/tgbotmehanikakomforta/crm_client.py
python3 - << 'PY'
from pathlib import Path
path = Path("/opt/tgbotmehanikakomforta/handlers.py")
text = path.read_text()
if "forward_lead" not in text:
    old = '''        await query.answer("Не удалось записать заявку, попробуйте ещё раз")
        return
    admin = admin_chat_id()
'''
    new = '''        await query.answer("Не удалось записать заявку, попробуйте ещё раз")
        return
    try:
        from crm_client import forward_lead
        forward_lead(lead)
    except Exception:
        log.exception("crm forward failed")
    admin = admin_chat_id()
'''
    if old not in text:
        raise SystemExit("hook point missing")
    path.write_text(text.replace(old, new, 1))
PY
python3 -m py_compile /opt/tgbotmehanikakomforta/crm_client.py /opt/tgbotmehanikakomforta/handlers.py

python3 - << 'PY'
from pathlib import Path
env = Path("/opt/tgbotmehanikakomforta/.env")
text = env.read_text()
token = Path("/root/crm-lead-token").read_text().strip()
if "CRM_URL=" not in text:
    extra = "\nCRM_URL=http://127.0.0.1:8080/api/leads\nLEAD_TOKEN=" + token + "\n"
    env.write_text(text.rstrip() + extra)
env.chmod(0o600)
PY
systemctl restart mehanika-bot.service
sleep 2
systemctl is-active mehanika-crm.service
systemctl is-active mehanika-bot.service
systemctl is-active nginx
