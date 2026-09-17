#!/bin/bash
#
# kur-selfupdate-test.sh — kur.sh klasör içinden çalıştırıldığında kendini günceller mi?
#
# Regresyon: depoyu bir kez indirip hep "./kur.sh" ile çalıştıran kullanıcı ESKİ kodda
# kalıyordu; eski kodun indirme önbelleği sürüm-duyarsız olduğu için satıcı yeni UDE
# sürümü yayınlasa da (link adı da değişti) uygulama silinip yeniden kurulsa bile ESKİ
# UDE sürümünde kalıyordu.
#
# Çalıştırma:  bash tests/kur-selfupdate-test.sh
# Ağ/derleme YOK: yerel depodan sahte bir "origin" klonlanır, çalışma kopyası geriye
# alınır ve kur.sh yalnız güncelleme adımına kadar koşturulur (UDE_KUR_SELFUPDATE_ONLY=1).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail=0
check() {
	if [ "$2" = "$3" ]; then printf '\033[32m✓\033[0m %s\n' "$1"
	else printf '\033[31m✗ %s\n    beklenen: %s\n    gerçek  : %s\033[0m\n' "$1" "$2" "$3"; fail=1; fi
}

git clone -q "$ROOT" "$TMP/origin" || { echo "klon başarısız"; exit 1; }
# Çalışma ağacındaki (henüz commit edilmemiş olabilir) kur.sh sınansın
cp "$ROOT/kur.sh" "$TMP/origin/kur.sh"
# Yazarın varsayılan davranışı sınansın: origin'de yerel eklenti işareti olmasın (kaleci07).
git -C "$TMP/origin" rm -q --ignore-unmatch .kur-yerel-koruma
git -C "$TMP/origin" -c user.email=t@t -c user.name=test commit -qam "test: sınanacak kur.sh" || true

# Kullanıcının kopyası: origin'den klonlanır, sonra origin ilerler (bayat kopya)
git clone -q "$TMP/origin" "$TMP/work"
BEHIND="$(git -C "$TMP/work" rev-parse HEAD)"
echo "# origin ilerledi" >> "$TMP/origin/README.md"
git -C "$TMP/origin" -c user.email=t@t -c user.name=test commit -qam "test: origin ilerledi"
ORIGIN_HEAD="$(git -C "$TMP/origin" rev-parse HEAD)"
check "başlangıç: çalışma kopyası geride" "geride" "$([ "$BEHIND" != "$ORIGIN_HEAD" ] && echo geride || echo ayni)"

# 1) Temiz bayat kopya → güncellenmeli
out="$(UDE_KUR_SELFUPDATE_ONLY=1 bash "$TMP/work/kur.sh" 2>&1)"
check "güncelleme çalıştı (çıkış 0)" "0" "$?"
check "HEAD origin'e ilerledi" "$ORIGIN_HEAD" "$(git -C "$TMP/work" rev-parse HEAD)"
check "yeniden başlatma mesajı basıldı" "var" "$(printf '%s' "$out" | grep -q 'yeniden başlatılıyor' && echo var || echo yok)"

# 2) İkinci çalıştırma → zaten güncel (sonsuz döngü yok)
out2="$(UDE_KUR_SELFUPDATE_ONLY=1 bash "$TMP/work/kur.sh" 2>&1)"
check "ikinci çalıştırma: zaten güncel" "var" "$(printf '%s' "$out2" | grep -q 'zaten güncel' && echo var || echo yok)"

# 3) Yerel değişiklik varsa (geliştirici kopyası) güncelleme ATLANIR
git -C "$TMP/work" reset --hard -q "$BEHIND"
echo "# yerel deneme" >> "$TMP/work/README.md"
out3="$(UDE_KUR_SELFUPDATE_ONLY=1 bash "$TMP/work/kur.sh" 2>&1)"
check "kirli kopyada uyarı verilir" "var" "$(printf '%s' "$out3" | grep -q 'yerel değişiklikler' && echo var || echo yok)"
check "kirli kopyada HEAD değişmez" "$BEHIND" "$(git -C "$TMP/work" rev-parse HEAD)"

# 3b) Geçmiş AYRILMIŞSA (ff-only düşer) ama ağaç temizse → uzak sürüme sert hizalanır.
#     Eskiden bu durumda yalnız uyarı basılır, kullanıcı sonsuza dek eski kodda kalırdı.
git -C "$TMP/work" checkout -q -- README.md
git -C "$TMP/work" reset --hard -q "$BEHIND"
echo "# yerel commit" >> "$TMP/work/README.md"
git -C "$TMP/work" -c user.email=t@t -c user.name=test commit -qam "test: ayrılan geçmiş"
out3b="$(UDE_KUR_SELFUPDATE_ONLY=1 bash "$TMP/work/kur.sh" 2>&1)"
check "ayrılan geçmişte hizalama mesajı" "var" "$(printf '%s' "$out3b" | grep -q 'hizalanıyor' && echo var || echo yok)"
check "ayrılan geçmiş origin'e hizalandı" "$ORIGIN_HEAD" "$(git -C "$TMP/work" rev-parse HEAD)"

