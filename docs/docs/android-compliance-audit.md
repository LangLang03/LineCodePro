# Android Compliance Audit

Date: 2026-06-08

## Status

- `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleRelease` pass.
- Release builds now require a complete `signing.properties`; debug signing is no longer used as a release fallback.
- `.linecode` exports redact model API keys, SSH passwords/private keys/passphrases, web search API keys, and sensitive MCP request headers.
- Browser and HTTP tool entry points now allow only HTTP(S), and cleartext HTTP is limited to `localhost`, `127.0.0.1`, or `10.0.2.2`.

## Changes Made

- Added `UrlPolicy` and wired it through built-in browser navigation, external link opening, web fetch/search, model HTTP protocols, model catalog lookup, and custom MCP HTTP calls.
- Hardened the built-in WebView by disabling file/content access, blocking mixed content, and defaulting JavaScript to off unless the user explicitly enables it.
- Added archive redaction coverage in `ArchiveSecretRedactor`, and unit tests for URL policy, archive redaction, and remote cleartext HTTP rejection.
- Added explicit release signing validation and kept release mapping/native-symbol purge behavior unchanged.

## Accepted Risks

- `MANAGE_EXTERNAL_STORAGE` remains because the app works as a coding workspace over user-selected project trees; Play distribution still needs policy justification for this permission.
- Secrets are redacted from exports, but existing local at-rest storage still uses the app-private SQLite database. Keystore-backed encryption is the next security hardening step.
- The app uses a large Java MVP coordinator and custom View UI. Current lint and unit coverage are green, but full accessibility/insets behavior still needs instrumented or manual device coverage.
