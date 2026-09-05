package com.liskovsoft.smartyoutubetv2.common.stplus;

/*
 * >>> STPLUS — SmartTube+ plugin code.
 * Ten plik jest NASZ (nie istnieje w upstreamie). Pełna lista zmian:
 * STPLUS/MODYFIKACJE.md w korzeniu repozytorium.
 *
 * Funkcja 1: na stronie głównej (Home) wstawia dodatkowy rząd "Twoje kanały"
 * jako PIERWSZY wiersz (pozycja 0). Rząd zawiera najnowsze materiały
 * z KANAŁÓW Z LOKALNEJ LISTY SUBSKRYPCJI (np. zaimportowanej z NewPipe).
 *
 * WAŻNE: działa BEZ logowania do YouTube (prywatność — sens SmartTube).
 * Zamiast getSubscriptionsObserve() (wymaga konta) bierzemy lokalne grupy
 * kanałów (ChannelGroupService) i ich publiczny RSS (getRssFeedObserve).
 *
 * CACHE-FIRST (2026-09-05): wiersz pojawia się NATYCHMIAST z cache
 * (SharedPreferences "stplus"), a odświeżenie RSS leci w tle i podmienia
 * zawartość dopiero gdy się skończy. Stan odświeżania idzie do wskaźnika
 * w górnej belce (ikonka obraca się / jest przyciskiem ręcznego odświeżenia).
 * <<< STPLUS
 */

