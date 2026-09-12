# HANDOFF — SmartTube+ (sesja CC #2, 2026-09-12, 05:30–15:10)

> **ZANIM COKOLWIEK TKNIESZ — PRZECZYTAJ ROZDZIAŁ 0 DO KOŃCA.**
> Ten plik NIE wystarcza. Jest spisem stanu, nie zasadami projektu.

---

## 0. ZASADY PROWADZENIA SESJI (obowiązują od pierwszej sekundy)

### 0.1. Czytanie dokumentacji — OBOWIĄZKOWE, ZANIM DOTKNIESZ KODU

Sam handoff NIE daje kontekstu i zaczniesz pracować wbrew ustalonym zasadom.
Przeczytaj **w całości**, w tej kolejności:

1. `README-SmartTube.md` — build, urządzenia testowe, reguły forka.
2. `STPLUS/MODYFIKACJE.md` — **architektura „plugin"** (zasada 1: własny kod w osobnych
   plikach, w upstreamie tylko minimalne haki `>>> STPLUS` / `<<< STPLUS`), tabele
   wszystkich zmian, pułapki, procedura reaplikacji po aktualizacji upstreama.
3. `STPLUS/AUDYT-CC.md` — ustalenia U1–U5, K1–K24 ze statusami `H`/`C`/`O`.
4. `.goal_2.md` — cel i dziennik kapitański.
5. `STPLUS/PLAN_WYDAJNOSC.md` — plan dla odtwarzacza (jeszcze nietknięty).
6. `STPLUS/research/analiza_newpipe_feed.md` — **zawiera błędną tezę „NewPipe robi
   wyłącznie RSS"; czytaj RAZEM z K22, gdzie jest sprostowanie.**
7. `D:/cc/MISTAKES.md` — moje błędy z tej sesji, żeby ich nie powtórzyć.

**Powód (User, ostro):** sesja #2 przeczytała tylko handoff, weszła prosto w kod
i napisała ~60 linii własnej logiki w środku pliku upstreamu — łamiąc zasadę 1
z `MODYFIKACJE.md`, o której nie wiedziała. Trzeba było cofać z backupu.

### 0.2. Komunikacja — TYLKO GŁOSEM, ZERO ŚCIAN TEKSTU

- **Każdy meldunek przez `say.exe "…"`, po polsku, ZAWSZE z polskimi ogonkami.**
- **W konsoli najwyżej jedno zdanie.** User powtarzał to w tej sesji **trzy razy**,
  za trzecim mocno. Nie wypisuj podsumowań, tabelek, list — to idzie w głos.
- Mów przy: rozpoczęciu każdego zadania, starcie długiej operacji, zakończeniu,
  gdy User ma coś kliknąć.
- **Ścieżki, adresy i liczby czytamy dosłownie** — „D:/cc/workspace", „192.168.2.141",
  „32.40". Nie rozpisywać na słowa.
