package com.liskovsoft.smartyoutubetv2.common.stplus;

/*
 * >>> STPLUS — SmartTube+ plugin code (plik NASZ, nie istnieje w upstreamie).
 *
 * Magazyn materialow wiersza "Twoje kanaly" — PER KANAL.
 *
 * Dlaczego tak (powod z pomiaru 2026-09-12, patrz STPLUS/AUDYT-CC.md K16):
 * poprzednia wersja trzymala JEDEN blob 200 pozycji w SharedPreferences i po kazdym
 * odswiezeniu nadpisywala go calym wynikiem. Gdy odswiezenie zwrocilo ogryzek
 * (3 kanaly z 36, bo pozostale zostaly przerwane), ogryzek nadpisywal dobre dane
 * i wiersz "gubil" subskrypcje. Teraz:
 *
 *   - kazdy kanal ma wlasny wpis (materialy + kiedy ostatnio sprawdzany),
 *   - odswiezenie SCALA wynik per kanal, nigdy nie czysci calosci,
 *   - kanal, ktory nie odpowiedzial, zachowuje poprzednie materialy,
 *   - wiersz rysuje sie zawsze z magazynu, posortowany po dacie publikacji malejaco
 *     (dlatego data jest zapisywana obok pozycji — Video.toString() jej nie niesie).
 *
 * Format pliku (filesDir/stplus/feed.txt), po jednej rzeczy w linii, pola na TAB:
 *   C <channelId> <lastTryMs> <lastOkMs>
 *   I <channelId> <publishedMs> <videoSpec>
 * Plik jest tekstowy celowo: da sie go zrzucic z urzadzenia i przeczytac przy diagnozie.
 * <<< STPLUS
 */

