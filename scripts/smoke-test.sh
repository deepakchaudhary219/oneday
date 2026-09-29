#!/usr/bin/env bash
# End-to-end smoke test against a running stack (e.g. `docker compose up`) using the dev liveness provider:
# sign up, verify, upload a photo straight to object storage with a signed URL, wait for the worker, publish,
# signal, reveal, refresh and sign out, then erase the account and check the stored object is gone.
# Needs curl, jq and base64.
#
#   scripts/smoke-test.sh [http://localhost:8080]
set -euo pipefail

B=${1:-http://localhost:8080}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
json() { curl -sS -H 'Content-Type: application/json' "$@"; }
code() { curl -sS -o /dev/null -w '%{http_code}' "$@"; }

for _ in $(seq 1 60); do
  [ "$(curl -s "$B/actuator/health" | jq -r .status 2>/dev/null)" = UP ] && break
  sleep 2
done
[ "$(curl -s "$B/actuator/health" | jq -r .status)" = UP ] || fail "API is not healthy at $B"

signup() {
  local response token
  response=$(json "$B/auth/register" -d "{\"email\":\"$1-$RANDOM$RANDOM@example.com\",\"password\":\"correct-horse-battery\",\"dateOfBirth\":\"1998-04-12\",\"displayName\":\"$1\",\"consentVersion\":\"2026-09\"}")
  token=$(echo "$response" | jq -r '.token // empty')
  [ -n "$token" ] || fail "sign-up: $response"
  echo "$response" | jq -r .refreshToken > "$WORK/$1.refresh"
  response=$(json "$B/verification/liveness" -H "Authorization: Bearer $token" -d '{"sessionToken":"dev-pass"}')
  token=$(echo "$response" | jq -r '.token.token // empty')
  [ -n "$token" ] || fail "verification: $response"
  echo "$token"
}
ASHA=$(signup asha)
RAVI=$(signup ravi)
for T in "$ASHA" "$RAVI"; do
  [ "$(code -X PUT "$B/location" -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"lat":12.9352,"lon":77.6245}')" = 200 ] || fail "location update"
done

# A 32x32 JPEG whose COM segment names the encoder: the served copy must not carry it.
echo '/9j/4AAQSkZJRgABAgAAAQABAAD//gAQTGF2YzYwLjMxLjEwMgD/2wBDAAgQEBMQExYWFhYWFhoYGhsbGxoaGhobGxsdHR0iIiIdHR0bGx0dICAiIiUmJSMjIiMmJigoKDAwLi44ODpFRVP/xACDAAEBAQEAAAAAAAAAAAAAAAAHBgUEAQADAQEBAAAAAAAAAAAAAAAFBgcIBAIQAAICAQMDAwMFAQAAAAAAAAIBAxEEBQASQSExwYJDIlETMjNxoWFSEQACAQIDBwQBBAMBAAAAAAACAREDBAUAEkOBg0LBwiETQTFhIiNRkXGhBhUU/8AAEQgAIAAgAwESAAISAAMSAP/aAAwDAQACEQMRAD8AgNDwjyXITTGOxTOuzatsRfhlTV/ZNN9NtGFjR4umYCjX7kX5SfayOURJttJXV8Vffikr7b8f+Z3BD5gRnU/f+l9vJm15t3XPR/2gwehWLTrrVdKog50yOqSNrlGV4TkphR5am+O7DidmSvOzMmNg45pI75X+MyC6qr4tXV9d5uo/H7vTbfZYPh4AxdtRqxH5VQGoUxDcmnEx8KF9ZZbXm3dc1O+sKGG06IW4sNWrWUtkbSBST/lwoFNuEpyYx3YcTsy0Z2JjRcPxwxR8uV8AELqqvilvR1H4/d6byXaYriGpk7u4Nj8a6hVF5nlNkP8AjK/a827rmdYjd17AqB29R0m9c6Y0lCFLULTEolxKce2R2O7DidmeXQ9TjmMoZGITGrAfCNDd8b6pPuN3XddroDmyZMOfGnidHETJeadcbF8WnxJdiV902t2lXLuBUqCGZj4c/H3kXa827rlvqYIOD1S9OozpVkOnXHqCwnUiaSFr8k00l+zXiW+Y7sOJ2Ze8aUJtM04gdpQCHhr6oxECXevBC19n03B6TrOMIMGMqqk3xGu/X9V/1trtebd1zKbuwqEYvVTSb92Xj+Bec/47sOJ2Zu9ecUGs6AuQU6SgWUzCHzplxH5NL7zG6j8fu9NoOTqcWMaZhL9V1SHp/JLeqrXm3dcxDCMFrGvUCvbVBUeRM38qYa9NQ/3ThrPHjuw4nZmNXWFmaGrTr21UKkwwMnDSFwSYJiUEvxfle+bjUfj93puC1bTJmYnziSbb7sunt/3catebd1zoGw/2a0P9MKN2ZeyGmBN+/hKpPtnvx3YcTsz5t6FTDzpAQlWI9WkaCZk9KTfhoX8ecl2ZEc8mPFGuRyFwFWlZExSVukrf37bV8bRcnIjpOIGPlET63/yJLptTtebd1y+X9wNYFAkLc+C0+P70kSzbcd2HE7MjLLH7I9YU/UqKnpkxCBLUuXWxPxDTkV9Z/9k=' | base64 -d > "$WORK/photo.jpg"
SIZE=$(wc -c < "$WORK/photo.jpg" | tr -d ' ')
TICKET=$(json "$B/media/uploads" -H "Authorization: Bearer $ASHA" -d "{\"kind\":\"PHOTO\",\"contentType\":\"image/jpeg\",\"sizeBytes\":$SIZE}")
REF=$(echo "$TICKET" | jq -r .mediaRef)
[ "$(code -X PUT "$(echo "$TICKET" | jq -r .uploadUrl)" -H 'Content-Type: image/jpeg' --data-binary @"$WORK/photo.jpg")" = 200 ] || fail "signed upload"
json "$B/media/uploads/complete" -H "Authorization: Bearer $ASHA" -d "{\"mediaRef\":\"$REF\"}" > /dev/null
STATUS=PROCESSING
for _ in $(seq 1 30); do
  STATUS=$(curl -sS "$B/media/uploads/status?mediaRef=$REF" -H "Authorization: Bearer $ASHA" | jq -r .status)
  [ "$STATUS" = PROCESSING ] || break
  sleep 1
done
[ "$STATUS" = READY ] || fail "media ended $STATUS"

MOMENT=$(json "$B/moments" -H "Authorization: Bearer $ASHA" -d "{\"kind\":\"PHOTO\",\"mediaRef\":\"$REF\",\"activityTag\":\"trek\",\"shareScope\":\"PUBLIC_DISCOVERY\",\"capturedLive\":true}" | jq -r .id)
VIEW=$(curl -sS "$B/moments/$MOMENT" -H "Authorization: Bearer $ASHA" | jq -r .mediaUrl)
[ "$(curl -sS -o "$WORK/served.jpg" -w '%{http_code}' "$VIEW")" = 200 ] || fail "signed view"
[ -s "$WORK/served.jpg" ] || fail "signed view returned nothing"
if grep -aq Lavc "$WORK/served.jpg"; then fail "served photo still carries metadata"; fi
[ "$(code "${VIEW%%\?*}")" != 200 ] || fail "media is readable without a signature"

SIGNAL=$(json "$B/signals" -H "Authorization: Bearer $RAVI" -d "{\"momentId\":\"$MOMENT\",\"reaction\":\"MADE_ME_SMILE\",\"activityRef\":\"trek\"}" | jq -r .id)
[ "$(code -X POST "$B/signals/$SIGNAL/reveal" -H "Authorization: Bearer $ASHA")" = 200 ] || fail "reveal"

# Sessions: the refresh token rotates (the old one is then refused), and signing out cuts the access token off.
RENEWED=$(json "$B/auth/refresh" -d "{\"refreshToken\":\"$(cat "$WORK/ravi.refresh")\"}")
RAVI2=$(echo "$RENEWED" | jq -r '.token // empty')
[ -n "$RAVI2" ] || fail "refresh: $RENEWED"
[ "$(code "$B/auth/sessions" -H "Authorization: Bearer $RAVI2")" = 200 ] || fail "renewed token"
[ "$(code -X POST "$B/auth/logout-all" -H "Authorization: Bearer $RAVI2")" = 204 ] || fail "sign out everywhere"
[ "$(code "$B/auth/sessions" -H "Authorization: Bearer $RAVI2")" = 401 ] || fail "token still works after sign-out"

[ "$(code -X DELETE "$B/privacy/account?confirm=DELETE" -H "Authorization: Bearer $ASHA")" = 204 ] || fail "erasure"
# The signed URL is still valid, so a refusal means the object is gone: stores answer 404, or 403 when the
# signer may not list the bucket (AWS without s3:ListBucket).
GONE=
for _ in $(seq 1 10); do
  case "$(code "$VIEW")" in 404|403) GONE=yes; break ;; esac
  sleep 1
done
[ -n "$GONE" ] || fail "erased media is still stored"

echo "Smoke test passed against $B"