- Nie kończ tury podsumowaniem i nie czekaj bezczynnie — jedziesz dalej listą.
- **ZANIM WGRASZ COKOLWIEK NA PROJEKTOR — POWIEDZ „wgrywam" NA GŁOS.** Za każdym razem,
  przed `adb install`, także przy serii kolejnych buildów. User siedzi przy projektorze
  i testuje; instalacja podmienia aplikację pod jego ręką (ekran mruga, apka się
  restartuje, ustawienia wracają do stanu z buildu) i bez zapowiedzi wygląda to jak
  awaria — zaczyna wtedy zgłaszać nieistniejące błędy. Zgłoszone ostro 2026-09-12
  („masz mi kurwa mówić: w tym momencie wgrywam. Wystarczy jedno słowo na głos")
  z poleceniem wpisania do handoffu i do zasad.

### 0.3. Jak podejmować decyzje

- **NIE PODSUWAJ FAŁSZYWYCH WYBORÓW „A czy B".** Jeśli da się zrobić jedno i drugie —
  **projektujesz przełącznik i robisz oba**. User: „obie opcje możemy mieć jako
  przełącznik, nie musimy z jednej zrezygnować kosztem drugiej".
- Pytać wolno i trzeba, ale **pytanie ma zawierać gotową, dobrą propozycję**.
  User: „Nie o to chodzi, że masz nie pytać (...) od razu dajesz dobrą propozycję".
- **Nie leć w drugą skrajność, gdy User cię zruga.** Gdy zgłosił mielenie procesora,
  pierwszym odruchem było zwolnić pobieranie — a to nie była przyczyna. User:
  „mądrze jest tylko optymalnie, pod wieloma parametrami (...) za wolno to nie jest mądrze".
- **Naprawiaj PRZYCZYNĘ, nie objaw.**

### 0.4. Dokumentacja na bieżąco — OBOWIĄZEK

Ustalenie → od razu `AUDYT-CC.md`. Zmiana w kodzie → od razu `MODYFIKACJE.md`
(tabela + opis). Decyzja/zwrot → `.goal_2.md`. Własny błąd → `D:/cc/MISTAKES.md`.
Myśl Usera rzucona mimochodem → `D:/cc/workspace/MIND-TREE-MAP/todo/` **dosłownie**.

### 0.5. Weryfikacja

- **Sprawdzaj na sprzęcie, zanim powiesz „działa".** Status `O` nadaje wyłącznie pomiar
  na urządzeniu; czytanie kodu to najwyżej hipoteza.
- **Nie ogłaszaj wniosku mocniejszego, niż pozwala dowód.** W tej sesji zrobiłem to
  dwa razy w jeden dzień (patrz K24 i `MISTAKES.md`).
- **Punkt kontrolny musi być tej samej natury co badany przypadek.** Sprawdzanie
  „czy YouTube działa" z adresu chmurowego nic nie dowodzi — Google odrzuca takie IP.
- **Koniec celu ogłasza WYŁĄCZNIE User**, po własnym teście. Nie ustawiaj `status: done`.

### 0.6. To jest sprzęt słaby — pamiętaj o tym zawsze

Tablet SM-T580 i projektor to **armv7 z 2015 roku**. User: „robimy na słabe urządzenia
i zawsze trzeba mieć w głowie, że to ma działać wydajnie i mądrze". Każda zmiana
w pętli odświeżania = pytanie „ile to kosztuje CPU i ile śmieci produkuje".

### 0.7. Nie przejmuj ekranu, na którym User testuje

W tej sesji zrobiłem `force-stop` aplikacji na projektorze, na którym User właśnie
testował. Nie rób tego — do pomiarów używaj tabletu.

---

## 1. Czego User chce (jego słowa, zebrane z całej sesji)

1. **SmartTube+ ma być SZYBSZY niż oryginalny SmartTube**, a ładowanie kanałów ma być
   jak w **NewPipe** („NewPipe ładuje szybciutko”). Docelowo ma działać też **na telefonie**.
2. **Problem wydajności to fakt** — User porównał z oryginałem, nie trzeba tego udowadniać.
3. **Nie przyjmować cudzej diagnozy na wiarę.** Robotę pi trzeba było skrytykować i sprawdzić;
   tak samo następna sesja ma sprawdzić moją.
4. **Wszystko ma być robione mądrze, „jak najlepsze systemy”**, nie po najmniejszej linii
   oporu. Cytat: przez robotę „na odpierdol” powstały te błędy i potem grozi nam blokada konta.
5. **Odświeżanie rotacyjne** — nie wszystkie kanały naraz; jedna runda bierze część, następna
   kolejną, „ale mądrze”.
6. **Shorty** nie powinny być w wierszu, ale to sprawa drugorzędna — odfiltrujemy sami.
7. **Nasze funkcje mają iść do osobnych ustawień SmartTube+** (własny ekran w ustawieniach),
   z możliwością włączenia/wyłączenia opcji.
8. **Tryb debugowy**: przy wierszu „Twoje kanały” linia ze stanem (ile kanałów, co się dzieje,
   czy jest problem), a **pod każdym filmem czas publikacji** („2 godz. temu”).
   **Uwaga z 05:30: ta linia nie mieści się na ekranie tabletu i litery są za duże** —
   skrócona do formatu `Twoje kanały · 6/173 · 90f · ↻12 · !8`, do sprawdzenia na sprzęcie.
9. **Na później (zapisane, nie zrobione)**: przy pustej liście subskrypcji wiersz „Twoje kanały”
   ma pokazywać mały, szary, **klikalny** napis „brak subskrypcji — zaimportuj listę kanałów
   (np. z NewPipe)”.
10. **Testy na tablecie** (`192.168.2.135:5555`, SM-T580), projektor (`192.168.2.141:5555`)
    to maszyna Usera — ale dziś kazał wgrywać **na oba od razu**.
11. **Najpierw naprawa zdiagnozowanych błędów i wersja do testów**, rozwój dalszych rzeczy potem.

## 2. Co zostało ustalone (dowody w `STPLUS/AUDYT-CC.md`)

Najważniejsze, w kolejności wagi:

- **K22 (OTWARTE, najważniejsze do rozstrzygnięcia) — PipePipe robi to samo BEZ blokady.**
  User ma w **PipePipe** ~170 kanałów, klika „odśwież" i **skanuje wszystkie**, nigdy go
  nie zablokowało (nie wiadomo, jakie kody dostaje ani czy były nowe filmy). To **podważa
  moje wyjaśnienie** z K17. Do zbadania: nagłówki/klient (SmartTube chodzi jako TVHTML5,
  PipePipe jako zwykły klient), dokładny adres zapytania, `If-Modified-Since`/304, i dopiero
  na końcu tempo. **Plan: podejrzeć ruch PipePipe na tym samym urządzeniu i sieci,
  powtórzyć zapytanie z identycznymi nagłówkami, porównać.** Do tego czasu nie powtarzać
  tezy „to dławienie" jako pewnika.
- **K23 — moja głupota (zapisane na polecenie Usera).** Zbudowałem pobieranie „wszystko
  naraz", choć wzorzec NewPipe miałem pod ręką; potem, badając problem, **sam wygenerowałem
  kilkanaście zapytań z komputera pod rząd**, czyli pogorszyłem i **zanieczyściłem** to,
  co badałem — po tym nie da się uczciwie rozdzielić, co spowodowało 404: aplikacja czy
  moje testy. Regułę zapisałem w `MISTAKES.md` i w audycie (K23).
- **K17 (obserwacja pewna, wyjaśnienie NIEPEWNE) — kody odpowiedzi RSS.** Pomiar na tablecie:
  **149 zapytań → 140 × 404**, 2 × 200, 7 × 500. Kontrola z komputera: znany, pewny kanał
  raz daje 200, po serii 404 — niezależnie od User-Agenta. `404` **na pewno nie znaczy
  „nie ma kanału"**. Moja robocza interpretacja („za szybko") **nie jest udowodniona**
  i kłóci się z K22 — traktować jako hipotezę, nie ustalenie.
