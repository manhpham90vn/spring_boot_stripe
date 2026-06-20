#!/usr/bin/env bash
#
# smoke-test.sh — Kiểm tra nhanh sau deploy: gọi thật các API qua EDGE và xác nhận
# không có gì gãy. Tập trung vào (1) sức khoẻ từng service và (2) MA TRẬN BẢO MẬT:
# public không cần token, route thường/admin bị chặn đúng, /internal không lộ ra biên.
#
# Cách dùng:
#   ./scripts/smoke-test.sh                     # mặc định gọi qua nginx http://localhost
#   BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh   # gọi thẳng gateway (bỏ qua nginx)
#   AUTH_DIRECT_URL=http://localhost:8081 ./scripts/smoke-test.sh   # test thêm /internal/jwks trực tiếp (dev)
#   ADMIN_EMAIL=admin@x.com ADMIN_PASSWORD=... ./scripts/smoke-test.sh  # test thêm happy-path ADMIN
#
# Biến môi trường:
#   BASE_URL         Cửa vào để test (mặc định http://localhost = nginx). Phải là EDGE.
#   HEALTH_PATH      Path probe sẵn sàng của edge (mặc định /healthz của nginx).
#   AUTH_DIRECT_URL  (tuỳ chọn) URL gọi THẲNG Auth (vd http://localhost:8081) để xác nhận
#                    /internal/jwks chạy nội bộ. Bỏ trống = chỉ kiểm tra nó KHÔNG lộ ở edge.
#   ADMIN_EMAIL/ADMIN_PASSWORD  (tuỳ chọn) tài khoản ADMIN đã seed để test happy-path admin.
#   WAIT_TIMEOUT     Số giây chờ edge sẵn sàng trước khi test (mặc định 60).
#   TEST_PASSWORD    Mật khẩu cho user test tạo mới (mặc định 'Test1234!').
#
# Quy ước: exit 0 nếu MỌI assertion pass; exit 1 nếu có bất kỳ fail (tiện gate CI/deploy).

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost}"
HEALTH_PATH="${HEALTH_PATH:-/healthz}"
AUTH_DIRECT_URL="${AUTH_DIRECT_URL:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-60}"
TEST_PASSWORD="${TEST_PASSWORD:-Test1234!}"

# --- Khung in màu (tự tắt nếu không phải terminal) --------------------------
if [ -t 1 ]; then
  GREEN=$'\033[32m'; RED=$'\033[31m'; YEL=$'\033[33m'; DIM=$'\033[2m'; NC=$'\033[0m'
else
  GREEN=''; RED=''; YEL=''; DIM=''; NC=''
fi

PASS=0
FAIL=0
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

pass() { PASS=$((PASS + 1)); printf "  ${GREEN}✓${NC} %s\n" "$1"; }
fail() { FAIL=$((FAIL + 1)); printf "  ${RED}✗ %s${NC}\n" "$1"; }
section() { printf "\n${YEL}▸ %s${NC}\n" "$1"; }

# req METHOD URL [DATA] [TOKEN] -> in ra HTTP status; body ghi vào $BODY_FILE.
# Lỗi kết nối → status "000".
req() {
  local method="$1" url="$2" data="${3:-}" token="${4:-}"
  local args=(-sS -o "$BODY_FILE" -w '%{http_code}' --max-time 15 -X "$method" "$url")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data "$data")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  curl "${args[@]}" 2>/dev/null || echo "000"
}

# expect DESC EXPECTED ACTUAL
expect() {
  if [ "$3" = "$2" ]; then pass "$1 ${DIM}($3)${NC}"; else fail "$1 — mong đợi $2, nhận $3"; fi
}

# body_has SUBSTRING -> kiểm tra body chứa chuỗi
body_has() { grep -q "$1" "$BODY_FILE" 2>/dev/null; }

# extract KEY -> lấy giá trị string của field JSON top-level từ $BODY_FILE
extract() {
  if command -v jq >/dev/null 2>&1; then
    jq -r ".$1 // empty" "$BODY_FILE" 2>/dev/null
  else
    sed -n 's/.*"'"$1"'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$BODY_FILE" | head -1
  fi
}

command -v curl >/dev/null 2>&1 || { echo "Cần có 'curl'."; exit 2; }

printf "${DIM}Edge   : %s\nProbe  : %s${NC}\n" "$BASE_URL" "$HEALTH_PATH"

# --- 0) Chờ edge sẵn sàng ---------------------------------------------------
section "Chờ edge sẵn sàng (tối đa ${WAIT_TIMEOUT}s)"
deadline=$(( $(date +%s) + WAIT_TIMEOUT ))
edge_up=0
while :; do
  if [ "$(req GET "$BASE_URL$HEALTH_PATH")" = "200" ]; then edge_up=1; break; fi
  [ "$(date +%s)" -ge "$deadline" ] && break
  sleep 2
done
if [ "$edge_up" = "1" ]; then
  pass "Edge phản hồi $HEALTH_PATH"
else
  fail "Edge KHÔNG phản hồi $HEALTH_PATH sau ${WAIT_TIMEOUT}s — dừng sớm"
  printf "\n${RED}Tổng: %d pass, %d fail${NC}\n" "$PASS" "$FAIL"
  exit 1
fi

