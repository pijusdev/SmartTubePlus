# HANDOFF — SmartTube+ (sesja CC #3, 2026-09-12, 15:20–17:00)

> **ZANIM COKOLWIEK TKNIESZ — PRZECZYTAJ ROZDZIAŁ 0 DO KOŃCA.**
> Ten plik jest spisem stanu, **nie** zasadami projektu. Zasady leżą w `README-SmartTube.md`
> i `STPLUS/MODYFIKACJE.md` i trzeba je przeczytać osobno.
> Poprzedni handoff: `HANDOFF-SmartTubePlus-20260912.md` (sesja #2) — dalej aktualny
> w części o silniku feedu i o karze 404.

---

## 0. ZASADY PROWADZENIA SESJI (obowiązują od pierwszej sekundy)

### 0.1. Czytanie dokumentacji — OBOWIĄZKOWE, ZANIM DOTKNIESZ KODU

W tej kolejności, w całości:

1. `README-SmartTube.md` — build, urządzenia, reguły forka.
2. `STPLUS/MODYFIKACJE.md` — **architektura „plugin"**: własny kod w osobnych plikach,
   w upstreamie tylko krótkie haki `>>> STPLUS` / `<<< STPLUS`. Tabele wszystkich zmian
   i pułapki, których nie wolno cofnąć.
3. `STPLUS/AUDYT-CC.md` — ustalenia K1–K29 ze statusami `H`/`C`/`O`.
4. `.goal_2.md` — cel i dziennik kapitański.
5. `HANDOFF-SmartTubePlus-20260912.md` — stan po sesji #2.
6. `D:/cc/MISTAKES.md` — moje błędy, żeby ich nie powtórzyć.

**Powód:** sesja #2 przeczytała sam handoff, weszła w kod i złamała architekturę projektu.
Handoff to *stan*, nie *zasady*.

### 0.2. Komunikacja — GŁOSEM, ZERO ŚCIAN TEKSTU

- **Każdy meldunek przez `say.exe "…"`, po polsku, ZAWSZE z polskimi ogonkami.**
- **W konsoli najwyżej jedno zdanie.** User zgłaszał to w tej sesji ostro, po raz kolejny:
  „Dlaczego kurwa napierdalasz mi znowu ściany tekstu". Podsumowania, tabelki, listy —
  to wszystko idzie w głos, nie na ekran.
- **ZANIM WGRASZ COKOLWIEK NA PROJEKTOR — POWIEDZ „wgrywam" NA GŁOS.** Za każdym razem,
  przed `adb install`, także przy serii kolejnych buildów. User siedzi przy projektorze;
  instalacja podmienia aplikację pod jego ręką i bez zapowiedzi wygląda to jak awaria —
  zaczyna wtedy zgłaszać nieistniejące błędy (zdarzyło się dziś: „Zaktualizowałeś znowu
  smarttuba, że nie ma pierwszej karty?"). Zasada zapisana też w pamięci CC.
- **Ścieżki, adresy i liczby czytamy dosłownie** — „192.168.2.141", „32.40".
- Mów przy: rozpoczęciu zadania, starcie długiej operacji, zakończeniu, gdy User ma coś kliknąć.
- Nie kończ tury podsumowaniem i nie czekaj bezczynnie — jedziesz dalej listą.

### 0.3. Zakres roboty

- **Rób to, o co poprosił — i nic obok.** Dziś przegenerowałem plik łatki, o który nikt nie
  prosił („Czemu to kurwa zmieniłeś?"). Pliki generowane (łatki, artefakty wydania)
  odświeżasz **tylko na wyraźne polecenie**. Opis procedury w dokumentacji mówi JAK, nie KIEDY.
- **NIE PODSUWAJ FAŁSZYWYCH WYBORÓW „A czy B".** Da się jedno i drugie → projektujesz
  przełącznik i robisz oba.
- Pytać wolno, ale **pytanie ma zawierać gotową propozycję**.
- **Naprawiaj PRZYCZYNĘ, nie objaw.**

### 0.4. Dokumentacja na bieżąco — OBOWIĄZEK

Ustalenie → `STPLUS/AUDYT-CC.md`. Zmiana w kodzie → `STPLUS/MODYFIKACJE.md`.
Decyzja/zwrot → `.goal_2.md`. Własny błąd → `D:/cc/MISTAKES.md` **od razu**.
Myśl Usera rzucona mimochodem → `D:/cc/workspace/MIND-TREE-MAP/todo/` **dosłownie**.

### 0.5. Weryfikacja

- **Sprawdzaj na sprzęcie, zanim powiesz „działa".** Status `O` daje wyłącznie pomiar.
- **Testuj w tempie człowieka.** Crash z tej sesji (K27) nie wyszedł w moich testach, bo
  klikałem po jednym razie z pauzą. User przewijał szybko i aplikacja padała. Serie
  naciśnięć, nie pojedyncze kliknięcia.
- **Nie ogłaszaj wniosku mocniejszego, niż pozwala dowód.**
- **Koniec celu ogłasza WYŁĄCZNIE User**, po własnym teście.

### 0.6. To jest sprzęt słaby

Tablet SM-T580 i projektor to **armv7 z 2015**. Każda zmiana w pętli odświeżania = pytanie
„ile to kosztuje CPU i ile śmieci produkuje".

### 0.7. Nie przejmuj ekranu, na którym User testuje

Do pomiarów **tablet** (`192.168.2.135:5555`). Projektor (`192.168.2.141:5555`) to jego
maszyna — czytać z niego wolno (`run-as`, `logcat`, `screencap`), ale nie robić `force-stop`
ani nie klikać po menu.

---

## 1. CO ZOSTAŁO ZROBIONE W TEJ SESJI

### 1.1. Zębatka i prawa strona nagłówka wiersza (zgłoszenie 5.8a z sesji #2)

Poprzednia wersja doklejała zębatkę jako `compound drawable` do `TextView` nagłówka i łapała
dotyk po współrzędnej X. **To nie mogło działać** — nagłówek wiersza w leanbacku nie dostaje
zdarzeń dotyku, a compound drawable nie przyjmie fokusu z pilota.

**Ustalenie ze sprzętu (K25):** belka nagłówka (`lb_row_container_header_dock`) to **poziomy
LinearLayout na całą szerokość ekranu** (zmierzone `[0,206][1200,260]`), a sam nagłówek jest
w niej `wrap_content`. Po prawej zostaje wolne miejsce na prawdziwe widoki.

Teraz w belce siedzi drugie dziecko (`layout_weight = 1`, wyrównane do prawej):
**wyblakły opis stanu + prawdziwy `ImageView` z zębatką**.

**Sprawdzone na tablecie:** klik palcem otwiera menu; z pilota DPAD_UP z pierwszego kafelka
wchodzi na zębatkę, ENTER otwiera menu, kolejny DPAD_UP wychodzi na górną belkę (bez pułapki
fokusu); po starcie zębatka **nie** zabiera zaznaczenia; przy przewijaniu nie wędruje
do innych wierszy.

**Trzy rzeczy, których nie wolno cofnąć** (szczegóły w `MODYFIKACJE.md`):
`onRequestFocusInDescendants() → false` na pasku (inaczej zębatka zabiera fokus po starcie),
wysokość paska `WRAP_CONTENT` (nie `MATCH_PARENT`), asymetryczny zapas wokół ikony
(pion 4 dp, poziom 16 dp — belka ma tylko 36 dp wysokości).

### 1.2. Wiersz „Twoje kanały" jest ZAWSZE (zgłoszenie 5.8b)

Było: `showRow` miało na wejściu `if (videos.isEmpty()) return;`, a magazyn (~1 MB) wczytywał
się dopiero przy pierwszym rysowaniu wiersza — więc wiersza po prostu nie było.

Jest: `StPlus.preload()` wołane z `MainApplication.onCreate()` (magazyn wczytuje się równolegle
z budową ekranu) + wiersz rysuje się także z pustą listą, z kartą stanu jako jedyną pozycją.

**Zmierzone:** dwanaście zrzutów `uiautomator` od startu — nasz wiersz jest w tej samej klatce
co wiersze upstreama, nie później.

### 1.3. Menu pod zębatką — nowe przełączniki i opisy

- **Karta SmartTube+ ma własny przełącznik**, osobny od trybu debugowania (User lubi tę ikonę,
  a znikała razem z debugiem). Przy pustym wierszu karta pokazuje się **zawsze** — bez niej
  leanback nie zrobiłby wiersza.
- **Tryb debugowania** = tylko szczegóły techniczne (opis przy prawej krawędzi + źródło,
  błąd i kody odpowiedzi na karcie). **Czas publikacji pod filmami jest ZAWSZE** — wyraźne
  życzenie Usera.
- **Język naszych tekstów**: jak w systemie / polski / angielski (`StPlusText`, nowy plik).
  Nie przez `values-en/`, bo zasoby idą za językiem systemu, a miał być przełącznik.
  „About SmartTube+" zostaje zawsze po angielsku (jest pisany pod README).
- **Przy każdej prędkości i każdym źródle dopisane „(przy ok. 200 kanałach)"** — sam czas
  obiegu bez podanej podstawy nic nie znaczy.
- **Przy shortach napisane wprost, gdzie filtr nie działa** (patrz 1.5).

### 1.4. Crash przy szybkim przewijaniu (K27) — NAPRAWIONY

`onRowScrollEnd` zmieniał adapter **wprost w callbacku wołanym ze środka przewijania**
→ `IllegalStateException: Cannot call this method while RecyclerView is computing a layout
or scrolling` → koniec aplikacji. Strażnik upstreama (`MultipleRowsFragment.isComputingLayout`)
obejmuje tylko ACTION_SYNC i ACTION_REPLACE; nasze REMOVE i APPEND szły bez osłony.

Naprawa: obie operacje przez `sMain.post`. **Dowód:** 300 szybkich naciśnięć w prawo,
6 doładowań wiersza w logu, zero wpisów FATAL.

### 1.5. Shorty (K29)

Kanał RSS YouTube'a **nie podaje ani znacznika shorta, ani czasu trwania** (sprawdzone
w magazynie: pole długości puste przy każdej pozycji z RSS). Zostaje tag `#shorts` w tytule,
którego autorzy zwykle nie wpisują — dlatego filtr „prawie nie działał" i User słusznie to zgłosił.

Zrobione: `isShort` bierze teraz pod uwagę **czas trwania ≤ 60 s** (ten sam próg co upstream),
czyli filtr działa naprawdę przy źródle „zakładki kanału". Ograniczenie jest opisane w menu
przy przełączniku i przy każdym źródle — na wyraźne polecenie Usera.

**Niezweryfikowane:** skuteczność filtra na ekranie (ile shortów faktycznie znika).

### 1.6. Daty publikacji i kolejność (K28) — NAJPOWAŻNIEJSZA USTERKA TEJ SESJI

**Objaw:** po przełączeniu źródła na „tylko zakładki kanału" na początku wiersza pojawiły się
filmy sprzed dwóch tygodni, potem sprzed czterech miesięcy.

**Pomiar:** magazyn z projektora — **2572 pozycje, każda z datą `0`**.

**Trzy przyczyny, wszystkie po mojej stronie:**

1. `BaseMediaItem.getPublishedDate()` to twarde `-1`, a **`getProductionDate()` przy gridzie
   kanału zwraca `null`** — czyli ścieżka „zakładki kanału" nie daje żadnej daty. Dowód:
   w logu nie pojawił się ANI JEDEN wpis „Unparsed relative date", bo funkcja przeliczająca
   kończy się na pierwszym warunku `text == null`.
2. `StPlusStore.merge` **bezwarunkowo zastępowało** zawartość kanału, więc wynik bez daty
   kasował dobrą datę z RSS-a.
3. W `fromRelative` warunek na dni stał PRZED tygodniami, a polskie „tygodnie" zawiera w sobie
   „dni" (ty-go-**dni**-e) — materiał sprzed dwóch tygodni dostawał datę sprzed dwóch dni.

**Naprawy:**
- czas publikacji wyciągany z **ostatniego członu podtytułu kafelka**
  („Autor • 1,7 tys. wyświetleń • 2 tygodnie temu") — jedyne miejsce, gdzie ta ścieżka go podaje;
- `merge`: pozycja bez daty **zachowuje datę, którą już miała**;
- `fromRelative`: jednostki od najdłuższej do najkrótszej (dni na końcu), formy skrócone,
  „wczoraj"/„dzisiaj", a nierozpoznany tekst trafia do logu zamiast po cichu dawać zero.

**Dowód (tablet, źródło „zakładki kanału", pełny obieg):** 2560 pozycji, **0 bez daty**,
na górze filmy sprzed 37 min, 51 min, 1 godz. Przed naprawą: 100% bez daty.

### 1.7. Opis stanu przy prawej krawędzi nagłówka

User: „nie ma nawet informacji ile pobrano filmów w ostatnim przejściu (...) kiedy to się
odświeża, nie wiem tego".

Teraz:
- w trakcie obiegu: **„skanuję… 140 z 173 kanałów, +1 nowych"** (zweryfikowane na ekranie),
- po obiegu: **„2560 filmów · skan 16:19, +45 · następny 17:19"** albo „· tylko ręcznie".

---

## 2. STAN NA KONIEC SESJI

- Build wgrany na **tablet i projektor** (ststable debug, armeabi-v7a, 32.40).
- Tablet: język polski, shorty wyłączone, źródło automatyczne — czysty stan wyjściowy.
- **Projektor: źródło ustawione na „tylko zakładki kanału" (`stplus_feed_source = 2`)**,
  a jego magazyn ma jeszcze wyzerowane daty z poprzedniego błędu. Powinien naprawić się sam
  w ciągu najbliższego obiegu (każdy odświeżony kanał dostaje teraz prawdziwe daty) —
  **to jest pierwsza rzecz do potwierdzenia na starcie następnej sesji**
  (`run-as org.smarttube.plus.stable cat files/stplus/feed.txt`, policzyć pozycje z datą 0).

### Niezweryfikowane / otwarte

1. **Czy magazyn projektora sam się naprawił.** Patrz wyżej.
2. **Skuteczność filtra shortów na ekranie** przy źródle „zakładki kanału".
3. **Brak przełącznika „skanuj automatycznie przy starcie"** (zgłoszenie 5.8c z sesji #2,
   dalej niezrobione). Dziś obieg przy starcie leci zawsze, bo `sCycleStarted` jest wtedy zerowe.
4. **Kolejność przy zakładkach kanału jest z natury przybliżona** — YouTube podaje tam tylko
   tekst względny („2 tygodnie temu"), więc w obrębie tygodnia porządek może być losowy.
   Do rozważenia: przy źródle AUTO dociągać daty z RSS-a nawet wtedy, gdy materiały przyszły
   z zakładek.
5. **Różnica debug vs release** — nadal nie zmierzona (build release blokowany przez firewall).
6. **CPU po zmianach** — nie zmierzone w tej sesji (`top -H`). Przed: 140%.

---

## 3. GDZIE CO JEST

| co | gdzie |
|---|---|
| nasza logika wiersza i rotacji | `common/.../stplus/StPlus.java` |
| magazyn per kanał | `common/.../stplus/StPlusStore.java` |
| nasze menu i ustawienia | `common/.../stplus/StPlusSettings.java` |
| teksty PL/EN | `common/.../stplus/StPlusText.java` |
| pasek nagłówka (opis + zębatka) | `smarttubetv/.../tv/presenter/StPlusRowPresenter.java` |
| wygląd karty stanu | `smarttubetv/.../tv/presenter/StPlusStatusCard.java` |
| druga droga pobierania | `MediaServiceCore/.../rss/StPlusFeedSource.kt` |
| przełączniki dla RssService | `MediaServiceCore/.../rss/RssOptions.java` |
| kopie przed edycją | `STPLUS/backup/20260912-*/` |
| zrzuty ekranu i magazyny z testów | `STPLUS/shots/` |

## 4. KOMENDY

```
build (offline, ~1 min):
  JAVA_HOME=C:/jdk/jdk-17.0.20.1+1 ./gradlew.bat :smarttubetv:assembleStstableDebug --build-cache --parallel --offline

APK (zawsze armeabi-v7a):
  smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_32.40_armeabi-v7a.apk

wgranie (PRZED projektorem powiedz "wgrywam" na glos!):
  C:/adb/platform-tools/adb.exe -s 192.168.2.135:5555 install -r --no-streaming <APK>   # tablet
  C:/adb/platform-tools/adb.exe -s 192.168.2.141:5555 install -r --no-streaming <APK>   # projektor

logi naszego kodu / crash:
  adb -s <urz> logcat -d -s StPlus:V
  adb -s <urz> logcat -d -b crash

magazyn i ustawienia z urzadzenia:
  adb -s <urz> shell "run-as org.smarttube.plus.stable cat files/stplus/feed.txt"
  adb -s <urz> shell "run-as org.smarttube.plus.stable cat shared_prefs/stplus.xml"

drzewo widokow (do ustalen o ukladzie):
  MSYS2_ARG_CONV_EXCL="*" adb -s <urz> shell uiautomator dump /sdcard/ui.xml
  MSYS2_ARG_CONV_EXCL="*" adb -s <urz> exec-out cat /sdcard/ui.xml > plik.xml
```

**PUŁAPKI ŚRODOWISKA:**
- **`MSYS2_ARG_CONV_EXCL="*"` przy każdym `adb shell` ze ścieżką** — inaczej bash zamienia
  `/sdcard/ui.xml` na `/Files/Git/sdcard/ui.xml` i zrzut ląduje w złym miejscu.
- `ls` z `--show-control-chars` wywala się na tym systemie — używaj `find` albo `stat`.
- **Bash heredoc `<<'PY'` z długim kodem Pythona potrafi się wywalić** („unexpected EOF") —
  pisz skrypt do pliku w scratchpadzie i odpalaj `python <plik>`.
- Do pomiarów **tablet**, nie projektor.

## 5. CO DALEJ (kolejność)

0. Potwierdzić punkty z rozdziału 2 „Niezweryfikowane" — **przed pisaniem nowego kodu**.
1. Przełącznik „skanuj automatycznie przy starcie" (5.8c z sesji #2).
2. Zmierzyć CPU (`top -H`) i zapisać liczby do `AUDYT-CC.md` (przed: 140%).
3. Rozmiar magazynu — 994 kB na 173 kanały; do rozważenia `MAX_PER_CHANNEL` 15 → 10.
4. Potwierdzenie przy akcjach kasujących w sekcji debug.
5. Podpowiedź importu przy pustych subskrypcjach — **zrobione częściowo**: karta stanu
   pokazuje „brak subskrypcji — zaimportuj listę kanałów (np. z NewPipe)" i jest klikalna.
6. Build release i uczciwe porównanie z oryginałem (K1).
7. Dopiero potem FAZA 1 z `PLAN_WYDAJNOSC.md` (odtwarzacz).
8. Na końcu, **po akceptacji Usera**: README po angielsku + release na GitHubie.