- **K16 (O) — sztorm odświeżeń NISZCZYŁ zawartość.** 6 × „Refreshing RSS for 50 channels”
  w 4,5 s; każdy kolejny fetch ubijał poprzedni, a ogryzek (42 pozycje z 3 kanałów)
  **nadpisywał** dobry cache (200 pozycji z 36 kanałów).
- **K13 (O) — wiersz pokazywał tylko kanały „od A do C”.** 178 subskrypcji, `MAX_CHANNELS = 50`
  brało pierwsze 50 z listy alfabetycznej → w wierszu 36 kanałów, koniec na „Coreteks”.
  Propozycja pi (50→12) pogłębiłaby to; odrzucona.
- **K1 (C) — na urządzeniach i na GitHubie stoi build DEBUG** z `HttpLoggingInterceptor.Level.BODY`
  (upstream sam ostrzega „expect slowdowns”): 9,5 MB logu w 70 s. Oryginał, z którym User
  porównuje, to release. **Release nie wymaga odinstalowania** — `keystore.properties`
  wskazuje `~/.android/debug.keystore` (ten sam klucz). Plik już utworzony.
- **K2 (C)** — pi mylnie przypisał blokadę main `runBlocking`; fetch leci na wątku roboczym,
  main był **zagłodzony** (50 równoległych korutyn + parsowanie + logowanie BODY).
- **K6 (O)** — mój błąd: `onHomeRowsLoaded` parsował cache 200 pozycji i przebudowywał wiersz
  **5–6 razy na starcie, na wątku głównym**.
- **K18 (O)** — zgłoszenie Usera po v2: wiersz skakał do góry, kółko blokowało belkę.
  Przyczyna: rotacja przerysowywała wiersz po każdej rundzie.
- **K19/K20/K15/K21** — tryb debugowy, podpowiedź importu, własne ustawienia, lekcja o tempie.

## 3. Co zrobione w kodzie (silnik v2)

