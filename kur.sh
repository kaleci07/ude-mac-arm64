#!/usr/bin/env bash
# kur.sh — UDE'yi macOS'ta (Apple Silicon veya Intel) tek komutla derleyip kuran betik.
# Mimari otomatik algılanır; build çalıştığı Mac için (arm64/x86_64) üretilir.
#
# README'deki adımları (geliştirici araçları + kaynak kodun indirilmesi +
# Java'ların indirilmesi + derleme + paketleme + Applications'a taşıma) sizin için
# sırayla yapar. Programcı olmanıza gerek yok.
#
# İki şekilde çalışır:
#   • İnternetten tek satırla (Apple Silicon ve Intel, aynı komut):
#       bash -c "$(curl -fsSL https://raw.githubusercontent.com/saidsurucu/ude-mac-arm64/main/kur.sh)"
#     (Terminal Rosetta modundaysa betik kendini arm64 olarak yeniden başlatır)
#     (kaynak kodu kendisi indirir, derler ve kurar)
#   • Depoyu zaten indirdiyseniz, klasörün içinde:  ./kur.sh
#
# Görünüm seçimi (varsayılan: modern düz görünüm):
#   • Eski/klasik (turkuaz) arayüz için SKIN=0 ortam değişkenini önden verin:
#       SKIN=0 arch -arm64 bash -c "$(curl -fsSL https://raw.githubusercontent.com/saidsurucu/ude-mac-arm64/main/kur.sh)"
#   • Depoyu indirdiyseniz aynı şey bayrakla da olur:  ./kur.sh --klasik
#
# Asıl derleme mantığı scripts/build.sh içindedir; bu betik onu sarmalar.

set -euo pipefail

REPO_URL="https://github.com/saidsurucu/ude-mac-arm64.git"
CLONE_DIR="$HOME/ude-mac-arm64"

# ----- Görünüm seçimi: --klasik/--eski bayrağı SKIN=0'a eşdeğerdir -----
# (curl | bash tek-satırında bayrak yerine "SKIN=0 <komut>" kullanılır; ikisi de kabul edilir.)
for _arg in "$@"; do
	case "$_arg" in
		--klasik|--eski|--classic) SKIN=0 ;;
	esac
done
SKIN="${SKIN:-1}"
# EXPORT şart: betik kendini iki kez devredebiliyor (indirilen kopyaya `exec bash`,
# Rosetta kabuğunda `exec arch -arm64`). Ortama koymazsak, seçim yalnız argümanla
# verildiğinde (`./kur.sh --klasik`) devirde kaybolur ve modern görünüm kurulur.
export SKIN

# ----- Renkli, anlaşılır mesajlar -----
if [ -t 1 ]; then
	BOLD=$'\033[1m'; GRN=$'\033[32m'; YLW=$'\033[33m'; RED=$'\033[31m'; BLU=$'\033[34m'; RST=$'\033[0m'
else
	BOLD=""; GRN=""; YLW=""; RED=""; BLU=""; RST=""
fi
say()  { printf '%s\n' "${BLU}›${RST} $*"; }
ok()   { printf '%s\n' "${GRN}✓${RST} $*"; }
warn() { printf '%s\n' "${YLW}!${RST} $*"; }
die()  { printf '%s\n' "${RED}✗ $*${RST}" >&2; exit 1; }
step() { printf '\n%s\n' "${BOLD}== $* ==${RST}"; }

# ----- Xcode komut satırı araçları (git, make, codesign vb.) -----
# Hem internetten indirme (git) hem derleme (make) için gerekli; bir kez kurulur.
ensure_clt() {
	if xcode-select -p >/dev/null 2>&1; then
		ok "Komut satırı araçları zaten kurulu"
		return
	fi
	warn "Komut satırı araçları yok; kurulum penceresi açılıyor…"
	xcode-select --install >/dev/null 2>&1 || true
	say "Açılan pencerede ${BOLD}\"Yükle\"${RST}ye basıp bitmesini bekleyin."
	say "Kurulum tamamlanınca bu betik kendiliğinden devam edecek…"
	# Kullanıcı kurulumu bitirene kadar bekle (iptal ederse Ctrl+C ile çıkabilir).
	until xcode-select -p >/dev/null 2>&1; do
		printf '.'
		sleep 5
	done
	printf '\n'
	ok "Komut satırı araçları kuruldu"
}

