# Rule: Snapdragon 8 Elite Phone Execution & Testing

Always utilize the connected Snapdragon 8 Elite Android device (`b9b2c03f`) via ADB for:
1. **Deploying & running the app**: After every meaningful UI/feature change, install and launch the APK using `sh ./tools/phone.sh deploy oss` (or `adb install -r <apk> && adb shell am start -n com.locus.app/.MainActivity`).
2. **Runtime verification**: Inspect `adb logcat` and `adb shell dumpsys activity` to ensure zero runtime crashes on device.
3. **On-device tests**: Run `sh ./tools/phone.sh test` or `sh ./gradlew connectedOssDebugAndroidTest`.
4. **Native C/C++ compilation & benchmarks**: Offload to the 8 Oryon cores via `sh ./tools/phone.sh ssh "<cmd>"` or `ssh -i ~/.ssh/id_turbotransfer -p 8022 localhost`.
