# Implementation Plan - Fix Microphone in Browser Version

The microphone functionality in the Web (Wasm) version is failing, likely due to browser permission restrictions or lack of a secure context (HTTPS). This plan improves the robustness of the Web Speech API implementation.

## User Review Required

> [!IMPORTANT]
> The Web Speech API **requires HTTPS** (except for `localhost`). If the app is served over a standard HTTP connection, the microphone will not work regardless of code changes. The updated code will now display a specific error message if this is the case.

## Proposed Changes

### Web Target (WasmJs)

#### [MODIFY] [main.kt](file:///C:/Users/engin/AndroidStudioProjects/Sesliportfoyara/app/src/wasmJsMain/kotlin/com/example/sesliportfoyara/main.kt)
- Update `jsStartVoiceRecognition` to:
    - Check for secure context (`window.isSecureContext`).
    - Explicitly request microphone permission using `navigator.mediaDevices.getUserMedia`.
    - Handle potential exceptions when starting recognition.
    - Add detailed console logging.
- Ensure proper cleanup in `jsStopVoiceRecognition`.

## Verification Plan

### Manual Verification
- Deploy the Web version (e.g., via `./gradlew wasmJsBrowserRun`).
- Test the microphone button on `localhost` (which is treated as secure).
- Verify that the browser prompts for microphone permission.
- Verify that voice input is correctly transcribed and searched.
- Test on a non-HTTPS remote URL (if possible) to verify the "Secure context required" error message.
