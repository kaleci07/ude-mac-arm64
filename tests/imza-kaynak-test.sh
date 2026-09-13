#!/bin/bash
#
# imza-kaynak-test.sh — build.sh imza_kaynak(): İmza Birleştirici kaynağının seçimi.
#
# UDE derlenirken yazarın deposundaki (IMZA_REPO) son commit'e bakılır; sabit kopyadan
# (vendor/udf-imza-birlestirici/KAYNAK.txt) farklıysa indirilip kullanılır. Lisans
# değişmişse, depoya ulaşılamazsa ya da IMZA_GUNCELLE=0 ise sabit kopya kalır.
#
# Çalıştırma:  bash tests/imza-kaynak-test.sh      (ağ gerekir: api.github.com + codeload)
# vendor/ geçici dizine kopyalanıp orada değiştirilir; depodaki dosyalara dokunulmaz.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
UDE_BUILD_LIB=1 source "$ROOT/scripts/build.sh"
set +e

fail=0
check() {  # $1=ad  $2=beklenen  $3=gerçek
	if [ "$2" = "$3" ]; then
		printf '\033[32m✓\033[0m %s\n' "$1"
	else
		printf '\033[31m✗ %s\n    beklenen: %s\n    gerçek  : %s\033[0m\n' "$1" "$2" "$3"
		fail=1
	fi
}
hazirla() {  # $1=sabit kopyanın commit'i sayılacak sha
	rm -rf "$TMP/vendor" "$TMP/dl"
	cp -R "$ROOT/vendor/udf-imza-birlestirici" "$TMP/vendor"; mkdir -p "$TMP/dl"
	local eski; eski="$(grep -oE '[0-9a-f]{40}' "$TMP/vendor/KAYNAK.txt" | head -1)"
	sed -i '' "s/$eski/$1/" "$TMP/vendor/KAYNAK.txt"
	IMZA_SRC="$TMP/vendor"; DOWNLOADS="$TMP/dl"; IMZA_GUNCELLE=1; IMZA_REPO="miasimbilir/udf-imza-birlestirici"
}

SON="$(curl -fsSL -m 20 -H 'Accept: application/vnd.github.sha' "https://api.github.com/repos/miasimbilir/udf-imza-birlestirici/commits/HEAD")"
printf '%s' "$SON" | grep -qE '^[0-9a-f]{40}$' || { echo "GitHub'a ulaşılamadı; test atlandı"; exit 2; }
ONCEKI="$(curl -fsSL -m 20 "https://api.github.com/repos/miasimbilir/udf-imza-birlestirici/commits?per_page=2" | python3 -c 'import sys,json;print(json.load(sys.stdin)[1]["sha"])')"

hazirla "$SON"
check "güncel sürümde sabit kopya kullanılır" "$TMP/vendor" "$(imza_kaynak 2>/dev/null)"

hazirla "$ONCEKI"
s="$(imza_kaynak 2>/dev/null)"
check "yeni sürüm varsa indirilip kullanılır" "$TMP/dl/imza-kaynak/$SON" "$s"
check "indirilen kaynağın KAYNAK.txt'si yeni commit'i yazar" "$SON" "$(grep -oE '[0-9a-f]{40}' "$s/KAYNAK.txt" 2>/dev/null | head -1)"
check "indirilen kaynağın lisansı sabit kopyayla aynı" "aynı" "$(cmp -s "$s/LICENSE" "$TMP/vendor/LICENSE" && echo aynı || echo farklı)"
n=0; for f in uygulama.py birlestirici.py cms_imza.py imza_dogrula.py udf_ortak.py plist_yaz.py LICENSE KULLANIM.txt ikon/uygulama.icns; do [ -f "$s/$f" ] && n=$((n+1)); done
check "indirilen kaynakta 9 zorunlu dosya var" "9" "$n"
isaret="$(stat -f %m "$s/.tamam" 2>/dev/null)"; sleep 1
check "ikinci derlemede önbellekten gelir, yeniden indirmez" "$isaret" "$(imza_kaynak >/dev/null 2>&1; stat -f %m "$s/.tamam" 2>/dev/null)"

hazirla "$ONCEKI"; printf '\nEk madde (sınama).\n' >> "$TMP/vendor/LICENSE"
check "lisans değişmişse yeni sürüm KULLANILMAZ" "$TMP/vendor" "$(imza_kaynak 2>/dev/null)"

hazirla "$ONCEKI"; IMZA_REPO="kaleci07/bu-depo-yok-imza-000"
check "depoya ulaşılamazsa sabit kopya kullanılır" "$TMP/vendor" "$(imza_kaynak 2>/dev/null)"

hazirla "$ONCEKI"; IMZA_GUNCELLE=0
check "IMZA_GUNCELLE=0 ise sabit kopya kullanılır" "$TMP/vendor" "$(imza_kaynak 2>/dev/null)"

[ "$fail" = 0 ] && echo "imza-kaynak-test: HEPSİ GEÇTİ"
exit "$fail"
