# STPLUS — SmartTube+: lista modyfikacji

> Fork: `yuliskov/SmartTube` v32.40 (commit `25b7def51`, gałąź master).
> Cel: własny build "SmartTube+" + funkcje, łatwe do reaplikowania
> po aktualizacji upstreama.

## Zasady architektury "plugin"

1. **Własny kod** siedzi w osobnych plikach/pakietach, których upstream nie ma:
   - `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/stplus/` —
     cała logika funkcji (pakiet `stplus`).
2. **Hooki w kodzie upstream** są minimalne (kilka linijek) i zawsze
   oznaczone parami komentarzy:
   ```
   // >>> STPLUS ... (opis, link do tego pliku)
   ...
   // <<< STPLUS
   ```
   Szukanie: `grep -rn ">>> STPLUS" --include="*.java" --include="*.xml" .`
3. **Pliki binarne** (ikony) generuje skrypt `STPLUS/tools/gen_icon.py`
   — po aktualizacji upstreama wystarczy go ponownie uruchomić.
4. **Patch**: `STPLUS/patches/stplus-001-rebrand-feed.patch` = pełny diff
   wszystkich zmian (bez plików binarnych ikon — te z gen_icon.py).
5. **Nadpisywanie plików upstream przy aktualizacji** (git checkout z upstream)
   kasuje hooki — wtedy: `git apply STPLUS/patches/stplus-001-*.patch`
   (lub ręcznie wg tabeli poniżej, jeśli kontekst się rozjechał).

## Rebrand (nazwa + ikona)

| # | Zmiana | Plik |
|---|--------|------|
| 1 | `app_name`: SmartTube → **SmartTube+** | `smarttubetv/src/main/res/values/strings.xml` (linia ~4) |
| 2 | `browse_title`: SmartTube → **SmartTube+** | `smarttubetv/src/main/res/values/strings.xml` (linia ~5) |
| 2b | **PUŁAPKA**: `app_name` + `browse_title` w pliku FLAVORA ststable nadpisywały main — tu też zmienione na SmartTube+ | `smarttubetv/src/ststable/res/values/strings.xml` (cały plik, 2 stringi) |
| 3 | Ikona launchera: telewizor, gradient pionowy róż `#FF4D9E` (góra) → niebieski `#2E6BFF` (dół), białe "S+" | `smarttubetv/src/main/res/mipmap-nodpi/app_icon.png` |
| 4 | j.w. (smak ststable) | `smarttubetv/src/ststable/res/mipmap-nodpi/app_icon.png` |
| 5 | j.w. (ststable, Android 13+) | `smarttubetv/src/ststable/res/mipmap-nodpi-v30/app_icon.png` |
| 6 | j.w. (smak stbeta) | `smarttubetv/src/stbeta/res/mipmap-nodpi/app_icon.png` |
| 7 | j.w. (stbeta, Android 13+) | `smarttubetv/src/stbeta/res/mipmap-nodpi-v30/app_icon.png` |
| 8 | j.w. (smak stfdroid) | `smarttubetv/src/stfdroid/res/mipmap-nodpi/app_icon.png` |
| 9 | j.w. (stfdroid, Android 13+) | `smarttubetv/src/stfdroid/res/mipmap-nodpi-v30/app_icon.png` |

Regeneracja ikon: `python STPLUS/tools/gen_icon.py` (bez argumentu nadpisuje
wszystkie 7 plików; kolory w stałych na górze skryptu).

## Osobny identyfikator pakietu (Plus ≠ oryginał)

| # | Zmiana | Plik |
|---|--------|------|
| 14 | `applicationId` smaku ststable: `org.smarttube.stable` → **`org.smarttube.plus.stable`** — Plus instaluje się OBOK oryginalnego SmartTube, nie nadpisuje go | `smarttubetv/build.gradle` (blok `productFlavors.ststable`, ~linia 167) |
| 15 | dodanie `org.smarttube.plus.stable` do `KNOWN_PACKAGES` (pełny About + cross-backup) | `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/utils/Utils.java` (~linia 118) |

Konsekwencje:
- `leanbackassistant` (search provider) i `appupdatechecker2` używają `${applicationId}`
  w manifestach — automatycznie dostają nowe authority, nic więcej zmieniać nie trzeba.
- Plus startuje z czystą pamięcią (dane przypięte do starego pakietu zostają).
- Zmiana applicationId NIGDY później — nadpisanie istniejącej instalacji Plusa
  wymusiłoby reinstalację.

## Ikona (wersja 2 — oficjalna od Usera)

Ikona generowana przez `gen_icon.py` została zastąpiona oficjalną ikoną
SmartTube+ (zip `android_smarttube_icon.zip`, master 1134px, S+ na
gradientowym kafelku). Wypakowana w `STPLUS/tools/icon_new/`.

- Wstawianie: `python STPLUS/tools/apply_icon.py` — nadpisuje:
  - 7 plików `app_icon.png` (320x320, ikona startowa),
  - **5 plików logo w belce** (320x180): `app_logo.png`, `app_logo_semi_red.png`,
    `app_logo_semi_grey.png`, `app_logo_none.png`, `app_icon_alt.png` w
    `smarttubetv/src/main/res/mipmap-nodpi/` — kwadratowa ikona 180x180 wycentrowana
    na przezroczystym pasku (slot `appLogo` w `styles.xml`),
  - **9 plików logo w belce w FLAVORACH** (180x180, kwadrat): `app_logo.png`,
    `app_logo_semi_red.png`, `app_logo_semi_grey.png` × `ststable/stbeta/stfdroid` w
    `smarttubetv/src/<flavor>/res/mipmap-nodpi/`.

> **PUŁAPKA (znaleziona 2026-09-05 07:00)**: flavor `ststable` ma WŁASNĄ kopię
> `app_logo*.png` (180x180) i **NADPISUJE** pliki z `main/res`. Edycja tylko `main`
> zostawia starą ikonę w belce (APK brał plik z flavora). apply_icon.py od wersji 2b
> nadpisuje OBA miejsca. Po każdej aktualizacji upstreama sprawdzaj, że logo w
> `ststable/res/mipmap-nodpi/` też jest nowa.

- Po aktualizacji upstreama: najpierw `apply_icon.py`, NIE `gen_icon.py`.

