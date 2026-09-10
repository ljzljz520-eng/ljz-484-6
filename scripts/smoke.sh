#!/usr/bin/env bash
# 冒烟测试：启动服务并验证“私密笔记不会出现在公开列表”
# 用法：./scripts/smoke.sh [端口]
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${1:-8099}"
BASE="http://localhost:$PORT"

./scripts/compile.sh >/dev/null
java -Dfile.encoding=UTF-8 -cp bin com.researchnotes.ResearchServer "$PORT" /tmp/rn-smoke-data web >/tmp/rn-smoke.log 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null || true' EXIT

echo "等待服务启动..."
for i in $(seq 1 30); do
  if curl -sf "$BASE/api/health" >/dev/null; then break; fi
  sleep 0.3
done

fail() { echo "FAIL: $1"; exit 1; }

# 1) 公开目录只含公开笔记
CATALOG=$(curl -sf "$BASE/api/catalog")
echo "$CATALOG" | grep -q "蛋白质结构预测方法综述" || fail "公开笔记应出现在目录中"
echo "$CATALOG" | grep -q "单细胞测序批次效应校正笔记（草稿）" && fail "私密笔记不得出现在公开目录" || true
echo "$CATALOG" | grep -q "实验室硬件校准数据（内部）" && fail "私密内部笔记不得出现在公开目录" || true

# 2) 私密笔记阅读接口必须 404（n2 是种子数据中的私密笔记）
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/notes/n2")
[ "$code" = "404" ] || fail "私密笔记应返回 404，实际 $code"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/notes/n5")
[ "$code" = "404" ] || fail "私密内部笔记应返回 404，实际 $code"

# 3) 公开笔记可读
curl -sf "$BASE/api/notes/n1" | grep -q "AlphaFold" || fail "公开笔记 n1 内容应可读取"

# 4) 后台新建笔记默认私密，且切换公开后才可见
NEW=$(curl -sf -X POST "$BASE/api/admin/notes" -H 'Content-Type: application/json' \
  -d '{"topicId":"t1","title":"冒烟测试临时笔记","content":"临时内容"}')
NID=$(echo "$NEW" | sed -n 's/.*"id": *"\(n[0-9]*\)".*/\1/p')
echo "$CATALOG" >/dev/null
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/notes/$NID")
[ "$code" = "404" ] || fail "新建笔记默认私密，公开接口应 404"
curl -sf -X PUT "$BASE/api/admin/notes/$NID" -H 'Content-Type: application/json' -d '{"isPublic":true}' >/dev/null
curl -sf "$BASE/api/notes/$NID" | grep -q "临时内容" || fail "公开后笔记应可读取"
curl -sf -X DELETE "$BASE/api/admin/notes/$NID" >/dev/null

echo "ALL SMOKE TESTS PASSED"
