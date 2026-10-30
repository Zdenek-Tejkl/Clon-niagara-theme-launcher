# Klid

Minimalistický Android launcher: oblíbené aplikace na ploše, abecední seznam s vlnící se abecedou, notifikace přímo u aplikací.

## Funkce

- Plocha: hodiny, datum, baterie, nejbližší události z kalendáře, přehrávač, jeden widget
- Plocha sama ukáže 6 nejčastěji otevíraných aplikací (připnuté zůstanou nahoře)
- Oblíbené aplikace s náhledem poslední notifikace a rychlou odpovědí
- Návrhy často používaných aplikací
- Seznam aplikací: tah nahoru, nebo tažení po abecedě u pravého okraje
- Řazení podle používání, skryté aplikace
- Hledání: aplikace, kontakty, web
- Dvojklepnutí na abecedu zamkne telefon
- Připomínka přestávky po N minutách v aplikaci

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
