#!/usr/bin/env bash
# Source for the isolated Android build (JDK, SDK and Gradle under .build/android).
AACKER_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$AACKER_ROOT/.build/android/jdk"
export ANDROID_HOME="$AACKER_ROOT/.build/android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$AACKER_ROOT/.build/android/user"
export GRADLE_USER_HOME="$AACKER_ROOT/.build/android/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$AACKER_ROOT/.build/android/gradle/bin:$PATH"
