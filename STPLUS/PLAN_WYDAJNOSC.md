# PLAN WYDAJNOŚCI — SmartTube+ (wersja pi)

> Proces badawczy, wieloetapowy. User zatwierdza koniec tylko po własnym teście
> na urządzeniach. Każda zmiana = moduł → `MODYFIKACJE.md` + patch.
> Cel nadrzędny: wydajność jak w NewPipe/oryginalnym ST + wiersz "Twoje kanały"
> **zawsze u góry** i stabilny.

## Legenda statusów
`[ ]` do zrobienia · `[~]` w trakcie · `[x]` zrobione i zweryfikowane · `[?]` do badania

---

## FAZA 0 — Baseline (pomiar stanu wyjściowego)

Cel: mieć liczby "przed", żeby porównywać "po".

- [x] Diagnoza przyczyny wolności (logcat, 2026-09-12) — patrz niżej "Diagnoza".
- [ ] Zmierz **czas klik→start odtwarzacza** na projektorze (bieżące domyślne = Cronet,
      domyślna jakość, domyślny bufor). 3–5 prób, średnia. To jest nasz punkt odniesienia.
- [ ] Zmierz **czas ładowania home** (od startu do stabilnego obrazu) + liczba
      "Skipped N frames" (baseline: widziałem 176/110/77 frames).
- [ ] Zmierz **PSS pamięci** STPlus vs oryginał (baseline: ~121 MB vs ~52 MB).
- [ ] Uchwycić **bieżące domyślne**: silnik (Cronet), jakość, bufor, kodek
      (sprawdzić co faktycznie jest ustawione na projektorze: `adb shell dumpsys` /
      `run-as` prefs).

**Metodologia pomiaru klik→start:**
- Opcja A (automat): logcat — znacznik czasu od `onVideoStart`/klik do pierwszego
  `STATE_READY`/`onPlay`. Szukać w logach ExoPlayer/Playback.
- Opcja B (ręczna, wiarygodna): User klika, pi mierzy stoperem / nagrywa ekran z
  timestampem. Prostsze, ale wolniejsze.
- Zapisywać: urządzenie, silnik, jakość, kodek, bufor, wynik (s), powtórzenia.
- Tabela wyników → na dole tego pliku ("Wyniki pomiarów").

---

## FAZA 1 — Domyślne ustawienia odtwarzacza (szybki start)

Zasada: ustawić **domyślnie** (w kodzie, nie ręcznie w UI) wartości dające
największą wydajność na projektorach. Wszystko co mądre: mniejsze obciążenie,
nie za dużo na raz, część w tle.

### 1.1 Silnik sieciowy (do pomiaru, NIE zgadywać)
- [ ] Zbudować 3 warianty do testu (lub przełączać w UI i mierzyć):
      - `PLAYER_DATA_SOURCE_CRONET` (2) — bieżące domyślne
      - `PLAYER_DATA_SOURCE_OKHTTP` (1)
      - `PLAYER_DATA_SOURCE_DEFAULT` (0)
- [ ] Zmierz czas klik→start dla każdego na projektorze (po 3–5 prób).
- [ ] Wybrać najszybszy → ustawić jako **domyślne** w `Utils.getFasterDataSource()`
      (hook `>>> STPLUS`, tylko dla naszego builda) LUB w `PlayerTweaksData` default.
- [ ] Zapisać wynik + uzasadnienie w `MODYFIKACJE.md`.

### 1.2 Jakość + kodek (1080p, VP9/AVC, niski bitrate)
- [ ] Zidentyfikować, gdzie siedzi domyślna jakość/formaty (`MediaServiceData`,
      `GlobalPreferences`, `PlayerTweaksData`).
- [ ] Ustawić domyślnie: **Full HD 1080p**, kodek **VP9** (fallback **AVC**),
      **wyłączyć 4K/AV1** (AV1 = miękkie dekodowanie na armv7 = wolne/zawiesza).
- [ ] Niski bitrate (nie najbardziej wyczerpujący wariant 1080p).
- [ ] Zweryfikować na projektorze: czy 1080p leci płynnie, czy start jest szybki.

### 1.3 Bufor (mały)
- [ ] Zidentyfikować rozmiar bufora (ExoPlayer `DefaultLoadControl` / `GlobalPreferences`).
- [ ] Ustawić **niski** bufor startowy (wideo leci od razu, nie czeka na MB).
- [ ] Balans: za mały bufor = przerywanie przy słabszym WiFi. Znaleźć optimum.

