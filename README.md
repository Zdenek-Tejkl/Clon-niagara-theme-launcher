# Klid

Minimalistický Android launcher: oblíbené aplikace na ploše, abecední seznam s vlnící se abecedou, notifikace přímo u aplikací.

## Funkce

- Plocha: hodiny, datum, baterie, nejbližší události z kalendáře, přehrávač, jeden widget
- Plocha sama ukáže 6 nejčastěji otevíraných aplikací (připnuté zůstanou nahoře)
- Oblíbené aplikace s náhledem poslední notifikace a rychlou odpovědí
- Návrhy často používaných aplikací
- Seznam aplikací: tah nahoru, nebo tažení po abecedě u pravého okraje
- Při tažení po abecedě vyjede u prstu karta s aplikacemi; posuň prst doleva na aplikaci a pusť
- Jednotný tvar ikon, písmo Manrope (licence OFL, viz LICENSE-Manrope.txt)
- Řazení podle používání, skryté aplikace, přejmenování aplikací
- Složka „Nepoužívané“ na konci seznamu: aplikace neotevřené přes 6 měsíců
- Hledání: aplikace, kontakty, web
- Dvojklepnutí na abecedu zamkne telefon
- Připomínka přestávky po N minutách v aplikaci
- Černobílý displej, barvy jen ve vybraných aplikacích (výchozí: Galerie, Fotky, Fotoaparát)
- Vlastní funkce hardwarového tlačítka (např. Bixby): 1×, 2× a 3× stisk

## Černobílý displej: jednorázové povolení

Android dovolí přepínat barvy jen s oprávněním uděleným z počítače:

```
adb shell pm grant cz.tejkl.klid android.permission.WRITE_SECURE_SETTINGS
```

Pak v Nastavení launcheru zapni Černobílý displej a službu přístupnosti „Klid: zamknutí a barvy“.

## Ovládání

- Tah nahoru: seznam aplikací
- Tah dolů: notifikační lišta
- Podržení prázdné plochy: nastavení
- Podržení aplikace: přidat na plochu, skrýt, odinstalovat

## Sestavení

```
./gradlew assembleRelease
```

APK najdeš v `app/build/outputs/apk/release/`. GitHub Actions ho sestaví při každém pushi (záložka Actions, artefakt `klid-apk`).

Požadavky: Android 10+, JDK 17+.

Náhledy obrazovek bez telefonu: `./gradlew testDebugUnitTest`, obrázky jsou v `app/build/outputs/roborazzi/`.