import android.content.Context;
import android.util.Log;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StPlusStore {
    private static final String TAG = "StPlusStore";
    private static final String DIR = "stplus";
    private static final String FILE = "feed.txt";
    private static final String TAB = "\t";
    /** Ile pozycji trzymamy na kanal (RSS i tak daje ~15 najnowszych). */
    private static final int MAX_PER_CHANNEL = 15;

    /** Jedna pozycja: material + jego data publikacji (do sortowania globalnego). */
    public static final class Item {
        public final long published;
        public final Video video;

        Item(long published, Video video) {
            this.published = published;
            this.video = video;
        }
    }

    /** Stan jednego kanalu. */
    private static final class Channel {
        long lastTry;   // kiedy ostatnio PROBOWALISMY (zabezpiecza rotacje przed zapetleniem)
        long lastOk;    // kiedy ostatnio UDALO sie pobrac
        final List<Item> items = new ArrayList<>();
    }

    /** channelId -> stan. LinkedHashMap: stabilna kolejnosc zapisu pliku. */
    private static final Map<String, Channel> sChannels = new LinkedHashMap<>();
    private static boolean sLoaded;
    /** Ile pozycji doszlo w ostatniej rundzie (do meldunku w naglowku wiersza). */
    private static int sLastNewItems;

    private StPlusStore() {
    }

    // --------------------------------------------------------------- plik ---

    private static File file(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, FILE);
    }

    /** Wczytanie magazynu. Wolac z watku roboczego — czyta plik. */
    public static synchronized void load(Context context) {
        if (sLoaded) {
            return;
        }
        sLoaded = true;

        File src = file(context);
        if (!src.exists()) {
            return;
        }

        long start = System.currentTimeMillis();
        int items = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(src), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(TAB, 4);
                if (parts.length < 3) {
                    continue;
                }
                if ("C".equals(parts[0])) {
                    Channel channel = channel(parts[1]);
                    channel.lastTry = parseLong(parts[2]);
                    channel.lastOk = parts.length > 3 ? parseLong(parts[3]) : 0;
                } else if ("I".equals(parts[0]) && parts.length == 4) {
                    Video video = Video.fromString(parts[3]);
                    if (video != null && video.videoId != null) {
                        channel(parts[1]).items.add(new Item(parseLong(parts[2]), video));
                        items++;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Load failed: " + e.getMessage());
        }

        Log.d(TAG, "Loaded " + sChannels.size() + " channels / " + items + " items in "
                + (System.currentTimeMillis() - start) + " ms");
    }

    /** Zapis magazynu. Wolac z watku roboczego. */
    public static synchronized void save(Context context) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file(context)), "UTF-8"))) {
            for (Map.Entry<String, Channel> entry : sChannels.entrySet()) {
                String id = entry.getKey();
                Channel channel = entry.getValue();
                writer.write("C" + TAB + id + TAB + channel.lastTry + TAB + channel.lastOk);
                writer.newLine();
                for (Item item : channel.items) {
                    String spec = item.video.toString();
                    if (spec == null) {
                        continue;
                    }
                    // TAB/nowa linia w tytule rozwalilyby format — czyscimy.
                    spec = spec.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
                    writer.write("I" + TAB + id + TAB + item.published + TAB + spec);
                    writer.newLine();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Save failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------- zawartosc ---

    private static Channel channel(String channelId) {
        Channel channel = sChannels.get(channelId);
        if (channel == null) {
            channel = new Channel();
            sChannels.put(channelId, channel);
        }
        return channel;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Rejestracja listy kanalow z lokalnych subskrypcji (bez ruszania danych). */
    public static synchronized void registerChannels(List<String> channelIds) {
        for (String id : channelIds) {
            channel(id);
        }
    }

    /** Odnotuj probe pobrania (niezaleznie od wyniku) — zeby rotacja szla dalej. */
    public static synchronized void markTried(List<String> channelIds, long now) {
        for (String id : channelIds) {
            channel(id).lastTry = now;
        }
    }

    /**
     * SCALENIE wyniku odswiezenia. Podmienia materialy TYLKO tych kanalow, ktore
     * faktycznie cos zwrocily. Pozostale zachowuja poprzednia zawartosc.
     *
     * @return liczba kanalow, ktore dostaly nowe dane
     */
    public static synchronized int merge(Map<String, List<Item>> fresh, long now) {
        int updated = 0;
        sLastNewItems = 0;
        for (Map.Entry<String, List<Item>> entry : fresh.entrySet()) {
            List<Item> items = entry.getValue();
            if (items == null || items.isEmpty()) {
                continue; // pusty wynik nigdy nie kasuje tego, co mamy
            }

            Channel channel = channel(entry.getKey());

            /*
             * DATA PUBLIKACJI JEST CENNIEJSZA NIZ SWIEZY WPIS.
             *
             * Sciezka "zakladki kanalu" nie podaje prawdziwej daty (BaseMediaItem
             * zwraca -1), tylko tekst wzgledny — a gdy tekstu nie da sie przeliczyc,
             * zostaje 0. Do 2026-09-12 taki wynik BEZWARUNKOWO zastepowal poprzedni,
             * wiec jedno przelaczenie zrodla na "tylko zakladki kanalu" kasowalo daty
             * calego magazynu. Dowod z projektora Usera: 2572 wpisy, KAZDY z data 0,
             * czyli kolejnosc wiersza kompletnie losowa — filmy sprzed czterech
             * miesiecy na samej gorze.
             *
             * Dlatego: pozycja, ktora przychodzi bez daty, a byla juz w magazynie
             * z data — zachowuje te date.
             */
            Map<String, Long> known = new HashMap<>();
            for (Item old : channel.items) {
                if (old.published > 0 && old.video != null && old.video.videoId != null) {
                    known.put(old.video.videoId, old.published);
                }
            }

            List<Item> repaired = new ArrayList<>(items.size());
            for (Item item : items) {
                String id = item.video != null ? item.video.videoId : null;
                if (item.published <= 0 && id != null) {
                    Long old = known.get(id);
                    if (old != null) {
                        repaired.add(new Item(old, item.video));
                        continue;
                    }
                }
                repaired.add(item);
                if (id != null && !known.containsKey(id)) {
                    sLastNewItems++;
                }
            }
            items = repaired;

            Collections.sort(items, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    return Long.compare(b.published, a.published);
                }
            });
            if (items.size() > MAX_PER_CHANNEL) {
                items = new ArrayList<>(items.subList(0, MAX_PER_CHANNEL));
            }
            channel.items.clear();
            channel.items.addAll(items);
            channel.lastOk = now;
            channel.lastTry = now;
            updated++;
        }
        return updated;
    }

    /** Ile pozycji w ostatniej rundzie bylo NOWYCH (nie bylo ich wczesniej w kanale). */
    public static synchronized int lastNewItems() {
        return sLastNewItems;
    }

    /** Wszystkie pozycje (z datami), najnowsze pierwsze, do podanego limitu. */
    public static synchronized List<Item> topItems(int limit) {
        List<Item> all = new ArrayList<>();
        for (Channel channel : sChannels.values()) {
            all.addAll(channel.items);
        }
        Collections.sort(all, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.published, a.published);
            }
        });
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    /** Ile kanalow mamy w rejestrze. */
    public static synchronized int channelCount() {
        return sChannels.size();
    }

    /** Ile kanalow ma juz jakiekolwiek materialy. */
    public static synchronized int withDataCount() {
        int count = 0;
        for (Channel channel : sChannels.values()) {
            if (!channel.items.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Ile pozycji lacznie w magazynie. */
    public static synchronized int itemCount() {
        int count = 0;
        for (Channel channel : sChannels.values()) {
            count += channel.items.size();
        }
        return count;
    }

    /** Wszystkie materialy, najnowsze pierwsze, do podanego limitu. */
    public static synchronized List<Video> topVideos(int limit) {
        List<Item> all = new ArrayList<>();
        for (Channel channel : sChannels.values()) {
            all.addAll(channel.items);
        }
        Collections.sort(all, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.published, a.published);
            }
        });

        List<Video> result = new ArrayList<>();
        for (Item item : all) {
            if (result.size() >= limit) {
                break;
            }
            result.add(item.video);
        }
        return result;
    }

    /**
     * Kanaly do odswiezenia w tej rundzie: najdawniej probowane pierwsze,
     * tylko te starsze niz prog. Tak dziala rotacja — jedna runda bierze porcje,
     * nastepna kolejna, bez sztormu.
     *
     * @param staleMs prog przeterminowania
     * @param batch   ile kanalow w jednej rundzie
     */
    public static synchronized List<String> pickStale(long staleMs, int batch, long now) {
        return pickStale(staleMs, staleMs, batch, now);
    }

    /**
     * Jak wyzej, ale kanaly, ktore NIGDY nic nie zwrocily (np. dostaly 404 od
     * dlawienia YouTube), wracaja do kolejki szybciej — po emptyRetryMs.
     * Bez tego jeden nieudany obieg skazywalby kanal na godzine nieobecnosci.
     */
    /**
     * Kanaly do odswiezenia, wybierane wzgledem PUNKTU W CZASIE, a nie "wieku".
     *
     * BLAD, KTORY TO NAPRAWIA (2026-09-12): reczne odswiezenie wolalo rotacje
     * z `staleMs = 0`, przez co warunek `now - lastTry >= 0` byl ZAWSZE prawdziwy —
     * kazdy kanal w kazdej chwili uchodzil za przeterminowany i **rotacja nigdy sie
     * nie konczyla**. User: "zeskanowal juz wszystkie kanaly i dalej skanuje, 173 ze 173
     * i dalej kurwa skanuje".
     *
     * Wersja z progiem czasowym nie da sie tak zapetlic: kanal kwalifikuje sie tylko
     * wtedy, gdy byl probowany PRZED punktem odciecia, a kazda proba ten znacznik
     * przesuwa do przodu. Obieg konczy sie zawsze.
     *
     * @param cutoff      kanaly z materialami: kwalifikuja sie, gdy lastTry < cutoff
     * @param emptyCutoff kanaly bez materialow: prog lagodniejszy (wracaja szybciej)
     */
    public static synchronized List<String> pickStaleBefore(long cutoff, long emptyCutoff, int batch) {
        List<Map.Entry<String, Channel>> entries = new ArrayList<>(sChannels.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Channel>>() {
            @Override
            public int compare(Map.Entry<String, Channel> a, Map.Entry<String, Channel> b) {
                return Long.compare(a.getValue().lastTry, b.getValue().lastTry);
            }
        });

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Channel> entry : entries) {
            if (result.size() >= batch) {
                break;
            }
            Channel channel = entry.getValue();
            long limit = channel.items.isEmpty() ? emptyCutoff : cutoff;
            if (channel.lastTry < limit) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /** Ile kanalow czeka na odswiezenie przy danym punkcie odciecia. */
    public static synchronized int staleCountBefore(long cutoff, long emptyCutoff) {
        int count = 0;
        for (Channel channel : sChannels.values()) {
            long limit = channel.items.isEmpty() ? emptyCutoff : cutoff;
            if (channel.lastTry < limit) {
                count++;
            }
        }
        return count;
    }

    public static synchronized List<String> pickStale(long staleMs, long emptyRetryMs, int batch, long now) {
        List<Map.Entry<String, Channel>> entries = new ArrayList<>(sChannels.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Channel>>() {
            @Override
            public int compare(Map.Entry<String, Channel> a, Map.Entry<String, Channel> b) {
                return Long.compare(a.getValue().lastTry, b.getValue().lastTry);
            }
        });

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Channel> entry : entries) {
            if (result.size() >= batch) {
                break;
            }
            Channel channel = entry.getValue();
            long threshold = channel.items.isEmpty() ? emptyRetryMs : staleMs;
            if (now - channel.lastTry >= threshold) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /** Ile kanalow czeka na odswiezenie (do decyzji, czy robic kolejna runde). */
    public static synchronized int staleCount(long staleMs, long now) {
        return staleCount(staleMs, staleMs, now);
    }

    public static synchronized int staleCount(long staleMs, long emptyRetryMs, long now) {
        int count = 0;
        for (Channel channel : sChannels.values()) {
            long threshold = channel.items.isEmpty() ? emptyRetryMs : staleMs;
            if (now - channel.lastTry >= threshold) {
                count++;
            }
        }
        return count;
    }

    public static synchronized boolean isEmpty() {
        for (Channel channel : sChannels.values()) {
            if (!channel.items.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Diagnostyka: ile kanalow ma dane, ile pozycji lacznie. */
    public static synchronized String stats() {
        int withData = 0;
        int items = 0;
        for (Channel channel : sChannels.values()) {
            if (!channel.items.isEmpty()) {
                withData++;
            }
            items += channel.items.size();
        }
        return "channels=" + sChannels.size() + " withData=" + withData + " items=" + items;
    }

    /** Grupowanie wyniku RSS po kanale — wejscie do merge(). */
    // ------------------------------------------------------- debug/testy ---

    /*
     * Narzedzia do TESTOWANIA NAPELNIANIA (zyczenie Usera, 2026-09-12): "przycisk,
     * ktory kasuje na przyklad ostatnie 30 filmow, zeby mozna bylo od nowa przetestowac
     * napelnianie. Niech bedzie cala sekcja debugu w tym menu".
     *
     * Kasujemy materialy ORAZ znacznik lastOk, bo inaczej kanal jest "swiezy" i rotacja
     * go pominie — czyli nie byloby czego testowac.
     */

    /**
     * Kasuje materialy z kanalow, tak by ubylo mniej wiecej `wanted` filmow.
     * Bierze kanaly od konca listy (najdawniej zapisane), zeby nie ruszac tego,
     * co User wlasnie oglada na poczatku wiersza.
     *
     * @return ile filmow faktycznie skasowano
     */
    public static synchronized int forgetItems(int wanted) {
        int removed = 0;

        List<String> ids = new ArrayList<>(sChannels.keySet());
        Collections.reverse(ids);

        for (String id : ids) {
            if (removed >= wanted) {
                break;
            }
            Channel ch = sChannels.get(id);
            if (ch == null || ch.items.isEmpty()) {
                continue;
            }
            removed += ch.items.size();
            ch.items.clear();
            ch.lastOk = 0;   // kanal znow "pusty" -> rotacja wroci po nim szybciej
            ch.lastTry = 0;
        }

        return removed;
    }

    /** Czysci CALY magazyn (kanaly zostaja zarejestrowane, materialy znikaja). */
    public static synchronized int forgetAll() {
        int removed = 0;
        for (Channel ch : sChannels.values()) {
            removed += ch.items.size();
            ch.items.clear();
            ch.lastOk = 0;
            ch.lastTry = 0;
        }
        return removed;
    }

    public static Map<String, List<Item>> group(List<Item> items) {
        Map<String, List<Item>> result = new HashMap<>();
        for (Item item : items) {
            String id = item.video.channelId;
            if (id == null || id.isEmpty()) {
                continue;
            }
            List<Item> list = result.get(id);
            if (list == null) {
                list = new ArrayList<>();
                result.put(id, list);
            }
            list.add(item);
        }
        return result;
    }

    public static Item item(long published, Video video) {
        return new Item(published, video);
    }
}
