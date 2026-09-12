package com.liskovsoft.smartyoutubetv2.common.stplus;

/*
 * >>> STPLUS — SmartTube+ plugin code.
 * Ten plik jest NASZ (nie istnieje w upstreamie). Pełna lista zmian:
 * STPLUS/MODYFIKACJE.md w korzeniu repozytorium.
 *
 * Funkcja 1: na stronie głównej (Home) wstawia dodatkowy rząd "Twoje kanały"
 * jako PIERWSZY wiersz. Rząd zawiera najnowsze materiały z KANAŁÓW Z LOKALNEJ
 * LISTY SUBSKRYPCJI (np. zaimportowanej z NewPipe) i działa BEZ logowania.
 *
 * SILNIK v2 (2026-09-12) — przebudowa po pomiarach, patrz STPLUS/AUDYT-CC.md:
 *   - dane trzymamy PER KANAŁ w StPlusStore (scalanie, nigdy nadpisanie całości),
 *   - wiersz rysuje się zawsze z magazynu, najnowsze pierwsze (sort po dacie),
 *   - odświeżanie jest ROTACYJNE: jedna runda bierze porcję najdawniej sprawdzanych
 *     kanałów (BATCH), kolejna następną — zamiast sztormu 50 kanałów naraz,
 *   - jedno żądanie naraz, guard CZASOWY (martwe odświeżanie samo wygasa),
 *   - w RssService: 3 kanały równolegle i bez ciężkiego browse per kanał
 *     (wzorzec z NewPipe — patrz STPLUS/research/analiza_newpipe_feed.md).
 * <<< STPLUS
 */

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.liskovsoft.mediaserviceinterfaces.ChannelGroupService;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.youtubeapi.rss.RssOptions;
import com.liskovsoft.youtubeapi.rss.StPlusFeedSource;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StPlus {
    private static final String TAG = "StPlus";

    /** Unikalne ID naszego rzędu (od "STP1"). Stałe ID => odświeżenie home
     *  podmienia rząd zamiast go duplikować. */
    public static final int SUBS_ROW_ID = 0x53545031;
    /** Pozycja rzędu: 0 = SAMA GÓRA (pierwszy wiersz). */
    private static final int SUBS_ROW_POSITION = 0;
    /** ID specjalnej grupy "Subscriptions" — tu ląduje import z NewPipe. */
    private static final String SUBSCRIPTIONS_GROUP_ID = "1000";
    /** Ile pozycji pokazac od razu na starcie. */
    private static final int PAGE_SIZE = 40;
    /** Ile dosypywac, gdy user dojedzie do konca wiersza. */
    private static final int PAGE_CHUNK = 20;
    /** Ile maksymalnie pozycji trzymamy w wierszu (zapas do dosypywania). */
    private static final int MAX_ITEMS = 200;

    // --- rotacja (wzorzec NewPipe: 3 rownolegle, tylko przeterminowane) ---
    /*
     * TEMPO (przyspieszone 2026-09-12 po pomiarze — User: "a ta RSS to tez mogles
     * kurwa przyspieszyc troche").
     *
     * Poprzednie ustawienie (5 kanalow / 1500 ms / 12 s) przeszlo caly obieg 173 kanalow
     * w 9 minut z ZEREM bledow — czyli byl duzy zapas. Nowe tempo trzyma sie wzorca
     * NewPipe (3 rownolegle) i daje ~10 kanalow na ~8 s, czyli okolo 75 kanalow/min,
     * pelny obieg w ~2,5 minuty.
     *
     * Bezpiecznik zostaje: StPlusFeedSource.backoffMs() po 3 bledach z rzedu sam zwalnia,
     * wiec jesli YouTube znow zacznie dlawic, aplikacja wyhamuje bez naszego udzialu.
     */
    /** Ile kanalow odswiezamy w JEDNEJ rundzie. */
    private static final int BATCH_CHANNELS = 10;
    /** Ile kanalow pobieranych rownolegle (NewPipe: PARALLEL_EXTRACTIONS = 3). */
    private static final int PARALLEL_CHANNELS = 3;
    /**
     * Odstep miedzy zapytaniami w rundzie. YouTube DLAWI serie zapytan do RSS
     * i odpowiada 404 (pomiar 2026-09-12: 140 z 149 zapytan = 404). Spokojne
     * tempo jest warunkiem, zeby feed w ogole dzialal.
     */
    private static final long CHANNEL_DELAY_MS = 600L;
    /** Kanal starszy niz to = przeterminowany (NewPipe domyslnie 5 min). */
    private static final long STALE_MS = 60 * 60 * 1000L;
    /**
     * Kanal, ktory jeszcze NIC nie zwrocil (np. dostal 404 od dlawienia), wraca
     * do kolejki szybciej — inaczej jeden nieudany obieg kasuje go na godzine.
     */
    private static final long EMPTY_RETRY_MS = 5 * 60 * 1000L;
    /** Odstep miedzy rundami rotacji — spokojnie, zeby nie dostac 404. */
    private static final long NEXT_ROUND_DELAY_MS = 5000L;
    /** Po tylu ms uznajemy odswiezanie za martwe i pozwalamy ruszyc od nowa. */
    private static final long REFRESH_DEAD_MS = 90 * 1000L;

    /**
     * TRYB DEBUGOWY (zyczenie Usera 2026-09-12): naglowek wiersza pokazuje stan
     * zbierania (ile kanalow ma dane / ile jest wszystkich, ile filmow, ile zostalo
     * do obiegu, ile kanalow odmowilo), a pod kazdym filmem jest czas publikacji
     * ("2 godz. temu"). Docelowo przelacznik w naszych ustawieniach (K15).
     */
    private static boolean debugLine() {
        return StPlusSettings.isDebugLine(sContext);
    }

    /** videoId sztucznej karty "Ładowanie…" na końcu wiersza. */
    private static final String LOADING_ITEM_ID = "stplus_loading";

    /** videoId karty statusu na POCZATKU wiersza (tryb debugowy). */
    private static final String STATUS_ITEM_ID = "stplus_status";

    /** Meldunek z ostatniej rundy, po ludzku — na karte statusu. */
    private static volatile String sLastRoundInfo = null;

    /*
     * HARMONOGRAM SKANOWANIA — jeden, jawny model (User, 2026-09-12: "masz znalezc to,
     * kiedy on skanuje, jak dlugo, ile razy, to trzeba ogarnac, ma byc przemyslane madrze").
     *
     * Zasady:
     *  - JEDEN obieg = jedno przejscie po wszystkich kanalach, ktore byly probowane
     *    przed `sCycleCutoff`. Kazda proba przesuwa znacznik kanalu do przodu, wiec obieg
     *    zawsze sie konczy (nie da sie go zapetlic tak, jak przy staleMs = 0).
     *  - Nowy obieg rusza tylko wtedy, gdy od poczatku poprzedniego minal interwal
     *    z ustawien (zebatka -> "Kiedy odswiezac").
     *  - Wejscie na home NIE jest wyzwalaczem skanowania. Wczesniej bylo i to jest powod,
     *    dla ktorego aplikacja skanowala bez konca: home przeladowuje sie czesto.
     */
    /** Punkt odciecia biezacego obiegu: kanaly probowane przed nim sa do odswiezenia. */
    private static volatile long sCycleCutoff = 0;
    /** Kiedy ruszyl ostatni obieg (do decyzji, czy juz czas na nastepny). */
    private static volatile long sCycleStarted = 0;
    /** Czy obieg trwa. */
    private static volatile boolean sCycleRunning = false;
    /** Ile rund od ostatniego zapisu magazynu na dysk. */
    private static volatile int sRoundsSinceSave = 0;
    /** Co ile rund magazyn laduje na dysk (poza koncem obiegu). */
    private static final int SAVE_EVERY_ROUNDS = 6;

    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static final ExecutorService sIo = Executors.newSingleThreadExecutor();

    private static Disposable sSubsRowAction;
    /** Znacznik startu odswiezania (0 = nic nie leci). Guard CZASOWY, nie boolean —
     *  zawieszone zadanie nie blokuje odswiezania na zawsze. */
    private static long sRefreshStart;
    /** Sygnatura tresci aktualnie pokazanej w wierszu — bez zmiany nie przerysowujemy. */
    private static String sShownSignature;
    /** Pelna lista pozycji wiersza (zapas do dosypywania przy przewijaniu). */
    private static final List<Video> sAllVideos = new ArrayList<>();
    /** Ile pozycji z sAllVideos jest juz w wierszu. */
    private static int sShownCount;
    /** Czy w tym cyklu ladowania home wiersz zostal juz wstawiony. */
    private static boolean sRowShownThisCycle;
    private static boolean sStoreReady;
    /**
     * Czy trwajaca rotacja jest CICHA: rundy w tle nie kreca kolkiem w belce i
     * NIE przerysowuja wiersza. Powod (zgloszenie Usera 2026-09-12): kazde
     * przerysowanie wiersza to w leanbacku usuniecie i wstawienie rzedu, czyli
     * skok ekranu do gory i wrazenie "ciagle sie odswieza". Dane zbieramy cicho,
     * a wiersz dostaje nowa tresc raz — na koncu obiegu albo przy nastepnym
     * wejsciu na ekran glowny.
     */
    private static boolean sQuietRotation = true;
    /** Ile kanalow w ostatniej rundzie nic nie zwrocilo (YouTube dlawi — patrz K17). */
    private static int sLastRoundSilent;
    /** Ile kanalow lacznie odmowilo od startu procesu. */
    private static int sSilentTotal;
    /** Stan do linii debugowej. */
    private static String sDebugState = "start";

    // Ostatni kontekst wywołania — potrzebny do ręcznego odświeżenia z belki.
    private static Context sContext;
    private static BrowseView sView;
    private static BrowseSection sSection;

    /** Wskaźnik odświeżania w górnej belce (BrowseFragment go rejestruje). */
    public interface RefreshListener {
        void onRefreshState(boolean refreshing);
    }

    private static RefreshListener sRefreshListener;

    // Zwijanie nawigacji: callback ustawiany przez BrowseFragment.
    private static Runnable sReShowNav;

    private StPlus() {
    }

    // ---------------------------------------------------------------- nav ---

    public static void setReShowNavCallback(Runnable r) {
        sReShowNav = r;
    }

    /** Klawisz menu — przywraca rail, jeśli jest ukryty. */
    public static void reShowNavIfHidden() {
        if (sReShowNav != null) {
            sReShowNav.run();
        }
    }

    // ------------------------------------------------------------ refresh ---

    public static void setRefreshListener(RefreshListener listener) {
        sRefreshListener = listener;
        if (listener != null) {
            listener.onRefreshState(isRefreshing());
        }
    }

    public static boolean isRefreshing() {
        return sRefreshStart != 0;
    }

    /** Ręczne odświeżenie (kliknięcie ikonki w belce) — bierze kolejną porcję od razu. */
    public static void requestManualRefresh() {
        // Reczne odswiezenie = nowy pomiar. Liczniki kodow RSS lecza sie od zera,
        // zeby "404x120" z poprzedniej godziny nie zaslanialo tego, co dzieje sie teraz.
        StPlusFeedSource.resetRoundStats();
        sLastRoundInfo = "skanuje...";
        if (sContext == null || sView == null || sSection == null) {
            return;
        }
        // Reczny refresh: widoczny (kolko w belce) i od razu odswieza wiersz po rundzie.
        sQuietRotation = false;
        maybeStartCycle(true); // true = na zadanie Usera, ignoruje harmonogram
    }

    private static void setRefreshing(boolean refreshing) {
        sRefreshStart = refreshing ? System.currentTimeMillis() : 0;
        // Kolko w belce tylko dla odswiezenia, o ktore User poprosil sam.
        if (sRefreshListener != null && !sQuietRotation) {
            sRefreshListener.onRefreshState(refreshing);
        }
    }

    /** Czy jakies odswiezenie faktycznie leci (martwe po REFRESH_DEAD_MS wygasa). */
    private static boolean refreshInFlight() {
        if (sRefreshStart == 0) {
            return false;
        }
        if (System.currentTimeMillis() - sRefreshStart > REFRESH_DEAD_MS) {
            Log.w(TAG, "Previous refresh looks dead - releasing guard");
            setRefreshing(false);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- row ---

    /** Sztuczna karta na końcu wiersza: "Ładowanie…" — sygnał, że jest więcej. */
    private static Video createLoadingItem(Context context) {
        Video video = new Video();
        video.videoId = LOADING_ITEM_ID;
        video.title = context.getString(R.string.stplus_loading_more);
        return video;
    }

    /** Czy to nasza karta-zaślepka (nie wolno jej otwierać ani kolejkować). */
    public static boolean isLoadingItem(Video video) {
        return video != null && LOADING_ITEM_ID.equals(video.videoId);
    }

    private static boolean hasMore() {
        return sShownCount < sAllVideos.size();
    }

    /** Sygnatura listy — pozwala pominąć przerysowanie, gdy nic się nie zmieniło. */
    private static String signature(List<Video> videos) {
        StringBuilder sb = new StringBuilder();
        int count = Math.min(videos.size(), PAGE_SIZE);
        for (int i = 0; i < count; i++) {
            sb.append(videos.get(i).videoId).append('|');
        }
        // UWAGA — NIE dokladac tu meldunku z rundy. Probowalem tak 2026-09-12 i kazda
        // runda zmieniala sygnature => wiersz byl przerysowywany, MRUGAL i zrzucal
        // Usera na poczatek listy ("cofam mi na poczatek listy, to jest niedorzeczne").
        // Karta statusu aktualizuje sie w miejscu, przez syncStatusCard() + ACTION_SYNC.
        return sb.toString();
    }

    /**
     * Zapis magazynu na dysk — ale NIE po kazdej rundzie.
     *
     * POMIAR (projektor, 2026-09-12): plik `feed.txt` ma ~994 kB i 2733 wpisy, a byl
     * przepisywany W CALOSCI po kazdej rundzie, czyli ~17 razy na obieg. To jest
     * kilkanascie megabajtow zapisu na flash i gora tymczasowych obiektow na kazdy
     * obieg — stad HeapTaskDaemon (GC) na 57% procesora przy odswiezaniu.
     *
     * Zapis kosztuje tyle samo niezaleznie od tego, ile kanalow doszlo, wiec robimy go
     * rzadziej: co SAVE_EVERY_ROUNDS rund (zeby nagle ubicie procesu nie kosztowalo
     * calego obiegu) i zawsze na koncu obiegu. Dane miedzy zapisami i tak sa w pamieci,
     * wiec wiersz nic nie traci.
     *
     * @param force true = zapisz teraz (koniec obiegu, czyszczenie magazynu)
     */
    private static void saveStore(Context context, boolean force) {
        sRoundsSinceSave++;
        if (!force && sRoundsSinceSave < SAVE_EVERY_ROUNDS) {
            return;
        }
        sRoundsSinceSave = 0;
        StPlusStore.save(context);
    }

    /**
     * Magazyn zostal wyczyszczony z menu debugu — wiersz musi o tym wiedziec,
     * inaczej pokazywalby stara liste z pamieci procesu.
     */
    public static void onStoreCleared() {
        sAllVideos.clear();
        sShownCount = 0;
        sShownSignature = null;
        sRowShownThisCycle = false;
        sLastRoundInfo = "magazyn wyczyszczony — skanuje od nowa";
        sSilentTotal = 0;
    }

    /**
     * Odswieza SAMA karte statusu, nie ruszajac wiersza.
     *
     * ACTION_SYNC podmienia pozycje w miejscu (notifyItemRangeChanged), wiec nie ma
     * usuwania i wstawiania rzedu — czyli nie ma mrugania ani zrzucania Usera na
     * poczatek listy. To byl jego wyrazny zarzut (2026-09-12) i nie wolno tego cofnac
     * "przy okazji" jakiegos uproszczenia.
     */
    private static void syncStatusCard() {
        if (!debugLine() || sContext == null || sView == null || sSection == null) {
            return;
        }

        try {
            List<Video> one = new ArrayList<>();
            one.add(createStatusItem(sContext));
            VideoGroup group = buildGroup(sContext, sSection, one, VideoGroup.ACTION_SYNC);
            sView.updateSection(group);
        } catch (Exception e) {
            Log.e(TAG, "status sync failed: " + e.getMessage());
        }
    }

    private static VideoGroup buildGroup(Context context, BrowseSection section, List<Video> videos, int action) {
        VideoGroup group = VideoGroup.from(videos, section, SUBS_ROW_POSITION);
        group.setId(SUBS_ROW_ID);
        group.setTitle(rowTitle(context));
        group.setAction(action);
        return group;
    }

    /**
     * Rysuje wiersz z podanej listy. Jesli tresc jest ta sama co na ekranie —
     * NIE robi nic (to leczy migotanie: home ladowal sie ~6x i za kazdym razem
     * przebudowywalismy wiersz na watku glownym).
     */
    /**
     * Naglowek wiersza. Przy wlaczonym trybie debugowania dopisujemy do niego
     * krotkie PODSUMOWANIE PELNYMI SLOWAMI (User chcial tego opisu wlasnie w linii
     * "Twoje kanaly", a wczesniejszy skrot "6/173 · 90f · ↻12" odrzucil jako
     * "skrot jak dla hakerow").
     *
     * Szczegoly — co zrobila ostatnia runda, jakie kody odpowiedzi, jaki blad —
     * zostaja na karcie statusu, bo tam jest miejsce i mala czcionka.
     */
    private static String rowTitle(Context context) {
        String base = context.getString(R.string.stplus_subscriptions_row);
        if (!debugLine() || !sStoreReady) {
            return base;
        }

        int all = StPlusStore.channelCount();
        int withData = StPlusStore.withDataCount();
        int items = StPlusStore.itemCount();

        return base + "  —  " + withData + " z " + all + " kanałów, " + items + " filmów";
    }

    /**
     * Sztuczna karta na POCZATKU wiersza — meldunek o tym, co sie naprawde dzieje.
     *
     * DLACZEGO karta, a nie naglowek wiersza (User, 2026-09-12, dwukrotnie):
     * naglowek ma duza czcionke i malo miejsca — pelne zdanie sie nie miescilo na
     * tablecie, a gdy je skrocilem do "6/173 · 90f · ↻12 · !8", User powiedzial wprost,
     * ze to "jakis skrot jak dla hakerow" i nadal nie widzi, co wyszlo ze skanowania.
     * Druga linia kafelka ma MALA czcionke i miesci cale zdanie — i o male litery
     * wlasnie chodzilo od poczatku.
     *
     * Karta pokazuje: ile kanalow ma materialy, ile jest filmow, co zrobila ostatnia
     * runda, z jakiego zrodla przyszly dane (RSS czy zakladki kanalu) i jaki byl blad
     * wraz z kodami odpowiedzi (np. "404x8").
     */
    private static Video createStatusItem(Context context) {
        Video video = new Video();
        video.videoId = STATUS_ITEM_ID;

        int all = StPlusStore.channelCount();
        int withData = StPlusStore.withDataCount();
        int items = StPlusStore.itemCount();

        video.title = withData + " z " + all + " kanalow, " + items + " filmow";

        StringBuilder sb = new StringBuilder();
        if (sLastRoundInfo != null) {
            sb.append(sLastRoundInfo);
        } else {
            sb.append("jeszcze nie skanowalem");
        }

        String src = RssOptions.lastSourceUsed;
        if (src != null) {
            sb.append(" • zrodlo: ").append("RSS".equals(src) ? "RSS" : "zakladki kanalu");
        }

        String err = RssOptions.lastError;
        if (err != null) {
            sb.append(" • ").append(err);
        }

        String codes = RssOptions.lastCodesSummary;
        if (codes != null) {
            sb.append(" • odpowiedzi RSS: ").append(codes);
        }

        video.secondTitle = sb.toString();
        return video;
    }

    /** Czy to nasza karta statusu (nie wolno jej otwierac). */
    public static boolean isStatusItem(Video video) {
        return video != null && STATUS_ITEM_ID.equals(video.videoId);
    }

    /**
     * Znacznik publikacji materialu, niezaleznie od tego, ktora droga przyszedl.
     *
     * RSS daje prawdziwy czas (`getPublishedDate()`). Zakladki kanalu NIE — tam
     * `getPublishedDate()` to twarde -1 (BaseMediaItem.kt:123), a jedyna informacja
     * o czasie siedzi w tekscie wzglednym `getProductionDate()` ("2 days ago",
     * "3 tygodnie temu"). Bez przeliczenia caly wiersz sortowalby sie losowo,
     * a pod filmami nie byloby "2 godz. temu", o ktore prosil User.
     *
     * Tekst jest w jezyku klienta, wiec rozpoznajemy rdzenie PL i EN. Gdy sie nie da —
     * zwracamy 0 (pozycja lezy nizej, ale nie znika).
     */
    private static long publishedMs(MediaItem item) {
        long exact = item.getPublishedDate();
        if (exact > 0) {
            return exact;
        }
        return fromRelative(item.getProductionDate());
    }

    /** "2 days ago" / "3 tygodnie temu" -> znacznik czasu. 0 = nie rozpoznano. */
    static long fromRelative(String text) {
        if (text == null) {
            return 0;
        }
        String t = text.toLowerCase();
        int num = 0;
        boolean seen = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                num = num * 10 + (c - '0');
                seen = true;
            } else if (seen) {
                break;
            }
        }
        if (!seen) {
            num = 1; // "rok temu", "a day ago"
        }

        long unit;
        if (t.contains("sekund") || t.contains("second")) {
            unit = 1000L;
        } else if (t.contains("minut") || t.contains("minute")) {
            unit = 60L * 1000L;
        } else if (t.contains("godz") || t.contains("hour")) {
            unit = 60L * 60L * 1000L;
        } else if (t.contains("dzie") || t.contains("dni") || t.contains("day")) {
            unit = 24L * 60L * 60L * 1000L;
        } else if (t.contains("tydz") || t.contains("tygod") || t.contains("week")) {
            unit = 7L * 24L * 60L * 60L * 1000L;
        } else if (t.contains("miesi") || t.contains("month")) {
            unit = 30L * 24L * 60L * 60L * 1000L;
        } else if (t.contains("rok") || t.contains("lat") || t.contains("year")) {
            unit = 365L * 24L * 60L * 60L * 1000L;
        } else {
            return 0;
        }

        return System.currentTimeMillis() - num * unit;
    }

    /** Godzina "HH:MM" z znacznika czasu — do meldunku o ostatnim skanowaniu. */
    private static String clock(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(java.util.Locale.US, "%02d:%02d",
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
    }

    /** Czas od publikacji po ludzku: "3 min temu", "2 godz. temu", "5 dni temu". */
    private static String relativeTime(long published) {
        if (published <= 0) {
            return null;
        }
        long diff = System.currentTimeMillis() - published;
        if (diff < 0) {
            return "zaraz";
        }
        long minutes = diff / 60000L;
        if (minutes < 60) {
            return Math.max(1, minutes) + " min temu";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + " godz. temu";
        }
        long days = hours / 24;
        if (days < 31) {
            return days + " dni temu";
        }
        long months = days / 30;
        return months + " mies. temu";
    }

    /** Dokleja czas publikacji do drugiej linii kafelka (tryb debugowy / czytelnosc). */
    private static void stampTime(StPlusStore.Item item) {
        String when = relativeTime(item.published);
        if (when == null) {
            return;
        }
        Video video = item.video;
        String author = video.secondTitle != null ? video.secondTitle.toString() : null;
        if (author != null) {
            int sep = author.indexOf(" • ");
            if (sep > 0) {
                author = author.substring(0, sep);
            }
            video.secondTitle = author + " • " + when;
        } else {
            video.secondTitle = when;
        }
    }

    /** Lista do wiersza z magazynu + doklejonym czasem publikacji. */
    private static List<Video> rowVideos() {
        // Bierzemy z zapasem, bo czesc odpadnie na filtrze shortow.
        List<StPlusStore.Item> items = StPlusStore.topItems(MAX_ITEMS * 2);
        boolean allowShorts = StPlusSettings.isShowShorts(sContext);
        List<Video> result = new ArrayList<>();
        for (StPlusStore.Item item : items) {
            if (result.size() >= MAX_ITEMS) {
                break;
            }
            if (!allowShorts && isShort(item.video)) {
                continue;
            }
            stampTime(item);
            result.add(item.video);
        }
        return result;
    }

    /**
     * Czy to short. Bez dodatkowego zapytania do YouTube da sie to poznac tylko
     * po sygnalach, ktore juz mamy:
     *  - sciezka "zakladki kanalu" oznacza shorty sama (Video.isShorts),
     *  - RSS nie oznacza ich wcale — zostaje tag w tytule, ktory autorzy wpisuja
     *    niemal zawsze (#shorts).
     * Heurystyka, nie pewnik — i tak jest napisane w menu.
     */
    private static boolean isShort(Video video) {
        if (video == null) {
            return false;
        }
        if (video.isShorts) {
            return true;
        }
        String title = video.title;
        if (title == null) {
            return false;
        }
        String t = title.toLowerCase();
        return t.contains("#short") || t.contains("#yt short");
    }

    /** Zmiana filtra w ustawieniach — wiersz trzeba przebudowac od razu. */
    public static void onFilterChanged() {
        sShownSignature = null;
        sMain.post(new Runnable() {
            @Override
            public void run() {
                if (sView != null && sSection != null && sContext != null) {
                    List<Video> videos = rowVideos();
                    if (!videos.isEmpty()) {
                        showRow(sContext, sView, sSection, videos, true);
                    }
                }
            }
        });
    }

    private static void showRow(Context context, BrowseView view, BrowseSection section, List<Video> videos, boolean force) {
        if (videos.isEmpty()) {
            return;
        }

        String signature = signature(videos);
        if (!force && signature.equals(sShownSignature)) {
            return;
        }
        sShownSignature = signature;

        sAllVideos.clear();
        sAllVideos.addAll(videos);
        sShownCount = Math.min(PAGE_SIZE, videos.size());

        List<Video> page = new ArrayList<>(videos.subList(0, sShownCount));
        if (debugLine()) {
            // Karta statusu na POCZATKU wiersza — cale zdanie mala czcionka w drugiej
            // linii kafelka (naglowek wiersza ma za duze litery, zeby to pomiescic).
            page.add(0, createStatusItem(context));
        }
        if (hasMore()) {
            page.add(createLoadingItem(context)); // kafelek "Ładowanie…" na końcu
        }

        // REPLACE: podmienia zawartość rzędu w miejscu (usuwa stary po ID,
        // wstawia nowy na pozycji 0) — bez duplikatów przy odświeżeniu.
        view.updateSection(buildGroup(context, section, page, VideoGroup.ACTION_REPLACE));
    }

    /**
     * Wywolywane, gdy user dojedzie do konca naszego wiersza (hook w
     * BrowsePresenter.onScrollEnd). Dosypuje kolejna partie z juz pobranego
     * zapasu — bez ruchu w sieci, wiec natychmiast.
     *
     * @return true jesli to nasz wiersz (upstream nie ma juz nic do roboty)
     */
    public static boolean onRowScrollEnd(int groupId) {
        if (groupId != SUBS_ROW_ID) {
            return false;
        }

        if (sContext == null || sView == null || sSection == null || !hasMore()) {
            return true;
        }

        int end = Math.min(sShownCount + PAGE_CHUNK, sAllVideos.size());
        List<Video> chunk = new ArrayList<>(sAllVideos.subList(sShownCount, end));
        sShownCount = end;

        // 1. zdejmij stary kafelek "Ładowanie…"
        List<Video> loading = new ArrayList<>();
        loading.add(createLoadingItem(sContext));
        sView.updateSection(buildGroup(sContext, sSection, loading, VideoGroup.ACTION_REMOVE));

        // 2. dosyp partie (i kafelek na koncu, jesli zostalo jeszcze wiecej)
        if (hasMore()) {
            chunk.add(createLoadingItem(sContext));
        }
        sView.updateSection(buildGroup(sContext, sSection, chunk, VideoGroup.ACTION_APPEND));

        Log.d(TAG, "Row page appended: " + chunk.size() + " (shown " + sShownCount + "/" + sAllVideos.size() + ")");
        return true;
    }

    // -------------------------------------------------------------- hooki ---

    /**
     * Hook wywolywany NA STARCIE ladowania home (zaraz po wyczyszczeniu rzedow).
     * Wstawia wiersz z magazynu, zeby byl widoczny OD RAZU.
     */
    public static void onHomeLoadStarted(Context context, BrowseView view, BrowseSection section) {
        if (context == null || view == null || section == null) {
            return;
        }

        sContext = context.getApplicationContext();
        sView = view;
        sSection = section;
        sRowShownThisCycle = false;
        // UWAGA: NIE zerowac tu sygnatury. Home laduje sie kilka razy pod rzad, a zerowanie
        // sygnatury kazalo za kazdym razem przerysowac wiersz — stad MRUGANIE przy starcie
        // i wiersz "pojawiajacy sie po czasie, przesuniety do gory" (User, 2026-09-12).
        // ACTION_REPLACE i tak trafia po ID, wiec wiersz wroci na swoje miejsce.

        renderFromStore(true);
    }

    /**
     * Hook wywoływany PO pętli domyślnych wierszy home. Dba, żeby wiersz był
     * na ekranie, i uruchamia rundę rotacyjnego odświeżania.
     */
    public static void onHomeRowsLoaded(Context context, BrowseView view, BrowseSection section) {
        if (context == null || view == null || section == null) {
            return;
        }

        sContext = context.getApplicationContext();
        sView = view;
        sSection = section;

        try {
            // Wiersz wstawil juz onHomeLoadStarted — drugi render w tym samym cyklu
            // tylko mruga. Renderujemy wylacznie wtedy, gdy tamten sie nie odbyl.
            if (!sRowShownThisCycle) {
                renderFromStore(false);
            }
            sQuietRotation = true; // rotacja w tle jest niewidoczna dla Usera
            // Wejscie na home NIE skanuje samo z siebie — decyduje harmonogram.
            maybeStartCycle(false);
        } catch (Exception e) {
            // Hook nigdy nie może zepsuć normalnego ładowania home
            Log.e(TAG, "StPlus hook failed: " + e.getMessage());
        }
    }

    /**
     * Rysuje wiersz z magazynu. Pierwsze wywolanie w procesie wczytuje plik
     * w tle (nie na watku glownym!), potem dane sa juz w pamieci.
     */
    private static void renderFromStore(final boolean firstOfCycle) {
        final Context context = sContext;
        final BrowseView view = sView;
        final BrowseSection section = sSection;
        if (context == null || view == null || section == null) {
            return;
        }

        if (sStoreReady) {
            List<Video> videos = rowVideos();
            if (!videos.isEmpty()) {
                showRow(context, view, section, videos, firstOfCycle && !sRowShownThisCycle);
                sRowShownThisCycle = true;
            }
            return;
        }

        sIo.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    StPlusStore.load(context);
                    sStoreReady = true;
                    List<Video> fromStore = rowVideos();
                    if (fromStore.isEmpty()) {
                        // Pierwsze uruchomienie silnika v2: magazyn jest pusty, ale moze
                        // lezec stary cache z v1 (jeden blob w SharedPreferences).
                        // Pokazujemy go, zeby wiersz nie byl pusty, dopoki rotacja
                        // nie zbierze danych. Tylko do wyswietlenia — nie zapisujemy go.
                        fromStore = legacyCache(context);
                    }
                    final List<Video> videos = fromStore;
                    if (videos.isEmpty()) {
                        return;
                    }
                    sMain.post(new Runnable() {
                        @Override
                        public void run() {
                            if (sView == null || sSection == null) {
                                return;
                            }
                            showRow(sContext, sView, sSection, videos, true);
                            sRowShownThisCycle = true;
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Store load failed: " + e.getMessage());
                }
            }
        });
    }

    /** Stary cache silnika v1 (jeden blob w SharedPreferences "stplus"). Tylko do odczytu. */
    private static List<Video> legacyCache(Context context) {
        List<Video> result = new ArrayList<>();
        try {
            String data = context.getApplicationContext()
                    .getSharedPreferences("stplus", Context.MODE_PRIVATE)
                    .getString("subs_row_cache", null);
            if (data == null || data.isEmpty()) {
                return result;
            }
            String[] specs = Helpers.splitArray(data);
            if (specs == null) {
                return result;
            }
            for (String spec : specs) {
                if (spec == null || spec.isEmpty()) {
                    continue;
                }
                Video video = Video.fromString(spec);
                if (video != null && video.videoId != null) {
                    result.add(video);
                }
            }
            Log.d(TAG, "Legacy cache shown: " + result.size() + " items");
        } catch (Exception e) {
            Log.e(TAG, "Legacy cache read failed: " + e.getMessage());
        }
        return result;
    }

    // ------------------------------------------------------------ rotacja ---

    /** ID kanałów z lokalnych subskrypcji (wszystkie — bez sztucznego limitu). */
    private static List<String> localChannelIds() {
        List<String> channelIds = new ArrayList<>();
        ChannelGroupService groups = YouTubeServiceManager.instance().getChannelGroupService();
        if (groups == null || groups.isEmpty()) {
            return channelIds;
        }

        addChannelIds(groups.findChannelGroupById(SUBSCRIPTIONS_GROUP_ID), channelIds);
        List<ItemGroup> allGroups = groups.getChannelGroups();
        if (allGroups != null) {
            for (ItemGroup group : allGroups) {
                addChannelIds(group, channelIds);
            }
        }
        return channelIds;
    }

    private static void addChannelIds(ItemGroup group, List<String> channelIds) {
        if (group == null) {
            return;
        }
        List<ItemGroup.Item> items = group.getItems();
        if (items == null) {
            return;
        }
        for (ItemGroup.Item item : items) {
            String id = item.getChannelId();
            if (id != null && !id.isEmpty() && !channelIds.contains(id)) {
                channelIds.add(id);
            }
        }
    }

    /**
     * Decyduje, czy ruszyc NOWY obieg skanowania.
     *
     * @param onDemand true = User kliknal odswiez (harmonogram nie obowiazuje)
     */
    private static void maybeStartCycle(boolean onDemand) {
        if (sContext == null || sView == null || sSection == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (sCycleRunning && !onDemand) {
            return; // obieg juz leci, nie nakladamy drugiego
        }

        if (!onDemand) {
            long every = StPlusSettings.getRefreshIntervalMs(sContext);
            if (every <= 0) {
                Log.d(TAG, "Auto refresh disabled - skip");
                return;
            }
            if (sCycleStarted != 0 && now - sCycleStarted < every) {
                Log.d(TAG, "Too early for next cycle (" + ((now - sCycleStarted) / 1000) + "s)");
                return;
            }
        }

        sCycleCutoff = now;
        sCycleStarted = now;
        sCycleRunning = true;
        Log.d(TAG, "Cycle start (onDemand=" + onDemand + ")");
        startRound(sContext, sView, sSection, 0L);
    }

    /**
     * Jedna runda rotacji: bierze porcję najdawniej sprawdzanych kanałów
     * i odświeża TYLKO je. Wynik scala się z magazynem — kanał, który nie
     * odpowiedział, zachowuje poprzednie materiały.
     */
    private static void startRound(final Context context, final BrowseView view,
                                   final BrowseSection section, final long staleMs) {
        if (refreshInFlight()) {
            Log.d(TAG, "Refresh already in flight - skip");
            return;
        }

        sIo.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    StPlusStore.load(context);
                    sStoreReady = true;

                    List<String> all = localChannelIds();
                    if (all.isEmpty()) {
                        Log.d(TAG, "No local channel ids - skip refresh");
                        return;
                    }
                    StPlusStore.registerChannels(all);

                    // Wybor wzgledem punktu odciecia biezacego obiegu — kazda proba
                    // przesuwa znacznik kanalu, wiec obieg ma gwarantowany koniec.
                    final List<String> batch = StPlusStore.pickStaleBefore(
                            sCycleCutoff, sCycleCutoff,
                            StPlusSettings.speedProfile(context)[0]);
                    if (batch.isEmpty()) {
                        Log.d(TAG, "Cycle done - all channels tried (" + StPlusStore.stats() + ")");
                        sCycleRunning = false;
                        return;
                    }

                    sMain.post(new Runnable() {
                        @Override
                        public void run() {
                            fetchBatch(context, view, section, batch, staleMs);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Round prepare failed: " + e.getMessage());
                }
            }
        });
    }

    /** Pobranie jednej porcji kanałów (subskrypcja Rx oddaje wynik na wątku głównym). */
    private static void fetchBatch(final Context context, final BrowseView view,
                                   final BrowseSection section, final List<String> batch,
                                   final long staleMs) {
        if (refreshInFlight()) {
            return;
        }

        ContentService content = YouTubeServiceManager.instance().getContentService();
        if (content == null) {
            return;
        }

        // Zrodlo: RSS jako pierwsza, tania proba; zakladki kanalu jako ODWROT,
        // gdy RSS nie odpowie (K22 — NIE rezygnujemy z RSS, User: "RSS zawsze
        // dzialal i bedzie dzialac, chyba ze nas zbanowali calkowicie").
        StPlusSettings.load(sContext); // zrodlo feedu z NASZYCH ustawien (zebatka)
        int[] profile = StPlusSettings.speedProfile(sContext);
        RssOptions.skipChannelSync = true;
        RssOptions.maxParallelChannels = profile[1];
        RssOptions.delayBetweenChannelsMs = profile[2];

        sDebugState = "pobieram " + batch.size() + " kan.";
        Log.d(TAG, "Round start: " + batch.size() + " channels (" + StPlusStore.stats() + ")");
        setRefreshing(true);

        Disposable prev = sSubsRowAction;
        if (prev != null && !prev.isDisposed()) {
            prev.dispose();
        }

        final String[] ids = batch.toArray(new String[0]);
        sSubsRowAction = content.getRssFeedObserve(ids).subscribe(
                group -> {
                    final List<MediaItem> items = group != null ? group.getMediaItems() : null;
                    sIo.execute(new Runnable() {
                        @Override
                        public void run() {
                            applyResult(context, batch, items, staleMs);
                        }
                    });
                },
                error -> {
                    Log.e(TAG, "RSS error: " + error.getMessage());
                    final long now = System.currentTimeMillis();
                    sLastRoundInfo = "ostatnie skanowanie " + clock(now) + ": blad — "
                            + (error.getMessage() != null ? error.getMessage() : "nieznany");
                    sIo.execute(new Runnable() {
                        @Override
                        public void run() {
                            // Proba odnotowana, zeby rotacja szla dalej mimo bledu kanalow.
                            StPlusStore.markTried(batch, now);
                            saveStore(context, false);
                        }
                    });
                    setRefreshing(false);
                }
        );
    }

    /** Scalenie wyniku rundy z magazynem + przerysowanie wiersza + kolejna runda. */
    private static void applyResult(final Context context, List<String> batch,
                                    List<MediaItem> items, final long staleMs) {
        try {
            long now = System.currentTimeMillis();
            List<StPlusStore.Item> parsed = new ArrayList<>();
            if (items != null) {
                for (MediaItem item : items) {
                    Video video = Video.from(item);
                    if (video != null && video.videoId != null) {
                        parsed.add(StPlusStore.item(publishedMs(item), video));
                    }
                }
            }

            Map<String, List<StPlusStore.Item>> grouped = StPlusStore.group(parsed);
            StPlusStore.markTried(batch, now);
            int updated = StPlusStore.merge(grouped, now);
            saveStore(context, false);

            sLastRoundSilent = Math.max(0, batch.size() - grouped.size());
            sSilentTotal += sLastRoundSilent;
            sDebugState = sLastRoundSilent > 0
                    ? ("runda: " + grouped.size() + "/" + batch.size() + " OK")
                    : "runda OK";

            // Meldunek na karte statusu — pelnym zdaniem, zeby User widzial, CO wyszlo
            // z ostatniego skanowania (jego uwaga: "nie ma zadnego debugu, co wyszlo,
            // czy byl blad, chuj wie co bylo").
            StringBuilder info = new StringBuilder();
            info.append("ostatnie skanowanie ").append(clock(now)).append(": ");
            info.append(batch.size()).append(" kanalow, ");
            info.append(grouped.size()).append(" z materialami, ");
            info.append(parsed.size()).append(" filmow");
            if (sLastRoundSilent > 0) {
                info.append(", ").append(sLastRoundSilent).append(" bez odpowiedzi");
            }
            sLastRoundInfo = info.toString();
            Log.d(TAG, "Round done: got " + parsed.size() + " items from " + grouped.size()
                    + " channels, updated " + updated + ", silent " + sLastRoundSilent
                    + " (" + StPlusStore.stats() + ")");

            final List<Video> videos = rowVideos();
            sMain.post(new Runnable() {
                @Override
                public void run() {
                    setRefreshing(false);
                    syncStatusCard(); // sam meldunek, w miejscu — bez przerysowania wiersza
                    // Cicha rotacja NIE przerysowuje wiersza (skakanie ekranu).
                    if (!sQuietRotation && sView != null && sSection != null && !videos.isEmpty()) {
                        showRow(sContext, sView, sSection, videos, false);
                        sRowShownThisCycle = true;
                        // Reczne odswiezenie dotyczy JEDNEJ rundy. Bez tego kolko kreci
                        // sie przez caly obieg (~35 rund) i wiersz przerysowuje sie po
                        // kazdej z nich — stad zarzuty Usera o mruganie, przeladowywanie
                        // i zrzucanie na poczatek listy.
                        sQuietRotation = true;
                    }
                    scheduleNextRound(staleMs);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Apply result failed: " + e.getMessage());
            sMain.post(new Runnable() {
                @Override
                public void run() {
                    setRefreshing(false);
                }
            });
        }
    }

    /**
     * Kolejna runda rotacji — z odstępem, żeby nie dusić UI. Rotacja zatrzymuje
     * się sama, gdy wszystkie kanały są świeże.
     */
    private static void scheduleNextRound(final long staleMs) {
        final Context context = sContext;
        final BrowseView view = sView;
        final BrowseSection section = sSection;
        if (context == null || view == null || section == null) {
            return;
        }

        sIo.execute(new Runnable() {
            @Override
            public void run() {
                final int left = StPlusStore.staleCountBefore(sCycleCutoff, sCycleCutoff);
                if (left <= 0) {
                    sDebugState = "obieg zakończony";
                    sCycleRunning = false;
                    saveStore(sContext, true); // koniec obiegu = jedyny pewny moment zapisu
                    Log.d(TAG, "Cycle complete (" + StPlusStore.stats() + ")");
                    // Koniec obiegu = jedyny moment, w ktorym cicha rotacja rusza wiersz.
                    final List<Video> videos = rowVideos();
                    sMain.post(new Runnable() {
                        @Override
                        public void run() {
                            sQuietRotation = true;
                            if (sView != null && sSection != null && !videos.isEmpty()) {
                                showRow(sContext, sView, sSection, videos, false);
                                sRowShownThisCycle = true;
                            }
                        }
                    });
                    return;
                }
                Log.d(TAG, "Rotation: " + left + " channels left");
                sMain.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startRound(context, view, section, staleMs);
                    }
                }, StPlusSettings.speedProfile(context)[3]);
            }
        });
    }
}
