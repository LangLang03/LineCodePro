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

## Run the deterministic fixed-reply fixture

This fixture is a protocol and UI test double. It is **not** a real AI service and
does not generate answers: every successful completion request returns the
configured text. It uses only the Python standard library.

Run its contract tests first:

```sh
python3 tools/test_fake_ai_server.py
```

For an Android Studio emulator, use Android's special `10.0.2.2` host address in
LineCode. If loopback binding is not reachable in your emulator setup, listen on all
host interfaces:

```sh
python3 tools/fake_ai_server.py \
  --host 0.0.0.0 \
  --port 18080 \
  --reply '这是像素测试的固定回复。'
```

`0.0.0.0` exposes this unauthenticated test fixture on every host interface. Use
that command only on a trusted local network, keep the host firewall enabled, and
stop the fixture after testing. Prefer `127.0.0.1` plus `adb reverse` for physical
devices because it does not expose the fixture to the LAN. The server also rejects
missing or invalid request lengths, caps JSON request bodies at 1 MiB, and closes
connections whose request bodies time out.

After the C++ HTTP client is wired, configure the app with:

- protocol: OpenAI compatible (or Responses/Codex)
- Base URL: `http://10.0.2.2:18080/v1`
- model: `linecode-test-model`
- API key: any non-empty test value; the fixture ignores authorization

Use `http://10.0.2.2:18080/health` to check reachability from the emulator. For a
USB/wireless physical device, `10.0.2.2` does not apply; use `adb reverse` instead:

```sh
adb -s SERIAL reverse tcp:18080 tcp:18080
python3 tools/fake_ai_server.py --host 127.0.0.1 --port 18080
```

Then set the device Base URL to `http://127.0.0.1:18080/v1`. Stop the fixture with
Ctrl-C or SIGTERM; both close the listening socket cleanly. `--quiet` suppresses
request logs.

The fixture supports `GET /health`, `GET /v1/models`, OpenAI Chat Completions and
Responses in both JSON and SSE modes. It also has a deterministic Anthropic Messages
compatibility route for testing that model option.

The fixture tests alone cover only the server contract. The product uses
`HuxCompletionGateway` and `HuxModelCatalogGateway`, both backed by HuxerUI's real
`HttpClient`; keep a separate device run in the release gate. A catalog run must
show only `linecode-test-model` from `GET /v1/models` (never the removed hard-coded
`test-model` / `gpt-4o-mini` values), and a chat run must persist the fixed reply
after a force-stop/cold restart.

## Native checks

```sh
cmake --build .huxerui/build/tests --parallel
ctest --test-dir .huxerui/build/tests --output-on-failure
git diff --check
```
