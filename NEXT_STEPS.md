# PRVN Music v0.01 — Next Steps & Roadmap

## 1. Physical Device Verification

1. **Deploy APK via ADB**:
   Connect an Android device with USB debugging enabled:
   ```bash
   adb install -r Builds/PRVN-Music-v0.01-arm64.apk
   ```
2. **Key Feature Verification**:
   - [ ] Verify instant playback start for search results.
   - [ ] Confirm lock screen and notification media controls update album artwork and elapsed time correctly.
   - [ ] Test background playback when screen turns off.
   - [ ] Verify audio equalizer adjustments apply in real-time without stutter.
   - [ ] Test offline music downloads and playback from local storage.

---

## 2. Release Signing Configuration (For Play Store / F-Droid / GitHub Releases)

To generate an officially signed production APK for distribution:
1. Generate an Android Keystore (if not already done):
   ```bash
   keytool -genkey -v -keystore prvn-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias prvn
   ```
2. Add keystore credentials to `local.properties`:
   ```properties
   android.signing.storeFile=/path/to/prvn-release.jks
   android.signing.storePassword=<store_password>
   android.signing.keyAlias=prvn
   android.signing.keyPassword=<key_password>
   ```
3. Re-run `:androidApp:assembleRelease`.

---

## 3. Multi-ABI Expansion (Future Versions)

PRVN Music v0.01 targets **ARM64 (`arm64-v8a`)**, which powers >95% of modern Android smartphones. For broader device compatibility in future versions (e.g. older 32-bit devices and Android emulators on x86 machines):
- Compile `armeabi-v7a` and `x86_64` FFmpeg slices in `ffmpeg-build`.
- Update `ANDROID_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")` in `buildSrc/src/main/kotlin/FfmpegBuild.kt`.

---

## 4. UI/UX Refinements

- **Custom PRVN Vector Icons**: Replace remaining legacy XML vectors with custom PRVN-branded high-DPI vector artwork.
- **Custom Splash Screen**: Enhance the Android 12+ SplashScreen API animation with the neon purple gradient.
