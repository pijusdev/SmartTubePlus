# AUDYT WYDAJNOŚCI I POPRAWNOŚCI — SmartTube+ (rewizja CC, 2026-09-12)

> Ten dokument jest **krytycznym przeglądem** diagnozy pi (`.goal_1.md`, `PLAN_WYDAJNOSC.md`)
> oraz rejestrem **wszystkich objawów zgłoszonych przez Usera**. Nic tu nie jest przyjęte
> na wiarę: każdy punkt ma *jak zbadane*, *dowód*, *status weryfikacji* i *co dalej*.
>
> Statusy: `H` = hipoteza (tylko rozumowanie/kod) · `C` = częściowo zweryfikowane
> (kod + pośredni dowód) · `O` = zweryfikowane na sprzęcie (pomiar/log/ekran).
>
> Zasada: **status `O` nadaje tylko pomiar na urządzeniu.** Czytanie kodu to najwyżej `C`.

---

## A. Objawy zgłoszone przez Usera (źródło prawdy — do wyjaśnienia co do jednego)

| # | Objaw (słowa Usera) | Status | Gdzie badane |
|---|---------------------|--------|--------------|
| U1 | „SmartTube+ muli, jest bardzo duża różnica wydajności względem oryginalnego SmartTube" | C | K1, K2, K3 |
| U2 | „kanały z subskrypcji praktycznie się nie odświeżają same, albo odświeżają się nie wiadomo kiedy" | **O — wyjaśnione (K13 + K16)** | K13, K16, K4, K5, K8, K9 |
| U3 | „problem z kolejnością — najnowsze powinny być na początku, to nie działa" | **O — sortowanie OK; winne obcięcie listy kanałów (K13) i niszczenie cache (K16)** | K13, K16, K10 |
| U4 | „obraz znikał, wielokrotnie się powtarzał, pojawiał, odświeżało się to samo kilka razy" | **O — wyjaśnione (K6 + K16)** | K16, K6, K11 |
| U5 | „SmartTube bardzo długo ładuje kanały, NewPipe ładuje szybciutko" (cel docelowy) | H | K3, K12 |

**Uwaga metodologiczna:** U2/U3/U4 to objawy, których diagnoza pi **w ogóle nie obejmuje**
(pi badał wyłącznie czas ładowania home i sztorm RSS). Dopisane tutaj jako osobne wątki.

---

## B. Krytyka diagnozy pi (co się broni, co nie)

### K1 — NAJWIĘKSZA DŹWIGNIA, POMINIĘTA PRZEZ pi: na urządzeniach stoi build **debug**
- **Status: C** (kod + skrypty; do potwierdzenia pomiarem release vs debug)
- **Jak zbadane:** `build-st-fast.bat` i `wyslij-st.sh` budują `assembleStstableDebug`;
  APK z `smarttubetv/build/outputs/apk/ststable/debug/` idzie na tablet, projektor
  **i na stały link/GitHub**. Oryginalny SmartTube, z którym User porównuje, to **release**.
- **Dlaczego to boli:**
  1. `OkHttpCommons.debugSetup()` → w `BuildConfig.DEBUG` wpina `HttpLoggingInterceptor`
     z poziomem **`Level.BODY`** dla **wszystkich** żądań. Upstream sam pisze w komentarzu:
     *„Also outputs to logcat tons of info. If you enable it to all requests — expect slowdowns."*
     Każda odpowiedź `youtubei/v1/browse` (setki kB–MB JSON) jest w całości zamieniana na
     Stringi i wypychana do logcatu — na armv7 to czysty CPU + GC.
     Pomiar pi to potwierdza pośrednio: **125 tys. linii logu w 42 s**.
  2. `debuggable=true` — ART wyłącza część optymalizacji, aplikacja jest z zasady wolniejsza.
  3. `minifyEnabled` jest **zakomentowane** także w release — release i tak nie jest
     obcięty, ale pozostałe dwa punkty odpadają.
- **Wniosek:** część „SmartTube+ muli" **nie jest winą naszego feedu, tylko typu builda**.
  Porównanie „my 121 MB PSS vs oryginał 52 MB" jest **nieuczciwe metodologicznie** —
  to debug vs release.
- **Podpis — sprostowanie (moja pomyłka w pierwszej wersji audytu):** najpierw napisałem, że
  release wymusi odinstalowanie apki. **Nieprawda.** Obecny debug jest podpisany domyślnym
  kluczem debugowym Androida (`%USERPROFILE%/.android/debug.keystore`, alias `androiddebugkey`,
  hasło `android`, SHA1 `D1:E5:44:C9:...`). Wystarczy wskazać **ten sam plik** w
  `keystore.properties` — wtedy `buildTypes.release` używa `signingConfigs.release` z tym
  kluczem, podpis jest identyczny z zainstalowanym i APK wchodzi zwykłym `install -r`,
  **bez utraty ustawień i listy subskrypcji**. (Docelowo do publikacji i tak potrzebny
  osobny, prawdziwy klucz — ale to osobna sprawa, nie blokuje pomiaru.)
- **Co dalej:** zmierzyć to samo (start home, klik→start, PSS) na debug i na release.
  Jeśli różnica jest duża — wydajemy release, a debug zostaje tylko do diagnostyki.

### K2 — pi błędnie opisuje przyczynę „main zablokowany 28 s" (D4)
- **Status: C**
- **Jak zbadane:** `YouTubeContentService.getRssFeedObserve()` → `RxHelper.fromCallable`
  → `subscribeOn(cachedScheduler)` + `observeOn(mainThread)`. Czyli `runBlocking`
  z `RssService` **nie stoi na main** — stoi na wątku roboczym Rx.
- **Prawdziwy mechanizm (hipoteza do pomiaru):** main nie jest *zablokowany*, tylko
  **zagłodzony**: `fetchFeeds` odpala `launch {}` dla **każdego** kanału naraz na
  `Dispatchers.IO` (do 64 wątków) → 50 równoległych pobrań RSS + 50 × parsowanie XML
  + 50 × `getChannelAsGrid` (ogromny JSON) + logowanie BODY. Na 4-rdzeniowym armv7
  to saturacja CPU i lawina GC → `Skipped N frames`.
- **Dlaczego to ważne:** inna przyczyna = inna naprawa. Lek na zagłodzenie to
  **ograniczenie równoległości** (semafor, np. 4–6 jednoczesnych kanałów) i zdjęcie
  ciężaru z każdego kanału — a nie samo obcięcie liczby kanałów.

### K3 — `MAX_CHANNELS` 50 → 12 to nie optymalizacja, tylko **obcięcie funkcji**
- **Status: O** (fakt wprost z kodu)
- User ma ~170 zaimportowanych kanałów. Po zmianie pi wiersz „Twoje kanały" pokazuje
  materiały z **12 pierwszych** kanałów. Reszta subskrypcji przestaje istnieć w feedzie.
  To dokładnie ten objaw, który User zgłasza jako U2/U3 („nie odświeżają się", „zła kolejność").