## Funkcja 1: rząd "Twoje kanały" pod "Wybrane dla Ciebie" (strona główna)

Działanie: na Home, gdy użytkownik ma LOKALNĄ LISTĘ KANAŁÓW (np. zaimportowaną
z NewPipe do menu "Subskrypcje"), pod pierwszym rzędem pojawia się poziomo
przewijany rząd "Twoje kanały" z najnowszymi materiałami z tych kanałów
(max 30 elementów, z max 50 kanałów).

**KLUCZOWE: działa BEZ logowania do YouTube.** User nie loguje się (prywatność).
Dlatego NIE używamy `getSubscriptionsObserve()` (wymaga konta — zwraca pusto
bez auth). Zamiast tego:
1. `ChannelGroupService.getChannelGroups()` — lokalne grupy kanałów (prefs, bez konta),
2. wyciągamy `Item.getChannelId()` z każdego kanału,
3. `ContentService.getRssFeedObserve(channelIds)` — publiczny RSS z YouTube
   (nie wymaga auth; `checkSigned()` w środku tylko aktualizuje nagłówki, nie rzuca),
4. RSS zwraca połączoną + posortowaną od najnowszych listę → bierzemy top 30.

| # | Zmiana | Plik |
|---|--------|------|
| 10 | **NOWY PLIK** — cała logika funkcji (klasa `StPlus`, wersja RSS) | `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/stplus/StPlus.java` |
| 11 | import `StPlus` | `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/BrowsePresenter.java` (blok importów, ~linia 42) |
| 12 | hook: **PO pętli** dodawania domyślnych wierszy w `updateVideoRows()` wywołanie `StPlus.onHomeRowsLoaded(...)` gdy `isHomeSection()` — WAŻNE: po pętli, nie przed (patrz uwaga o wyścigu) | ten sam plik `BrowsePresenter.java` (koniec callbacku `groups.subscribe`, ~linia 778) |
| 12b | hook: **na starcie ładowania home** (zaraz po `firstGroup` ACTION_REPLACE) `StPlus.onHomeLoadStarted(...)` — wstawia wiersz z cache, zanim przyjdzie odpowiedź z sieci | `BrowsePresenter.java` (`updateVideoRows`, ~linia 726) |
| 12c | hook w `onScrollEnd()`: `if (group != null && StPlus.onRowScrollEnd(group.getId())) return;` | `BrowsePresenter.java` (~linia 488) |
| 12d | naprawa znikającej górnej belki przy wstawieniu wiersza na pozycję 0 | `smarttubetv/.../tv/ui/browse/video/MultipleRowsFragment.java` (`update()`) |
| 12e | haki w `onVideoItemClicked` / `onVideoItemLongClicked`: ignorowanie karty "Ładowanie…" | `BrowsePresenter.java` |
| 13 | stringi `stplus_subscriptions_row` = "Twoje kanały" i `stplus_loading_more` = "Ładowanie…" | `common/src/main/res/values/strings.xml` (po `suggestions`, ~linia 490) |

### Jak to działa technicznie (dla przyszłych napraw)

- Home ładuje rzędy: `BrowsePresenter.updateVideoRows()` → `getHomeObserve()` →
  lista `MediaGroup` → każdy staje się rzędem (`getView().updateSection(VideoGroup)`).
- Hook (po pętli domyślnych wierszy) pobiera lokalne grupy kanałów, wyciąga ID,
  asynchronicznie `getRssFeedObserve(ids)` i tworzy `VideoGroup` z `position=0` —
  `MultipleRowsFragment` wstawia go na pierwsze miejsce po nagłówku kanału
  (`mRowsAdapter.add(position+1, row)`; +1 bo jest `mChannelHeaderCallback`).

- **UWAGA — WYŚCIG (poprawione 2026-09-05):** hook MUSI być wywoływany PO pętli
  `for (MediaGroup ...)` dodającej domyślne wiersze, NIE przed nią. Jeśli hook jest
  przed pętlą, asynchroniczny RSS kończy się W TRAKCIE pętli i mój wiersz (pozycja 1)
  jest wypychany przez kolejne wiersze home (też na pozycjach 1,2,3...) — efekt:
  wiersz czasem na górze, czasem zniknął. Po przeniesieniu hooka po pętlę wiersz
  jest zawsze wstawiany po domyślnych → stabilnie na pozycji 0.
- Stałe ID rzędu (`0x53545031`) + `ACTION_APPEND` → odświeżenie home nie duplikuje rzędu.
- RSS: `RssService.getFeed()` łączy feedy wszystkich kanałów i sortuje po `publishedDate`
  (najnowsze na górze) — `internal` w module youtubeapi, ale dostępny przez
  `ContentService.getRssFeedObserve()` (publiczny).
- RxJava: obserwable emituje na main thread — UI bezpieczny.
- Hook jest w 100% wyjątkoodporny (try/catch) — nie może zepsuć ładowania home.

### Cache-first i odświeżanie (2026-09-05)

Wiersz **nie czeka na sieć**. Kolejność:

1. Hook czyta cache z `SharedPreferences("stplus")`, klucz `subs_row_cache`
   (lista `Video.toString()` sklejona `Helpers.mergeArray`) → wiersz jest
   na ekranie **natychmiast po starcie**, jeszcze zanim ruszy RSS.
2. Równolegle startuje RSS. Na czas pobierania ikonka w prawym górnym rogu
   **kręci się** (`StPlus.RefreshListener` → `BrowseFragment.showStPlusRefreshing`).
3. Dopiero **po** zakończeniu pobierania stary wiersz jest podmieniany
   (`ACTION_REPLACE`, to samo ID) i zapisywany do cache. Gdy lista filmów
   jest identyczna jak w cache — podmiany nie ma (brak mrugania).
4. Kliknięcie ikonki = ręczne odświeżenie (`StPlus.requestManualRefresh()`).

Odświeżanie automatyczne dzieje się przy każdym załadowaniu/odświeżeniu Home
(upstreamowy `refreshIfNeeded()` robi to m.in. po powrocie do apki, gdy od
ostatniej aktualizacji minęły 3 h).

### PUŁAPKA — znikająca górna belka (naprawione 2026-09-05)

