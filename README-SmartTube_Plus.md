# SmartTube+ — SmartTube z feedem subskrypcji i obsługą tabletu

Fork [SmartTube](https://github.com/yuliskov/SmartTube) (MIT) z trzema zmianami,
których brakowało na tablecie i bez logowania do konta Google.

![SmartTube+ — wiersz "Twoje kanały" na górze strony głównej](STPLUS/docs/screenshot-hero.png)

## Co dodaje

### 1. Wiersz „Twoje kanały" na górze strony głównej

Pierwszy wiersz Home to najnowsze filmy **z Twojej lokalnej listy kanałów** —
tej, którą SmartTube trzyma w menu „Subskrypcje" (import z NewPipe, PocketTube,
GrayJay albo dodane ręcznie).

- **Działa bez logowania do YouTube.** Nie używamy API subskrypcji (wymaga konta),
  tylko publicznych feedów RSS kanałów.
- **Cache-first**: wiersz jest na ekranie od razu po starcie, z ostatnio pobranej
  listy, jeszcze zanim doładują się domyślne wiersze YouTube'a. Świeże dane
  podmieniają go dopiero, gdy pobieranie się skończy — nic nie miga i nic nie czeka.
- **Doładowywanie**: na start 40 pozycji, po dojechaniu do końca wiersza dosypuje
  kolejne 20 (zapas do 200 pozycji jest już pobrany, więc dzieje się to natychmiast).

### 2. Dwa przyciski dotykowe na górnej belce (tryb tabletowy)

SmartTube jest projektowany pod pilota i telewizor. Na tablecie brakowało sposobu,
żeby palcem schować i wysunąć lewe menu.

| przycisk | gdzie | działanie |
|----------|-------|-----------|
| hamburger | lewy górny róg | zwija / rozwija boczne menu |
| odświeżanie | prawy górny róg | kręci się, gdy feed się odświeża; kliknięcie = ręczne odświeżenie |

Przyciski **nie są focusowalne** — nawigacja pilotem działa dokładnie tak jak
w oryginale, przyciski istnieją tylko dla dotyku.

### 3. Rebrand: własna nazwa, ikona i osobny pakiet

`org.smarttube.plus.stable` — instaluje się **obok** oryginalnego SmartTube'a,
nie nadpisuje go, ma własne dane i własną listę kanałów.

## Dlaczego to powstało

Na tablecie oryginał ma dwa praktyczne problemy: nie da się palcem schować menu,
a strona główna bez logowania pokazuje wyłącznie propozycje YouTube'a — nie
widać własnych kanałów, mimo że aplikacja trzyma ich listę lokalnie.

Obie rzeczy da się załatwić małą łatką: **cały kod funkcji to jeden plik**
(`common/src/main/java/.../stplus/StPlus.java`), a w kodzie oryginału jest
siedem krótkich haków po kilka linijek. Byłoby świetnie, gdyby ta funkcjonalność
trafiła do SmartTube'a na stałe — ten fork jest raczej dowodem, że się da,
niż propozycją osobnego bytu.

## Instalacja

Pobierz APK z sekcji Releases i zainstaluj (potrzebne „instalowanie z nieznanych
źródeł"). Oryginalny SmartTube może zostać — to osobna aplikacja.

Wymaganie: żeby wiersz „Twoje kanały" się pojawił, trzeba mieć lokalną listę
kanałów — menu **Subskrypcje → import** (NewPipe / PocketTube / GrayJay) albo
dodać kanały ręcznie. Bez listy wiersza po prostu nie ma (celowo).

## Budowanie ze źródeł

```bat
git clone <to repozytorium>
cd SmartTube
build-st-fast.bat          :: tylko smak ststable, ~1 min z ciepłym cache
```

APK ląduje w `smarttubetv/build/outputs/apk/ststable/debug/`.

Wymagane: JDK 17 (ścieżka na górze `build-st-fast.bat`), Android SDK.

## Dokumentacja techniczna

Pełna lista zmian, mapa haków w kodzie upstreamu, pułapki i procedura
reaplikacji łatki po aktualizacji SmartTube'a: **[STPLUS/MODYFIKACJE.md](STPLUS/MODYFIKACJE.md)**.

## Licencja

MIT — jak oryginalny SmartTube. Autor oryginału: [yuliskov](https://github.com/yuliskov/SmartTube).
