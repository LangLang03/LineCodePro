# Security Policy

**Project:** LineCode Pro — an Android AI coding workspace  
**Application ID:** `cn.lineai`  
**Repository:** <https://github.com/OpenLineCode/LineCodePro>  
**Security contact:** <jiyu03@qq.com> (see [Section 2](#2-reporting-a-vulnerability))  
**Policy version:** 1.0  
**Last updated:** 14 November 2026  
**Applies to:** LineCode Pro 2.1.x (current release: 2.1.1, versionCode 50)

LineCode Pro is an Android application in which a configured language model can inspect a
workspace, propose and apply file changes, run approved shell commands, search the web, use MCP
servers, and — when explicitly enabled — control the device through an accessibility service.
Because the application is designed to act with authority granted by its user, its security
depends on correct default policies, honest documentation of residual risk, and responsible
review by the person operating it.

This document describes the supported versions, the private vulnerability-reporting channels, the
disclosure process, the security model and its trust boundaries, known limitations, and the
hardening measures available to users. It covers the application, the source code in this
repository, the official build and release pipeline, and the official release artifacts.

**This policy applies to this project only.** It does not authorize testing of third-party
services, and it does not modify the licensing terms in [LICENSE](LICENSE).

## Table of Contents

1. [Supported Versions](#1-supported-versions)
2. [Reporting a Vulnerability](#2-reporting-a-vulnerability)
3. [Response and Disclosure Process](#3-response-and-disclosure-process)
4. [Scope](#4-scope)
5. [Security Model](#5-security-model)
6. [Security-Relevant Settings](#6-security-relevant-settings)
7. [Known Limitations and Residual Risks](#7-known-limitations-and-residual-risks)
8. [Hardening Recommendations for Users](#8-hardening-recommendations-for-users)
9. [Safe Harbor](#9-safe-harbor)
10. [Recognition](#10-recognition)
11. [Contact and Non-Security Reports](#11-contact-and-non-security-reports)
12. [Policy Changes](#12-policy-changes)

## 1. Supported Versions

Security fixes are developed in this repository, merged to the `master` branch, verified by the
project's CI checks, and published as new releases on the
[GitHub Releases](https://github.com/OpenLineCode/LineCodePro/releases) page. Release notes and
advisories describe the affected and fixed versions.

| Release line | Supported | Notes |
| --- | --- | --- |
| 2.1.x (current: 2.1.1, versionCode 50) | Yes | Receives security fixes through new GitHub releases. |
| 2.0.x and earlier | No | No backports. Upgrade to the latest release before reporting or retesting. |
| Source builds from `master` or the `dev` branch | Development only | Not a supported release; fixes land there first and no support guarantee is provided. |

Only artifacts downloaded from the official GitHub Releases page are supported. Debug APKs
attached to CI runs, locally built APKs, and builds obtained from any other source are not
supported and may be unsigned, debuggable, or otherwise unsuitable for daily use.

Official releases target Android 8.0 (API 26) or later and, as of version 2.1.1, target API 37.

## 2. Reporting a Vulnerability

### 2.1 Private reporting channels

**Primary channel: email**

Write to **<jiyu03@qq.com>** with the subject line:

```text
[SECURITY] LineCode Pro — <short description>
```

Send the technical details in the same message or in follow-up messages once a maintainer has
responded. If you require an end-to-end encrypted channel, send a first message that states the
request and contains no sensitive details; the maintainers will arrange an appropriate channel
before you send technical details.

**Alternative: GitHub private vulnerability reporting**

If the repository's Security tab offers a **Report a vulnerability** action, you may also use
GitHub private vulnerability reporting:

<https://github.com/OpenLineCode/LineCodePro/security/advisories/new>

A private advisory is visible only to the maintainers until it is published. This channel retains
the full report history, supports attachments, and allows the maintainers to issue a CVE through
GitHub's CNA process when appropriate. If the Security tab does not offer private reporting for
this repository, use email instead.

### 2.2 Do not disclose publicly

Do not open a public issue, discussion, or pull request, and do not publish details on social
media or other platforms before a fix has been released and the maintainers have coordinated
disclosure with you. Public issues that contain vulnerability details will be edited or closed to
protect users.

### 2.3 What to include

A complete report should contain:

- the affected LineCode Pro version and versionCode (for example `2.1.1`, versionCode 50), and
  whether the slim or local-model APK is affected;
- the Android version, device model, and ROM or emulator details;
- the affected component, module, screen, or feature (for example tool approval, path
  containment, exports, SSH, MCP, accessibility control, or the release workflow);
- step-by-step reproduction instructions that work on a clean installation where possible;
- a minimal proof of concept, with secrets, personal data, and third-party content redacted;
- the observed impact and the preconditions an attacker needs, including which permissions,
  settings, or user actions are required;
- whether real user data, credentials, or third-party systems were exposed, and whether the issue
  is already being exploited;
- any suggested remediation, if available;
- your preferred name and URL for credit, or a request for anonymity;
- any disclosure constraints or deadlines you are subject to.

### 2.4 What not to do

While investigating, do not:

- access, modify, delete, or exfiltrate data that does not belong to you, or data of other users;
- use real API keys, SSH private keys, personal workspace content, or production accounts in a
  proof of concept;
- run destructive, persistence, or denial-of-service tests against services, devices, or users;
- perform social engineering against maintainers or third parties;
- test third-party model providers, search providers, SkillHub, MCP servers, SSH hosts, GitHub,
  or other external services on behalf of this project; this policy does not authorize that.

If you unexpectedly encounter another person's data, stop immediately and report the incident
privately without retaining or sharing the data.

## 3. Response and Disclosure Process

The maintainers aim to follow this process. The timings below are targets, not contractual
commitments, and depend on the complexity of the report.

| Stage | Target |
| --- | --- |
| Acknowledgement of receipt | Within 5 business days |
| Initial triage, severity assessment, and reproduction plan | Within 10 business days |
| Status updates while the report is open | At least every 14 calendar days |
| Remediation and release for critical or high-severity issues | As soon as practicable, typically within 90 days |

The process is:

1. **Acknowledged** — the report is recorded, a private advisory is opened when GitHub is used,
   and a maintainer is assigned.
2. **Triaged** — the maintainers validate the report, assess severity, determine affected
   versions, and tell the reporter whether the issue is in scope.
3. **Remediated** — a fix is developed on `master`, reviewed, and validated by the project's
   architecture, unit, and lint checks. The fix is released through GitHub Releases.
4. **Disclosed** — when appropriate, a
   [GitHub Security Advisory](https://github.com/OpenLineCode/LineCodePro/security/advisories) is
   published together with or after the fixed release. The advisory describes the affected
   versions, impact, fixed version, credit, and recommended action without publishing an exploit
   that would harm users who cannot yet update.

**Coordinated disclosure.** The maintainers aim to publish an advisory once a fixed release is
available. If a fix cannot be produced within 90 days, the maintainers will discuss a disclosure
date with the reporter. Please do not publish before that date has been agreed. If a report is
being used in the wild, state this clearly so the timeline can be shortened.

**Severity.** Severity is assessed according to practical impact on a typical user: what an
attacker must do, which trust boundary is crossed, whether credentials or user data are exposed,
and whether user interaction is required. Reports that affect many users without interaction are
prioritized.

**Bug bounty.** The project does not currently operate a bug bounty program and does not offer
monetary rewards. Reports are still valued and are handled according to this policy.

## 4. Scope

### 4.1 In scope

Security issues in the application, the source code in this repository, the official release
artifacts, and the build and release pipeline, including for example:

- **Workspace containment** — bypassing path containment without enabling the documented bypass
  switch, for example through absolute paths, `..` traversal, symlinks, canonicalization
  differences, SAF or document-provider paths, archive import, or workspace switching.
- **Tool authorization** — a mutating tool executing in read-only mode, a mutating call executing
  without approval in confirm mode, shell tools remaining available after shell access is
  disabled, phone-control tools exposed outside control mode, or an approval prompt that
  misleadingly represents the action being approved.
- **Credential and data exposure** — API keys, SSH credentials, SkillHub session material, or user
  content being disclosed to an unintended recipient through exports, logs, crash reports, error
  messages, URLs, redirects, WebView state, clipboard, diffs, snapshots, or file sharing.
- **Transport policy** — bypassing the HTTPS gate without enabling the documented switch,
  cleartext traffic to a non-loopback host, redirect downgrades, or bypassing URL validation in
  model, MCP, web-fetch, browser, or link handling.
- **WebView and browser handling** — JavaScript injection, escaping errors, bridge exposure,
  cookie or session leakage to non-official domains, or file access in the built-in browser.
- **Exported components and IPC** — abuse of an exported activity, service, or provider; URI
  permission grants; DocumentsProvider path traversal; IPC provider signature checks being
  bypassed; or a terminal-provider package being trusted without the required certificate match
  or explicit user switch.
- **Extensions and skills** — archive install path traversal or zip-slip, unsafe file writes,
  installation of a skill that escapes its intended directory, or bypass of the sensitive-file
  checks in the publish flow.
- **Accessibility and device control** — screen observation or device actions performed while the
  accessibility service is disabled or outside control mode, or exposure of captured screen
  content.
- **Release and supply chain** — signing-material exposure, artifact tampering, CI secret
  leakage, or release artifacts that do not match the published source.
- **Local model backend** — memory-safety or isolation issues in the application's own native
  bridge code. Defects inside upstream llama.cpp should be reported upstream; the maintainers
  will coordinate updates and credit when relevant.

### 4.2 Out of scope

- Vulnerabilities in third-party products and services, including model providers, search
  providers, the SkillHub service, MCP servers, SSH hosts, Termux and other terminal applications,
  GitHub, Android, device firmware, and upstream libraries such as llama.cpp, commonmark-java, and
  JSch. Report these to their maintainers; the project will coordinate when a fix requires an
  application update.
- Behavior that requires a documented unsafe option to be enabled — auto approval mode, bypassed
  path protection, ignored HTTP restrictions, or trusting differently signed extension
  packages — unless the corresponding warning or confirmation is missing or ineffective, or the
  option does not behave as documented. Those gaps are in scope.
- Model behavior itself. Reports that a model follows malicious instructions contained in
  untrusted content are security-relevant only when they cross an application policy boundary or
  bypass an approval control; requests for stronger model refusal are treated as hardening
  suggestions rather than vulnerabilities.
- Issues that require physical access to an unlocked device, a rooted or jailbroken device, a
  compromised operating system, a malicious accessibility service, ADB-level access, or a
  malicious application holding equivalent permissions.
- Social engineering, phishing, spam, automated scanner output without a demonstrated impact, and
  denial-of-service attacks against devices, services, or the maintainers.
- Issues in unsupported versions or in artifacts that were not obtained from the official GitHub
  Releases page.
- Publicly disclosed credentials or API keys. Report them privately so they can be revoked; do not
  test them.

## 5. Security Model

### 5.1 Trust boundaries and assumptions

LineCode Pro assumes the following trust model:

- **The device and operating system are trusted.** The application's controls are enforced inside
  the application process. A compromised operating system, a rooted device, a malicious
  accessibility service, or a malicious application with equivalent permissions can defeat them.
- **The user is the authority.** The application acts only through tools that the user makes
  available, according to the permission mode in effect. The configured model is not trusted to
  act without approval in the default mode.
- **Model output is untrusted.** Model output may be influenced by workspace files, web pages,
  skill content, MCP responses, command output, and other content the model processes. Approval
  prompts, diffs, and the visible execution timeline exist so the user can review actions before
  or after execution.
- **Third-party services are outside the trust boundary.** Model providers, search providers,
  SkillHub, MCP servers, SSH hosts, and other configured endpoints receive the data the user sends
  to them, and their own security and privacy policies apply.

### 5.2 Local-first data and credentials

LineCode Pro stores its user data locally in an app-private SQLite database and app-private
directories. This includes conversations and message content, durable memories, projects and
workspace metadata, file-diff history, error logs, settings, model configurations, API keys,
web-search credentials, SSH hosts and credentials, MCP and terminal-provider configurations, and
installed skills.

- Credentials are stored in app-private storage without an additional application-level
  encryption layer. Their protection is the Android application sandbox together with device
  storage encryption and the device screen lock. Anyone who can extract the app's private data
  from an unlocked or rooted device can read them.
- Cloud backup and device-to-device transfer of app data are disabled by the manifest and by the
  backup and data-extraction rules. Moving to a new device is done with the application's export
  and import feature, which is also the supported way to back up data.
- The SkillHub login flow uses an in-app WebView for the official SkillHub page. The resulting
  session cookie stays in the WebView cookie store and is read at request time; the application
  does not record the account password. The session remains active until the official service
  invalidates it or the application's WebView data is cleared.
- Error logs and crash reports are stored locally and are shared only when the user explicitly
  exports or shares them. Error logs are retained in a bounded store and can be cleared by the
  user. Conversations, memories, and diff history remain until the user deletes them or removes
  the application's data.
- The application includes no analytics, advertising, telemetry, or third-party crash-reporting
  SDK. Network requests are made only when the user invokes a feature that needs them: a configured
  model endpoint receives conversation content; configured search providers receive search
  queries; MCP servers receive tool calls; SkillHub receives SkillHub requests; GitHub receives
  repository archive requests for GitHub skill installation; configured SSH hosts receive remote
  commands. When a local GGUF model is selected, prompts are processed on the device and are not
  sent to a model provider.

The following table summarizes the principal data flows.

| Data | Stored locally | May be transmitted to |
| --- | --- | --- |
| Model API keys and model settings | App-private database | The configured model endpoint, as request credentials |
| Conversations, prompts, memories, file diffs | App-private database | The configured model endpoint as conversation context |
| Workspace files | The selected workspace | The configured model endpoint when read by a tool; an MCP server when passed in a tool call |
| Command output and tool results | Session state; diff and error records | The configured model endpoint as tool output |
| Phone-control observations; screenshots are saved into the workspace | Session state and the workspace | The configured model endpoint when returned as tool output or read by an image tool |
| SSH credentials and private keys | App-private database | The configured SSH host, as authentication material |
| Search API keys and search settings | App-private database | The configured search provider |
| SkillHub session | WebView cookie store | The official SkillHub domains |
| Error logs and crash reports | App-private storage | Only the location the user chooses when exporting or sharing |

### 5.3 Transport security

- Transport connections use the platform's TLS implementation with default certificate and
  hostname validation. The application installs no custom trust manager, disables no hostname
  verification, and performs no certificate pinning.
- HTTPS is required by default for model endpoints, MCP endpoints, web fetching, the built-in
  browser, and links opened from answers. Plain HTTP is accepted only for loopback hosts and the
  standard Android emulator loopback address (`10.0.2.2`).
- Model endpoints, MCP endpoints, and the web-fetch tool enforce the HTTPS rule on their own and
  are not relaxed by the browser setting described below.
- The security settings include an **Ignore HTTP restrictions** switch, which is off by default
  and permits plain HTTP to any host in the built-in browser and in opened links. It is intended
  for trusted local networks and development environments only.
- A custom web-search endpoint is contacted at the URL and scheme the user configures. The
  application does not override that choice, so a plain-HTTP endpoint would carry search terms
  and any configured API key without transport encryption; use HTTPS endpoints.
- SSH connections use trust on first use: the remote host key is pinned in an app-private
  `known_hosts` file. When a connection fails because the host key changed, the client clears the
  pinned keys and trusts the new key on the next attempt so that a regenerated Termux `sshd` key
  can recover; this intentional behavior weakens strict host-key verification. For
  security-sensitive hosts, verify the host key fingerprint out of band.

### 5.4 Workspace containment and file access

- Local file tools resolve model-supplied paths through a single containment check. The path is
  canonicalized and rejected if it is absolute, escapes the workspace root through `..` segments,
  or resolves through a symlink to a location outside the workspace. The directory search tool
  additionally rejects absolute patterns and patterns containing parent-directory segments, and
  enumerates only the workspace tree.
- The **Bypass path protection** switch is off by default and requires an explicit confirmation
  dialog that warns that the AI will be able to access any file on the device, including system
  files and application data.
- The application declares the `MANAGE_EXTERNAL_STORAGE` permission so that a user can open an
  existing project directory outside the application's private storage as a workspace. Android
  grants broad file access when this permission is approved; the file tools themselves remain
  confined to the selected workspace unless the bypass switch is enabled.
- Remote workspaces — SSH hosts, Termux, and AIDL terminal providers — do not expose local file
  tools. Commands are forwarded to the remote system and execute there with the privileges of the
  configured account, governed by the remote operating system's permissions rather than by the
  application's path containment.
- Other applications can reach the app-private workspace only through the system document picker,
  whose exported provider is protected by `MANAGE_DOCUMENTS`, or through a per-URI grant from the
  application's file providers. The document provider grants read or write access per URI, the
  read-only file provider shares an individual file with the viewer the user chooses, and neither
  provider is exported.
- Files supplied by other applications through the system picker are treated as untrusted
  content.

### 5.5 Tool execution and approval

- Three permission modes control tool execution: **Confirm** (the default), **Auto**, and
  **Read-only**.
  - In **Confirm** mode, read-only tools run automatically and every mutating tool call requires
    explicit approval, unless the user has added that tool to the conversation's always-allow
    list.
  - In **Auto** mode, all tools run without asking. Users should enable it only when they trust
    the model, the workspace content, and every source of content the model can read.
  - In **Read-only** mode, mutating tools are removed from the registry entirely, so they cannot
    be requested at all.
- Four chat modes further constrain the tool set: **Agent** exposes the coding tools, **Plan** and
  **Chat** expose read-only tools, and **Control** is the only mode that exposes phone-control
  tools.
- The local shell runs commands through `sh -c` with the workspace as the working directory, as
  the application's own Linux user ID. It is not a sandbox: an approved command can read any file
  the application can read, including the app-private database, and can use the network access
  and storage grants the application holds. The shell tool family can be disabled entirely in the
  tool settings.
- Tool arguments, commands, and file diffs are presented in the timeline for review. Tool output,
  including command output, web content, and file content, is untrusted and may contain
  instructions aimed at the model or at the user.
- File changes made through the workspace tools are recorded as diffs that can be reviewed and
  rolled back from the application.

### 5.6 Device control through the accessibility service

Phone-control tools are an explicitly opt-in feature. The accessibility service is disabled by
default and must be enabled by the user in Android Settings. Phone-control tools are exposed only
in **Control** chat mode, and they fail when the service is not enabled. When active, the service
can observe the accessibility tree, take screenshots, and perform taps, swipes, and global
actions. Screen content may contain passwords, one-time codes, messages, and other sensitive
information. That content becomes model input when a phone-control tool returns it, and a
screenshot saved into the workspace becomes model input when another tool reads the image. The
in-application disclaimer recommends against using this feature with payments, private data, or
critical systems.

### 5.7 Extensions, skills, MCP servers, and terminal providers

- **Skills** are instruction packages built around a `SKILL.md` file together with supporting
  files. They can be installed globally for the application or into the current project, and are
  downloaded from SkillHub or from a GitHub repository archive and stored as plain files. Skills
  are not executed automatically, but their instructions influence the model and may cause it to
  propose commands or file changes. Install skills only from sources you trust, and review the
  files before use.
- **MCP servers** are reached over HTTP or HTTPS only; the application does not spawn local MCP
  processes. The endpoint must use HTTPS except for loopback and emulator hosts. An MCP server is
  a third party that receives tool calls and returns tool descriptions and results, both of which
  can influence the model.
- **Terminal providers** are separate applications discovered through AIDL and reached through
  their published IPC interface. Provider packages are compared by the SHA-256 digests of their
  signing certificates: a provider signed with the same certificate as LineCode Pro is trusted
  automatically, and a provider signed with a different certificate is loaded only when the user
  enables the **Trust extensions with a different signature** switch, which is off by default. The
  application does not load executable code dynamically from downloaded content. A terminal
  provider is third-party code with its own privileges and its own access to the device.

### 5.8 Exports, logs, and diagnostics

- Export produces a ZIP archive containing database tables and, when available, the workspace,
  skills, and attachment trees. During export, model API keys are removed and settings rows whose
  names identify them as secrets are dropped. The archive still contains conversations, memories,
  attachments, installed skills, and workspace files, which may themselves contain sensitive
  content. Inspect an archive before sharing it.
- Import validates the manifest, required entries, and archive paths before staging files and
  replacing data; entries with absolute paths or parent-directory traversal are rejected.
- Error logs and crash reports contain technical details such as stack traces, file paths, and
  error text. They are never uploaded automatically. Review them and redact sensitive content
  before sharing.

### 5.9 Release and update integrity

- Release artifacts are built by the project's GitHub Actions workflow from the `master` branch
  when a new version is published. The workflow validates the release signing configuration
  before building, signs the APKs with the project's release key (never the debug key), creates a
  version tag, and attaches the artifacts to a GitHub Release. Signing material is never committed
  to the repository; the workflow injects it from encrypted repository secrets and removes it
  afterwards.
- Official builds are published through the [GitHub Releases](https://github.com/OpenLineCode/LineCodePro/releases)
  page. Release artifact names include the version and the build variant (`slim` or `local`).
- The application has no self-update mechanism and does not download or install updates on its
  own. Updates are installed manually from an official release. Users should verify that the APK
  application ID is `cn.lineai` and that the signing certificate is consistent with the builds
  they previously installed, for example with `apksigner verify --print-certs <apk>`.

### 5.10 Android permissions requested

The application requests the following permissions. It does not request location, camera,
microphone, contacts, calendar, SMS, call-log, body-sensor, or account permissions.

| Permission | Purpose |
| --- | --- |
| `INTERNET` | Model, search, MCP, SkillHub, and SSH network access. |
| `MANAGE_EXTERNAL_STORAGE` (plus legacy read/write storage permissions) | Open existing project directories outside app-private storage as workspaces; effective only when the user approves all-files access. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `WAKE_LOCK` | Keep long-running workspace tasks alive and visible through a foreground notification. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Let the user exempt the application from aggressive battery restrictions when background tasks must continue. |
| `BIND_ACCESSIBILITY_SERVICE` | Declares the optional accessibility service; the service remains inactive until the user enables it in Android Settings. |
| `com.termux.permission.RUN_COMMAND` | Execute commands in Termux when the user selects the Termux execution mode. |
| `cn.lineai.permission.IPC_TERMINAL_PROVIDER` | Connect to installed AIDL terminal-provider applications. |

## 6. Security-Relevant Settings

The following settings materially change the application's security posture. Values in the
**Default** column are the shipped defaults for the current release.

| Setting | Default | Effect when changed |
| --- | --- | --- |
| Permission mode (chat header and tool settings) | Confirm | Auto runs mutating tools without approval; Read-only removes them. |
| Chat mode | Agent | Plan and Chat expose read-only tools; Control exposes phone-control tools. |
| Bypass path protection (Security settings) | Off | Allows tools and agents to access paths outside the workspace. |
| Ignore HTTP restrictions (Security settings) | Off | Allows the built-in browser and opened links to use plain HTTP with any host; model, MCP, and web-fetch traffic still require HTTPS except for loopback hosts. |
| Browser JavaScript (Security settings) | Off | Allows scripts to run in the in-app browser. |
| Trust extensions with a different signature (Security settings) | Off | Loads extension and terminal-provider packages signed by a different certificate. |
| Local shell tool group (tool settings) | Enabled | Disabling removes the shell tool family from the model. Execution is still subject to the permission mode. |
| Accessibility service (Android Settings) | Disabled | Enables phone-control tools in Control mode. |
| Cloud backup and device transfer (manifest and backup rules) | Disabled | Not user-configurable; data leaves the device only through export or explicit sharing. |

## 7. Known Limitations and Residual Risks

The maintainers document these limitations rather than implying that the application provides
stronger guarantees than it does.

1. **No additional encryption at rest for credentials.** API keys, SSH passwords, private keys,
   and passphrases are protected by the Android sandbox and device encryption, not by an
   application-level key. A rooted or compromised device, an unlocked device, or a full data
   extraction can expose them.
2. **In-application controls are policy, not a sandbox.** Approval modes, path containment, URL
   restrictions, and signature checks are enforced by the application process. They protect
   against model mistakes, malicious model output, and untrusted content; they do not defend
   against a compromised operating system or a malicious application with equivalent permissions.
3. **Prompt injection cannot be eliminated.** Workspace files, web pages, command output, skills,
   and MCP responses can contain instructions that influence the model. Confirm mode, diff review,
   and the execution timeline are the effective mitigation, and they depend on the user reviewing
   what is proposed.
4. **The local shell is not a sandbox.** An approved command runs with the application's own
   privileges and can read application data, including stored credentials.
5. **SSH host keys are re-pinned automatically after a key change.** This supports recovery from
   regenerated Termux `sshd` keys but weakens strict host-key verification. The application does
   not generate or manage SSH keys, and password or key authentication is configured by the user.
6. **Documented unsafe settings deliberately reduce protection.** Auto permission mode, bypassed
   path protection, ignored HTTP restrictions, enabled browser JavaScript, and trust in
   differently signed extensions are user choices; each is off or restrictive by default and
   requires an explicit action.
7. **Third-party services receive what the user sends them.** Model providers, search providers,
   SkillHub, MCP servers, SSH hosts, and other endpoints are governed by their own policies; the
   application cannot attest to their handling of data. Third-party pages rendered in the in-app
   browser or in login flows are outside the application's control.
8. **The local-model build includes native code.** The optional llama.cpp backend runs in-process
   and parses user-supplied GGUF files. Only load model files from trusted sources. Defects in the
   upstream runtime are outside this project's control.
9. **No anti-tampering or integrity attestation.** The application does not verify its own
   installation. Users are responsible for obtaining official artifacts and checking their
   signature.
10. **Exports and diagnostics are only as safe as their destination.** The application redacts
    known credential fields, but it cannot identify secrets embedded in conversation text,
    workspace files, or command output that users then share.
11. **Uninstalling removes app-private data but not copies that were exported or shared.** Data
    exported to shared storage or sent to third-party services is outside the application's
    control.

## 8. Hardening Recommendations for Users

The maintainers recommend the following practices:

- Install LineCode Pro only from the official GitHub Releases page, and verify the signing
  certificate is consistent with previous official builds.
- Protect the device with a screen lock and storage encryption, keep the operating system patched,
  and avoid rooting or installing untrusted system-level tools.
- Keep the permission mode on **Confirm** and review every command and diff before approving it.
  Avoid **Auto** mode for untrusted workspaces, untrusted web content, or untrusted skills.
- Keep **Bypass path protection**, **Ignore HTTP restrictions**, and **Trust extensions with a
  different signature** disabled unless a specific, trusted task requires them.
- Use dedicated model and search credentials with spending limits, rotate them periodically, and
  remove credentials that are no longer needed.
- Store projects in the application's workspace or a dedicated project directory rather than
  exposing the entire shared storage tree, and open only workspaces you trust.
- Install skills only from trusted publishers, and read `SKILL.md` and its supporting files before
  installing. Do not place API keys or other credentials in skill files.
- Add MCP servers and terminal providers only when you trust the operator, and prefer HTTPS
  endpoints.
- Verify SSH host-key fingerprints out of band for sensitive hosts, and use dedicated SSH keys
  rather than reusable personal keys.
- Disable the accessibility service when phone control is not in use, and avoid Control mode in
  screens that contain payments, credentials, or private information.
- Review exported archives, error logs, and crash reports before sharing them; share the smallest
  relevant excerpt and redact unrelated content.
- When retiring a device or reinstalling, delete application data and revoke any API keys, SSH
  keys, and SkillHub sessions that the installation used.
- Report suspected vulnerabilities privately through the channels in Section 2.

## 9. Safe Harbor

The maintainers welcome good-faith security research and will not pursue or support legal action
against researchers who:

- act in good faith and comply with this policy and applicable law;
- test only their own devices, accounts, and copies of this project, or systems they are explicitly
  authorized to test;
- avoid privacy violations, data destruction, service disruption, and access to other people's
  data;
- use the minimum access and data necessary to demonstrate the issue, and stop immediately if they
  encounter data that does not belong to them;
- report the issue privately and promptly, and do not disclose it publicly before the coordinated
  disclosure date;
- do not demand payment, use the issue for extortion, or exploit the issue for personal gain.

This safe harbor applies only to the project's own components and official artifacts. It does not
authorize research against third-party services, and it does not override the policies of those
services or the laws of your jurisdiction.

## 10. Recognition

The maintainers credit reporters in the published advisory and in release notes unless anonymity
is requested. Please state the name and URL you would like used. Reports that materially improve
the security of the project are acknowledged in the release notes for the fixed version. There is
currently no monetary reward program.

## 11. Contact and Non-Security Reports

- **Security reports:** email <jiyu03@qq.com>, or GitHub private vulnerability reporting when the
  repository offers it, as described in Section 2.
- **Reproducible bugs and scoped feature requests:** [GitHub Issues](https://github.com/OpenLineCode/LineCodePro/issues).
- **Design discussion and general questions:** [GitHub Discussions](https://github.com/OpenLineCode/LineCodePro/discussions).
- **Commercial licensing, CLA, and legal questions:** <jiyu03@qq.com>, as described in
  [COMMERCIAL-LICENSE.md](COMMERCIAL-LICENSE.md) and [CLA.md](CLA.md).

Please do not use public issue trackers, discussion groups, or the community chat group to report
or discuss an unfixed vulnerability.

## 12. Policy Changes

This policy is reviewed as the application's security model evolves. Material changes are made
through a pull request against this repository and recorded in the document history below.
Corrections and improvements to this policy are welcome through the same process described in
[CONTRIBUTING.md](CONTRIBUTING.md).

| Version | Date | Change |
| --- | --- | --- |
| 1.0 | 14 November 2026 | Initial publication. |
