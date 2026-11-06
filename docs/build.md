# Build inputs

Android APKs and checks are built in GitHub Actions. The `Verify Android changes`
workflow builds all four configured ABIs and runs the Android 13 regression tests.
Local Rosetta installation is not needed for this workflow.

`app/src/main/jni/CMakeLists.txt` is the authoritative native source list. It retains
all 87 translation units from the former ndk-build build, including the v402
compatibility readers. Gradle selects the existing NDK r21 and CMake 3.22.1 with
`c++_static`; changing NDK or page-size support is a separate task. Native test
sources remain in `jni/tests`; the removed AOSP `mmm` launchers were not usable
from this standalone Gradle project. `jni/tests/CMakeLists.txt` now runs format,
geometry, trie-map, bounded dictionary reading and GC regression tests on the GitHub Linux runner
with GoogleTest, AddressSanitizer and UndefinedBehaviorSanitizer. The workflow
uploads their JUnit report alongside the Android 13 reports. Host tests compile
the production dictionary core using the same authoritative CMake source list.

`app/proguard-rules.pro` contains both project and inherited LatinIME keep rules.
Release and CI use that file together with the standard Android rules. Resource
shrinking and legacy theme assets have not been changed.

The app's `preBuild` depends on both resource generators:

- `:tools:make-keyboard-text:makeText` writes Java into that module's
  `build/generated/java`. Only default, English and Russian tables are included.
- `:tools:make-emoji-keys:makeEmoji` writes XML into that module's
  `build/generated/res`.

Gradle registers these output directories as application sources. The generator
JARs are declared inputs and the generated directories are declared outputs, so
resource/template changes invalidate generation. Generated files are not checked
into `app/src/main`, and generation does not modify the working tree.