import android.content.Context;
import android.content.SharedPreferences;
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
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

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
    /** Ile maksymalnie pozycji trzymamy w pamieci/cache (zapas do dosypywania). */
    private static final int MAX_ITEMS = 200;
    private static final int MAX_CHANNELS = 50;

    private static final String PREFS_NAME = "stplus";
    private static final String KEY_ROW_CACHE = "subs_row_cache";
    private static final String KEY_ROW_TIME = "subs_row_time";

    private static Disposable sSubsRowAction;
    private static boolean sRefreshing;
    /** Czy cache zostal juz wstawiony w tym cyklu ladowania home. */
    private static boolean sCacheShown;
    /** Pelna lista pobranych materialow (zapas do dosypywania przy przewijaniu). */
    private static final List<Video> sAllVideos = new ArrayList<>();
    /** Ile pozycji z sAllVideos jest juz w wierszu. */
    private static int sShownCount;

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
            listener.onRefreshState(sRefreshing);
        }
    }

    public static boolean isRefreshing() {
        return sRefreshing;
    }

    /** Ręczne odświeżenie (kliknięcie ikonki w belce). */
    public static void requestManualRefresh() {
        if (sContext == null || sView == null || sSection == null || sRefreshing) {
            return;
        }
        fetchFeed(sContext, sView, sSection);
    }

    private static void setRefreshing(boolean refreshing) {
        sRefreshing = refreshing;
        if (sRefreshListener != null) {
            sRefreshListener.onRefreshState(refreshing);
        }
    }

    // -------------------------------------------------------------- cache ---

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static List<Video> loadCache(Context context) {
        List<Video> result = new ArrayList<>();
        try {
            String data = prefs(context).getString(KEY_ROW_CACHE, null);
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
        } catch (Exception e) {
            Log.e(TAG, "Cache read failed: " + e.getMessage());
        }
        return result;
    }

    private static void saveCache(Context context, List<Video> videos) {
        try {
            String[] specs = new String[videos.size()];
            for (int i = 0; i < videos.size(); i++) {
                specs[i] = videos.get(i).toString();
            }
            prefs(context).edit()
                    .putString(KEY_ROW_CACHE, Helpers.mergeArray((Object[]) specs))
                    .putLong(KEY_ROW_TIME, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Cache write failed: " + e.getMessage());
        }
    }

    /** Sygnatura listy — pozwala pominąć podmianę, gdy nic się nie zmieniło. */
    private static String signature(List<Video> videos) {
        StringBuilder sb = new StringBuilder();
        for (Video video : videos) {
            sb.append(video.videoId).append('|');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- row ---

    private static void showRow(Context context, BrowseView view, BrowseSection section, List<Video> videos) {
        if (videos.isEmpty()) {
            return;
        }

        sAllVideos.clear();
        sAllVideos.addAll(videos);
        sShownCount = Math.min(PAGE_SIZE, videos.size());

        VideoGroup row = VideoGroup.from(new ArrayList<>(videos.subList(0, sShownCount)), section, SUBS_ROW_POSITION);
        row.setId(SUBS_ROW_ID);
        row.setTitle(context.getString(R.string.stplus_subscriptions_row));
        // REPLACE: podmienia zawartość rzędu w miejscu (usuwa stary po ID,
        // wstawia nowy na pozycji 0) — bez duplikatów przy odświeżeniu.
        row.setAction(VideoGroup.ACTION_REPLACE);
        view.updateSection(row);
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

        if (sContext == null || sView == null || sSection == null || sShownCount >= sAllVideos.size()) {
            return true;
        }

        int end = Math.min(sShownCount + PAGE_CHUNK, sAllVideos.size());
        List<Video> chunk = new ArrayList<>(sAllVideos.subList(sShownCount, end));
        sShownCount = end;

        VideoGroup more = VideoGroup.from(chunk, sSection, SUBS_ROW_POSITION);
        more.setId(SUBS_ROW_ID);
        more.setTitle(sContext.getString(R.string.stplus_subscriptions_row));
        more.setAction(VideoGroup.ACTION_APPEND);
        sView.updateSection(more);

        Log.d(TAG, "Row page appended: " + chunk.size() + " (shown " + sShownCount + "/" + sAllVideos.size() + ")");
        return true;
    }

    /**
     * Hook wywolywany NA STARCIE ladowania home (zaraz po wyczyszczeniu rzedow,
     * jeszcze przed odpowiedzia z sieci). Wstawia wiersz z cache, zeby byl
     * widoczny OD RAZU — bez czekania na zaladowanie domyslnych rzedow.
     * Domyslne rzedy dopisuja sie ponizej (maja position = -1 => na koniec).
     */
    public static void onHomeLoadStarted(Context context, BrowseView view, BrowseSection section) {
        if (context == null || view == null || section == null) {
            return;
        }

        sContext = context.getApplicationContext();
        sView = view;
        sSection = section;
        sCacheShown = false;

        try {
            List<Video> cached = loadCache(context);
            if (!cached.isEmpty()) {
                Log.d(TAG, "Row from cache (early): " + cached.size() + " items");
                showRow(context, view, section, cached);
                sCacheShown = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Early cache row failed: " + e.getMessage());
        }
    }

    /**
     * Hook wywoływany z BrowsePresenter.updateVideoRows() PO pętli domyślnych
     * wierszy home. Najpierw pokazuje cache (natychmiast), potem odświeża w tle.
     * Bezpieczny: nie rzuca wyjątków w górę.
     */
    public static void onHomeRowsLoaded(Context context, BrowseView view, BrowseSection section) {
        if (context == null || view == null || section == null) {
            return;
        }

        sContext = context.getApplicationContext();
        sView = view;
        sSection = section;

        try {
            if (!sCacheShown) {
                List<Video> cached = loadCache(context);
                if (!cached.isEmpty()) {
                    Log.d(TAG, "Row from cache (late): " + cached.size() + " items");
                    showRow(context, view, section, cached);
                }
            }
            sCacheShown = false;

            fetchFeed(context, view, section);
        } catch (Exception e) {
            // Hook nigdy nie może zepsuć normalnego ładowania home
            Log.e(TAG, "StPlus hook failed: " + e.getMessage());
        }
    }

    /** Dodaje ID kanałów z grupy do listy (do limitu MAX_CHANNELS). */
    private static void addChannelIds(ItemGroup group, List<String> channelIds) {
        if (group == null || channelIds.size() >= MAX_CHANNELS) {
            return;
        }
        List<ItemGroup.Item> items = group.getItems();
        if (items == null) {
            return;
        }
        for (ItemGroup.Item item : items) {
            if (channelIds.size() >= MAX_CHANNELS) {
                break;
            }
            String id = item.getChannelId();
            if (id != null && !id.isEmpty() && !channelIds.contains(id)) {
                channelIds.add(id);
            }
        }
    }

    /** Pobranie RSS w tle. Cache zostaje na ekranie aż do końca pobierania. */
    private static void fetchFeed(Context context, BrowseView view, BrowseSection section) {
        try {
            ChannelGroupService groups = YouTubeServiceManager.instance().getChannelGroupService();
            if (groups == null || groups.isEmpty()) {
                Log.d(TAG, "No local channel groups - skip refresh");
                return;
            }

            List<String> channelIds = new ArrayList<>();
            addChannelIds(groups.findChannelGroupById(SUBSCRIPTIONS_GROUP_ID), channelIds);
            List<ItemGroup> allGroups = groups.getChannelGroups();
            if (allGroups != null) {
                for (ItemGroup group : allGroups) {
                    addChannelIds(group, channelIds);
                    if (channelIds.size() >= MAX_CHANNELS) {
                        break;
                    }
                }
            }

            if (channelIds.isEmpty()) {
                Log.d(TAG, "No channel ids - skip refresh");
                return;
            }

            ContentService content = YouTubeServiceManager.instance().getContentService();
            if (content == null) {
                return;
            }

            Disposable prev = sSubsRowAction;
            if (prev != null && !prev.isDisposed()) {
                prev.dispose();
            }

            Log.d(TAG, "Refreshing RSS for " + channelIds.size() + " channels");
            setRefreshing(true);

            final String[] ids = channelIds.toArray(new String[0]);
            sSubsRowAction = content.getRssFeedObserve(ids).subscribe(
                    group -> {
                        try {
                            List<Video> videos = new ArrayList<>();
                            List<MediaItem> items = group != null ? group.getMediaItems() : null;
                            if (items != null) {
                                for (MediaItem item : items) {
                                    if (videos.size() >= MAX_ITEMS) {
                                        break;
                                    }
                                    Video video = Video.from(item);
                                    if (video != null && video.videoId != null) {
                                        videos.add(video);
                                    }
                                }
                            }

                            if (videos.isEmpty()) {
                                Log.d(TAG, "RSS empty - keep cache");
                                return;
                            }

                            String fresh = signature(videos);
                            String old = signature(loadCache(context));
                            saveCache(context, videos);

                            if (fresh.equals(old)) {
                                Log.d(TAG, "RSS unchanged - keep row as is");
                                return;
                            }

                            showRow(context, view, section, videos);
                            Log.d(TAG, "Row refreshed: " + videos.size() + " items");
                        } finally {
                            setRefreshing(false);
                        }
                    },
                    error -> {
                        Log.e(TAG, "RSS error: " + error.getMessage());
                        setRefreshing(false);
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Refresh failed: " + e.getMessage());
            setRefreshing(false);
        }
    }
}
