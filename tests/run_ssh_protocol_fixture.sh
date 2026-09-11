#!/usr/bin/env bash
set -euo pipefail

test_binary=$1
sshd_binary=$2
ssh_keygen_binary=$3

fixture_root=$(mktemp -d)
sshd_pid=
cleanup() {
  if [[ -n ${sshd_pid} ]]; then
    kill "${sshd_pid}" 2>/dev/null || true
    wait "${sshd_pid}" 2>/dev/null || true
  fi
  if [[ ${fixture_root} == /tmp/* && -d ${fixture_root} ]]; then
    rm -rf -- "${fixture_root}"
  fi
}
trap cleanup EXIT

port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')
user_name=$(id -un)

"${ssh_keygen_binary}" -q -t rsa -b 2048 -m PEM -N '' -f "${fixture_root}/client_key"
"${ssh_keygen_binary}" -q -t rsa -b 2048 -N '' -f "${fixture_root}/host_key"
"${ssh_keygen_binary}" -q -t rsa -b 2048 -N '' -f "${fixture_root}/alternate_host_key"
cp "${fixture_root}/client_key.pub" "${fixture_root}/authorized_keys"
mkdir "${fixture_root}/remote"
chmod 700 "${fixture_root}" "${fixture_root}/remote"
chmod 600 "${fixture_root}/authorized_keys" "${fixture_root}/client_key" "${fixture_root}/host_key"

"${sshd_binary}" -t -f /dev/null \
  -o "Port=${port}" \
  -o ListenAddress=127.0.0.1 \
  -o "HostKey=${fixture_root}/host_key" \
  -o "AuthorizedKeysFile=${fixture_root}/authorized_keys" \
  -o StrictModes=no \
  -o UsePAM=no \
  -o PasswordAuthentication=no \
  -o KbdInteractiveAuthentication=no \
  -o PubkeyAuthentication=yes \
  -o PermitRootLogin=yes \
  -o "AllowUsers=${user_name}" \
  -o "PidFile=${fixture_root}/sshd.pid" \
  -o 'Subsystem=sftp internal-sftp'

"${sshd_binary}" -D -e -f /dev/null \
  -o "Port=${port}" \
  -o ListenAddress=127.0.0.1 \
  -o "HostKey=${fixture_root}/host_key" \
  -o "AuthorizedKeysFile=${fixture_root}/authorized_keys" \
  -o StrictModes=no \
  -o UsePAM=no \
  -o PasswordAuthentication=no \
  -o KbdInteractiveAuthentication=no \
  -o PubkeyAuthentication=yes \
  -o PermitRootLogin=yes \
  -o "AllowUsers=${user_name}" \
  -o "PidFile=${fixture_root}/sshd.pid" \
  -o 'Subsystem=sftp internal-sftp' \
  >"${fixture_root}/sshd.log" 2>&1 &
sshd_pid=$!

for _ in $(seq 1 100); do
  if python3 -c "import socket; s=socket.socket(); s.settimeout(.05); s.connect(('127.0.0.1', ${port})); s.close()" 2>/dev/null; then
    break
  fi
  if ! kill -0 "${sshd_pid}" 2>/dev/null; then
    cat "${fixture_root}/sshd.log"
    exit 1
  fi
  sleep 0.05
done

"${test_binary}" "${port}" "${user_name}" \
  "${fixture_root}/client_key" "${fixture_root}/known_hosts" \
  "${fixture_root}/remote" "${fixture_root}/alternate_host_key.pub"