# --- 1) Sức khoẻ từng service (qua nginx /health/<svc>) ---------------------
section "Sức khoẻ service (/health/<svc> → actuator)"
for svc in apigateway auth catalog inventory order payment ticket notification waitingroom; do
  st="$(req GET "$BASE_URL/health/$svc")"
  if [ "$st" = "200" ] && body_has '"status":"UP"'; then
    pass "$svc UP"
  else
    fail "$svc không UP (status $st)"
  fi
done

# --- 2) Luồng Auth (register → login → me) ----------------------------------
section "Auth: đăng ký → đăng nhập → /me"
EMAIL="smoke+$(date +%s)-${RANDOM}@example.com"

st="$(req POST "$BASE_URL/api/auth/public/register" \
  "{\"email\":\"$EMAIL\",\"password\":\"$TEST_PASSWORD\"}")"
expect "Đăng ký user mới" 201 "$st"

st="$(req POST "$BASE_URL/api/auth/public/login" \
  "{\"email\":\"$EMAIL\",\"password\":\"$TEST_PASSWORD\"}")"
expect "Đăng nhập" 200 "$st"
TOKEN="$(extract accessToken)"
if [ -n "$TOKEN" ]; then pass "Nhận access token"; else fail "KHÔNG lấy được access token"; fi

st="$(req GET "$BASE_URL/api/auth/me" "" "$TOKEN")"
if [ "$st" = "200" ] && body_has "$EMAIL"; then
  pass "/me trả đúng user ${DIM}(200)${NC}"
else
  fail "/me với token hợp lệ — mong đợi 200 + email, nhận $st"
fi

# --- 3) Phân biệt PUBLIC vs CẦN-JWT (đúng quy ước path) ---------------------
section "Bảo mật: public vs cần-token"

st="$(req GET "$BASE_URL/api/catalog/public/events")"
expect "GET catalog public (KHÔNG token) cho qua" 200 "$st"

st="$(req GET "$BASE_URL/api/catalog/public/venues")"
expect "GET catalog public venues (KHÔNG token) cho qua" 200 "$st"

st="$(req GET "$BASE_URL/api/auth/me")"
expect "GET /me KHÔNG token bị chặn" 401 "$st"

st="$(req GET "$BASE_URL/api/auth/me" "" "rac.khong.hop.le")"
expect "GET /me token rác bị chặn" 401 "$st"

# --- 4) Phân quyền ADMIN ----------------------------------------------------
section "Bảo mật: khu admin"

st="$(req POST "$BASE_URL/api/catalog/admin/venues" '{"name":"smoke"}')"
expect "POST admin KHÔNG token → 401 (chặn ở biên)" 401 "$st"

st="$(req POST "$BASE_URL/api/catalog/admin/venues" '{"name":"smoke"}' "$TOKEN")"
expect "POST admin bằng token USER → 403 (chặn ở service)" 403 "$st"

if [ -n "$ADMIN_EMAIL" ] && [ -n "$ADMIN_PASSWORD" ]; then
  st="$(req POST "$BASE_URL/api/auth/public/login" \
    "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")"
  ADMIN_TOKEN="$(extract accessToken)"
  if [ "$st" = "200" ] && [ -n "$ADMIN_TOKEN" ]; then
    pass "Đăng nhập ADMIN"
    st="$(req POST "$BASE_URL/api/catalog/admin/venues" \
      '{"name":"Smoke Venue","address":"1 Test St","city":"Hanoi"}' "$ADMIN_TOKEN")"
    expect "POST admin bằng token ADMIN → 201" 201 "$st"
    VENUE_ID="$(extract id)"
    if [ -n "$VENUE_ID" ]; then
      st="$(req DELETE "$BASE_URL/api/catalog/admin/venues/$VENUE_ID" "" "$ADMIN_TOKEN")"
      expect "DELETE venue vừa tạo (dọn dẹp) → 204" 204 "$st"
    fi
  else
    fail "Đăng nhập ADMIN thất bại (status $st) — bỏ qua happy-path admin"
  fi
else
  printf "  ${DIM}• Bỏ qua happy-path ADMIN (đặt ADMIN_EMAIL/ADMIN_PASSWORD để bật)${NC}\n"
fi

# --- 5) Internal-only: KHÔNG lộ ra biên -------------------------------------
section "Bảo mật: /internal không lộ ra ngoài"

st="$(req GET "$BASE_URL/internal/jwks")"
if [ "$st" = "404" ] || [ "$st" = "403" ]; then
  pass "/internal/jwks KHÔNG với tới từ edge ${DIM}($st)${NC}"
else
  fail "/internal/jwks KHÔNG được lộ ở edge — nhận $st (cần 404/403)"
fi

if [ -n "$AUTH_DIRECT_URL" ]; then
  st="$(req GET "$AUTH_DIRECT_URL/internal/jwks")"
  if [ "$st" = "200" ] && body_has '"keys"'; then
    pass "/internal/jwks gọi THẲNG Auth chạy đúng ${DIM}(200, có keys)${NC}"
  else
    fail "/internal/jwks gọi thẳng Auth — mong đợi 200 + keys, nhận $st"
  fi
fi

# --- Tổng kết ---------------------------------------------------------------
printf "\n"
if [ "$FAIL" -eq 0 ]; then
  printf "${GREEN}✔ TẤT CẢ PASS — %d kiểm tra.${NC}\n" "$PASS"
  exit 0
else
  printf "${RED}✘ CÓ LỖI — %d pass, %d fail.${NC}\n" "$PASS" "$FAIL"
  exit 1
fi
