#!/usr/bin/env bash
# 编译全部 Java 源码到 bin/（仅需 JDK 8+，不依赖 Maven/Gradle）
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA_HOME="${JAVA_HOME:-}"
JAVAC="javac"
if [ -n "$JAVA_HOME" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
fi
mkdir -p bin
find src -name '*.java' > bin/sources.txt
"$JAVAC" -encoding UTF-8 -d bin @bin/sources.txt
rm -f bin/sources.txt
echo "编译完成：bin/"