Leanback pokazuje górną belkę (logo + szukajka) tylko gdy zaznaczony jest
**pierwszy** wiersz. Wstawienie naszego wiersza na pozycję 0 przesuwało
zaznaczenie z 0 na 1 → belka znikała. Naprawa w `MultipleRowsFragment.update()`:
jeśli wiersz wstawiono dokładnie na pozycji, na której stało zaznaczenie,
zaznaczenie jest przywracane (`setSelectedPosition(insertPos, false)`).
Gdy user jest niżej — nic nie jest przesuwane (nie wyrywamy go na górę).

### Doładowywanie pozycji w wierszu (2026-09-05)

RSS pobiera **do 200** najnowszych materiałów z kanałów, ale wiersz pokazuje
na start **40**. Gdy user dojedzie do końca wiersza, dosypywane jest kolejne
**20** — z już pobranego zapasu, więc bez ruchu w sieci i natychmiast.

Na końcu wiersza stoi sztuczna karta **„Ładowanie…"** (`videoId = stplus_loading`)
— sygnał, że jest więcej. Po dosypaniu partii karta jest zdejmowana
(`ACTION_REMOVE`) i doklejana na nowym końcu, dopóki zostało coś w zapasie.
Kliknięcie/przytrzymanie tej karty jest ignorowane (haki w `onVideoItemClicked`
i `onVideoItemLongClicked` → `StPlus.isLoadingItem()`), żeby nie próbowała
odtworzyć nieistniejącego filmu.

Hak: `BrowsePresenter.onScrollEnd()` — jeśli grupa ma nasze ID, woła
`StPlus.onRowScrollEnd(groupId)` i **przerywa** normalne `continueGroup()`
(nasz wiersz nie ma tokenu kontynuacji od YouTube'a, upstream i tak nic by nie
zrobił). `ACTION_APPEND` dokłada pozycje do istniejącego wiersza.

### Konfiguracja (stałe w `StPlus.java`)

- `SUBS_ROW_POSITION = 0` — pozycja rzędu (PIERWSZY wiersz, na samej górze).
- `PAGE_SIZE = 40` — ile pozycji widać od razu.
- `PAGE_CHUNK = 20` — ile dosypać po dojechaniu do końca.
- `MAX_ITEMS = 200` — ile maksymalnie trzymamy w pamięci i cache (zapas).
- `MAX_CHANNELS = 50` — ile kanałów odpytujemy przez RSS (szybkość na tablecie;
  RSS leci per kanał, więc to główny koszt czasu odświeżania).
- `SUBS_ROW_ID = 0x53545031` — unikalne ID rzędu.

### Wymaganie po stronie Usera

User musi mieć LOKALNĄ listę kanałów (menu "Subskrypcje" → import z NewPipe / PocketTube /
GrayJay, albo ręczne dodawanie). Bez lokalnych kanałów rząd się nie pojawia (to zamierzone).

## SILNIK FEEDU v2 (2026-09-12) — przebudowa po pomiarach

> Pełne uzasadnienie, dowody i pomiary: `STPLUS/AUDYT-CC.md` (punkty K13, K16, K17, K18).
> Wzorzec, z którego korzystamy: `STPLUS/research/analiza_newpipe_feed.md` (NewPipe).

**Dlaczego przebudowa (trzy potwierdzone pomiarem usterki v1):**

1. **Sztorm odświeżeń niszczył zawartość.** Home ładował się wielokrotnie, każde ładowanie
   startowało nowy fetch, a nowy fetch robił `dispose()` poprzedniego. Ostatni, ocalały
   zwracał ogryzek (42 pozycje z 3 kanałów) i **bezwarunkowo nadpisywał** cache
   (200 pozycji z 36 kanałów).
2. **Limit `MAX_CHANNELS = 50`** brał pierwsze 50 ID z listy alfabetycznej — przy 178
   subskrypcjach wiersz kończył się na literze „C".
3. **YouTube dławi serię zapytań RSS** i odpowiada `404` (149 zapytań → 140 × 404).
   To nie jest błąd ID ani naszego kodu — to tempo.

**Nowe pliki (nasze):**

- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/stplus/StPlusStore.java`
  — magazyn PER KANAŁ: materiały + `lastTry`/`lastOk`. Plik tekstowy
  `filesDir/stplus/feed.txt` (celowo czytelny, do diagnozy przez `run-as`).
  Kluczowa zasada: **scalanie, nigdy nadpisanie całości** — kanał, który nie odpowiedział,
  zachowuje poprzednie materiały. Sortowanie po dacie publikacji (dlatego data jest
  zapisywana obok pozycji: `Video.toString()` jej nie niesie).
- `MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/rss/RssOptions.java`
  — przełączniki dla `RssService` (ten jest `internal object`, więc nie da się go dotknąć
  z modułu `common`): `skipChannelSync`, `maxParallelChannels`, `delayBetweenChannelsMs`.
  Domyślne wartości = zachowanie upstreama.

**Zmiany w upstreamie (hooki `>>> STPLUS`):**

- `MediaServiceCore/.../rss/RssService.kt`:
  - `fetchFeeds()` — porcje przez `chunked(limit)` zamiast „wszystko naraz";
    **nie używać blokującego semafora** (korutyny `runBlocking` dzielą jeden wątek →
    zakleszczenie, popełnione i naprawione 2026-09-12),
  - odstęp między zapytaniami (`delayBetweenChannelsMs`) — lek na dławienie 404,
  - `fetchFeed()` — `syncWithChannel()` (drugie, ciężkie `POST youtubei/v1/browse`
    na każdy kanał) pomijane, gdy `RssOptions.skipChannelSync`.
    **Uwaga:** to zapytanie wycinało też shorty — filtr shortów trzeba zrobić inaczej.

**`StPlus.java` — jak działa teraz:**

- wiersz rysuje się **zawsze z magazynu** (natychmiast, bez sieci), najnowsze pierwsze;
- odświeżanie **rotacyjne**: runda bierze porcję najdawniej sprawdzanych kanałów,
  kolejna następną, aż wszystkie są świeże;
- rotacja w tle jest **cicha**: nie przerysowuje wiersza i nie kręci wskaźnikiem w belce
  (przerysowanie = usunięcie i wstawienie rzędu w leanbacku → ekran skacze do góry).
  Wiersz dostaje nową treść raz — na koniec obiegu albo przy następnym wejściu na home;
- guard **czasowy** (`REFRESH_DEAD_MS`), nie boolowski — zawieszone odświeżanie samo wygasa
  i nie blokuje feedu do końca życia procesu;
- **tryb debugowy** (`DEBUG_LINE`): nagłówek wiersza pokazuje `6/173 · 90f · ↻12 · !8`
  (kanały z danymi / wszystkie · filmy w magazynie · zostało w obiegu · bez odpowiedzi),
  a pod każdym filmem czas publikacji („2 godz. temu");
- przy pierwszym starcie po aktualizacji pokazywany jest stary cache v1 (tylko do odczytu),
  żeby wiersz nie był pusty.

**Stałe tempa (`StPlus.java`) — dobierane pod dławienie YouTube, nie pod „szybkość":**

| stała | wartość | po co |
|---|---|---|
| `BATCH_CHANNELS` | 8 | ile kanałów w jednej rundzie |
| `PARALLEL_CHANNELS` | 2 | ile naraz (NewPipe: 3) |
| `CHANNEL_DELAY_MS` | 700 | odstęp między zapytaniami |
| `NEXT_ROUND_DELAY_MS` | 6000 | przerwa między rundami |
| `STALE_MS` | 60 min | kiedy kanał z danymi jest przeterminowany |
| `EMPTY_RETRY_MS` | 5 min | szybszy powrót kanału, który nic nie zwrócił |
| `MAX_ITEMS` | 200 | ile pozycji w wierszu |

**Czego NIE robić przy kolejnych zmianach:**

- nie nadpisywać magazynu wynikiem jednej rundy,
- nie przerysowywać wiersza z pracy w tle,
- nie zwiększać tempa bez pomiaru kodów odpowiedzi (`404` = za szybko),
- nie wprowadzać limitu liczby kanałów jako „optymalizacji" — to obcięcie funkcji.

## ŹRÓDŁA FEEDU v3 (2026-09-12) — RSS **i** zakładki kanału, z przełącznikiem

> Pełne ustalenie i dowody: `STPLUS/AUDYT-CC.md`, punkt **K22** (wraz ze sprostowaniem).

**Dlaczego:** feed przestał się napełniać — `feeds/videos.xml` zwracał **404 na wszystkich
173 kanałach**, także na tych, które godzinę wcześniej odpowiadały poprawnie. Przy okazji
wyszło, że komentarz w naszym kodzie („NewPipe robi wyłącznie RSS") był **nieprawdą**:
NewPipe domyślnie ciągnie zakładki kanału, a RSS ma jako opcjonalny „fast mode"
(`FeedLoadManager.kt:66`, wartość domyślna `false`). Dlatego PipePipe odświeża 170 kanałów
bez kary — on w ogóle nie dotyka RSS-a.

**Decyzja Usera:** nie rezygnujemy z żadnej drogi — ma być **przełącznik**, a RSS zostaje
pierwszy („RSS zawsze działał i będzie działać, chyba że nas zbanowali całkowicie").

| # | Zmiana | Plik |
|---|--------|------|
| 30 | **NOWY PLIK (nasz)** — druga droga do materiałów kanału (`fromBrowse`), liczniki kodów odpowiedzi RSS, narastająca przerwa po serii błędów (`backoffMs`), meldunki dla UI | `MediaServiceCore/.../rss/StPlusFeedSource.kt` |
| 31 | `feedSource` (AUTO/RSS/BROWSE) + pola meldunkowe (`lastSourceUsed`, `lastError`, `lastCodesSummary`, `browseFallbackCount`); sprostowanie komentarza o NewPipe | `MediaServiceCore/.../rss/RssOptions.java` |
| 32 | **3 krótkie haki** w `fetchFeed()`: wybór źródła, odwrót przy pustym wyniku, odwrót przy wyjątku + zapis kodu HTTP | `MediaServiceCore/.../rss/RssService.kt` |
| 33 | karta statusu na początku wiersza, przeliczanie daty względnej (`publishedMs`/`fromRelative`), meldunek z rundy, wolniejsze tempo | `common/.../stplus/StPlus.java` |
| 34 | karta statusu nieklikalna (rozszerzenie istniejących haków o `isStatusItem`) | `common/.../app/presenters/BrowsePresenter.java` |

### Jak działa wybór źródła

`RssOptions.feedSource`:
- **`SOURCE_AUTO`** (domyślne) — najpierw RSS (tani: małe XML, prawdziwa data publikacji),
  a gdy zwróci błąd **albo pustą listę** → zakładki kanału (`youtubei/v1/browse`).
  To jest dokładnie wzorzec NewPipe (`FeedLoadManager.kt:175-190`).
- **`SOURCE_RSS`** — tylko RSS, bez odwrotu. Po to, żeby dało się **uczciwie zmierzyć**,
  czy endpoint wrócił do życia.
- **`SOURCE_BROWSE`** — tylko zakładki kanału.

### Dwie pułapki ścieżki „browse" (obie obsłużone, nie usuwać)

1. **Brak daty publikacji.** `BaseMediaItem.getPublishedDate()` to twarde `-1`
   (`BaseMediaItem.kt:123`); czas jest tylko tekstem względnym w `getProductionDate()`
   („2 days ago"). Bez przeliczenia wiersz sortowałby się losowo — stąd
   `StPlus.fromRelative()` (rdzenie PL i EN).
2. **Brak `channelId` w pozycjach.** Grid kanału go nie powtarza, a magazyn STPLUS
   grupuje materiały właśnie po `channelId` — bez uzupełnienia **cała runda wyglądałaby
   na pustą**. `StPlusFeedSource.fromBrowse()` dopisuje ID, bo wie, czyj to kanał.

### Karta statusu zamiast linii debugowej w nagłówku

Nagłówek wiersza to zwykły `String` w `VideoGroup` (brak spanów) i ma **dużą czcionkę** —
pełne zdanie się nie mieściło na tablecie, a skrót `6/173 · 90f · ↻12 · !8` User odrzucił
(„jakiś skrót jak dla hakerów", „nie widzę, co wyszło ze skanowania"). Dlatego:

- nagłówek wrócił do czystego **„Twoje kanały"**,
- stan jest na **karcie na początku wiersza**, gdzie druga linia kafelka ma **małą czcionkę**
  i mieści całe zdanie:
  `6 z 173 kanałów, 90 filmów` / `ostatnie skanowanie 14:07: 5 kanałów, 3 z materiałami,
  45 filmów, 2 bez odpowiedzi • źródło: zakładki kanału • RSS: błąd 404 • odpowiedzi RSS: 404x5`
- karta jest nieklikalna (hak `isStatusItem` obok istniejącego `isLoadingItem`),
- sygnatura wiersza uwzględnia treść meldunku — inaczej karta o stałym `videoId`
  nigdy by się nie odświeżyła.

### Tempo (zmienione po ustaleniu, że 404 to nie kwestia tempa)

| stała | było | jest | po co |
|---|---|---|---|
| `BATCH_CHANNELS` | 8 | **5** | mniejsza porcja = mniej ruchu z jednego adresu |
| `CHANNEL_DELAY_MS` | 700 | **1500** | spokojniej; User zgłosił, że feed chodził „co 15 s na trzech urządzeniach" |
| `NEXT_ROUND_DELAY_MS` | 6000 | **12000** | j.w. |

Dodatkowo `StPlusFeedSource.backoffMs()` — po 3 nieudanych RSS-ach przerwa **rośnie**
(do 10 s). Wcześniej tempo było stałe niezależnie od tego, ile razy z rzędu dostaliśmy 404.

**UWAGA przy diagnozie:** tablet i projektor odświeżające równocześnie to **podwojony ruch
z jednego publicznego adresu** — wtedy mierzy się własny sztorm, a nie zachowanie YouTube
(błąd K23). Na czas pomiaru zostawiać **jedno** urządzenie.

## PRAWA STRONA NAGŁÓWKA WIERSZA (2026-09-12, sesja #3) — opis + zębatka

> Zgłoszenia Usera z rozdziału 5.8 handoffu `HANDOFF-SmartTubePlus-20260912.md`
> (a) zębatka nieklikalna, (b) wiersz pojawia się za późno — plus nowe z tej sesji:
> opis skanowania ma zejść z tytułu na prawą stronę i być wyblakły.

### Co było źle

1. **Zębatka była tylko obrazkiem.** Poprzednia wersja doklejała ją jako
   `compound drawable` do `TextView` nagłówka i łapała dotyk przez
   `setOnTouchListener` po współrzędnej X. Nagłówek wiersza w leanbacku **nie
   dostaje zdarzeń dotyku**, więc kliknięcie nie działało ani palcem, ani (tym
   bardziej) pilotem — compound drawable nie może dostać fokusu.
2. **Opis stanu siedział w tytule wiersza** (`rowTitle()` doklejał
   „— 173 z 173 kanałów, 2560 filmów”), czyli tą samą dużą czcionką co tytuł,
   tuż obok niego. User: „ten opis (...) powinien być raczej po prawej stronie
   i bardziej wyblakły, mniej rzucający się w oczy (...) i mniej przeszkadzający”.
3. **Wiersz „Twoje kanały” potrafił nie pojawić się po starcie.** `showRow()`
   miało na wejściu `if (videos.isEmpty()) return;`, a magazyn (~1 MB) wczytywał
   się z pliku dopiero przy pierwszym rysowaniu wiersza — więc dopóki się nie
   wczytał, wiersza nie było wcale.

### Ustalenie ze sprzętu (zrzut `uiautomator`, tablet SM-T580, gęstość 240)

Belka nagłówka wiersza to `lb_row_container_header_dock` — **poziomy LinearLayout
na całą szerokość ekranu**, zmierzone `[0,206][1200,260]`. Sam nagłówek siedzi w niej
jako `wrap_content`: `[112,206][933,260]`. Czyli **po prawej jest wolne miejsce**
i da się tam posadzić prawdziwe widoki. To jest podstawa całej tej zmiany.

| # | Zmiana | Plik |
|---|--------|------|
| 40 | **Przepisany** — do belki nagłówka dokładamy drugie dziecko (`layout_weight = 1`, wyrównane do prawej): wyblakły `TextView` z opisem + `ImageView` z zębatką. Prawdziwy widok = własne pole dotyku, własny `OnClickListener`, `focusable` dla pilota | `smarttubetv/.../tv/presenter/StPlusRowPresenter.java` |
| 41 | `rowTitle()` zwraca ZAWSZE czyste „Twoje kanały”; doszły `summaryText()` i `SummaryListener` (opis dla warstwy UI, wzorzec jak `RefreshListener`) | `common/.../stplus/StPlus.java` |
| 42 | `showRow()` rysuje wiersz także przy PUSTEJ liście (karta stanu jako jedyna pozycja); `renderFromStore()` stawia wiersz od razu, nie czekając na wczytanie magazynu | `common/.../stplus/StPlus.java` |
| 43 | **NOWE** `StPlus.preload(context)` — wczytanie magazynu w tle już przy starcie procesu | `common/.../stplus/StPlus.java` |
| 44 | hak: `StPlus.preload(this)` w `onCreate()` | `smarttubetv/.../tv/ui/main/MainApplication.java` |
| 45 | hak rozszerzony: kliknięcie karty stanu otwiera `StPlusSettings.show()` (droga do menu dla pilota) | `common/.../app/presenters/BrowsePresenter.java` |

### Trzy pułapki, których nie wolno cofnąć

1. **`onRequestFocusInDescendants()` → `false`** na naszym pasku. Bez tego zębatka
   **zabiera fokus zaraz po starcie** aplikacji (sprawdzone: pierwszy build tej sesji
   miał ją podświetloną od razu po wejściu na ekran główny). `requestFocus()` z góry —
   tak okno wybiera pierwszy focusowalny widok — schodzi w dół właśnie tą metodą,
   a nasza belka jest pierwszym dzieckiem wiersza. `focusSearch` z pilota idzie przez
   `FocusFinder`/`addFocusables` i tego **nie dotyczy**, więc strzałką dalej się tam wchodzi.
2. **Wysokość paska `WRAP_CONTENT`, nigdy `MATCH_PARENT`.** Belka nagłówka sama jest
   `wrap_content`, więc dziecko z `MATCH_PARENT` dostałoby całą dostępną wysokość
   i rozepchnęło nagłówek.
3. **Zapas wokół ikony jest asymetryczny** (pion 4 dp, poziom 16 dp). Belka ma 36 dp
   wysokości; kwadratowe pole dotyku 44 dp byłoby od niej wyższe i podniosło cały wiersz.
   Pole dotyku rośnie więc w poziomie: zmierzone na tablecie `[1080,204][1184,260]`,
   czyli ok. 69 × 37 dp.

### Recykling widoków

Leanback używa tych samych `ViewHolder`ów do różnych wierszy, więc przy **każdym**
`onBindRowViewHolder` pasek jest jawnie włączany albo `GONE`. Bez tego zębatka
wędruje do „Wybrane dla Ciebie”. Rozpoznanie naszego wiersza: ID grupy pierwszej
pozycji (`StPlus.SUBS_ROW_ID`), tytuł tylko jako zapas.

### Sprawdzone na sprzęcie (tablet, 2026-09-12 15:40–15:50)

- klik palcem w zębatkę → otwiera się `AppDialogActivity` z menu SmartTube+,
- pilot: DPAD_UP z pierwszego kafelka → fokus na zębatce; ENTER → menu; kolejny
  DPAD_UP → górna belka (nie ma pułapki fokusu),
- po starcie zębatka **nie** ma fokusu (fokus na pierwszym kafelku),
- po przewinięciu w dół żaden inny nagłówek nie ma zębatki,
- „Twoje kanały” jest w **pierwszym zrzucie, w którym strona główna ma jakiekolwiek
  wiersze** — razem z „Wybrane dla Ciebie”, nie po czasie.

## USTAWIENIA I DANE (2026-09-12, sesja #3, część druga)

> Zgłoszenia Usera z tej sesji: shorty przechodzą mimo filtra, karta SmartTube+ znikała
> razem z trybem debugowania, w opisie nie widać ile pobrano i kiedy, prędkości podane bez
> podstawy („przy ilu kanałach?"), brak przełącznika języka, crash przy szybkim przewijaniu,
> losowa kolejność po przełączeniu źródła.

| # | Zmiana | Plik |
|---|--------|------|
| 50 | **NOWY PLIK (nasz)** — teksty PL/EN i przełącznik języka NASZYCH napisów (menu, karta stanu, nagłówek wiersza, czasy względne) | `common/.../stplus/StPlusText.java` |
| 51 | Osobny przełącznik **karty stanu** (`KEY_STATUS_CARD`, domyślnie włączony) — do tej pory karta znikała razem z trybem debugowania | `common/.../stplus/StPlusSettings.java` |
| 52 | Przełącznik **języka** (`KEY_LANG`: jak w systemie / polski / angielski) | `common/.../stplus/StPlusSettings.java` |
| 53 | Opisy w menu: przy shortach i przy każdym źródle napisane wprost, gdzie filtr działa; przy każdej prędkości dopisane **„(przy ok. 200 kanałach)"** | `common/.../stplus/StPlusSettings.java` |
| 54 | `onRowScrollEnd` — doładowanie wiersza przez `sMain.post` (naprawa crasha, K27) | `common/.../stplus/StPlus.java` |
| 55 | `fromRelative` — jednostki od najdłuższej do najkrótszej, formy skrócone, log nierozpoznanego tekstu (K28) | `common/.../stplus/StPlus.java` |
| 56 | `isShort` — dochodzi próg czasu trwania ≤ 60 s (K29) | `common/.../stplus/StPlus.java` |
| 57 | `summaryText` — opis przy prawej krawędzi nagłówka mówi, co się dzieje: postęp obiegu albo ostatni/następny obieg i ile przyniósł | `common/.../stplus/StPlus.java` |
| 58 | `merge` — pozycja bez daty zachowuje datę, którą już miała; licznik nowych pozycji (`lastNewItems`) | `common/.../stplus/StPlusStore.java` |

### Trzy rzeczy, których nie wolno cofnąć

1. **`merge` nie zastępuje znanej daty pustą.** To jest jedyna ochrona przed tym, co się
   stało 2026-09-12: przełączenie źródła na „zakładki kanału" wyzerowało daty **całego**
   magazynu (2572 pozycje, wszystkie `0`) i kolejność wiersza stała się losowa. Dowód i opis:
   `AUDYT-CC.md`, K28.
2. **W `fromRelative` dni są sprawdzane OSTATNIE.** Polskie „tygodnie" zawiera w sobie „dni",
   a „tydzień" zawiera „dzie" — przy odwrotnej kolejności materiał sprzed dwóch tygodni
   dostaje datę sprzed dwóch dni i wskakuje na górę.
3. **`onRowScrollEnd` nie rusza adaptera synchronicznie.** Jest wołany ze środka przewijania;
   każda zmiana listy w tym momencie to `IllegalStateException` i koniec aplikacji (K27).
   Strażnik upstreama obejmuje tylko SYNC i REPLACE.

### Język naszych tekstów

`StPlusText` zamiast `res/values-en/` — bo zasoby idą za językiem systemu, a User chciał
**przełącznik w naszym menu**, działający bez restartu aplikacji. Dodatkowo obie wersje
tekstu leżą obok siebie w jednym pliku, co ma znaczenie przy publikacji na GitHubie.
Tekst „About SmartTube+" zostaje **zawsze po angielsku** — jest pisany pod README.

## Funkcja 2: przyciski na górnej belce (tryb tabletowy)

SmartTube jest robiony pod pilot/kursor na TV. Na tablecie brakuje sposobu, żeby
**schować i wysunąć** lewy rail nawigacji palcem — stąd dwa przyciski dotykowe
dorysowane nad treścią (nie są focusowalne, więc nawigacja pilotem bez zmian):

| róg | ikona | działanie |
|-----|-------|-----------|
| lewy górny | `icon_collapse_menu` | zwija / rozwija rail nawigacji |
| prawy górny | `ic_refresh_white` | kręci się gdy feed "Twoje kanały" się odświeża; **kliknięcie = ręczne odświeżenie** |

| # | Zmiana | Plik |
|---|--------|------|
| 20 | **NOWY PLIK** — ikona przycisku menu (panel + chevron, 128×128) | `common/src/main/res/drawable-nodpi/icon_collapse_menu.png` |
| 21 | string `stplus_collapse_menu` = "Zwiń menu" (zapasowy, opis przycisku) | `common/src/main/res/values/strings.xml` |
| 26 | blok STPLUS: `setupStPlusToolbar()`, `toggleNavigationCollapse()`, `showStPlusRefreshing()`, `setupStPlusNavCollapse()` | `smarttubetv/.../tv/ui/browse/BrowseFragment.java` (jeden ciągły blok) |
| 27 | wywołanie `setupStPlusToolbar()` + `setupStPlusNavCollapse()` w `onActivityCreated` | ten sam `BrowseFragment.java` |
| 28 | klawisz menu → `StPlus.reShowNavIfHidden()` | `common/.../misc/MotherActivity.java` (`onKeyDown`) |
| 29 | `setReShowNavCallback()` / `reShowNavIfHidden()` / `setRefreshListener()` / `requestManualRefresh()` | `common/.../stplus/StPlus.java` |

### PUŁAPKA — czarny ekran po zwinięciu menu (naprawione 2026-09-05 18:xx)

Pierwsza wersja dodawała "Zwiń menu" jako **sekcję w railu** (`TYPE_COLLAPSE_NAV`)
i chowała rail przez `setVisibility(GONE)`. Efekt: **czarny ekran**. Przyczyna:
w leanbacku każda sekcja raila to `PageRow`; samo **najechanie** na nią (nie klik!)
każe `BrowseSupportFragment` podmienić fragment treści na fragment tej sekcji.
Nasza sekcja nie miała żadnej treści → pusty fragment → czarne tło, a rail
dodatkowo znikał.

Naprawa: **żadnej sekcji w railu**. Przycisk to zwykły `ImageView` dorysowany do
roota fragmentu (`FrameLayout`), a zwijanie idzie **natywnym przejściem leanbacka**
`startHeadersTransition(false/true)` — tym samym, którego używa strzałka w prawo.
Fragmenty nietknięte, więc nie ma czego zgubić.

> Zasada na przyszłość: **nie dodawaj do raila pozycji, która nie ma treści.**

## Reaplikacja po aktualizacji upstreama — procedura

```bat
cd D:\cc\workspace\android\SmartTube
git fetch upstream
git checkout upstream/master        :: (albo tag nowej wersji)
git apply STPLUS/patches/stplus-001-rebrand-feed.patch
python STPLUS/tools/apply_icon.py
MSYS2_ARG_CONV_EXCL="*" cmd.exe /c build-st.bat
```

Jeśli `git apply` zgłasza konflikty (upstream zmienił kontekst):
1. `grep -rn ">>> STPLUS"` w starym buildzie / patczy — lista miejsc.
2. Wklej haki ręcznie wg tabeli powyżej (każdy hak to ≤ 6 linijek).
3. `StPlus.java` wklej 1:1 (plik nowościowy, nie powinien kolidować).
4. Ikona `icon_collapse_menu.png` — wklej do `common/src/main/res/drawable-nodpi/`.
5. Zaktualizuj patch — **względem bazy upstreamu**, bo nasze zmiany są już
   commitami w forku:
   ```bat
   git diff 25b7def51 -- ":!*.png" ":!.github/*" ":!STPLUS/patches/*" > STPLUS/patches/stplus-001-rebrand-feed.patch
   ```
   (`25b7def51` = commit v32.40, od którego odbiliśmy fork. Po aktualizacji
   podstaw nowy commit bazowy.)

## Szybka kompilacja

| skrypt | co robi | czas |
|--------|---------|------|
| `build-st.bat` | wszystkie 3 smaki (ststable/stbeta/stfdroid) | ~5 min |
| `build-st-fast.bat` | **tylko `ststable` (ten na tablecie)** + daemon + build cache + `--offline` | ~1–3 min |

- `gradle.properties` ma blok `>>> STPLUS` z `org.gradle.caching`, `parallel`,
  `daemon`, `kotlin.incremental` — **to jest plik upstreamu**, po aktualizacji
  trzeba dopisać blok ponownie (jest w patchu).
- **PUŁAPKA**: task ze słowem `Ststable` włącza plugin `google-services`
  (warunek w `build.gradle`) → w `google-services.json` musi być klient
  `org.smarttube.plus.stable`. Jest dodany; po aktualizacji upstreama pilnuj tego.
- `--offline` pomija sprawdzanie zależności w sieci; skrypt sam ponawia bez
  `--offline`, jeśli build padnie.
- APK: `smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_<wersja>_armeabi-v7a.apk`


## Wydawanie wersji — `wyslij-st.sh` (2026-09-05)

Rutyna jak w projekcie MyTas (ten sam User, ten sam hosting): każdy build idzie
**bez pytania** na wszystkie urządzenia i na stały link.

```bash
sh wyslij-st.sh              # build (ststable) -> tablet -> projektor -> stały link
sh wyslij-st.sh --bez-buildu # tylko wgraj to, co już zbudowane
```

| etap | co robi | weryfikacja |
|------|---------|-------------|
| build | `gradlew :smarttubetv:assembleStstableDebug --build-cache --parallel`, potem `gradlew --stop` (demon nie zostaje w tle) | `BUILD SUCCESSFUL` |
| tablet | `adb install -r` (T580, armv7) | `dumpsys package org.smarttube.plus.stable` → versionName |
| projektor | `adb install -r` (huanglong, armv7) | to samo |
| hosting | `curl -F fileUpload` na prywatny hosting | pobranie + `aapt2 dump badging` |

- APK: zawsze wariant **armeabi-v7a** — i tablet, i projektor są armv7.
- Etapy są niezależne: jak jedno urządzenie nie wstaje (DHCP zmieniło IP),
  reszta leci dalej, a na końcu jest podsumowanie `NIE UDALO SIE`.
- **PUŁAPKA**: IP urządzeń zmienia się z DHCP (tablet: .138→.135, projektor:
  .143→.141). Jak instalacja nie wejdzie — `adb devices` i poprawić stałe
  `TABLET`/`PROJEKTOR` na górze skryptu.

## Publikacja na GitHubie — granice i instrukcja (dla `pi`)

> Repozytorium ma **nie** stać na koncie Usera — publikuje je `pi` ze swojego konta.
> Ten rozdział jest instrukcją dla wykonawcy.

### CZEGO NIE WOLNO WYPCHNĄĆ (twarda lista)

1. **Zrzutów ekranu z tabletu Usera.** Katalog `STPLUS/*.png` to ~50 zrzutów,
   na których widać jego subskrypcje, historię i godziny. Do repo publicznego
   wchodzi **wyłącznie** `STPLUS/docs/screenshot-hero.png` (skadrowana belka +
   pierwszy wiersz) — zgoda Usera dotyczy tylko tego jednego pliku.
   Reszta → `.gitignore`.
2. **Lokalnych ścieżek i adresów** (`D:/cc/...`, adresy IP z sieci domowej,
   nazwy hostów, `adb -s <ip>:5555`). Przed publikacją przelecieć dokumentację
   i skrypty; `build-st-fast.bat` ma ścieżki lokalne → w repo wersja z relatywną
   ścieżką albo zmienną środowiskową.
3. **Keystore'a.** Nigdy w repo — tylko jako zaszyfrowany sekret GitHub Actions
   (`base64 -w0 keystore.jks` → secret `SIGNING_KEYSTORE`).
4. **`google-services.json`** z kluczem API dla naszego pakietu.
5. Notatek roboczych (`.goal*.md`, logi buildów, `build_fast.log`).

### Klucze i dostęp do konta GitHub (zasada Usera, 2026-09-05)

- Do konta `pijusdev` ma dostęp **TYLKO `pi`**. Claude Code i inni agenci NIE
  pushują na to konto i NIE mają dostępu do kluczy.
- Token PAT leży **tylko** w pliku konta agenta Pi
  (`C:/bun/pi-agent/workspace/agent-pi-konta/github/klucz github1.txt`).
  Nigdy: w repo, w `.git/config` (remote z tokenem w URL), w dokumentacji
  współdzielonej, w pamięci innych agentów.
- Push robimy **jednorazowym URL-em z tokenem**
  (`git push https://pijusdev:<token>@github.com/...`), żeby token nie został
  zapisany w konfiguracji repozytorium (które inne agenty też otwierają).
- Token wygasł 2026-09-05 (stary `ghp_MkL...`); nowy wygenerowany przez
  przeglądarkę (logowanie Google → GitHub), **bez wygasania**, scope `repo` +
  `public_repo`. Uwaga: push plików `.github/workflows/*` wymaga scope
  `workflow` — dlatego workflow upstreama usunięto z forka.
- `google-services.json`: dodany klient to **kopia** klienta `org.smarttube.stable`
  z upstreama (ten sam projekt Firebase, ten sam publiczny klucz API) — nie ma
  tam nowego sekretu.

### Ryzyka (ustalone z Userem 2026-09-05)

- **Licencja: czysto.** SmartTube jest na **MIT** — wolno forkować, zmienić nazwę
  i ikonę, publikować. Obowiązek: zostawić `LICENSE` i atrybucję autora.
- **Uwaga Google / DMCA.** Repo publiczne z gotowymi APK w Releases to typowy cel
  zgłoszenia. Ryzyko dotyczy głównie **wydań binarnych**, nie samego kodu.
  Wniosek: kod publicznie (żeby ktoś to zobaczył i ew. wciągnął do upstreama),
  binarka najwyżej jako osobny link, świadomie.
- **Podpis APK.** Android nadpisze instalację tylko tym samym kluczem. Build
  z Actions podpisany innym kluczem = konieczność odinstalowania (czyli utrata
  lokalnej listy kanałów). Dlatego keystore idzie do sekretów.

### Zarys automatu (jeśli robimy Actions)

- Workflow `build.yml`: `ubuntu-latest`, JDK 17, `./gradlew :smarttubetv:assembleStstableDebug`.
  Czas ~5–10 min. Darmowe: publiczne repo bez limitu, prywatne 2000 min/mies.
- Workflow `upstream-sync.yml` (cron dziennie): sprawdza nowy tag u `yuliskov/SmartTube`,
  robi merge naszych commitów na nowy tag, buduje, publikuje. Konflikt = workflow
  pada i zostaje zgłoszenie do ręcznej poprawki haków (patrz tabele wyżej).
- **User nie chce auto-aktualizacji w aplikacji** — wydania mają być do pobrania
  ręcznie, nie wypychane do urządzenia.

### Intencja projektu

Celem nie jest utrzymywanie osobnego forka w nieskończoność, tylko pokazanie,
że obie funkcje (feed z lokalnych kanałów bez logowania + obsługa dotyku na
tablecie) da się zrobić małą łatką. Jeśli trafi to do upstreama — fork przestaje
być potrzebny i bardzo dobrze. Dlatego README jest napisany pod czytelnika
z zewnątrz, a haki są celowo krótkie i opisane.

## Historia wersji

- **005** (2026-09-05): automat wydawania `wyslij-st.sh` (build → tablet →
  projektor → stały link na prywatnym hostingu, wzór z MyTasa) + publikacja forka
  na GitHubie (konto `pijusdev`).
- **002** (2026-09-05): rząd "Twoje kanały" jako PIERWSZY wiersz (RSS, bez logowania,
  z lokalnej listy kanałów — grupa 1000 + custom) + przycisk "Zwiń menu" na dole raila.
  Baza: v32.40.
- **004** (2026-09-05): doładowywanie wiersza (40 na start, +20 po dojechaniu
  do końca, zapas 200), README-SmartTube_Plus.md dla ludzi z zewnątrz, rozdział
  o publikacji na GitHubie, demon Gradle ubijany po buildzie.
- **003** (2026-09-05): cache-first dla wiersza "Twoje kanały" (jest od razu po
  starcie) + wskaźnik/przycisk odświeżania w prawym górnym rogu + przycisk menu
  w lewym górnym rogu zamiast sekcji w railu (naprawa czarnego ekranu) + naprawa
  znikającej górnej belki + szybszy build. Baza: v32.40.
- **001** (2026-09-05): rebrand + rząd subskrypcji. Baza: v32.40.
