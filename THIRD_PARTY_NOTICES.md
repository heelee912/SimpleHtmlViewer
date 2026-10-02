# Third-party notices

The app code and the provided HTML template are licensed under MIT. This does not replace third-party licenses.

- The unmodified Gradle 8.14 Wrapper is build tooling. Its bundled license is preserved in [gradle/wrapper/LICENSE](gradle/wrapper/LICENSE). See the [Gradle source](https://github.com/gradle/gradle/tree/v8.14.0).
- Android SDK and Android Gradle Plugin are acquired separately when building. They are not bundled into the app as runtime libraries.
- The HTML template references Pretendard CSS from jsDelivr. The font files are not included here. Pretendard retains its [SIL Open Font License](https://github.com/orioncactus/pretendard/blob/main/LICENSE).

The 2.x APK uses AndroidX Activity, Core, Lifecycle, Compose UI, Foundation and Material 3. It also includes Kotlin standard libraries and Kotlin coroutines. These components retain their Apache 2.0 licenses. A copy is in [docs/licenses/Apache-2.0.txt](docs/licenses/Apache-2.0.txt).

- [AndroidX source and license](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt)
- [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)
- [Kotlin coroutines license](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)

JUnit, AndroidX Test and UI Automator are test dependencies. They are included only in the separate test APK. JUnit retains its Eclipse Public License 1.0. AndroidX Test and UI Automator retain Apache 2.0.