Nowe pliki (nasze):
- `common/.../common/stplus/StPlusStore.java` — **magazyn per kanał**: materiały + `lastTry`/`lastOk`,
  plik tekstowy `filesDir/stplus/feed.txt` (da się zrzucić przez `run-as` i przeczytać),
  **scalanie** wyniku (kanał bez odpowiedzi zachowuje stare materiały), sortowanie po dacie
  publikacji, wybór kanałów do rundy (`pickStale`), statystyki do linii debugowej.
- `MediaServiceCore/youtubeapi/.../rss/RssOptions.java` — przełączniki: `skipChannelSync`,
  `maxParallelChannels`, `delayBetweenChannelsMs`.

Zmienione:
- `common/.../common/stplus/StPlus.java` — przepisany: wiersz zawsze z magazynu, **rotacja
  cicha** (rundy w tle nie przerysowują wiersza i nie kręcą kółkiem; wiersz odświeżany raz,
  na koniec obiegu), guard **czasowy** (`REFRESH_DEAD_MS = 90 s`), tryb debugowy w nagłówku,
  czas publikacji pod filmem, fallback na stary cache v1 przy pierwszym starcie.
  Tempo: `BATCH_CHANNELS = 8`, `PARALLEL_CHANNELS = 2`, `CHANNEL_DELAY_MS = 700`,
  `NEXT_ROUND_DELAY_MS = 6000`, `STALE_MS = 60 min`, `EMPTY_RETRY_MS = 5 min`.
- `MediaServiceCore/.../rss/RssService.kt` — hooki `>>> STPLUS`: pomijanie `syncWithChannel`
  (drugie, ciężkie zapytanie `browse` na każdy kanał), porcjowanie przez `chunked()` i odstęp
  między zapytaniami.
- `.gitignore` + `keystore.properties` (klucz debugowy, do buildu release).
- `build-st-release.bat`, `STPLUS/tools/bez-adnotacji-lint.init.gradle` (pomocnicze).

**Łatka pi** (jego niezacommitowane zmiany: guard boolowy, cooldown 30 min, MAX_CHANNELS 12)
leży nietknięta w `STPLUS/patches/pi-wip-20260912-guard-cooldown-maxchannels.patch`.
Jej pomysły zostały ocenione w audycie (część odrzucona z dowodem).

## 4. Błędy popełnione w tej sesji (i jak naprawione) — do MISTAKES.md

1. **Blokujący `java.util.concurrent.Semaphore` w korutynach `runBlocking`** → zakleszczenie,
   runda startowała i nigdy nie kończyła. Naprawa: `chunked()` zamiast semafora.
2. **Przerysowywanie wiersza po każdej rundzie** → skakanie ekranu (zgłoszenie Usera).
   Naprawa: cicha rotacja.
3. **Za szybkie odpytywanie YouTube** (burst) → 404 na prawie całej liście. Naprawa: 2 naraz,
   700 ms odstępu, rundy po 8, 6 s przerwy. **Lekcja (K21): cudze API projektujemy od tempa.**
4. **Za długa linia debugowa** w nagłówku wiersza — nie mieści się na tablecie. Skrócona,
   **do potwierdzenia na sprzęcie**.

## 5. STAN NA TERAZ (15:10) — co zrobiła sesja #2

### 5.1. Feed DZIAŁA (zweryfikowane na tablecie)

Pełny obieg 14:07 → 14:16: **173 ze 173 kanałów z materiałami, 2560 filmów, ZERO błędów.**
Punkt wyjścia rano: 6 kanałów, 90 filmów, 140 z 149 zapytań = 404.

**Przyczyna 404 (K24): kara dla naszego publicznego adresu** za sztorm — feed odświeżał się
„co 15 sekund na trzech urządzeniach w kółko". Kara minęła po nocnej przerwie. To **nie** była
awaria endpointu; moja teza „YouTube wygasza RSS" została **obalona pomiarem** — szczegóły
i lekcja w K24 oraz w `MISTAKES.md`.

### 5.2. Co doszło w kodzie (pełne tabele w `STPLUS/MODYFIKACJE.md`, rozdział „ŹRÓDŁA FEEDU v3")

**Nasze nowe pliki:**
- `MediaServiceCore/.../rss/StPlusFeedSource.kt` — druga droga (zakładki kanału przez
  `youtubei/v1/browse`), liczniki kodów HTTP, narastający backoff po 3 błędach z rzędu.
