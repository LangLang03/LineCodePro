#pragma once

#include <chrono>
#include <functional>
#include <string>
#include <string_view>

#include "domain/termux_integration.h"

namespace linecode::application {

struct TermuxCapabilities final {
  bool integration{};

  bool operator==(const TermuxCapabilities &) const = default;
};

class TermuxIntegrationGateway {
public:
  using VoidCompletion =
      std::function<void(domain::TermuxResult<void>)>;
  using StateCompletion = std::function<void(
      domain::TermuxResult<domain::TermuxPlatformState>)>;
  using SetupCompletion =
      std::function<void(domain::TermuxResult<std::string>)>;

  virtual ~TermuxIntegrationGateway() = default;

  [[nodiscard]] virtual TermuxCapabilities Capabilities() const noexcept = 0;
  virtual void QueryState(StateCompletion completion) = 0;
  virtual void RequestRunCommandPermission(VoidCompletion completion) = 0;
  virtual void OpenTermux(VoidCompletion completion) = 0;
  virtual void SetupOpenSsh(std::string script,
                           std::chrono::milliseconds timeout,
                           SetupCompletion completion) = 0;
};

inline constexpr std::string_view kTermuxAllowExternalAppsCommand =
    "mkdir -p ~/.termux\n"
    "properties_path=\"$HOME/.termux/termux.properties\"\n"
    "touch \"$properties_path\"\n"
    "grep -qxF 'allow-external-apps=true' \"$properties_path\" || printf "
    "'\\nallow-external-apps=true\\n' >> \"$properties_path\"\n"
    "termux-reload-settings >/dev/null 2>&1 || true";

inline constexpr std::string_view kTermuxOpenSshSetupScript = R"SCRIPT(set -eu
export DEBIAN_FRONTEND=noninteractive
mkdir -p "$HOME/.termux"
properties_path="$HOME/.termux/termux.properties"
touch "$properties_path"
grep -qxF 'allow-external-apps=true' "$properties_path" || printf '\nallow-external-apps=true\n' >> "$properties_path"
termux-reload-settings >/dev/null 2>&1 || true

if ! command -v pkg >/dev/null 2>&1; then
  echo "Termux pkg command not found" >&2
  exit 127
fi
pkg update -y
pkg install -y openssh

user_name="$(whoami 2>/dev/null || id -un 2>/dev/null || echo "")"
shell_path="${SHELL:-}"
if [ -z "$shell_path" ] && command -v getent >/dev/null 2>&1; then
  shell_path="$(getent passwd "$user_name" | awk -F: '{print $7}' || true)"
fi
if [ -z "$shell_path" ]; then
  shell_path="${PREFIX:-/data/data/com.termux/files/usr}/bin/sh"
fi
shell_name="$(basename "$shell_path")"
start_line='command -v sshd >/dev/null 2>&1 && { command -v pgrep >/dev/null 2>&1 && pgrep -x sshd >/dev/null 2>&1 || sshd >/dev/null 2>&1 || true; }'
case "$shell_name" in
  fish)
    rc_path="$HOME/.config/fish/config.fish"
    mkdir -p "$(dirname "$rc_path")"
    start_line='command -v sshd >/dev/null 2>&1; and begin; command -v pgrep >/dev/null 2>&1; and pgrep -x sshd >/dev/null 2>&1; or sshd >/dev/null 2>&1; or true; end'
    ;;
  zsh) rc_path="$HOME/.zshrc" ;;
  bash) rc_path="$HOME/.bashrc" ;;
  *) rc_path="$HOME/.profile" ;;
esac
touch "$rc_path"
if ! grep -Fq 'LineAI:sshd-autostart' "$rc_path"; then
  printf '\n# LineAI:sshd-autostart\n%s\n' "$start_line" >> "$rc_path"
fi

chmod 700 "$HOME" 2>/dev/null || true
mkdir -p "$HOME/.ssh"
chmod 700 "$HOME/.ssh"
key_path="$HOME/.ssh/lineai_rsa"
if [ ! -f "$key_path" ] || ! ssh-keygen -y -f "$key_path" >/dev/null 2>&1; then
  rm -f "$key_path.pub"
  ssh-keygen -t rsa -b 4096 -m PEM -f "$key_path" -N "" -C "lineai@termux" >/dev/null
fi
chmod 600 "$key_path"
if [ ! -f "$key_path.pub" ]; then
  ssh-keygen -y -f "$key_path" > "$key_path.pub"
fi
auth_path="$HOME/.ssh/authorized_keys"
touch "$auth_path"
pub_key="$(cat "$key_path.pub")"
grep -qxF "$pub_key" "$auth_path" || printf '%s\n' "$pub_key" >> "$auth_path"
chmod 600 "$auth_path"
if command -v pgrep >/dev/null 2>&1; then
  pgrep -x sshd >/dev/null 2>&1 || sshd >/dev/null 2>&1 || true
else
  sshd >/dev/null 2>&1 || true
fi
sleep 1

set +e
test_output="$(ssh -o BatchMode=yes -o ConnectTimeout=8 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i "$key_path" -p 8022 "$user_name"@127.0.0.1 'echo LineAI SSH OK && whoami && pwd' 2>&1)"
test_exit=$?
set -e

printf 'LINEAI_TERMUX_USERNAME=%s\n' "$user_name"
printf 'LINEAI_TERMUX_SHELL=%s\n' "$shell_name"
printf 'LINEAI_TERMUX_RC=%s\n' "$rc_path"
printf 'LINEAI_TERMUX_HOST=127.0.0.1\n'
printf 'LINEAI_TERMUX_PORT=8022\n'
printf 'LINEAI_TERMUX_TEST_EXIT=%s\n' "$test_exit"
printf 'LINEAI_TERMUX_TEST_BEGIN\n%s\nLINEAI_TERMUX_TEST_END\n' "$test_output"
printf 'LINEAI_PRIVATE_KEY_BEGIN\n'
cat "$key_path"
printf '\nLINEAI_PRIVATE_KEY_END\n'
)SCRIPT";

} // namespace linecode::application
