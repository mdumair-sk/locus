# 🚨 MANDATORY AI INSTRUCTION: SNAPDRAGON 8 ELITE ADB DEPLOY, TEST & BUILD POLICY

> **CRITICAL RULES FOR ALL AI ASSISTANTS / AGENTS WORKING IN THIS REPOSITORY**
>
> 1. **MANDATORY ADB DEPLOYMENT & TESTING**: Whenever you finish building or modifying UI, navigation, database, or app code, ALWAYS deploy to the connected **Snapdragon 8 Elite** Android phone via ADB:
>    `sh ./tools/phone.sh deploy oss` (or `full`) OR:
>    `adb install -r app/build/outputs/apk/oss/debug/app-oss-debug.apk && adb shell am start -n com.locus.app/.MainActivity`
> 2. **ON-DEVICE RUNTIME VERIFICATION**: Use `adb logcat` or `adb shell dumpsys activity` to verify that the app launches and runs without crashes. Do not rely solely on unit tests when the physical device is connected.
> 3. **OFFLOAD NATIVE C/C++ / LLAMA.CPP TASKS**: The phone runs Qualcomm Snapdragon 8 Elite (2x Prime Oryon @ 4.32 GHz + 6x Perf Oryon @ 3.53 GHz) on TSMC 3nm with native ARM64 Clang/LLVM. When working on native code (e.g. Prompt 24 `llama.cpp JNI`, embedding runners, or benchmarks), offload native compilation and benchmark execution directly to the phone via the SSH tunnel:
>    `sh ./tools/phone.sh ssh "<command>"`
>    or via `adb forward tcp:8022 tcp:8022` + `ssh -i ~/.ssh/id_turbotransfer -p 8022 localhost "<command>"`.
> 4. **ANDROID GRADLE BUILD PIPELINE**: AGP (Android Gradle Plugin) executes on the host PC (due to Google's bundled x86_64/glibc `aapt2` requirements), and then immediately streams to the phone over ADB for installation and testing.

---

## ⚡ Hardware Rationale

* **Host Laptop**: AMD Ryzen 5 3550H (4 Cores / 8 Threads, 2019). High thermal load, lower IPC.
* **Target Phone**: OnePlus / Qualcomm Snapdragon 8 Elite (`CPH2723`, 8 Oryon cores, 12 GB RAM, Android 15 / API 35). This is also the project's primary benchmark device per **REQUIREMENTS.md §12 (NF-3)**.
* **Result**: Connected over high-speed ADB tunnel (`b9b2c03f`). Instant deployment and verification on real hardware.

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

### 5. Execute Commands on Phone's 8 Oryon Cores (Termux Native Node)
```bash
sh ./tools/phone.sh ssh "uname -a && nproc"
sh ./tools/phone.sh ssh "clang --version"
```