# 4) UDE_KUR_SELFUPDATED=1 ile hiç denenmez (bootstrap yolundan gelen çocuk süreç)
git -C "$TMP/work" reset --hard -q "$BEHIND"
out4="$(UDE_KUR_SELFUPDATED=1 UDE_KUR_SELFUPDATE_ONLY=1 bash "$TMP/work/kur.sh" 2>&1)"
check "SELFUPDATED=1 ile güncelleme denenmez" "yok" "$(printf '%s' "$out4" | grep -q 'güncelleniyor' && echo var || echo yok)"
check "SELFUPDATED=1 sonrası HEAD değişmez" "$BEHIND" "$(git -C "$TMP/work" rev-parse HEAD)"

# 5) GERÇEK kurulum düzeni: --depth 1 (sığ) klon da güncellenebilmeli
#    (kur.sh depoyu böyle indiriyor; sığ klonda fetch/reset davranışı farklı olabilir)
git clone -q --depth 1 "file://$TMP/origin" "$TMP/shallow"
echo "# origin bir kez daha ilerledi" >> "$TMP/origin/README.md"
git -C "$TMP/origin" -c user.email=t@t -c user.name=test commit -qam "test: origin 2"
ORIGIN_HEAD2="$(git -C "$TMP/origin" rev-parse HEAD)"
UDE_KUR_SELFUPDATE_ONLY=1 bash "$TMP/shallow/kur.sh" >/dev/null 2>&1
check "sığ (--depth 1) klon güncellendi" "$ORIGIN_HEAD2" "$(git -C "$TMP/shallow" rev-parse HEAD)"

# 6) kaleci07 YEREL EKLENTİ KORUMASI: .kur-yerel-koruma + uzakta olmayan yerel commit varsa
#    reset --hard YAPILMAZ; uzak sürüm birleştirilir, yerel eklenti korunur.
git clone -q "$TMP/origin" "$TMP/eklenti"
git -C "$TMP/eklenti" reset --hard -q "$BEHIND"
printf 'yerel eklenti işareti\n' > "$TMP/eklenti/.kur-yerel-koruma"
echo "yerel eklenti" > "$TMP/eklenti/EKLENTI.txt"
git -C "$TMP/eklenti" add .kur-yerel-koruma EKLENTI.txt
git -C "$TMP/eklenti" -c user.email=t@t -c user.name=test commit -qm "test: yerel eklenti"
YEREL="$(git -C "$TMP/eklenti" rev-parse HEAD)"
UZAK="$(git -C "$TMP/origin" rev-parse HEAD)"
out6="$(UDE_KUR_SELFUPDATE_ONLY=1 bash "$TMP/eklenti/kur.sh" 2>&1)"
check "korumalı kopya: birleştirme mesajı" "var" "$(printf '%s' "$out6" | grep -q 'korunarak' && echo var || echo yok)"
check "korumalı kopya: sert hizalama YAPILMADI" "yok" "$(printf '%s' "$out6" | grep -q 'hizalanıyor' && echo var || echo yok)"
check "korumalı kopya: yerel eklenti commit'i duruyor" "evet" "$(git -C "$TMP/eklenti" merge-base --is-ancestor "$YEREL" HEAD && echo evet || echo hayır)"
check "korumalı kopya: uzak sürüm de alındı" "evet" "$(git -C "$TMP/eklenti" merge-base --is-ancestor "$UZAK" HEAD && echo evet || echo hayır)"
check "korumalı kopya: eklenti dosyası yerinde" "var" "$([ -f "$TMP/eklenti/EKLENTI.txt" ] && echo var || echo yok)"
check "korumalı kopya: ağaç temiz" "temiz" "$([ -z "$(git -C "$TMP/eklenti" status --porcelain)" ] && echo temiz || echo kirli)"

# 7) Korumalı kopyada ÇAKIŞMA: hiçbir şey değişmez, yarım birleştirme kalmaz.
git clone -q "$TMP/origin" "$TMP/catisma"
git -C "$TMP/catisma" reset --hard -q "$BEHIND"
printf 'yerel eklenti işareti\n' > "$TMP/catisma/.kur-yerel-koruma"
echo "# yerel çakışan satır" >> "$TMP/catisma/README.md"
git -C "$TMP/catisma" add .kur-yerel-koruma README.md
git -C "$TMP/catisma" -c user.email=t@t -c user.name=test commit -qm "test: çakışan yerel eklenti"
YEREL2="$(git -C "$TMP/catisma" rev-parse HEAD)"
out7="$(UDE_KUR_SELFUPDATE_ONLY=1 bash "$TMP/catisma/kur.sh" 2>&1)"
check "çakışmada uyarı verilir" "var" "$(printf '%s' "$out7" | grep -q 'çakıştı' && echo var || echo yok)"
check "çakışmada HEAD değişmez" "$YEREL2" "$(git -C "$TMP/catisma" rev-parse HEAD)"
check "çakışmada yarım birleştirme kalmaz" "yok" "$([ -f "$TMP/catisma/.git/MERGE_HEAD" ] && echo var || echo yok)"
check "çakışmada ağaç temiz" "temiz" "$([ -z "$(git -C "$TMP/catisma" status --porcelain)" ] && echo temiz || echo kirli)"

[ "$fail" = 0 ] && printf '\033[32mTÜM TESTLER GEÇTİ\033[0m\n' || printf '\033[31mBAŞARISIZ\033[0m\n'
exit "$fail"