- **Alternatywa bez straty funkcji:** zostawić dużo kanałów, ale:
  (a) ograniczyć równoległość, (b) pominąć `syncWithChannel` (patrz K7),
  (c) **warunkowy GET RSS** (`If-Modified-Since`/ETag → 304 zamiast pełnego XML),
  (d) odświeżać kanały **partiami/rotacyjnie** i dokładać do wiersza przyrostowo.
  To jest dokładnie model NewPipe (sam RSS, bez `browse` per kanał).

### K4 — guard `if (sRefreshing) return;` może **trwale zabić odświeżanie**
- **Status: C** (analiza kodu; do potwierdzenia testem z odciętą siecią)
- `sRefreshing` zdejmowane jest **wyłącznie** w callbacku `onNext`/`onError` subskrypcji.
  Jeśli strumień nigdy nie emituje (dispose przy zmianie ekranu, zawis sieci, wyjątek
  połknięty w `fetchFeedsSafe` → `null` → brak emisji), `sRefreshing`
  zostaje `true` **do końca życia procesu**. Wtedy nie odświeży się ani automat,
  ani ręczny przycisk (`requestManualRefresh` też sprawdza `sRefreshing`).
  **Przed zmianą pi ratował to `prev.dispose()` + restart.**
- **To jest bezpośredni kandydat na U2** („nie odświeża się, albo nie wiadomo kiedy").
- **Poprawka:** guard **czasowy** (znacznik startu odświeżania, np. > 2 min = uznaj za
  martwy i pozwól ruszyć od nowa) + `timeout()` na strumieniu, a nie goły boolean.

### K5 — `sForceRefresh` nie jest czyszczone przy wcześniejszym wyjściu
- **Status: O** (wprost z kodu)
- Gdy ręczny refresh wejdzie, a `fetchFeed` wyjdzie wcześniej (brak grup kanałów /
  brak ID / brak `ContentService`), `sForceRefresh` zostaje `true` — pierwszy kolejny
  **automatyczny** fetch ominie cooldown. Drobne, ale to błąd stanu.

### K6 — MÓJ błąd, którego pi **nie znalazł**: `onHomeRowsLoaded` robi ciężką robotę na main przy KAŻDYM ładowaniu home
- **Status: C**
- `onHomeRowsLoaded` (wołane ~6× wg logów pi) przy każdym wywołaniu: `loadCache()` —
  parsowanie stringa z SharedPreferences na **do 200 obiektów `Video`** (`Video.fromString`)
  — a potem `showRow()` = przebudowa wiersza. **Wszystko na wątku głównym.**
- Cooldown pi **tego nie łapie** — guard siedzi dopiero w `fetchFeed`, a `loadCache`
  + `showRow` są przed nim.
- **To jest bezpośredni kandydat na U4** („obraz znikał, powtarzał się, odświeżało się kilka razy”):
  6 × `ACTION_REPLACE` na tym samym wierszu = 6 × usunięcie i wstawienie wiersza na ekranie.
- **Poprawka:** trzymać cache w pamięci (raz sparsowany), guard na **cały hook**
  (nie tylko na fetch), i nie przebudowywać wiersza, gdy zawartość się nie zmieniła.

### K7 — „obejść `syncWithChannel`” nie jest darmowe (pi tego nie odnotował)
- **Status: O** (z kodu `RssService.syncWithChannel`)
- `syncWithChannel` nie tylko dokłada `badge/isLive/isUpcoming/preview/percentWatched` —
  robi też `Helpers.removeIf`, który **usuwa z wyniku wszystkie pozycje nieobecne w gridzie
  kanału** (tak wypadają m.in. **shorty**). Pominięcie go = **shorty wracają do wiersza**
  i znikają oznaczenia LIVE/premiera.
- **Decyzja Usera (2026-09-12):** shortów w wierszu być nie powinno, ale to **sprawa
  drugorzędna** — odfiltrujemy je własnym sposobem (NewPipe też to potrafi bez `browse`).
  `syncWithChannel` idzie do wycięcia; filtr shortów dorobimy osobno.
- **Niepewne (H):** czym dokładnie odróżnić shorta bez `browse`. Kandydaci do zbadania:
  długość filmu (RSS jej nie podaje), osobny feed shortów, heurystyka po tytule/miniaturze,
  albo jedno zbiorcze zapytanie zamiast jednego na kanał. **Nie wiem jeszcze, który zadziała.**

### K8 — cooldown 30 min **pogarsza** objaw U2
- **Status: C**
- User skarży się, że subskrypcje „praktycznie się nie odświeżają”. Cooldown pi mówi:
  jeśli cache młodszy niż 30 min — **nie ruszaj sieci w ogóle**. To jest lek na sztorm,
  ale jednocześnie dokładka do dolegliwości Usera.
- **Lepszy model:** cooldown krótki (np. 2–5 min) **+** twardy guard na równoległość
  **+** odświeżanie przyrostowe (partie kanałów), żeby wiersz żył, a nie zamarzał.

---

## C. Wątki, których diagnoza pi w ogóle nie obejmuje

### K9 — kiedy w ogóle następuje odświeżenie? (U2)
- **Status: H** — do prześledzenia na sprzęcie.
- Fetch startuje **wyłącznie** z `onHomeRowsLoaded`, czyli przy ładowaniu ekranu home.
  Nie ma odświeżania po powrocie z odtwarzacza, po czasie, ani w tle. Jeśli User trzyma
  aplikację otwartą, wiersz może nie zmienić się godzinami.
- **Do zbadania:** czy wejście/wyjście z ekranu w ogóle wywołuje hook; czy wiersz
  odświeża się po powrocie z wideo.

### K10 — kolejność „najnowsze pierwsze” (U3)
- **Status: H**
- Z kodu sortowanie **wygląda poprawnie**: `RssService.getFeed()` robi
  `items.sortByDescending { it.publishedDate }`, a `publishedDate` to **`long`**
  (unix ms z `DateHelper.toUnixTimeMs`) — więc nie jest to sortowanie po tekście.
- **Podejrzenie:** to, co User widzi, to **stary cache** (kolejność z chwili ostatniego
  udanego pobrania), a nie bieżący stan — czyli U3 może być objawem U2, nie osobnym błędem.
- **Drugi trop:** `Video.toString()` (nasz cache) **nie zapisuje `publishedDate`** —
  po odczycie z cache kolejność da się odtworzyć wyłącznie z zapisanej kolejności listy;
  każda operacja, która by tę listę przemieszała, jest nieodwracalna.
- **Do zbadania na sprzęcie:** zrzucić pierwsze 10 pozycji wiersza + ich realne daty
  publikacji i porównać z YouTube/NewPipe.

### K11 — migotanie/duplikaty wiersza (U4)
- **Status: H**
- Kandydaci: (a) 6 × `ACTION_REPLACE` z K6; (b) wyścig „cache wcześnie” (`onHomeLoadStarted`)
  z „cache późno” (`onHomeRowsLoaded`); (c) `ACTION_REMOVE` kafelka „Ładowanie…”
  + `ACTION_APPEND` przy przewijaniu nakładające się na `REPLACE` z odświeżenia w tle.
- **Do zbadania:** log z licznikiem każdego wejścia w hooki + nagranie ekranu startu.

### K12 — dlaczego NewPipe ładuje kanały szybko (cel U5)
- **Status: H** — do potwierdzenia researchem + testem.
- Hipoteza: NewPipe bierze **wyłącznie** RSS (`feeds/videos.xml`, mały XML, dobrze
  cache'owalny, wspiera `If-Modified-Since`), pobiera **równolegle, ale z ograniczeniem**,
  zapisuje do bazy i renderuje z bazy; **nie robi** żadnego `youtubei/v1/browse` per kanał.
  Nasz koszt per kanał jest wielokrotnie większy właśnie przez `syncWithChannel`.

### K13 — ZWERYFIKOWANE NA SPRZĘCIE: wiersz pokazuje tylko kanały „od A do C" (178 → 36)
- **Status: O** (zrzut z tabletu, 2026-09-12 04:20)
- **Jak zbadane:** `run-as org.smarttube.plus.stable cat shared_prefs/stplus.xml` (nasz cache
  wiersza) + `files/yt_service_prefs/anonymous_channel_group_data` (lokalne subskrypcje).
- **Dowód:**
  - lokalna lista subskrypcji: **178 unikalnych ID kanałów**;
  - cache wiersza: 200 pozycji, ale pochodzą z **36 kanałów** i wszystkie z początku
    alfabetu: `0xSero, 1littlecoder, 4nrgy pl, 80,000 Hours, AI …, Astrofaza, Asianometry,
    August Micota, Bijan Bowen, Black Hat, bycloud, Caleb Writes Code, ColdFusion,
    Copernicus, Coreteks` — dalej niż „C" nic nie ma.
  - `MAX_CHANNELS = 50` bierze **pierwsze 50 ID** z listy w kolejności, w jakiej leżą
    w grupie (alfabetycznej), a z tych 50 do 200 najnowszych pozycji weszło 36.
- **Wniosek:** to jest **główna przyczyna U2 i U3**. Kanały spoza początku alfabetu
  **nigdy** nie pojawiają się w wierszu — z perspektywy Usera wygląda to jak „nie odświeża się"
  i „zła kolejność". Samo sortowanie po dacie jest poprawne (najnowsze na górze — sprawdzone
  na zrzucie: 12 wrz, potem 11 wrz, potem 10 wrz…).
- **Konsekwencja dla planu pi:** `MAX_CHANNELS` 50 → 12 **pogłębiłoby** dokładnie tę usterkę
  (zostałaby sama litera „A"). Odrzucone.

### K14 — Model docelowy: odświeżanie rotacyjne (decyzja Usera, 2026-09-12)
- **Status: H** (projekt do zaimplementowania i zmierzenia — jeszcze nic z tego nie działa)
- **Decyzja Usera:** nie odświeżamy wszystkich kanałów naraz; odświeżamy **partiami,
  rotacyjnie** — jedno odświeżenie bierze jedną porcję kanałów, kolejne następną,
  „ale mądrze".
- **Szkic (moja propozycja, do skrytykowania i sprawdzenia):**
  1. **Trwały magazyn pozycji** (nie jeden blob z 200 pozycjami): dla każdego kanału
     jego ostatnie materiały + znacznik „kiedy ostatnio sprawdzany".
  2. Wiersz renderowany **zawsze z magazynu**, posortowany po dacie malejąco —
     natychmiast, bez sieci. Świeżość wiersza przestaje zależeć od tego, czy zdążyliśmy
     odpytać wszystkie 178 kanałów.
  3. Każde odświeżenie bierze **N najdawniej sprawdzanych kanałów** (np. 10–15),
     z ograniczoną równoległością (np. 4–6 naraz) → pełny obieg 178 kanałów rozkłada się
     na kilkanaście cykli zamiast jednego sztormu.
  4. **Warunkowy GET** (`If-Modified-Since`/ETag) → kanał bez zmian kosztuje 304, nie XML.
  5. Kanały z częstymi publikacjami sprawdzane częściej (priorytet po historii publikacji) —
     to jest ta „mądrość”; do zmierzenia, czy warto.
- **Czego NIE wiem (uczciwie):** czy YouTube RSS honoruje `If-Modified-Since` dla tych feedów;
  jaki rozmiar porcji i jaki limit równoległości są optymalne na armv7; czy magazyn ma być
  bazą (Room/SQLite) czy plikiem — to wszystko do zmierzenia, nie do zgadnięcia.

### K16 — ZWERYFIKOWANE: sztorm odświeżeń **niszczy zawartość wiersza i cache**
- **Status: O** (pomiar świeżego startu na tablecie, 2026-09-12 04:27, log 48 728 linii)
- **Jak zbadane:** `am force-stop` → `logcat -c` → start z launchera → 70 s logcatu
  (`scratchpad/base-debug.log`), potem zrzut `shared_prefs/stplus.xml` przed i po.
- **Dowód z logu (ta sama sekunda po sekundzie):**
  ```
  04:27:46.994  Row from cache (early): 200 items
  04:27:49.518  Refreshing RSS for 50 channels
  04:27:51.832  Row from cache (late): 200 items      <- przebudowa wiersza na main
  04:27:51.837  Refreshing RSS for 50 channels        <- drugi fetch ubija pierwszy
  04:27:52.438  Row from cache (late): 200 items
  04:27:52.443  Refreshing RSS for 50 channels
  04:27:53.028  Row from cache (late): 200 items
  04:27:53.032  Refreshing RSS for 50 channels
  04:27:53.539  Row from cache (late): 200 items
  04:27:53.543  Refreshing RSS for 50 channels
  04:27:54.089  Refreshing RSS for 50 channels        <- szósty w 4,5 s
  04:27:56.223  Row refreshed: 42 items               <- i tyle zostało
  ```
- **Stan cache przed pomiarem:** 200 pozycji z **36 kanałów**.
  **Stan po pomiarze:** 42 pozycje z **3 kanałów** (`Astrofaza`, `BaldTV`, `Caleb Writes Code`).
- **Mechanizm:** każdy kolejny `fetchFeed` robi `prev.dispose()`. Dispose przerywa
  `runBlocking`/korutyny w `RssService` w locie (stąd `InterruptedException` w logach pi),
  więc ostatni, jedyny ocalały fetch zwraca **tylko te kanały, które zdążyły dojść**.
  Nasz kod bezwarunkowo robi `saveCache(...)` i `showRow(...)` z tym ogryzkiem —
  **dobry cache zostaje nadpisany śmieciem**.
- **To jest bezpośrednie, potwierdzone wyjaśnienie U2, U3 i U4 naraz**: wiersz „gubi"
  kanały, zmienia się w trakcie patrzenia i pokazuje przypadkowy podzbiór subskrypcji.
  Diagnoza pi widziała sztorm, ale **nie zauważyła, że sztorm niszczy treść**.
- **Wnioski projektowe (to musi wejść do naprawy):**
  1. Wynik odświeżenia **nigdy nie nadpisuje** magazynu w całości — jest **scalany**
     (per kanał), więc kanał, który nie odpowiedział, zachowuje poprzednie materiały.
  2. Nie zapisujemy wyniku krótszego/uboższego bez sprawdzenia, z ilu kanałów pochodzi.
  3. Fetch ma być **jeden naraz** (guard czasowy, K4) i nie wolno go ubijać w locie
     bez potrzeby; zamiast `dispose()` przy każdym wejściu — pomiń nowe żądanie.

### Pozostałe liczby z tego samego pomiaru (baseline, build debug)
- `Choreographer: Skipped frames`: 5 zdarzeń, **suma 395 klatek**, największe 175 (≈2,9 s).
- Log: **9,5 MB w 70 s**, 1392 linie dłuższe niż 2000 znaków — potwierdzenie K1 (logowanie
  `BODY` w buildzie debug). 1848 linii dotyczy `feeds/videos.xml`, 51 linii `youtubei/v1/browse`.
- `Row from cache (late)` **5×** w 2 s = 5 × parsowanie 200 pozycji i przebudowa wiersza
  na głównym wątku → potwierdzenie **K6** (status `O`).

### K17 — ZWERYFIKOWANE: YouTube **dławi** zapytania RSS i odpowiada 404 (to jest prawdziwa przyczyna „nie odświeża się")
- **Status: O** (kody odpowiedzi z logu aplikacji + kontrola z komputera, 2026-09-12 05:02)
- **Jak zbadane:** zliczenie odpowiedzi OkHttp dla `feeds/videos.xml` w świeżym logu
  z tabletu oraz próby `curl` z komputera na ten sam adres.
- **Dowód:** w jednym przebiegu **149 zapytań RSS: 140 × `404 Not Found`, 2 × `200`, 7 × `500`**.
  Z komputera ten sam, pewny kanał (`UCBR8-60-B28hp2BmDPdntcQ`, kanał YouTube) raz zwraca
  `200`, a po serii zapytań zaczyna zwracać `404` — niezależnie od nagłówka `User-Agent`
  (sprawdzone: brak UA, curl, Chrome desktop, Chrome Android, Cobalt/TV, Feedfetcher).
- **Wniosek (UWAGA: podważony przez K22 — PipePipe odświeża 170 kanałów bez kary):**
  `404` tutaj **nie znaczy „kanał nie istnieje"**; roboczo czytamy to jako odpowiedź dławiącą
  (rate limit po stronie YouTube, liczona na adres IP/klienta). Dlatego wiersz miał dane
  tylko z kilku kanałów, mimo 178 subskrypcji. **Żadna ilość optymalizacji kodu tego nie
  naprawi — trzeba pobierać spokojniej.**
- **To wyjaśnia również, dlaczego stary silnik „gubił" kanały:** burst 50 kanałów × 2 zapytania
  dostawał 404 niemal w całości, a wynik (ogryzek) nadpisywał cache (K16).
- **Co zrobione (v4):** 2 kanały równolegle, **700 ms odstępu** między zapytaniami w rundzie,
  runda = 8 kanałów, 6 s przerwy między rundami, próg przeterminowania 60 min. Dane trzymane
  per kanał, więc kanał, który dostał 404, zachowuje poprzednie materiały i wraca w kolejnym obiegu.
- **Niepewne (do zmierzenia):** jakie tempo YouTube toleruje trwale (700 ms to pierwsza próba,
  nie zmierzony optimum); czy po dłuższej ciszy limit się resetuje i po jakim czasie;
  czy `If-Modified-Since` zmniejsza dławienie (304 może nie być liczone jak pełne żądanie).

### K18 — ZGŁOSZENIE USERA (v2): wiersz skakał, kółko blokowało belkę
- **Status: O** (objaw zgłoszony przez Usera na projektorze; przyczyna wprost z mojego kodu)
- Rotacja po **każdej** rundzie wołała `showRow()`, a to w leanbacku = usunięcie i wstawienie
  rzędu → ekran przeskakiwał do góry i wyglądało to jak ciągłe odświeżanie. Dodatkowo każda
  runda kręciła kółkiem w belce, co blokowało przewijanie belki.
- **Naprawa (v4):** rotacja w tle jest **cicha** — nie rusza wiersza i nie kręci kółkiem.
  Wiersz dostaje nową treść **raz**, na końcu obiegu, albo przy następnym wejściu na home.
  Kółko zostaje tylko dla odświeżenia wywołanego ręcznie przez Usera.

### K19 — Tryb debugowy wiersza (życzenie Usera, 2026-09-12)
- **Status: w robocie**
- **Co chce User:** linia debugowa przy wierszu „Twoje kanały" — ile kanałów przeskanowanych,
  co się dzieje, jaki stan, czy jest problem. Oraz pod każdym filmem **kiedy został
  opublikowany** (ile minut/godzin temu).
- **Realizacja (v5):** nagłówek wiersza niesie stan (np. „Twoje kanały · 42/178 kan. ·
  75 filmów · 404: 12"), a drugi wiersz kafelka pokazuje czas względny. Docelowo
  przełącznik w naszych ustawieniach (K15), na czas testów włączone na stałe.

### K20 — Puste subskrypcje: podpowiedź importu (do zrobienia później)
- **Status: H** (zapisane na życzenie Usera, nie zaczęte)
- Gdy ktoś startuje na świeżym koncie/instalacji i **nie ma żadnych subskrypcji**, wiersz
  „Twoje kanały" ma się pokazać z małym, szarym napisem w stylu „brak subskrypcji —
  zaimportuj listę kanałów (np. z NewPipe)". Napis **klikalny** — prowadzi do importu.
- **Niepewne:** czy kafelek-podpowiedź da się zrobić bez własnego presentera w leanbacku
  i gdzie dokładnie wpiąć akcję importu.

### K21 — Lekcja: nie wolno „wziąć wszystkiego naraz" wobec cudzego serwisu
- **Status: O** (wniosek po K17; uwaga Usera 2026-09-12)
- NewPipe celowo pobiera **3 naraz** i pauzuje 6–12 s co 50 kanałów. Nasza pierwsza wersja
  (i v2) szła po najmniejszej linii oporu: wszystko naraz. Skutek: 404 na prawie całej
  liście, a w skrajnym przypadku ryzyko blokady klienta/konta.
- **Reguła na przyszłość:** każde odpytywanie cudzego API projektujemy od tempa
  (równoległość, odstęp, backoff, retry w kolejnym obiegu), a nie od „ile zdążymy".

### K24 — POMIAR ROZSTRZYGAJĄCY (2026-09-12, 14:07): **RSS ŻYJE. Moja diagnoza była błędna, User miał rację**
- **Status: O — zmierzone na tablecie, na sprzęcie, po wgraniu buildu z 14:06**

**Pomiar** (tablet SM-T580, `192.168.2.135`, build z dwoma źródłami, tempo 5 kanałów /
1500 ms / 12 s przerwy):

```
14:07:46  Round done: got 75 items from 5 channels, updated 5, silent 0  (withData=9  items=135)
14:08:02  Round done: got 75 items from 5 channels, updated 5, silent 0  (withData=14 items=210)
14:08:18  Round done: got 67 items from 5 channels, updated 5, silent 0  (withData=19 items=277)
14:08:34  Round done: got 75 items from 5 channels, updated 5, silent 0  (withData=23 items=337)
14:08:49  Round done: got 75 items from 5 channels, updated 5, silent 0  (withData=28 items=412)
```

- **`silent 0` w każdej rundzie** — ani jeden kanał bez odpowiedzi.
- **`System.err` pusty** — zero wyjątków, czyli **ani jednego 404**.
- **15 pozycji na kanał** — to sygnatura RSS-a (`feeds/videos.xml` oddaje 15 wpisów).
  Zakładki kanału dałyby ~30. **Odwrót na browse nie uruchomił się ani razu.**
- Magazyn: **6 → 28 kanałów z materiałami, 90 → 412 filmów w 90 sekund**.
  Tempo obiegu: 5 kanałów / ~16 s → pełne 173 kanały w ~9 minut.

**Wniosek — co było naprawdę:**
404 było **karą nałożoną na nasz publiczny adres** za sztorm zapytań („co 15 sekund
na trzech urządzeniach w kółko", ~173 kanały × 3 urządzenia). Kara **minęła** po nocnej
przerwie w ruchu. Wystarczyło zwolnić tempo i przestać walić.

**Co z tego wynika dla moich wcześniejszych ustaleń:**
- Teza „endpoint `feeds/videos.xml` jest globalnie niesprawny / YouTube wygasza otwarty
  RSS" — **OBALONA pomiarem**. Opierała się na kontrolnym zapytaniu z adresu chmurowego,
  który Google odrzuca z zupełnie innego powodu. To był zły punkt kontrolny i wyciągnąłem
  z niego za mocny wniosek.
- Stanowisko Usera („RSS zawsze działał i będzie działać, chyba że nas zbanowali
  całkowicie (...) można stopniowo parę tych RSS-ów naraz sprawdzać") — **potwierdzone
  pomiarem co do joty**.
- **K17 wraca do łask w pierwotnej postaci**: 404 to odpowiedź dławiąca za tempo.
  Moje „obalenie" K17 (zapisane wyżej o 05:45) było przedwczesne — to K17 miał rację,
  a nie ja o 05:45.
- **K22 zostaje ważne w części o NewPipe**: to nadal fakt z kodu, że NewPipe domyślnie
  nie używa RSS (`useFeedExtractor = false`), i to nadal tłumaczy, czemu PipePipe nigdy
  nie oberwał — po prostu nie generuje ruchu na tym endpointcie. Ale **nie** wynika z tego,
  że RSS jest martwy.

**Lekcja (do MISTAKES.md):** dwa razy tego samego dnia ogłosiłem wniosek mocniejszy, niż
pozwalał dowód — najpierw „to dławienie" jako pewnik (K23), potem „endpoint jest martwy"
na podstawie jednego zapytania z chmury. Użytkownik, który zna zachowanie systemu z praktyki,
miał rację w obu przypadkach szybciej niż ja z pomiarami. **Zanim ogłoszę, że coś nie działa
globalnie, muszę mieć punkt kontrolny tej samej natury co badany (zwykłe łącze, nie chmura)
— albo napisać wprost, że nie wiem.**

**Ustawienia, które to osiągnęły (nie ruszać bez pomiaru):**
`BATCH_CHANNELS = 5`, `PARALLEL_CHANNELS = 2`, `CHANNEL_DELAY_MS = 1500`,
`NEXT_ROUND_DELAY_MS = 12000`, backoff narastający po 3 błędach z rzędu.

**WYNIK PEŁNEGO OBIEGU (14:16, ten sam build):**

```
14:16:39  Round done: got 45 items from 3 channels, updated 3, silent 0
14:16:39  Rotation complete (channels=173 withData=173 items=2560)
```

**173 ze 173 kanałów z materiałami. 2560 filmów. Zero błędów przez cały obieg.**
Czas: 14:07 → 14:16, czyli **9 minut** na komplet subskrypcji. Punkt wyjścia: 6 kanałów,
90 filmów. Funkcja działa tak, jak miała działać od początku.

**Nadal otwarte:**
- Czy to tempo jest trwale bezpieczne przez wiele godzin (obieg przeszedł raz, bez błędu).
- Czy przy dwóch urządzeniach naraz (tablet + projektor) kara wraca — **na razie nie
  odświeżać równocześnie**.

### K22 — ROZSTRZYGNIĘTE (2026-09-12, ~05:45): PipePipe nie dostaje blokady, bo **w ogóle nie używa RSS**
- **Status: O — zweryfikowane w kodzie źródłowym NewPipe i pomiarem z dwóch różnych łączy**

#### Rozstrzygnięcie (dwa niezależne dowody)

**Dowód 1 — 404 nie jest karą dla naszego adresu IP.**
Zapytanie o `feeds/videos.xml?channel_id=UCX6xikMVxBvmr3y-pYiMMmg` (MrBeast, kanał na pewno
istniejący) zwraca **404 także z łącza spoza naszego domu** (pobranie przez narzędzie
harnessu, inny kraj, inny adres). To samo 404 dostają:
- kanały, które **godzinę wcześniej odpowiadały poprawnie** na tablecie
  (`UCrXSVX9a1mj8l0CMLwKgMVw`, `UCCehmsLClWwxA_SDLtff6xA` — obydwa mają `lastOk != 0` w magazynie),
- zapytanie z UA `okhttp/3.12.13` **i** z UA przeglądarki Chrome — **bez różnicy**
  (eksperyment: `STPLUS/research/exp-K22-ua/test_ua.js`, `test_kanaly.js`).

Treść odpowiedzi to generyczna strona błędu Google (`Error 404 (Not Found)!!1`, robot.png),
a **nie** komunikat „nie ma takiego kanału". Endpoint jest globalnie niesprawny — problem jest
szeroko zgłaszany od końca 2025 (forum Google AI Developers, n8n, RSS-Bridge #2113).

**Dowód 2 — NewPipe/PipePipe domyślnie NIE używa RSS.**
Cytat z `STPLUS/research/newpipe/app/src/main/java/org/schabi/newpipe/local/feed/service/FeedLoadManager.kt:66`:
```kotlin
val useFeedExtractor = defaultSharedPreferences.getBoolean(
    context.getString(R.string.feed_use_dedicated_fetch_method_key),
    false)          // <-- DOMYŚLNIE WYŁĄCZONE
```
a niżej (`:186`) odwrót, gdy dedykowany feed nic nie dał:
```kotlin
if (originalInfo == null) { getChannelInfo(serviceId, url, true) ... }
```
Opis tej opcji u nich (`strings.xml:738`): *„Available in some services, it is usually much
faster but may return a limited amount of items and often incomplete information"* — czyli RSS
to u NewPipe **opcjonalny tryb szybki**, włączany ręcznie, sam przez autorów opisany jako niepełny.

**Wniosek:** PipePipe odświeża 170 kanałów bez kary, bo nigdy nie dotyka `feeds/videos.xml` —
ciągnie zakładki kanału przez wewnętrzne API (InnerTube/browse).

#### SPROSTOWANIE tego samego dnia (User, 05:55) — dowód 1 jest SŁABSZY, niż go ogłosiłem

- **Kontrolne zapytanie z „obcego łącza" szło z serwerowni w chmurze.** Google rutynowo
  odrzuca ruch z adresów centrów danych, więc tamto 404 mogło mieć **zupełnie inną przyczynę**
  niż nasze. To **nie jest** dobry punkt kontrolny i nie dowodzi, że endpoint jest martwy
  globalnie. Status dowodu 1: **H (hipoteza)**, nie `O`.
- **Fakt od Usera, którego wcześniej nie miałem:** feed odświeżał się „co 15 sekund
  **na trzech urządzeniach** w kółko". Trzy urządzenia za jednym publicznym adresem, każde
  robiące pełny obieg po ~173 kanały — to **potrojony sztorm z jednego IP**. Kara nałożona
  na nasz adres jest w tym świetle bardzo prawdopodobna i tłumaczy, czemu u nas 404 jest
  na 100% zapytań, a u innych RSS bywa sprawny.
- **Stanowisko Usera:** „RSS zawsze działał i będzie działać, chyba że nas zbanowali
  całkowicie (...) można stopniowo parę tych RSS-ów naraz sprawdzać". Przyjęte.
- **Co z tego wynika:** NIE ogłaszamy RSS-a za martwy i **nie wycinamy go**. RSS zostaje
  pierwszą, tanią drogą; zakładki kanału są **odwrotem**, żeby wiersz w ogóle się napełnił.
- **Dowód 2 (NewPipe domyślnie nie używa RSS) zostaje w mocy** — jest niezależny od tego
  sprostowania, bo to cytat z kodu, i nadal tłumaczy zachowanie PipePipe.

**Jak to rozstrzygnąć uczciwie (do zrobienia):**
1. Kontrola z **normalnego, mieszkalnego łącza spoza naszego domu** (nie z chmury) —
   np. telefon na transmisji komórkowej z wyłączonym WiFi. To jedyny tani test, który
   rozdzieli „ban na nasz adres" od „endpoint niesprawny globalnie".
2. **Jedno urządzenie na raz.** Dopóki trwa diagnoza, tablet i projektor nie mogą
   odświeżać równocześnie — inaczej znów mierzymy własny sztorm (błąd K23 po raz drugi).
3. **Licznik kodów odpowiedzi wbudowany w aplikację** (200 / 404 / 500 / inne) pokazywany
   na ekranie, żeby było widać, czy RSS wraca do życia, zamiast zgadywać.
4. Backoff: po serii 404 odstęp ma **rosnąć**, a nie zostawać stały.

#### Co z tego wynika dla K17 i K21
- **K17**: obserwacja (kody odpowiedzi) zostaje faktem, ale **wyjaśnienie „za szybko pytamy"
  jest OBALONE**. 404 leci również przy jednym zapytaniu na minutę i z obcego łącza.
- **K21** (lekcja o tempie) zostaje słuszna jako zasada projektowa wobec cudzego API,
  ale **nie była przyczyną tej awarii**. Nie przypisywać jej tej zasługi.
- Dobieranie `CHANNEL_DELAY_MS` / `BATCH_CHANNELS` „żeby nie drażnić YouTube" **nie naprawi
  feedu**. To było strzelanie do złego celu.

#### Czego nadal nie wiem
- Czy YouTube wyłączył `feeds/videos.xml` trwale, czy to długa awaria (obserwować).
- Czy są kanały/warunki, w których RSS jeszcze odpowiada 200 (jeden taki przypadek był
  o 05:17:58 — 15 pozycji z 1 kanału; nie wiem, co go odróżniało).
- Ile realnie kosztuje `browse` na armv7 przy 173 kanałach — **niezmierzone**, to jest
  następny pomiar.

#### Decyzja (User, 2026-09-12): **obie drogi, przełącznik, nie wybór**
Cytat: „obie opcje możemy mieć jako przełącznik (...) nie musimy z jednej zrezygnować
kosztem drugiej". Realizacja: `RssOptions.feedSource` = AUTO / RSS / BROWSE, AUTO jako
domyślne (RSS jako pierwsza, tańsza próba, browse jako odwrót) — dokładnie wzorzec NewPipe.

---

#### Zapis pierwotny (stan sprzed rozstrzygnięcia, zostawiony jako ślad rozumowania)
- **Status w chwili zapisu: H — otwarte, moja wcześniejsza diagnoza (K17) jest NIEPEŁNA**
- **Fakt od Usera (2026-09-12):** w **PipePipe** (fork NewPipe) ma ~170 kanałów, klika
  „odśwież" i **skanuje wszystkie**, nigdy go nie zablokowało. Nie wiadomo, jakie kody
  odpowiedzi dostaje ani czy w tych kanałach faktycznie były nowe filmy.
- **Dlaczego to ważne:** ja stwierdziłem, że `404` = dławienie po stronie YouTube i że
  „trzeba zwolnić". Skoro PipePipe robi to samo bez kary, **moja diagnoza nie tłumaczy
  wszystkiego**. Możliwe, że przyczyna `404` leży gdzie indziej i zwalnianie tempa jej
  nie naprawi (a przy okazji psuje działanie).
- **Hipotezy do sprawdzenia (żadna nie zweryfikowana):**
  1. **Inne nagłówki/klient.** SmartTube chodzi jako klient TV (`TVHTML5`, własny UA,
     zgody/ciasteczka `CONSENT`), NewPipe/PipePipe idzie jako zwykły klient HTTP.
     Może to nagłówki, a nie tempo, powodują `404`.
  2. **Inny adres/ścieżka.** Sprawdzić, pod co dokładnie PipePipe wysyła zapytanie
     (`feeds/videos.xml?channel_id=`, a może `/feeds/videos.xml?user=`, inny host,
     inny ekstraktor).
  3. **Warunkowe zapytania / cache.** Jeśli PipePipe wysyła `If-Modified-Since` i dostaje
     `304`, to nie obciąża limitu tak samo.
  4. **Stan sieci/urządzenia.** Nasze 404 pojawiły się i na tablecie, i z komputera — więc
     albo faktycznie adres IP dostał karę, albo obie ścieżki mają tę samą wadę
     (np. brak ciasteczka zgody).
  5. **Kolejność i tempo mimo wszystko.** PipePipe pobiera 3 naraz i pauzuje; my
     wcześniej szliśmy „wszystko naraz". To mogło zadziałać jak wyzwalacz, ale nie musi
     być całą przyczyną.
- **Jak to rozstrzygnąć (konkretny plan):**
  1. Na tym samym urządzeniu/sieci odpalić PipePipe z odświeżeniem i **podejrzeć jego ruch**
     (logcat PipePipe / proxy), zanotować kody odpowiedzi i nagłówki.
  2. Powtórzyć to samo zapytanie z komputera z **dokładnie takimi samymi nagłówkami**
     jak PipePipe i porównać z nagłówkami SmartTube.
  3. Dopiero wtedy zdecydować, czy problemem jest tempo, nagłówki, czy coś trzeciego.
- **Do czasu rozstrzygnięcia:** nie powtarzać tezy „to dławienie" jako pewnika.
  W dokumentacji K17 zostaje jako obserwacja (kody odpowiedzi są faktem), ale **wyjaśnienie
  przyczyny jest otwarte**.

### K23 — MOJA GŁUPOTA (zapis na wyraźne polecenie Usera, 2026-09-12)
- Zbudowałem pobieranie feedu tak, że szło **wszystko naraz** (50 kanałów × 2 zapytania),
  choć NewPipe — którego kod miałem pod ręką — od lat pokazuje, że robi się to 3 naraz,
  z pauzami i z zapisem per kanał.
- Potem, badając problem, **sam dołożyłem serię zapytań z komputera** (kilkanaście prób
  pod rząd na ten sam adres), zamiast zaplanować kilka rzadkich prób z odstępem.
  Pogorszyłem stan, który badałem, i **zanieczyściłem własny eksperyment** — po tym nie da
  się już uczciwie powiedzieć, czy 404 wzięło się z aplikacji, czy z moich testów.
- Wyciągnąłem z tego wniosek („to dławienie") i ogłosiłem go jako ustalenie, mimo że
  User od razu wskazał kontrprzykład (PipePipe, 170 kanałów, bez blokady), którego moja
  teza nie tłumaczy.
- **Reguła:** badanie cudzego serwisu planuje się jak eksperyment — z góry ustalona liczba
  prób, odstępy, zapis kodów odpowiedzi i **kontrola na drugim, znanym kliencie** (tu:
  PipePipe), zanim ogłoszę przyczynę. Nigdy nie generować ruchu „na próbę" w trakcie
  diagnozowania ruchu.

### K15 — Własny ekran ustawień „SmartTube+” (życzenie Usera, 2026-09-12)
- **Status: H** (do zaprojektowania i wykonania; jeszcze nie istnieje)
- **Życzenie Usera:** nasze funkcje mają być **osobną opcją w ustawieniach** — najlepiej
  osobna sekcja „SmartTube+” z naszymi ustawieniami, obok ustawień upstreama.
- Kandydaci na przełączniki: wiersz „Twoje kanały” (wł./wył.), rozmiar porcji odświeżania,
  częstotliwość odświeżania, filtr shortów, liczba kanałów w feedzie (bez twardego limitu
  50 z kodu), tryb diagnostyczny (logi pomiarowe).
- **Zaleta architektoniczna:** osobny ekran = nasze zmiany nie wchodzą w ekrany upstreama,
  więc aktualizacja upstreama jest tańsza (zasada „zmiany jak plugin”).
- **Niepewne:** gdzie dokładnie wpiąć wejście do ekranu (`SettingsPresenter` vs osobny kafel)
  i czy da się to zrobić bez modyfikacji plików upstreama poza jednym punktem zaczepienia.

---

### K25 — ZWERYFIKOWANE NA SPRZĘCIE (2026-09-12, sesja #3): belka nagłówka wiersza ma pełną szerokość ekranu

**Status: `O`** (pomiar, nie lektura kodu).

**Pomiar:** zrzut `uiautomator dump` z tabletu SM-T580 (1200×1920, gęstość 240),
ekran główny SmartTube+, wiersz „Twoje kanały”:

```
LinearLayout [0,206][1200,260]  lb_row_container_header_dock
  LinearLayout [112,206][933,260]
    TextView   [112,206][933,260]  row_header  "Twoje kanały  —  173 z 173 ..."
```

Czyli belka nagłówka (`lb_row_container_header_dock`, poziomy `NonOverlappingLinearLayout`)
idzie na **całą szerokość ekranu**, a sam nagłówek jest w niej `wrap_content`.
Potwierdza to źródło w repozytorium: `leanback-1.0.0/src/main/res/layout/lb_row_container.xml`
ma dok z `layout_width="match_parent"`, a `RowContainerView.addHeaderView()` wkłada
nagłówek na pozycję 0.

**Co z tego wynika:** po prawej stronie nagłówka jest wolne miejsce na **prawdziwe
widoki** (własne pole dotyku, `OnClickListener`, `focusable`). To obala domniemanie
z poprzedniej sesji, że zębatkę da się tam wsadzić tylko jako `compound drawable`.
Realizacja i trzy pułapki (kradzież fokusu przy starcie, `MATCH_PARENT` rozpychające
nagłówek, asymetryczny zapas wokół ikony) — `STPLUS/MODYFIKACJE.md`, rozdział
„PRAWA STRONA NAGŁÓWKA WIERSZA”.

**Czego nie wiem:** czy na urządzeniach z innym motywem leanbacka (`rowHeaderDockStyle`)
dok ma ten sam padding. Sprawdzone na dwóch: tablet SM-T580 i projektor (oba gęstość 240).

### K26 — ZWERYFIKOWANE: „wiersz pojawia się za późno” to była pusta lista, nie powolny render

**Status: `O`.**

**Przyczyna:** `StPlus.showRow()` miało na wejściu `if (videos.isEmpty()) return;`,
a magazyn `filesDir/stplus/feed.txt` (~994 kB) wczytywał się dopiero przy pierwszym
rysowaniu wiersza. Dopóki plik się nie wczytał, lista była pusta → wiersz nie powstawał
w ogóle (leanback też nie tworzy rzędu bez pozycji: `MultipleRowsFragment.update()`
kończy na `if (group.isEmpty()) return;`). Stąd „czasem go nie ma, a potem dochodzi
i przesuwa układ”.

**Naprawa:** (1) `StPlus.preload()` wołane z `MainApplication.onCreate()` — magazyn
wczytuje się równolegle z budowaniem ekranu; (2) wiersz rysuje się także z pustą listą,
z kartą stanu jako jedyną pozycją.

**Pomiar po naprawie (tablet, 2026-09-12 15:47):** dwanaście kolejnych zrzutów
`uiautomator` od momentu startu — w pierwszym nie ma jeszcze żadnego wiersza strony
głównej, a od drugiego widać `Twoje kanały | Wybrane dla Ciebie | Sztuczna inteligencja
i inne`. Czyli nasz wiersz jest obecny **w tej samej klatce co wiersze upstreama**,
nie później.

### K27 — ZWERYFIKOWANE: crash przy szybkim przewijaniu wiersza do końca

**Status: `O`** (pełny ślad stosu z projektora, poprawka potwierdzona testem obciążeniowym).

**Objaw (User):** „jadę w prawo po kanałach, jak jadę szybko, to dojeżdżam do końca
i nagle się crashuje".

**Ślad (projektor, 2026-09-12 16:25):**

```
java.lang.IllegalStateException: Cannot call this method while RecyclerView is
computing a layout or scrolling ... app:id/row_content
  at VideoGroupObjectAdapter.remove(VideoGroupObjectAdapter.java:184)
  at MultipleRowsFragment.update(MultipleRowsFragment.java:236)
  at StPlus.onRowScrollEnd(StPlus.java:770)
  at BrowsePresenter.onScrollEnd(BrowsePresenter.java:510)
  at MultipleRowsFragment$ItemViewSelectedListener.onItemSelected
  at GridLayoutManager.dispatchChildSelected ... scrollHorizontallyBy
```

**Przyczyna:** `onRowScrollEnd` zmieniał adapter (ACTION_REMOVE + ACTION_APPEND) **wprost
w callbacku wołanym ze środka przewijania**. Strażnik upstreama
(`MultipleRowsFragment.isComputingLayout`) obejmuje tylko ACTION_SYNC i ACTION_REPLACE.

**Naprawa:** obie operacje idą przez `sMain.post(...)`, czyli w następnej klatce, gdy
RecyclerView już nie liczy układu.

**Dowód naprawy:** tablet, 300 szybkich naciśnięć w prawo pod rząd; 6 doładowań wiersza
w logu (`Row page appended`), zero wpisów FATAL w buforze crash, aplikacja żyje.

### K28 — ZWERYFIKOWANE: źródło „zakładki kanału" kasowało daty publikacji CAŁEGO magazynu

**Status: `O`** (pomiar na pliku magazynu z projektora).

**Objaw (User):** po przełączeniu źródła na „tylko zakładki kanału" na początku wiersza
pojawiły się filmy sprzed dwóch tygodni, potem sprzed czterech miesięcy. Jego diagnoza:
„w ogóle nie masz daty tego filmu".

**Pomiar:** `files/stplus/feed.txt` z projektora, 2572 pozycje — **min = 0, max = 0**.
Każda pozycja bez daty publikacji. Ten sam plik z tabletu (źródło RSS) ma daty poprawne,
a lista posortowana prawidłowo (najnowsze: „19 min temu", „43 min temu", „2 godz. temu").

**Dwie przyczyny, obie po mojej stronie:**

1. **`StPlusStore.merge` bezwarunkowo zastępowało zawartość kanału.** Ścieżka „zakładki
   kanału" nie niesie prawdziwej daty (`BaseMediaItem.getPublishedDate()` = `-1`), więc
   przy nieudanym przeliczeniu tekstu wpisywane było `0` — i kasowało dobrą datę z RSS-a.
   Naprawa: pozycja przychodząca **bez** daty zachowuje datę, którą już miała w magazynie.
2. **Kolizja zawierania w `StPlus.fromRelative`.** Warunek na dni (`contains("dni")`) stał
   PRZED warunkiem na tygodnie, a polskie „tygodnie" zawiera w sobie „dni" (ty-go-**dni**-e),
   „tydzień" zawiera „dzie". Materiał sprzed dwóch tygodni dostawał datę sprzed dwóch dni
   i wskakiwał na górę wiersza. Naprawa: jednostki sprawdzane od najdłuższej do najkrótszej,
   dni na samym końcu; doszły formy skrócone („tyg", „mies") oraz „wczoraj"/„dzisiaj";
   tekst, którego nie da się przeliczyć, trafia do logu (`Unparsed relative date`), zamiast
   po cichu zapisywać zero.

**Czego nie wiem:** który dokładnie napis przychodził z `getProductionDate()` na projektorze.
Karta pokazywała „2 tygodnie temu", ale zapisana data to 0 — czyli albo pole było puste, albo
miało formę, której nie rozpoznawałem. Log z punktu 2 odpowie na to przy następnym obiegu
po zakładkach kanału. **Do sprawdzenia w następnej sesji.**

**Skutek uboczny do posprzątania:** magazyn na projektorze ma wyzerowane daty i sam się nie
naprawi — trzeba przepuścić pełny obieg po źródle dającym daty (RSS / automatycznie).

### K29 — Filtr shortów: jedyny pewny sygnał to CZAS TRWANIA, a daje go tylko ścieżka „zakładki kanału"

**Status: `O`** dla obserwacji, `C` dla skuteczności filtra (nie zmierzona na ekranie).

**Objaw (User):** „dlaczego dalej pojawiają mi się shorty w pierwszej linii, jak mam je wyłączone".

**Ustalenie:** magazyn z tabletu (źródło RSS) — pozycje mają **puste pole długości** i żadnego
znacznika shorta; kanał RSS YouTube'a (`feeds/videos.xml`) nie podaje ani jednego, ani drugiego.
Upstream rozpoznaje shorty po długości (≤ 60 s), po znacznikach w adresie miniatury i po tagu
w tytule (`YouTubeHelper.isShorts`) — z tego przy RSS mamy wyłącznie tag w tytule, którego
autorzy zwykle nie wpisują. **To ograniczenie danych, nie usterka przełącznika.**

**Zrobione:** `StPlus.isShort` bierze teraz pod uwagę `Video.getDurationMs() <= 60 s` (ten sam
próg co upstream), więc przy źródle „zakładki kanału" filtr działa naprawdę. W menu przy
przełączniku shortów i przy każdym źródle jest napisane wprost, gdzie filtr działa, a gdzie nie
— na wyraźne polecenie Usera: „trzeba to zaznaczyć w ustawieniach naszych, żeby było wiadomo,
że jest taki problem".

## D. Kolejność prac wynikająca z audytu (propozycja)

1. **Pomiar uczciwy**: debug vs release na tablecie (K1). Bez tego wszystkie liczby kłamią.
2. **Instrumentacja hooków**: licznik wejść w `onHomeLoadStarted`/`onHomeRowsLoaded`/`fetchFeed`
   + znaczniki czasu (K6, K9, K11). Dopiero to daje `O` zamiast `H`.
3. **Naprawa stanu**: guard czasowy zamiast boolowego (K4), czyszczenie `sForceRefresh` (K5),
   cache w pamięci + brak zbędnych `REPLACE` (K6).
4. **Model pobierania jak NewPipe**: ograniczona równoległość + pominięcie/leniwe
   `syncWithChannel` + warunkowy GET (K2, K3, K7, K12) — zamiast cięcia kanałów do 12.
5. **Dopiero potem** ustawienia odtwarzacza (silnik, bufor, kodek) z FAZY 1 planu pi.

## E. Rejestr pomiarów (uzupełniać — pusty = nie zmierzone)

| # | Data | Urządzenie | Build | Home do stabilnego (s) | Skipped frames | PSS (MB) | Klik→start (s) | Uwagi |
|---|------|-----------|-------|------------------------|----------------|----------|----------------|-------|
| 1 | 2026-09-12 04:27 | tablet SM-T580 | **debug** (HEAD, bez zmian pi) | wiersz z cache w 6 s; „Row refreshed" w 9 s | 5 zdarzeń / **395** klatek (max 175) | — | — | 6× „Refreshing RSS" w 4,5 s; cache zniszczony: 200 poz./36 kan. → **42 poz./3 kan.**; log 9,5 MB/70 s |