### 1.4 Reszta (SponsorBlock / metadane / inne ciężkie rzeczy w tle)
- [ ] Sprawdzić, co jeszcze blokuje start (SponsorBlock, komentarze, sugestie).
- [ ] Przenieść to, co nie krytyczne, do tła / opóźnić (nie blokować odtwarzacza).
- [ ] `mIsSectionPlaylistEnabled` — na słabych RAM "Cause severe GC stuttering"
      (komentarz w kodzie). Rozważyć wyłączenie na projektorze.

---

## FAZA 2 — Stabilność + wydajność wiersza "Twoje kanały" (nasz kod)

### 2.1 Wydajność feedu (diagnoza → naprawa)
Diagnoza (2026-09-12): `RssService.getFeed()` dla 50 kanałów = 2× ciężkie zapytania
per kanał (RSS + `syncWithChannel`→browse) + home 3× + `runBlocking` czeka na wszystkie.

- [ ] **Obejść `syncWithChannel`** (największy zysk): nie potrzebujemy badge/live/
      preview z browse. Mały hook w `RssService.kt` (flaga, domyślne zachowanie bez
      zmian) → oszczędza ~50 ciężkich POST browse.
- [ ] **Cooldown cache**: jeśli cache świeży (< 30 min), pokaż cache i NIE ruszaj sieci.
- [ ] **`MAX_CHANNELS` 50 → 12** (w `StPlus.java`).
- [ ] **Guard anti-refetch**: nie odpalaj fetchu, jeśli już świeży / w trakcie
      (zabija 3×-krotne powtórki przy home 3×).
- [ ] Zweryfikować: home ładuje się płynnie, brak "Skipped N frames" > ~30.

### 2.2 Stabilność wiersza (znika / pojawia się / dziwne rzeczy)
- [ ] **Prześledzić dokładnie** kolejność zdarzeń przy starcie (logi StPlus +
      BrowsePresenter + MultipleRowsFragment): kiedy wiersz wchodzi, kiedy wychodzi,
      co go przesuwa.
- [ ] Sprawdzić, czy **MY** powodujemy problem (np. konflikt pozycji z domyślnymi
      wierszami home, dispose przy 3× home, race cache-first vs sieć).
- [ ] Cel: wiersz **zawsze u góry**, stabilny, bez znikania.
- [ ] Naprawić konkretne przyczyny (po prześledzeniu).

---

## FAZA 3 — Testy i porównania (iteracje)

- [ ] Każdą zmianę: build → wgraj (tablet + projektor) → zmierz → porównaj z baseline.
- [ ] Pokazywać Userowi wersje (screeny / nagrania / liczby).
- [ ] Tabela "Wyniki pomiarów" na dole — aktualizować po każdej iteracji.
- [ ] Trzymać się: nie psuć tego, co działa (rebrand, pakiet, wiersz, toolbar, automat).

---

## FAZA 4 — Dokumentacja i publikacja (NA KONCU, po wszystkim)

- [ ] Uaktualnić `MODYFIKACJE.md` + patch `stplus-001` (całość zmian pi).
- [ ] **GitHub: README po angielsku** (nie po polsku).
- [ ] **GitHub: przeprosiny**, że poprzednia wersja była zbugowana i wolna.
- [ ] Push (token tylko w `klucz github1.txt`, push jednorazowym URL-em).
- [ ] Release z nowym APK (wyslij-st.sh).
- [ ] User zatwierdza koniec celu (po własnym teście na urządzeniach).

---

## Diagnoza — dlaczego wolno (2026-09-12)

Wiersz "Twoje kanały" (`StPlus.fetchFeed()` → `RssService.getFeed()`):
- Dla **50 kanałów** każdy robi: (1) GET `feeds/videos.xml` (RSS), (2) parse XML,
  (3) **`syncWithChannel` → `getChannelAsGrid` → POST `youtubei/v1/browse`** (ogromny JSON).
- Zmierzone w 20 s: **55 GET RSS + 20+ POST browse** = 100+ ciężkich zapytań.
- **Home ładuje się 3×** (26/32/35/36 s) → 3× sztorm (dispose→`InterruptedException`).
- **`runBlocking`** czeka na WSZYSTKIE kanały (czas = najwolniejszy, 30 s+).
- Efekt: `Choreographer: Skipped 176 frames` (≈3 s zamrożenia UI), PSS ~121 MB vs ~52 MB.
- Oryginał nie ma tego wiersza → dlatego szybszy.

## Wyniki pomiarów (tabela — uzupełniać)

| # | Data | Urządzenie | Silnik | Jakość/Kodek | Bufor | Klik→start (s) | Home (s) | Skipped frames | PSS (MB) | Uwagi |
|---|------|-----------|--------|--------------|-------|----------------|----------|----------------|----------|-------|
| 0 | 2026-09-12 | projektor | Cronet | domyślne | domyślny | ? (do zmierzenia) | ~30+ (3×) | 176/110/77 | ~121 | baseline |
