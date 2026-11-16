#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$project_dir"

if [[ -z "${JAVA_HOME:-}" && -x "$HOME/.local/lib/jvm/homeappolon-17/bin/java" ]]; then
    export JAVA_HOME="$HOME/.local/lib/jvm/homeappolon-17"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/Android/Sdk" ]]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
fi

if [[ $# -eq 0 ]]; then
    set -- testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
fi

# Keep compilation within one Gradle JVM and one worker by default so the
# emulator has enough memory during feature-module builds.
exec ./gradlew "$@" --console=plain --max-workers="${ANDROID_CHECK_WORKERS:-1}" \
    -Dorg.gradle.jvmargs='-Xmx4096m -XX:ActiveProcessorCount=4 -Dfile.encoding=UTF-8' \
    -Pkotlin.compiler.execution.strategy=in-process
