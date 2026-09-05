# LineCode migration test workflow

The migration is verified against two applications installed on the same Android device:

- legacy baseline: `cn.lineai.legacy`
- C++ candidate: `cn.lineai`

The legacy repository has a `baseline` build type whose only package change is the
`.legacy` application ID suffix. Its version remains `32 / 1.2.8-max`, and it uses the
same LineCode release certificate as the candidate.

## Build the applications

```sh
cd /home/LangLang/AndroidStudioProjects/LineCode
./gradlew :app:assembleBaseline

cd /home/LangLang/AndroidStudioProjects/LineCodePro
huxerui build android --profile release
```

## Run UI and functional parity scenarios

```sh
python3 tools/ui_parity_test.py \
  --serial emulator-5554 \
  --baseline-apk /home/LangLang/AndroidStudioProjects/LineCode/app/build/outputs/apk/baseline/app-baseline.apk \
  --candidate-apk platform/android/app/build/outputs/apk/release/app-release.apk \
  --baseline-package cn.lineai.legacy \
  --candidate-package cn.lineai \
  --scenarios tools/ui_scenarios.json \
  --output artifacts/ui-parity-side-by-side
```

The runner fixes resolution, density, font scale and animation scales, clears each
package independently, replays the same gestures, verifies visible semantics, and
writes screenshots, UIAutomator trees, activity dumps, image diffs and `report.json`.
Non-identical screenshots intentionally make the command fail until pixel parity is
reached.

## Run deterministic AI fixture tests

```sh
python3 tools/test_fake_ai_server.py
python3 tools/fake_ai_server.py --host 127.0.0.1 --port 18080
adb reverse tcp:18080 tcp:18080
```

The fixture implements model catalog, OpenAI Chat Completions, OpenAI Responses and
Anthropic Messages endpoints. Streaming and non-streaming requests return the same
fixed Chinese response for deterministic device tests.

## Native checks

```sh
cmake --build .huxerui/build/tests --parallel
ctest --test-dir .huxerui/build/tests --output-on-failure
git diff --check
```