- `common/.../stplus/StPlusSettings.java` — nasze menu (zębatka) + trwałe ustawienia
  w prefs `stplus`.
- `smarttubetv/.../tv/presenter/StPlusRowPresenter.java` — zębatka doklejana do końca
  nagłówka „Twoje kanały".
- `smarttubetv/.../tv/presenter/StPlusStatusCard.java` — ciemny wygląd karty stanu.

**Haki w upstreamie (wszystkie krótkie, oznaczone `>>> STPLUS`):**
- `rss/RssService.kt` — 3 haki w `fetchFeed()`: wybór źródła, odwrót przy pustym wyniku,
  odwrót przy wyjątku + zapis kodu HTTP.
- `sharedutils/okhttp/OkHttpCommons.java` — profiler OFF, `HttpLoggingInterceptor`
  z `BODY` na `BASIC`.
- `data/Video.java` — `sync()` kopiuje tytuł/podtytuł dla karty statusu.
- `browse/video/MultipleRowsFragment.java` — podmiana prezentera wiersza (1 linia).
- `tv/presenter/VideoCardPresenter.java` — dekoracja karty statusu.
- `app/presenters/BrowsePresenter.java` — `isStatusItem` obok `isLoadingItem`.

### 5.3. Wydajność — co zżerało procesor (User: „mieli jak pojebany, 140%")

Pomiar wątków na projektorze: `HeapTaskDaemon` 57%, wątek roboczy 56%, korutyny 23%.
**Jeden proces aplikacji, żadnego dodatkowego w tle** (User o to pytał).

Dwie przyczyny, obie naprawione:
1. **Zapis magazynu po KAŻDEJ rundzie.** `files/stplus/feed.txt` ma **994 kB / 2733 wpisy**
   i był przepisywany w całości ~17 razy na obieg. Teraz: co 6 rund + zawsze na końcu obiegu
   (`StPlus.saveStore()`).
2. **Logowanie ciał odpowiedzi w buildzie debug.** 5836 z 13424 linii logcata to był
   `HttpLoggingInterceptor.Level.BODY` — każdy XML zamieniany na String i wypluwany do logu.
   Teraz `BASIC` (metoda, adres, kod) + profiler wyłączony.

**Tempa NIE zwalniałem** — to nie była przyczyna. Zamiast tego jest **regulacja w menu**.

### 5.4. Bezkresne skanowanie — znalezione i naprawione