# ----- "sudo ./kur.sh" ile başlatıldıysa normal kullanıcıya dön -----
# Root olarak yazılan kaynak kod/önbellek kullanıcının ev dizininde root'a ait
# kalır ve sonraki (sudo'suz) kurulum "Permission denied" ile düşer. Yönetici
# izni yalnızca /Applications adımında, gerektiği anda isteniyor.
if [ "$(id -u)" = "0" ]; then
	SRC0="${BASH_SOURCE[0]:-}"
	if [ -n "${SUDO_USER:-}" ] && [ "$SUDO_USER" != "root" ] && [ -f "$SRC0" ]; then
		warn "Kurulum 'sudo' ile başlatıldı; normal kullanıcı ($SUDO_USER) olarak devam ediliyor."
		exec sudo -u "$SUDO_USER" -H /bin/bash "$(cd "$(dirname "$SRC0")" && pwd)/$(basename "$SRC0")"
	fi
	die "Bu kurulumu 'sudo' ile çalıştırmayın. Normal kullanıcı olarak: ./kur.sh"
fi

# ----- 0) Ortam kontrolü -----
# Mimari otomatik: arm64 (Apple Silicon) ya da x86_64 (Intel) kabul edilir.
# Apple Silicon'da Rosetta terminali (proc_translated=1) kabul edilmez — orada
# x86_64 üretmek yanlış olur (Mac aslında arm64); bu durumda betik kendini
# arm64 olarak yeniden başlatır.
step "Ortam denetimi"
[ "$(uname -s)" = "Darwin" ] || die "Bu betik yalnızca macOS içindir."
ARCH="$(uname -m)"
ARCH_SWITCH=0
case "$ARCH" in
	arm64)
		ok "Apple Silicon Mac algılandı"
		;;
	x86_64)
		# Rosetta terminali: Mac aslında Apple Silicon; x86_64 üretmek yanlış olur.
		# Elle "arch -arm64" yazdırmak yerine betiği arm64 olarak yeniden başlatıyoruz.
		if [ "$(sysctl -n sysctl.proc_translated 2>/dev/null || true)" = "1" ] \
		   || [ "$(sysctl -n hw.optional.arm64 2>/dev/null || true)" = "1" ]; then
			if [ "${KUR_ARCH_SWITCHED:-0}" != "1" ] && command -v arch >/dev/null 2>&1; then
				warn "Terminal Rosetta (x86_64) modunda; otomatik olarak arm64'e geçilecek."
				ARCH_SWITCH=1
			else
				die "Terminaliniz Rosetta (x86_64) modunda çalışıyor ve arm64'e geçilemedi.
  Terminal'in ${BOLD}Bilgi Al${RST} (⌘I) penceresinde ${BOLD}\"Rosetta kullanarak aç\"${RST} işaretini kaldırıp terminali yeniden açın."
			fi
		else
			ok "Intel Mac algılandı"
		fi
		;;
	*)
		die "Desteklenmeyen mimari: $ARCH (yalnız arm64 / x86_64)."
		;;
esac

# Rosetta terminalinde başlatıldıysak betiği arm64 olarak yeniden başlatırız.
# (Hemen yapamıyoruz: "curl | bash" ile çalıştırıldığında yeniden başlatılacak
#  bir dosya henüz diskte yok — kaynak kod indikten sonra çağrılıyor.)
reexec_arm64() {
	say "arm64 mimarisine geçiliyor…"
	export KUR_ARCH_SWITCHED=1
	local script="$1"; shift
	exec arch -arm64 /bin/bash "$script" ${1+"$@"}
}

