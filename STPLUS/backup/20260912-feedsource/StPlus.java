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
    /** Ile kanalow odswiezamy w JEDNEJ rundzie. */
    private static final int BATCH_CHANNELS = 8;
    /** Ile kanalow pobieranych rownolegle (NewPipe: PARALLEL_EXTRACTIONS = 3). */
    private static final int PARALLEL_CHANNELS = 2;
    /**
     * Odstep miedzy zapytaniami w rundzie. YouTube DLAWI serie zapytan do RSS
     * i odpowiada 404 (pomiar 2026-09-12: 140 z 149 zapytan = 404). Spokojne
     * tempo jest warunkiem, zeby feed w ogole dzialal.
     */
    private static final long CHANNEL_DELAY_MS = 700L;
    /** Kanal starszy niz to = przeterminowany (NewPipe domyslnie 5 min). */
    private static final long STALE_MS = 60 * 60 * 1000L;
    /**
     * Kanal, ktory jeszcze NIC nie zwrocil (np. dostal 404 od dlawienia), wraca
     * do kolejki szybciej — inaczej jeden nieudany obieg kasuje go na godzine.
     */
    private static final long EMPTY_RETRY_MS = 5 * 60 * 1000L;
    /** Odstep miedzy rundami rotacji — spokojnie, zeby nie dostac 404. */
    private static final long NEXT_ROUND_DELAY_MS = 6000L;
    /** Po tylu ms uznajemy odswiezanie za martwe i pozwalamy ruszyc od nowa. */
    private static final long REFRESH_DEAD_MS = 90 * 1000L;

    /**
     * TRYB DEBUGOWY (zyczenie Usera 2026-09-12): naglowek wiersza pokazuje stan
     * zbierania (ile kanalow ma dane / ile jest wszystkich, ile filmow, ile zostalo
     * do obiegu, ile kanalow odmowilo), a pod kazdym filmem jest czas publikacji
     * ("2 godz. temu"). Docelowo przelacznik w naszych ustawieniach (K15).
     */
    private static final boolean DEBUG_LINE = true;

    /** videoId sztucznej karty "Ładowanie…" na końcu wiersza. */
    private static final String LOADING_ITEM_ID = "stplus_loading";

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
        if (sContext == null || sView == null || sSection == null) {
            return;
        }
        // Reczny refresh: widoczny (kolko w belce) i od razu odswieza wiersz po rundzie.
        sQuietRotation = false;
        startRound(sContext, sView, sSection, 0L);
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
        return sb.toString();
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
    /** Naglowek wiersza — z linia debugowa, gdy tryb debugowy wlaczony. */
    private static String rowTitle(Context context) {
        String base = context.getString(R.string.stplus_subscriptions_row);
        if (!DEBUG_LINE || !sStoreReady) {
            return base;
        }
        int all = StPlusStore.channelCount();
        int withData = StPlusStore.withDataCount();
        int items = StPlusStore.itemCount();
        int left = StPlusStore.staleCount(STALE_MS, EMPTY_RETRY_MS, System.currentTimeMillis());
        // Naglowek wiersza ma DUZA czcionke i malo miejsca (uwaga Usera 2026-09-12,
        // na tablecie tekst nie miescil sie na ekranie) — skrot zamiast zdania.
        // Format: "Twoje kanały · 6/173 · 90f · !12"
        //   6/173 = kanaly z danymi / wszystkie, 90f = filmow w magazynie,
        //   !12   = kanaly bez odpowiedzi (dlawienie YouTube), pomijane gdy 0.
        StringBuilder sb = new StringBuilder(base);
        sb.append(" · ").append(withData).append('/').append(all);
        sb.append(" · ").append(items).append('f');
        if (left > 0) {
            sb.append(" · ↻").append(left);
        }
        if (sSilentTotal > 0) {
            sb.append(" · !").append(sSilentTotal);
        }
        return sb.toString();
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
        List<StPlusStore.Item> items = StPlusStore.topItems(MAX_ITEMS);
        List<Video> result = new ArrayList<>();
        for (StPlusStore.Item item : items) {
            stampTime(item);
            result.add(item.video);
        }
        return result;
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
        // Wiersze zostaly wyczyszczone przez home — nasza sygnatura juz nie opisuje ekranu.
        sShownSignature = null;

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
            renderFromStore(false);
            sQuietRotation = true; // rotacja w tle jest niewidoczna dla Usera
            startRound(sContext, sView, sSection, STALE_MS);
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

                    long now = System.currentTimeMillis();
                    final List<String> batch = StPlusStore.pickStale(staleMs, Math.min(staleMs, EMPTY_RETRY_MS), BATCH_CHANNELS, now);
                    if (batch.isEmpty()) {
                        Log.d(TAG, "All channels fresh (" + StPlusStore.stats() + ") - nothing to do");
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

        // Wzorzec NewPipe: tylko RSS, 3 kanaly rownolegle.
        RssOptions.skipChannelSync = true;
        RssOptions.maxParallelChannels = PARALLEL_CHANNELS;
        RssOptions.delayBetweenChannelsMs = CHANNEL_DELAY_MS;

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
                    sIo.execute(new Runnable() {
                        @Override
                        public void run() {
                            // Proba odnotowana, zeby rotacja szla dalej mimo bledu kanalow.
                            StPlusStore.markTried(batch, now);
                            StPlusStore.save(context);
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
                        parsed.add(StPlusStore.item(item.getPublishedDate(), video));
                    }
                }
            }

            Map<String, List<StPlusStore.Item>> grouped = StPlusStore.group(parsed);
            StPlusStore.markTried(batch, now);
            int updated = StPlusStore.merge(grouped, now);
            StPlusStore.save(context);

            sLastRoundSilent = Math.max(0, batch.size() - grouped.size());
            sSilentTotal += sLastRoundSilent;
            sDebugState = sLastRoundSilent > 0
                    ? ("runda: " + grouped.size() + "/" + batch.size() + " OK")
                    : "runda OK";
            Log.d(TAG, "Round done: got " + parsed.size() + " items from " + grouped.size()
                    + " channels, updated " + updated + ", silent " + sLastRoundSilent
                    + " (" + StPlusStore.stats() + ")");

            final List<Video> videos = rowVideos();
            sMain.post(new Runnable() {
                @Override
                public void run() {
                    setRefreshing(false);
                    // Cicha rotacja NIE przerysowuje wiersza (skakanie ekranu).
                    if (!sQuietRotation && sView != null && sSection != null && !videos.isEmpty()) {
                        showRow(sContext, sView, sSection, videos, false);
                        sRowShownThisCycle = true;
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
                final int left = StPlusStore.staleCount(staleMs, Math.min(staleMs, EMPTY_RETRY_MS), System.currentTimeMillis());
                if (left <= 0) {
                    sDebugState = "obieg zakończony";
                    Log.d(TAG, "Rotation complete (" + StPlusStore.stats() + ")");
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
                }, NEXT_ROUND_DELAY_MS);
            }
        });
    }
}
