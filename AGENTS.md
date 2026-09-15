# 🚨 MANDATORY AI INSTRUCTION: PHONE ADB DEPLOY, TEST & BUILD POLICY

> **CRITICAL RULES FOR ALL AI ASSISTANTS / AGENTS WORKING IN THIS REPOSITORY**
>
> 1. **MANDATORY ADB DEPLOYMENT & TESTING**: Whenever you finish building or modifying UI, navigation, database, or app code, ALWAYS deploy to the connected phone via ADB:
>    `sh ./tools/phone.sh deploy oss` (or `full`) OR:
>    `adb install -r app/build/outputs/apk/oss/debug/app-oss-debug.apk && adb shell am start -n com.locus.app/.MainActivity`
> 2. **ON-DEVICE RUNTIME VERIFICATION**: Use `adb logcat` or `adb shell dumpsys activity` to verify that the app launches and runs without crashes. Do not rely solely on unit tests when the phone is connected.
> 3. **OFFLOAD NATIVE C/C++ / LLAMA.CPP TASKS**: When working on native code (e.g. Prompt 24 `llama.cpp JNI`, embedding runners, or benchmarks), offload native compilation and benchmark execution directly to the phone via the SSH tunnel:
>    `sh ./tools/phone.sh ssh "<command>"`
>    or via `adb forward tcp:8022 tcp:8022` + `ssh -i ~/.ssh/id_turbotransfer -p 8022 localhost "<command>"`.
> 4. **ANDROID GRADLE BUILD PIPELINE**: AGP (Android Gradle Plugin) executes on the host PC (due to Google's bundled `aapt2` host requirements), and then immediately streams to the phone over ADB for installation and testing.

---

## 🛠️ Command Reference for AI Agents

### 1. Check Device & Node Status
```bash
sh ./tools/phone.sh status
```

### 2. Deploy App to Phone
```bash
# Build and deploy oss debug flavor
sh ./tools/phone.sh deploy oss

# Build and deploy full debug flavor
sh ./tools/phone.sh deploy full
```

### 3. Run On-Device Connected Tests
```bash
sh ./tools/phone.sh test
# Or directly:
sh ./gradlew connectedOssDebugAndroidTest
```

### 4. Monitor App Logcat
```bash
sh ./tools/phone.sh logcat
```

### 5. Execute Commands on Phone (Termux Native Node)
```bash
sh ./tools/phone.sh ssh "uname -a && nproc"
sh ./tools/phone.sh ssh "clang --version"
```