# ----- Kaynak kodu uzak sürüme getir -----
# `git pull --ff-only` iki durumda düşer: (a) klasörde yerel değişiklik var,
# (b) geçmiş ayrılmış/bozulmuş. Eskiden ikisinde de yalnız uyarı basılıp ESKİ kodla
# devam ediliyordu → kullanıcı kurulum komutunu tekrar tekrar çalıştırsa bile eski
# UDE sürümünde kalıyordu ve bunu hiç fark etmiyordu. Artık: yerel değişiklik varsa
# dokunulmaz (geliştirici kopyası), TEMİZ ağaçta kaybedilecek bir şey olmadığından
# uzak dala sert hizalanır.
repo_update() {  # $1=depo dizini → 0: güncel/güncellendi, 1: güncellenemedi
	local d="$1"
	git -C "$d" pull --ff-only --quiet 2>/dev/null && return 0
	if [ -n "$(git -C "$d" status --porcelain 2>/dev/null)" ]; then
		warn "Kaynak kodda yerel değişiklikler var; otomatik güncelleme atlandı."
		return 1
	fi
	local br; br="$(git -C "$d" rev-parse --abbrev-ref HEAD 2>/dev/null || echo main)"
	[ "$br" = "HEAD" ] && br="main"
	git -C "$d" fetch --quiet origin "$br" 2>/dev/null \
		|| git -C "$d" fetch --quiet origin main 2>/dev/null \
		|| { warn "Uzak depoya erişilemedi (internet?); mevcut sürümle devam ediliyor."; return 1; }
	# YEREL EKLENTİ KORUMASI: depoda .kur-yerel-koruma varsa ve HEAD'de uzakta olmayan
	# commit'ler duruyorsa (ör. İmza Birleştirici) reset --hard onları SİLERDİ. Bu durumda uzak
	# sürüm BİRLEŞTİRİLİR; çakışırsa birleştirme geri alınır ve mevcut kodla devam edilir.
	# İşaret dosyası olmayan kopyalarda davranış değişmez (aşağıdaki sert hizalama).
	if [ -f "$d/.kur-yerel-koruma" ] \
		&& [ "$(git -C "$d" rev-list --count FETCH_HEAD..HEAD 2>/dev/null || echo 0)" -gt 0 ]; then
		say "Yerel eklentiler korunarak uzak sürüm birleştiriliyor…"
		local ad eposta
		ad="$(git -C "$d" config user.name 2>/dev/null || true)"
		eposta="$(git -C "$d" config user.email 2>/dev/null || true)"
		if git -C "$d" -c user.name="${ad:-UDE kur.sh}" -c user.email="${eposta:-kur.sh@localhost}" \
			merge --no-edit --quiet FETCH_HEAD >/dev/null 2>&1; then
			return 0
		fi
		git -C "$d" merge --abort >/dev/null 2>&1 || true
		warn "Uzak sürüm yerel eklentilerle çakıştı; birleştirilmedi, mevcut sürümle devam ediliyor."
		return 1
	fi
	say "Normal güncelleme yapılamadı; kaynak kod uzak sürüme hizalanıyor…"
	git -C "$d" reset --hard --quiet FETCH_HEAD 2>/dev/null \
		|| { warn "Hizalama başarısız. Temiz kurulum için: rm -rf \"$d\" ve komutu tekrar çalıştırın."; return 1; }
	return 0
}

# ----- Önyükleme: depo klasörünün içinde miyiz? -----
# curl ... | bash ile çalıştırıldığında BASH_SOURCE boş/geçersiz olur; bu durumda
# kaynak kodu kendimiz indirip oradaki kur.sh'yi yeniden çalıştırırız.
SRC="${BASH_SOURCE[0]:-}"
SCRIPT_DIR=""
if [ -n "$SRC" ] && [ -f "$SRC" ]; then
	SCRIPT_DIR="$(cd "$(dirname "$SRC")" && pwd)"
fi

