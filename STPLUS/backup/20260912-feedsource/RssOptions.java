package com.liskovsoft.youtubeapi.rss;

/*
 * >>> STPLUS — SmartTube+ plugin code (plik NASZ, nie istnieje w upstreamie).
 *
 * Przelaczniki zachowania RssService ustawiane z zewnatrz (z modulu common).
 * Domyslne wartosci = zachowanie upstreama, wiec bez ustawienia niczego
 * nic sie nie zmienia.
 *
 * Dlaczego osobny plik: RssService to `internal object` w Kotlinie, wiec nie da
 * sie go dotknac z innego modulu. Ten maly obiekt jest publiczny i RssService
 * tylko go czyta.
 * <<< STPLUS
 */
public final class RssOptions {
    /**
     * Pomijaj syncWithChannel() (POST youtubei/v1/browse per kanal).
     * Ten zapytanie dokladalo badge/live/preview i wycinalo pozycje nieobecne
     * w gridzie kanalu (m.in. shorty), ale kosztuje drugie, ciezkie zapytanie
     * na KAZDY kanal. NewPipe robi tylko RSS.
     */
    public static volatile boolean skipChannelSync = false;

    /**
     * Ile kanalow pobierac rownolegle (0 = bez ograniczen, jak w upstreamie).
     * NewPipe: 3 (PARALLEL_EXTRACTIONS).
     */
    public static volatile int maxParallelChannels = 0;

    /**
     * Odstep (ms) po pobraniu kazdego kanalu. 0 = bez odstepu.
     *
     * POWOD (pomiar 2026-09-12): YouTube DLAWI serie zapytan do feeds/videos.xml
     * i odpowiada 404 — na 149 zapytan 140 wrocilo z 404, choc kanaly istnieja
     * (ten sam adres z komputera daje raz 200, a po serii 404). To nie blad ID.
     * NewPipe ma na to pauze 6-12 s co 50 kanalow; my pobieramy wolniej i maly
     * odstep miedzy kanalami okazuje sie potrzebny.
     */
    public static volatile long delayBetweenChannelsMs = 0;

    private RssOptions() {
    }
}