`requestManualRefresh()` wołał rundy z `staleMs = 0`, więc warunek `now - lastTry >= 0`
był **zawsze prawdziwy** — każdy kanał zawsze uchodził za przeterminowany i **rotacja nigdy
się nie kończyła** (User: „173 ze 173 i dalej kurwa skanuje").

Naprawa: `StPlusStore.pickStaleBefore(cutoff, emptyCutoff, batch)` — wybór względem
**punktu w czasie**, a każda próba przesuwa znacznik kanału, więc obieg ma gwarantowany koniec.
Doszedł jawny model cyklu w `StPlus`: `sCycleCutoff`, `sCycleStarted`, `sCycleRunning`,
`maybeStartCycle(onDemand)`.

**Wejście na stronę główną NIE jest już wyzwalaczem skanowania** — wcześniej było i to jest
powód, dla którego aplikacja mieliła bez końca (home przeładowuje się często).

### 5.5. Menu pod zębatką (`StPlusSettings.show`)

- przełącznik **shortów** (domyślnie WYŁĄCZONE; rozpoznawanie heurystyczne: `Video.isShorts`
  ze ścieżki browse albo tag `#shorts` w tytule — RSS nie oznacza ich wcale),
- przełącznik **trybu debugowania** (opis w linii nagłówka + karta stanu + czas publikacji),
- **kiedy odświeżać**: tylko ręcznie / przy starcie i nie częściej niż co 30 min / 1 h / 3 h / 6 h,
- **prędkość pobierania**: oszczędna ~35 kan./min, normalna ~75, szybka ~150
  (profile w `StPlusSettings.speedProfile()`: kanałów w rundzie / równolegle / odstęp / przerwa),
- **skąd pobierać**: automatycznie / tylko RSS / tylko zakładki kanału, z podanym tempem,
- **odśwież teraz**,
- sekcja **debug**: „zapomnij ok. 30 filmów" i „ok. 200" — do testowania napełniania,
- **About SmartTube+** — opis projektu PO ANGIELSKU, gotowy do README na GitHubie.

**Usunięte świadomie:** „wyczyść cały magazyn". User o to nie prosił i uznał za niebezpieczne
bez potwierdzenia. Jeśli wróci — musi mieć pytanie potwierdzające.

### 5.6. Wygląd

- **Zębatka** na końcu napisu „Twoje kanały" (compound drawable), mała (18 dp), wyblakła
  (alpha 90/255), pełna jasność gdy wiersz zaznaczony. Klik dotykiem w prawy skraj nagłówka.
  **Druga droga do menu dla pilota: kliknięcie karty stanu** — dotyku na pilocie nie ma.
- **Karta stanu** ciemna (`#1B1B1B`), z ikoną aplikacji zamiast szarej zaślepki Glide'a.
- **Ikona odświeżania**: obrót 2600 ms zamiast 900, alpha 0.35/0.25, `setRotation(0)` po
  zakończeniu (wcześniej „przeskakiwała w złe miejsce"), kręci się **tylko przy ręcznym**
  odświeżeniu — ręczne dotyczy teraz jednej rundy, nie całego obiegu.
- **Mruganie i zrzucanie na początek listy** — dwie przyczyny usunięte: (a) `onHomeLoadStarted`
  zerował `sShownSignature` przy każdym ładowaniu home, (b) wiersz był renderowany dwa razy
  w jednym cyklu. **Nie dokładać meldunku z rundy do `signature()`** — próbowałem, to
  natychmiast wraca jako mruganie.

### 5.7. NIEZWERYFIKOWANE — do sprawdzenia na starcie następnej sesji

Ostatni build (15:05) wgrany na tablet i projektor, ale **User nie potwierdził jeszcze**:
- czy zębatka jest widoczna i klikalna (poprzednia wersja jej nie znalazła — nagłówek bywa
  kontenerem, teraz szukamy TextView rekurencyjnie),
- czy opis w linii „Twoje kanały" się pojawia i mieści,
- czy mruganie i przesuwanie wiersza przy starcie ustąpiło,
- czy CPU spadło po naprawie zapisu magazynu i logowania (**zmierzyć `top -H`**).

### 5.8. ZGŁOSZENIA USERA Z KOŃCA SESJI — NIEZROBIONE, DO WZIĘCIA NA POCZĄTEK

Zgłoszone 15:10–15:20, **świadomie NIE naprawiane** (User kazał tylko zapisać).

**(a) ZĘBATKA JEST NIEKLIKALNA — mój błąd projektowy, do przerobienia od podstaw.**

Zrobiłem ją jako `compound drawable` doklejony do `TextView` nagłówka
(`StPlusRowPresenter.attachGear()`), a klikanie przez `setOnTouchListener` sprawdzający
współrzędną X. **To nie działa: nagłówek wiersza w leanbacku nie dostaje zdarzeń dotyku**,
więc ikona jest tylko obrazkiem. User: „za tym nie da się jej kurwa kliknąć. Co ty
odpierdalasz?"

Wymagania Usera (powtórzone trzy razy — przeczytaj uważnie, zanim zaczniesz):
- zębatka **na końcu linii „Twoje kanały", po PRAWEJ stronie**,
- **mała**,
- **ledwo widoczna / wyblakła**, a **dopiero po najechaniu wyraźniejsza**,
- **musi dać się kliknąć** — i palcem (tablet), i z pilota (projektor).

Czego NIE robić: nie sadzać jej w prawym górnym rogu belki obok odświeżania —
**nachodzi na logo SmartTube**, już tak było i User to odrzucił.

Kierunek do rozważenia (nie zweryfikowany): prawdziwy `ImageView` dodany jako overlay
do widoku wiersza (`RowPresenter.ViewHolder.view`), wyrównany do prawej krawędzi
na wysokości nagłówka, `focusable` żeby dało się dojść pilotem. **Zanim zbudujesz —
sprawdź na sprzęcie, czy w ogóle dostaje fokus i kliknięcie.**

Zapasowe wejście do menu, które warto dodać niezależnie: **kliknięcie karty stanu
otwiera `StPlusSettings.show()`** — karta jest normalnie klikalna i pilotem, i palcem.
Dziś kliknięcie w nią jest tylko ignorowane (hak `isStatusItem` w `BrowsePresenter`).

**(b) WIERSZ „TWOJE KANAŁY" POJAWIA SIĘ ZA PÓŹNO PO STARCIE.**

Po restarcie aplikacji wiersza nie ma, dochodzi dopiero po chwili (i wtedy przesuwa układ).
User: „masz go wstrzykiwać wcześniej już, żeby tam był (...) Mądrze to trzeba zrobić."

Stan: wiersz rysuje się z magazynu przez `onHomeLoadStarted` → `renderFromStore(true)`,
ale magazyn jest wczytywany z pliku **dopiero przy pierwszym wywołaniu**
(`StPlusStore.load()` na wątku roboczym, plik ma ~994 kB). Do tego czasu wiersza nie ma.

Do rozważenia: wczytywać magazyn wcześniej (przy starcie procesu, nie przy pierwszym
renderze), trzymać mały „szybki" plik z samą pierwszą stroną wiersza (40 pozycji), albo
wstawiać pusty wiersz-szkielet od razu i dopiero wypełniać. **Uwaga: cokolwiek wybierzesz,
nie wolno przy tym przywrócić mrugania ani przesuwania układu** (patrz 5.6).

**(c) BRAKUJE PRZEŁĄCZNIKA „SKANUJ AUTOMATYCZNIE PRZY STARCIE".**

Dziś w menu jest „kiedy odświeżać" (tylko ręcznie / co 30 min / 1 h / 3 h / 6 h), ale
**obieg przy starcie aplikacji leci zawsze**, bo `sCycleStarted` jest wtedy zerowe
(`StPlus.maybeStartCycle()`). User chce jawnego wyboru: „czy będzie nam skanował
automatycznie przy starcie, czy sami sobie będziemy skanować".

**(d) SHORTY — zrobione, ale NIEZWERYFIKOWANE.**

Przełącznik „Pokazuj shorty" jest w menu i **domyślnie wyłączony**
(`StPlusSettings.isShowShorts()` → `false`), filtr w `StPlus.isShort()`.
Rozpoznawanie jest heurystyczne: `Video.isShorts` (tylko ścieżka „zakładki kanału")
albo tag `#shorts` w tytule — **RSS nie oznacza shortów wcale**, więc część przejdzie.
User dopytywał, czy o tym nie zapomniałem: nie, jest w kodzie, ale **nikt tego jeszcze
nie sprawdził na ekranie**.

## 6. CO DALEJ (kolejność)

0. **Zrobić (a), (b), (c) z rozdziału 5.8** — to są świeże zgłoszenia Usera, zapisane
   na jego polecenie zamiast naprawiane. Zębatka jest najpilniejsza: dziś jej po prostu
   nie da się kliknąć, więc całe menu ustawień jest niedostępne z ekranu.
1. **Potwierdzić punkty z 5.7 na sprzęcie** — to jest pierwsze zadanie, nie pisz nowego kodu
   przed tym.
2. **Zmierzyć CPU po zmianach** (`top -H -n 1 -b` na procesie) i zapisać liczby do `AUDYT-CC.md`.
   Przed: 140% (GC 57 + roboczy 56 + korutyny 23).
3. **Rozmiar magazynu** — 994 kB na 173 kanały. User pytał, czy „potrzebujemy mieć w historii
   milion plików". Do rozważenia: `MAX_PER_CHANNEL` 15 → 10, krótszy zapis pozycji
   (`Video.toString()` to ~360 bajtów na wpis).
4. **Potwierdzenie przy akcjach kasujących** w sekcji debug.
5. Własny ekran ustawień jako pełna sekcja (K15) — dziś jest dialog, może wystarczy.
6. Podpowiedź importu przy pustych subskrypcjach (K20).
7. Build release i uczciwe porównanie z oryginałem (K1) — **uwaga: Gradle nie ma dostępu
   do sieci** (simplewall blokuje javę z `C:/jdk`); `--offline` działa, ale
   `assembleStstableRelease` potrzebuje `lint-gradle` z sieci.
8. Dopiero potem FAZA 1 z `PLAN_WYDAJNOSC.md`: silnik sieciowy, bufor, kodek — szybki start
   odtwarzacza.
9. Na końcu, **po akceptacji Usera**: README po angielsku (tekst jest już w
   `StPlusSettings.about()`) + release na GitHubie.

## 7. Gdzie co zapisywać

- **Cel + dziennik kapitański** → `.goal_2.md` (w katalogu projektu). Wpis po każdym istotnym
  odkryciu, decyzji Usera albo zmianie kursu.
- **Ustalenia, objawy, dowody, hipotezy** → `STPLUS/AUDYT-CC.md` (U = objawy Usera,
  K = ustalenia; każdy ze statusem `H`/`C`/`O` i sekcją „czego nie wiem”).
- **Lista naszych zmian w kodzie + łatki** → `STPLUS/MODYFIKACJE.md` i `STPLUS/patches/`
  (**do uzupełnienia o silnik v2 — jeszcze nie zrobione**).
- **Moje błędy** → `D:/cc/MISTAKES.md`.
- **Myśli Usera rzucone mimochodem** → `D:/cc/workspace/MIND-TREE-MAP/todo/` (dosłownie).
- **Badania cudzych rozwiązań** → `STPLUS/research/` (jest tam klon NewPipe i analiza).

## 8. Narzędzia i komendy

```
build (szybki, offline):
  JAVA_HOME=C:/jdk/jdk-17.0.20.1+1 ./gradlew.bat :smarttubetv:assembleStstableDebug       --build-cache --parallel --offline

APK (zawsze armeabi-v7a — tablet i projektor sa armv7):
  smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_32.40_armeabi-v7a.apk

wgranie:
  C:/adb/platform-tools/adb.exe -s 192.168.2.135:5555 install -r --no-streaming <APK>   # tablet
  C:/adb/platform-tools/adb.exe -s 192.168.2.141:5555 install -r --no-streaming <APK>   # projektor

logi naszego kodu:
  adb -s <urz> logcat -d -s StPlus:V
  adb -s <urz> logcat -d -s System.err:V | grep -oE "code=[0-9]+" | sort | uniq -c   # kody HTTP

magazyn z urzadzenia:
  adb -s <urz> shell "run-as org.smarttube.plus.stable cat files/stplus/feed.txt"
  adb -s <urz> shell "run-as org.smarttube.plus.stable wc -lc files/stplus/feed.txt"

CPU (wg watkow procesu):
  adb -s <urz> shell "top -H -n 1 -b -o PID,TID,%CPU,CMD | grep <pid> | sort -k3 -rn | head"

pelna rutyna z hostingiem (TYLKO za zgoda Usera):  sh wyslij-st.sh
```

**PUŁAPKI ŚRODOWISKA (sprawdzone w tej sesji):**
- **Bash heredoc zjada backslashe** — `'
'` w skrypcie Pythona odpalanym przez `<<'PY'`
  wyląduje w pliku jako prawdziwe łamanie linii i rozwali literał Javy.
  Buduj przez `chr(92) + 'n'`. (Jest to też w `D:/cc/CLAUDE.md`.)
- `ls` z `--show-control-chars` wywala się na tym systemie — używaj `find` albo `stat`.
- **Do pomiarów używaj tabletu, nie projektora** — na projektorze User testuje.

## 9. Rzeczy otwarte / niepewne (nie udawać, że wiadomo)

- **Czy obecne tempo jest trwale bezpieczne.** Obieg przeszedł raz bez błędu przy 5 kan./1500 ms;
  potem przyspieszony do profilu „normalna" (10 / 3 równolegle / 600 ms / 5 s) — **przy tym
  tempie pełny obieg jeszcze nie został zmierzony do końca**.
- **Czy przy dwóch urządzeniach odświeżających naraz kara wraca.** Na czas diagnozy trzymać
  jedno. To był pierwotny powód bana (trzy urządzenia co 15 s).
- **Ile realnie kosztuje ścieżka „zakładki kanału"** — liczba 20–25 kan./min w menu jest
  **szacunkiem, nie pomiarem**. Zmierzyć i poprawić opis.
- **Jak rozpoznać shorta z samego RSS** — dziś tylko tag `#shorts` w tytule. To heurystyka,
  część shortów przejdzie.
- Czy `If-Modified-Since` / ETag pomaga na `feeds/videos.xml` (nie sprawdzone).
- Realna różnica debug vs release (nie zmierzona, build release blokowany przez firewall).
- **Rozmiar magazynu** — 994 kB przy 173 kanałach; czy przycinać.