if [ -z "$SCRIPT_DIR" ] || [ ! -f "$SCRIPT_DIR/scripts/build.sh" ]; then
	step "Kaynak kodun indirilmesi"
	ensure_clt
	command -v git >/dev/null 2>&1 || die "git bulunamadı (komut satırı araçları eksik olabilir)."
	if [ -d "$CLONE_DIR/.git" ]; then
		say "Depo zaten var, en güncel sürüme güncelleniyor: $CLONE_DIR"
		repo_update "$CLONE_DIR" || warn "Güncelleme atlandı; mevcut sürümle devam ediliyor."
	else
		[ -e "$CLONE_DIR" ] && die "$CLONE_DIR zaten var ama bir git deposu değil (ZIP olarak indirilmiş olabilir). Şunu çalıştırıp komutu tekrarlayın:  rm -rf \"$CLONE_DIR\""
		say "Kaynak kod indiriliyor: $CLONE_DIR"
		git clone --depth 1 "$REPO_URL" "$CLONE_DIR" --quiet
	fi
	ok "Kaynak kod hazır"
	# İndirilen depodaki kur.sh'yi devral (bu noktadan sonrasını o yürütür).
	# Güncelleme burada yapıldı → çocuk süreç tekrar denemesin.
	export UDE_KUR_SELFUPDATED=1
	if [ "$ARCH_SWITCH" = "1" ]; then reexec_arm64 "$CLONE_DIR/kur.sh" ${1+"$@"}; fi
	exec bash "$CLONE_DIR/kur.sh" ${1+"$@"}
fi

cd "$SCRIPT_DIR"

# Kaynak kod diskte; Rosetta terminalinden geldiysek burada arm64'e geçiyoruz.
if [ "$ARCH_SWITCH" = "1" ]; then reexec_arm64 "$SCRIPT_DIR/kur.sh" ${1+"$@"}; fi

# ----- Kaynak kodu güncelle (klasörün içinden çalıştırıldığında da) -----
# Depoyu bir kez indirip sonra hep "./kur.sh" ile çalıştıran kullanıcı, eskiden
# ESKİ kodda kalıyordu: tek satırlık kurulum komutu güncelliyordu ama klasör içinden
# çalıştırma güncellemiyordu. Sonuç: satıcı yeni UDE sürümü yayınlasa bile (link adı
# değişmiş + eski kodun önbelleği sürüm-duyarsız) kullanıcı uygulamayı silip yeniden
# kursa da ESKİ UDE sürümünde kalıyordu. Artık her çalıştırmada güncellenir.
# Geliştirici kopyası korunur: yerel değişiklik varsa ya da dal bir uzak dalı
# izlemiyorsa güncelleme atlanır (yalnız uyarı).
self_update() {
	[ "${UDE_KUR_SELFUPDATED:-0}" = "1" ] && return 0
	command -v git >/dev/null 2>&1 || return 0
	git -C "$SCRIPT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 || return 0
	local up; up="$(git -C "$SCRIPT_DIR" rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null || true)"
	[ -n "$up" ] || { warn "Kaynak kod güncellenemedi (dal uzak depoyu izlemiyor); mevcut sürümle devam ediliyor."; return 0; }
	if [ -n "$(git -C "$SCRIPT_DIR" status --porcelain 2>/dev/null)" ]; then
		warn "Kaynak kodda yerel değişiklikler var; otomatik güncelleme atlandı."
		return 0
	fi
	local before after
	before="$(git -C "$SCRIPT_DIR" rev-parse HEAD 2>/dev/null || echo '')"
	say "Kaynak kod güncelleniyor: $SCRIPT_DIR"
	repo_update "$SCRIPT_DIR" || return 0
	after="$(git -C "$SCRIPT_DIR" rev-parse HEAD 2>/dev/null || echo '')"
	if [ "$before" = "$after" ]; then ok "Kaynak kod zaten güncel"; return 0; fi
	ok "Kaynak kod güncellendi; kurulum güncel betikle yeniden başlatılıyor…"
	export UDE_KUR_SELFUPDATED=1
	exec bash "$SCRIPT_DIR/kur.sh" ${1+"$@"}
}
step "Kaynak kod güncelliği"
self_update ${1+"$@"}
# Sadece güncelleme yolunu sınamak için (tests/kur-selfupdate-test.sh).
[ "${UDE_KUR_SELFUPDATE_ONLY:-0}" = "1" ] && exit 0

