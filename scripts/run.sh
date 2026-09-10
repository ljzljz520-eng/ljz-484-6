#!/usr/bin/env bash
# 启动服务：./scripts/run.sh [端口] [数据目录] [前端目录]
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA_HOME="${JAVA_HOME:-}"
JAVA="java"
if [ -n "$JAVA_HOME" ]; then
  JAVA="$JAVA_HOME/bin/java"
fi
if [ ! -d bin ] || [ -z "$(find bin -name '*.class' -print -quit 2>/dev/null)" ]; then
  ./scripts/compile.sh
fi
exec "$JAVA" -Dfile.encoding=UTF-8 -cp bin com.researchnotes.ResearchServer "$@"
