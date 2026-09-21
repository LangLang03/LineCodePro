## Summary

<!-- What changed, why it is needed, and which issue it resolves. -->

Closes #

## User-visible behavior

<!-- Describe the behavior before and after this pull request. -->

## Screenshots or recordings

<!-- Required for UI changes. Include comparable before/after states. -->

## Validation

- [ ] GoogleTest suite passes with Ninja and `-j8`
- [ ] Fake AI server tests pass
- [ ] Android lint passes
- [ ] Android debug build succeeds
- [ ] Verified on a device or emulator, or explained why this is not applicable

Commands and environment used:

```text

```

## Design and safety checklist

- [ ] The change keeps domain, application, infrastructure, platform, and presentation responsibilities separated.
- [ ] New behavior is behind an abstraction or data-driven descriptor where extension is expected; it does not grow a UI `if` chain.
- [ ] New or changed behavior has GoogleTest coverage.
- [ ] No credential, signing key, personal data, generated build output, or incompatible third-party code is included.
- [ ] User-facing text is available in both English and Chinese where applicable.
- [ ] UI changes preserve the intended LineCode behavior and have been visually checked.

## CLA — required

Checking the following box and typing the pull request author's exact GitHub username constitutes an electronic signature of the [Contributor License Agreement](https://github.com/LangLang03/LineCodePro/blob/master/CLA.md). Both lines are validated by the required `CLA / verify` CI check.

- [ ] I have read and agree to the [Contributor License Agreement](https://github.com/LangLang03/LineCodePro/blob/master/CLA.md).

CLA Signature: @your-github-username