APP_NAME="Uyap Doküman Editörü.app"
BUILT_APP="$SCRIPT_DIR/build/$APP_NAME"
DEST_APP="/Applications/$APP_NAME"

# ----- 1) Xcode komut satırı araçları (make, codesign vb.) -----
step "Geliştirici araçları (bir kez)"
ensure_clt
command -v make >/dev/null 2>&1 || die "make bulunamadı (komut satırı araçları eksik olabilir)."

# ----- 2) Gömülecek arm64 Java 11 -----
step "arm64 Java 11 (gömülecek çalışma zamanı)"
make jdk

# ----- 3) Paketleyici JDK (jpackage'lı 17+) -----
step "Paketleyici JDK (jpackage)"
make jpackage-jdk

# ----- 4) Derle + modern ikonlarla paketle + imzala -----
step "Derleme + paketleme (birkaç dakika sürebilir)"
if [ "$SKIN" = "0" ]; then
	say "Görünüm: klasik (eski turkuaz arayüz) — SKIN=0"
else
	say "Görünüm: modern düz arayüz (varsayılan). Klasik istiyorsanız: SKIN=0 ile çalıştırın ya da ./kur.sh --klasik"
fi
SKIN="$SKIN" ICONS=1 make all
[ -d "$BUILT_APP" ] || die "Beklenen uygulama üretilemedi: $BUILT_APP"
ok "Uygulama hazır: $BUILT_APP"

# ----- 5) /Applications'a taşı (gerekirse eskisini değiştir) -----
step "Applications'a kurulum"
if pgrep -f "$APP_NAME/Contents/MacOS" >/dev/null 2>&1; then
	warn "Uygulama açık görünüyor; kapatılıyor…"
	osascript -e 'tell application "Uyap Doküman Editörü" to quit' >/dev/null 2>&1 || true
	sleep 2
fi
if [ -e "$DEST_APP" ]; then
	say "Eski sürüm bulundu, değiştiriliyor…"
	rm -rf "$DEST_APP" 2>/dev/null || sudo rm -rf "$DEST_APP"
fi
if mv "$BUILT_APP" "$DEST_APP" 2>/dev/null; then
	ok "Kuruldu: $DEST_APP"
else
	warn "/Applications yazılamadı; yönetici izniyle taşınıyor…"
	sudo mv "$BUILT_APP" "$DEST_APP"
	ok "Kuruldu: $DEST_APP"
fi

# ----- Bitti -----
printf '\n'
# Kurulan UDE sürümünü göster: kullanıcı "güncel mi?" sorusunu tek bakışta yanıtlayabilsin
# (paketin kendi Info.plist'inden gelir, bizim varsayımımızdan değil).
INSTALLED_VER="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$DEST_APP/Contents/Info.plist" 2>/dev/null || true)"
[ -n "$INSTALLED_VER" ] && ok "Kurulan UDE sürümü: ${BOLD}$INSTALLED_VER${RST}"
ok "${BOLD}BİTTİ.${RST} UDE artık Launchpad ve Applications'ta. .udf dosyalarına çift tıklayarak da açabilirsiniz."
say "Açmak için: ${BOLD}open \"$DEST_APP\"${RST}"
printf '\n'
if [ "$ARCH" = "arm64" ]; then
	warn "E-imza kullanacaksanız: TÜBİTAK AKİS'in ${BOLD}Apple Silicon (Arm)${RST} sürücüsünü kurun"
	say "  https://akiskart.bilgem.tubitak.gov.tr/destek/  → \"Mac OS Arm (Apple Silicon)\""
else
	warn "E-imza kullanacaksanız: TÜBİTAK AKİS'in ${BOLD}Mac OS Intel${RST} sürücüsünü kurun"
	say "  https://akiskart.bilgem.tubitak.gov.tr/destek/  → \"Mac OS Intel\""
fi
printf '\n'
say "Yeni UDE sürümü çıktığında bu betiği yeniden çalıştırmanız yeterli (en güncel sürüm otomatik iner)."
