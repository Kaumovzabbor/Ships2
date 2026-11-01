# Ships (Kemachalar)

Bir xil Wi-Fi yoki hotspot orqali 2–6 do'st bilan 10x10 maydonda Dengiz jangi.
Internet va tashqi server kerak emas.

## APK olish (GitHub Actions)
1. Bu papkani GitHub'dagi yangi repoga push qiling.
2. Repo → **Actions** → "Build APK" → run tugashini kuting.
3. Run sahifasidagi **Artifacts** bo'limidan `Ships-debug-apk` ni yuklab oling (ZIP ichida `.apk`).
4. Telefonlarga o'rnating ("Noma'lum manbalardan o'rnatish"ni yoqing).

## O'ynash
Bitta telefon "Xona yaratish" bosadi (host). Qolganlar "Xonaga qo'shilish" → ro'yxatdan xonani tanlaydi,
yoki lobbidagi IP manzilni qo'lda kiritadi (port 8765). Yolg'iz sinash uchun lobbida "Bot qo'shish".

## Arxitektura
- `game/` — sof Kotlin qoidalar (`Fleet.kt` joylashtirish, `Engine.kt` otish/navbat/g'olib), unit testlar bilan.
- `network/` — `GameServer` (host telefonda, avtoritar: kemalar joyi faqat shu yerda), `GameClient`, `Discovery` (NSD/mDNS `_ships._tcp`), `Protocol` (JSON xabarlar).
- `ui/` — `GameViewModel` (StateFlow) va Compose ekranlar.

## Xabar protokoli (TCP, har qator = bitta JSON `Msg`)
| t | Yo'nalish | Ma'nosi |
|---|---|---|
| JOIN | mijoz→host | ismi bilan qo'shilish |
| WELCOME / REJECT | host→mijoz | qabul / rad (NAME, FULL, STARTED) |
| LOBBY | host→hamma | o'yinchilar ro'yxati |
| ADDBOT, START | mijoz→host | faqat host |
| PLACE | host→hamma | joylashtirish boshlandi (90 s) |
| READY | mijoz→host | flot joylashuvi (host tekshiradi) |
| FLEET | host→mijoz | vaqt tugasa tasodifiy flot |
| BATTLE | host→hamma | jang, birinchi navbat |
| FIRE | mijoz→host | nishon va katak |
| RESULT | host→hamma | MISS/HIT/SUNK, cho'kkan kema, keyingi navbat, g'olib |
| LEFT | host→hamma | o'yinchi chiqib ketdi |
| REMATCH | mijoz→host, host→hamma | lobbiga qaytish |

## Qoidalar (host lobbida tanlaydi)
- **Klassik (Hasbro):** 5-4-3-3-2 kemalar.
- **Rus "Морской бой":** 1x4, 2x3, 3x2, 4x1 (10 kema), kemalar burchakda ham tegmaydi, urgan yana otadi.
- Alohida sozlamalar: "tegmaslik", "urgan yana otadi", navbat vaqti (cheksiz / 15 / 30 / 60 s).
- Kema cho'ksa va "tegmaslik" yoqilgan bo'lsa, atrofidagi suv avtomatik belgilanadi.
- Barcha kemasi cho'kkan o'yinchi chiqadi, oxirgi qolgan g'olib.

## Imkoniyatlar
Kemalarni sudrab joylashtirish (bosib aylantirish), qolgan kemalar ko'rsatkichi, o'q animatsiyasi,
ovoz va tebranish, och/to'q mavzu, til tanlash (O'zbekcha, Русский, English), bot bilan mashq.

## Hozircha yo'q
Qayta ulanish (reconnect) va heartbeat.
