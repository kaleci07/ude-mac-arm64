#!/bin/bash
#
# jdk-select-test.sh — build.sh'nin GÖMÜLECEK Java 11 seçimini doğrular.
#
# Regresyon: makinede Oracle JDK 11 kuruluyken build.sh onu gömüyordu ve üretilen
# .app açılıştan ~0,3 sn sonra SIGSEGV ile çöküyordu (issue #7). Artık fallback
# yalnız OpenJDK derlemelerini (Zulu/Temurin/Corretto…) kabul eder.
#
# Çalıştırma:  bash tests/jdk-select-test.sh
#
# Gerçek JDK indirmez: sahte JAVA_HOME dizinleri (bin/java betiği `-version`
# çıktısını taklit eder) + java_home saplaması (JAVA_HOME_TOOL) kullanır.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_SH="${BUILD_SH:-$ROOT/scripts/build.sh}"   # regresyon kanıtı için değiştirilebilir
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail=0
check() {  # $1=ad  $2=beklenen  $3=gerçek
	if [ "$2" = "$3" ]; then
		printf '\033[32m✓\033[0m %s\n' "$1"
	else
		printf '\033[31m✗ %s\n    beklenen: %s\n    gerçek  : %s\033[0m\n' "$1" "$2" "$3"
		fail=1
	fi
}

# Sahte JDK: $1=dizin  $2=`java -version` çıktısı (stderr'e basılır)
fake_jdk() {
	mkdir -p "$1/bin"
	{ printf '#!/bin/bash\ncat >&2 <<%s\n%s\n%s\n' 'VER' "$2" 'VER'; } > "$1/bin/java"
	chmod +x "$1/bin/java"
}

ORACLE_11='java version "11.0.24" 2024-07-16 LTS
Java(TM) SE Runtime Environment 18.9 (build 11.0.24+7-LTS-271)
Java HotSpot(TM) 64-Bit Server VM 18.9 (build 11.0.24+7-LTS-271, mixed mode)'
ZULU_11='openjdk version "11.0.32" 2026-01-20 LTS
OpenJDK Runtime Environment Zulu11.86+16-CA (build 11.0.32+1-LTS)
OpenJDK 64-Bit Server VM Zulu11.86+16-CA (build 11.0.32+1-LTS, mixed mode)'
ZULU_17='openjdk version "17.0.12" 2024-07-16 LTS
OpenJDK Runtime Environment Zulu17.52+17-CA (build 17.0.12+7-LTS)
OpenJDK 64-Bit Server VM Zulu17.52+17-CA (build 17.0.12+7-LTS, mixed mode)'

fake_jdk "$TMP/oracle11" "$ORACLE_11"
fake_jdk "$TMP/zulu11"   "$ZULU_11"
fake_jdk "$TMP/zulu17"   "$ZULU_17"

# java_home saplaması: STUB_JH dosyasındaki yolu döndürür (boşsa hata → kurulu JDK yok)
cat > "$TMP/java_home" <<'STUB'
#!/bin/bash
p="$(cat "$STUB_JH" 2>/dev/null || true)"
[ -n "$p" ] || exit 1
echo "$p"
STUB
chmod +x "$TMP/java_home"
export STUB_JH="$TMP/stub_jh"
export JAVA_HOME_TOOL="$TMP/java_home"

# build.sh'yi kütüphane olarak yükle. HOME'u yönlendirerek JDK11_DEST'i tmp'ye al
# (böylece testin makinedeki gerçek Zulu kurulumuyla ilgisi kalmaz).
export HOME="$TMP/home"
mkdir -p "$HOME"
export UDE_BUILD_LIB=1
# shellcheck source=/dev/null
. "$BUILD_SH"

DEST_HOME="$JDK11_DEST/Contents/Home"

# 1) Makinede yalnız Oracle JDK 11 → gömülmeye UYGUN runtime YOK (Zulu indirilecek)
echo "$TMP/oracle11" > "$STUB_JH"
check "Oracle JDK 11 fallback olarak KABUL EDİLMEZ" "" "$(jdk11_home)"

# 2) Makinede Zulu 11 (OpenJDK) → kabul edilir
echo "$TMP/zulu11" > "$STUB_JH"
check "Zulu 11 fallback olarak kabul edilir" "$TMP/zulu11" "$(jdk11_home)"

# 3) java_home yanlış major döndürürse (17) → reddedilir
echo "$TMP/zulu17" > "$STUB_JH"
check "Yanlış major (17) reddedilir" "" "$(jdk11_home)"

# 4) UDE_ALLOW_ANY_JDK=1 → Oracle kabul edilir (bilinçli geçersiz kılma)
echo "$TMP/oracle11" > "$STUB_JH"
check "UDE_ALLOW_ANY_JDK=1 Oracle'ı kabul eder" "$TMP/oracle11" "$(UDE_ALLOW_ANY_JDK=1 jdk11_home)"

# 5) Kurduğumuz Zulu ($JDK11_DEST) her zaman önceliklidir
fake_jdk "$DEST_HOME" "$ZULU_11"
echo "$TMP/oracle11" > "$STUB_JH"
check "JDK11_DEST önceliklidir" "$DEST_HOME" "$(jdk11_home)"

# 6) Kurulu hiçbir JDK yokken de temiz boş sonuç (hata değil)
rm -rf "$JDK11_DEST"
: > "$STUB_JH"
check "JDK yokken boş döner" "" "$(jdk11_home)"

# 7) jvm_desc sağlayıcı satırını verir (check-deps çıktısında görünür)
echo "$TMP/oracle11" > "$STUB_JH"
check "jvm_desc sağlayıcıyı gösterir" \
	"Java(TM) SE Runtime Environment 18.9 (build 11.0.24+7-LTS-271)" \
	"$(jvm_desc "$TMP/oracle11")"

[ "$fail" = 0 ] && printf '\033[32mTÜM TESTLER GEÇTİ\033[0m\n' || printf '\033[31mBAŞARISIZ\033[0m\n'
exit "$fail"
