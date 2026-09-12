# Jak NewPipe ładuje feed subskrypcji (odczytane ze źródeł, 2026-09-12)

Źródło: `STPLUS/research/newpipe` (klon `TeamNewPipe/NewPipe`, `--depth 1`).
Kluczowy plik: `app/src/main/java/org/schabi/newpipe/local/feed/service/FeedLoadManager.kt`.
**Wszystko poniżej to cytaty z kodu, nie domysły.**

## 1. Równoległość: **3 kanały naraz**

```kotlin
private const val PARALLEL_EXTRACTIONS = 3          // FeedLoadManager.kt:352
...
.parallel(PARALLEL_EXTRACTIONS, PARALLEL_EXTRACTIONS * 2)
.runOn(Schedulers.io(), PARALLEL_EXTRACTIONS * 2)
```

Dla porównania: nasz `RssService.fetchFeeds()` odpala `launch {}` dla **każdego** kanału
naraz — przy 50 kanałach to 50 równoległych pobrań + parsowań. **To jest różnica ~17×.**

## 2. Odświeżane są TYLKO przeterminowane kanały (próg domyślny 5 minut)

```kotlin
val thresholdOutdatedSeconds = ... feed_update_threshold_key ...
outdatedThreshold = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(thresholdOutdatedSeconds)
... feedDatabaseManager.outdatedSubscriptions(outdatedThreshold)
```

`<string name="feed_update_threshold_default_value">300</string>` → **300 s = 5 min**.
Każda subskrypcja ma własny znacznik „ostatnio sprawdzona”; odświeżane są tylko te,
które się przeterminowały. To jest gotowy model **rotacji**, o którą prosił User.

## 3. Ochrona przed rate-limitem YouTube: pauza co 50 kanałów

```kotlin
private const val BATCH_SIZE = 50
private val DELAY_BETWEEN_BATCHES_MILLIS = (6000L..12000L)
// co BATCH_SIZE ekstrakcji: Thread.sleep(DELAY_BETWEEN_BATCHES_MILLIS.random())
```

Dodatkowo kolejność subskrypcji jest **losowana** (`it.shuffled()`) — „to attempt to resist
fingerprinting”.

## 4. Wyniki idą do bazy, lista renderuje się z bazy

```kotlin
.buffer(BUFFER_COUNT_BEFORE_INSERT)   // = 20
.doOnNext(DatabaseConsumer())         // zapis w runInTransaction
```

Zapis partiami po 20 kanałów, w transakcji Room. UI (`FeedViewModel`/`FeedFragment`)
czyta z bazy, **nie czeka na sieć**.

## 5. Błąd jednego kanału NIE psuje listy

Każdy kanał wraca jako `Notification<FeedUpdateInfo>`; w `DatabaseConsumer`:

```kotlin
notification.isOnError -> { feedResultsHolder.addError(error!!) ... }
```

Błąd jest **odkładany na bok**, dane pozostałych kanałów zapisują się normalnie,
a poprzednia zawartość kanału zostaje w bazie.

## 6. Czego NewPipe NIE robi

Nie wykonuje dodatkowego zapytania „strona kanału” per kanał, gdy używa ekstraktora feedu
(RSS). U nas `RssService.syncWithChannel()` robi `getChannelAsGrid()` →
`POST youtubei/v1/browse` **dla każdego kanału** — to jest ukryty, drugi koszt.

## Wniosek — wzorzec do przeniesienia do SmartTube+

| Cecha | NewPipe | SmartTube+ dziś |
|---|---|---|
| równoległość | 3 | bez ograniczeń (50) |
| co odświeżamy | tylko przeterminowane (>5 min) | zawsze wszystkie |
| ile zapytań na kanał | 1 (RSS) | 2 (RSS + browse) |
| magazyn | baza Room, render z bazy | jeden blob w SharedPreferences |
| błąd kanału | izolowany, stare dane zostają | **kasuje cały wiersz** (patrz K16) |
| rate limit | pauza 6–12 s co 50 | brak |

**Niepewne / do sprawdzenia u nas:** czy YouTube honoruje `If-Modified-Since` dla
`feeds/videos.xml` (NewPipe tego nie używa — warto zmierzyć samemu, bo to dodatkowy zysk);
jaka wielkość porcji i próg przeterminowania są optymalne na armv7.
