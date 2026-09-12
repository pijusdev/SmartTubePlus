package com.liskovsoft.smartyoutubetv2.common.stplus;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.youtubeapi.rss.RssOptions;

import java.util.ArrayList;
import java.util.List;

/*
 * >>> STPLUS — SmartTube+ plugin code (plik NASZ, nie istnieje w upstreamie).
 *
 * Nasze wlasne, male menu ustawien — otwierane zebatka na belce (zyczenie Usera,
 * 2026-09-12): "na koncu tej linii po prawej, gornej (...) bedzie mala zebatka.
 * Jak ja klikujemy, to bedzie pojawiac nasze menu jakies takie male z naszymi
 * ustawieniami. I tam bedzie mozna np. wlaczyc to debugowanie itp. Moze bedzie
 * tez przycisk informacja, ktory wyswietli nasz cel i co robimy. I ten projekt
 * bedzie na Githubie pozniej, wiec te informacje tez moga byc od razu po angielsku."
 *
 * Dlatego tekst informacyjny jest PO ANGIELSKU — ma sie nadawac wprost do README
 * repozytorium, bez tlumaczenia.
 *
 * Ustawienia trzymamy we wlasnym pliku prefs "stplus" (nie ruszamy GeneralData
 * upstreama — to by byl hak w cudzej klasie i przepadlby przy aktualizacji).
 * <<< STPLUS
 */
public final class StPlusSettings {
    private static final String PREFS = "stplus";
    private static final String KEY_DEBUG = "stplus_debug_line";
    private static final String KEY_FEED_SOURCE = "stplus_feed_source";
    private static final String KEY_REFRESH_EVERY = "stplus_refresh_every_min";
    private static final String KEY_SPEED = "stplus_speed";
    private static final String KEY_SHORTS = "stplus_show_shorts";

    private static Boolean sDebugLine;
    private static Integer sFeedSource;
    private static Integer sRefreshEveryMin;
    private static Integer sSpeed;
    private static Boolean sShowShorts;

    private StPlusSettings() {
    }

    // ------------------------------------------------------------- stan ---

    /** Czy pokazywac karte statusu i czas publikacji pod filmami. */
    public static boolean isDebugLine(Context context) {
        if (sDebugLine == null && context != null) {
            sDebugLine = prefs(context).getBoolean(KEY_DEBUG, true);
        }
        return sDebugLine != null ? sDebugLine : true;
    }

    public static void setDebugLine(Context context, boolean enabled) {
        sDebugLine = enabled;
        if (context != null) {
            prefs(context).edit().putBoolean(KEY_DEBUG, enabled).apply();
        }
    }

    /** Zrodlo feedu — patrz RssOptions.SOURCE_*. */
    public static int getFeedSource(Context context) {
        if (sFeedSource == null && context != null) {
            sFeedSource = prefs(context).getInt(KEY_FEED_SOURCE, RssOptions.SOURCE_AUTO);
        }
        return sFeedSource != null ? sFeedSource : RssOptions.SOURCE_AUTO;
    }

    public static void setFeedSource(Context context, int source) {
        sFeedSource = source;
        RssOptions.feedSource = source;
        if (context != null) {
            prefs(context).edit().putInt(KEY_FEED_SOURCE, source).apply();
        }
    }

    /**
     * Co ile minut wolno ruszyc NOWY obieg skanowania. 0 = tylko recznie.
     * Domyslnie 60 min — przy starcie aplikacji obieg leci zawsze (bo wtedy
     * `sCycleStarted` jest zerowe), potem juz wedlug tego interwalu.
     */
    public static int getRefreshEveryMin(Context context) {
        if (sRefreshEveryMin == null && context != null) {
            sRefreshEveryMin = prefs(context).getInt(KEY_REFRESH_EVERY, 60);
        }
        return sRefreshEveryMin != null ? sRefreshEveryMin : 60;
    }

    public static long getRefreshIntervalMs(Context context) {
        return getRefreshEveryMin(context) * 60L * 1000L;
    }

    public static void setRefreshEveryMin(Context context, int minutes) {
        sRefreshEveryMin = minutes;
        if (context != null) {
            prefs(context).edit().putInt(KEY_REFRESH_EVERY, minutes).apply();
        }
    }

    /*
     * PREDKOSC POBIERANIA — regulowana, bo "madrze" nie znaczy ani "na maksa", ani
     * "na zderno" (User, 2026-09-12: "pobieramy szybko jak sie da, ale wydajnie jak sie
     * da (...) madrze jest tylko optymalnie, pod wieloma parametrami").
     *
     * Trzy profile. Liczby to: ile kanalow w rundzie / ile rownolegle / odstep miedzy
     * zapytaniami / przerwa miedzy rundami.
     */
    public static final int SPEED_GENTLE = 0;   // slabe urzadzenia, tlo
    public static final int SPEED_NORMAL = 1;   // domyslne
    public static final int SPEED_FAST = 2;     // mocniejszy sprzet / szybkie lacze

    public static int getSpeed(Context context) {
        if (sSpeed == null && context != null) {
            sSpeed = prefs(context).getInt(KEY_SPEED, SPEED_NORMAL);
        }
        return sSpeed != null ? sSpeed : SPEED_NORMAL;
    }

    public static void setSpeed(Context context, int speed) {
        sSpeed = speed;
        if (context != null) {
            prefs(context).edit().putInt(KEY_SPEED, speed).apply();
        }
    }

    /** {kanalow w rundzie, rownolegle, odstep ms, przerwa miedzy rundami ms} */
    public static int[] speedProfile(Context context) {
        switch (getSpeed(context)) {
            case SPEED_GENTLE:
                return new int[] {5, 2, 1200, 8000};
            case SPEED_FAST:
                return new int[] {14, 4, 350, 2500};
            case SPEED_NORMAL:
            default:
                return new int[] {10, 3, 600, 5000};
        }
    }

    /**
     * Czy wpuszczac shorty do wiersza. DOMYSLNIE NIE (zyczenie Usera 2026-09-12).
     *
     * Rozpoznawanie shorta bez dodatkowego zapytania jest heurystyczne: sciezka
     * "zakladki kanalu" oznacza je sama (Video.isShorts), a w RSS jedynym sygnalem
     * jest tag w tytule (#shorts). Dlatego filtr wylapie wiekszosc, ale nie wszystko —
     * i tak jest napisane w menu, zeby nikt nie liczyl na komplet.
     */
    public static boolean isShowShorts(Context context) {
        if (sShowShorts == null && context != null) {
            sShowShorts = prefs(context).getBoolean(KEY_SHORTS, false);
        }
        return sShowShorts != null ? sShowShorts : false;
    }

    public static void setShowShorts(Context context, boolean show) {
        sShowShorts = show;
        if (context != null) {
            prefs(context).edit().putBoolean(KEY_SHORTS, show).apply();
        }
    }

    /** Wczytanie zapisanych ustawien przy starcie (wola StPlus). */
    public static void load(Context context) {
        RssOptions.feedSource = getFeedSource(context);
        isDebugLine(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ dialog ---

    /** Nasze male menu — otwierane zebatka. */
    public static void show(Context context) {
        if (context == null) {
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        appendShortsSwitch(context, dialog);
        appendDebugSwitch(context, dialog);
        appendScheduleCategory(context, dialog);
        appendSpeedCategory(context, dialog);
        appendFeedSourceCategory(context, dialog);
        appendRefreshButton(context, dialog);
        appendDebugSection(context, dialog);
        appendAboutButton(context, dialog);

        dialog.showDialog("SmartTube+");
    }

    private static void appendShortsSwitch(Context context, AppDialogPresenter dialog) {
        dialog.appendSingleSwitch(UiOptionItem.from(
                "Pokazuj shorty w wierszu (rozpoznawane po tagu w tytule)",
                option -> {
                    setShowShorts(context, option.isSelected());
                    StPlus.onFilterChanged();
                },
                isShowShorts(context)));
    }

    private static void appendDebugSwitch(Context context, AppDialogPresenter dialog) {
        dialog.appendSingleSwitch(UiOptionItem.from(
                "Tryb debugowania (karta stanu + czas publikacji)",
                option -> setDebugLine(context, option.isSelected()),
                isDebugLine(context)));
    }

    /*
     * Kiedy skanowac. Model jest prosty i jawny: obieg rusza PRZY STARCIE aplikacji,
     * a potem nie czesciej niz co wybrany czas. Wejscie na strone glowna samo z siebie
     * NIE skanuje — wczesniej skanowalo i dlatego aplikacja mielila bez konca.
     */
    private static void appendScheduleCategory(Context context, AppDialogPresenter dialog) {
        List<OptionItem> items = new ArrayList<>();
        int current = getRefreshEveryMin(context);

        int[] minutes = {0, 30, 60, 180, 360};
        String[] labels = {
                "Tylko recznie (przycisk odswiez)",
                "Przy starcie i nie czesciej niz co 30 min",
                "Przy starcie i nie czesciej niz co 1 h",
                "Przy starcie i nie czesciej niz co 3 h",
                "Przy starcie i nie czesciej niz co 6 h"
        };

        for (int i = 0; i < minutes.length; i++) {
            final int value = minutes[i];
            items.add(UiOptionItem.from(labels[i],
                    option -> setRefreshEveryMin(context, value),
                    current == value));
        }

        dialog.appendRadioCategory("Kiedy odswiezac (jeden obieg = wszystkie kanaly)", items);
    }

    private static void appendSpeedCategory(Context context, AppDialogPresenter dialog) {
        List<OptionItem> items = new ArrayList<>();
        int current = getSpeed(context);

        items.add(UiOptionItem.from(
                "Oszczedna — ok. 35 kanalow/min, najmniej obciaza slaby sprzet",
                option -> setSpeed(context, SPEED_GENTLE), current == SPEED_GENTLE));

        items.add(UiOptionItem.from(
                "Normalna — ok. 75 kanalow/min (zalecana)",
                option -> setSpeed(context, SPEED_NORMAL), current == SPEED_NORMAL));

        items.add(UiOptionItem.from(
                "Szybka — ok. 150 kanalow/min, mocniej obciaza procesor",
                option -> setSpeed(context, SPEED_FAST), current == SPEED_FAST));

        dialog.appendRadioCategory("Predkosc pobierania", items);
    }

    private static void appendFeedSourceCategory(Context context, AppDialogPresenter dialog) {
        List<OptionItem> items = new ArrayList<>();
        int current = getFeedSource(context);

        // Liczby przy opcjach sa z POMIARU na tablecie (SM-T580, armv7), nie z sufitu.
        // RSS: zmierzone 2026-09-12. Zakladki kanalu: jeszcze nie zmierzone na pelnym
        // obiegu — dlatego napisane wprost "szacunkowo", zeby nikt tego nie wzial za fakt.
        items.add(UiOptionItem.from(
                "Automatycznie — ok. 75 kanalow/min, pelna lista w ok. 2,5 min\n"
                        + "Najpierw RSS, a gdy odmowi, zakladki kanalu. Zalecane.",
                option -> setFeedSource(context, RssOptions.SOURCE_AUTO),
                current == RssOptions.SOURCE_AUTO));

        items.add(UiOptionItem.from(
                "Tylko RSS — ok. 75 kanalow/min, pelna lista w ok. 2,5 min\n"
                        + "Najtansze, z dokladna data publikacji. Po 15 filmow z kanalu.\n"
                        + "Gdy YouTube zacznie odmawiac (blad 404), lista stoi w miejscu.",
                option -> setFeedSource(context, RssOptions.SOURCE_RSS),
                current == RssOptions.SOURCE_RSS));

        items.add(UiOptionItem.from(
                "Tylko zakladki kanalu — szacunkowo ok. 20-25 kanalow/min\n"
                        + "Wolniejsze i ciezsze (duzy JSON zamiast malego XML), za to nie\n"
                        + "zalezy od RSS. Czas publikacji tylko przyblizony.",
                option -> setFeedSource(context, RssOptions.SOURCE_BROWSE),
                current == RssOptions.SOURCE_BROWSE));

        dialog.appendRadioCategory("Skąd pobierać materiały kanałów (tempo zmierzone na tablecie)", items);
    }

    private static void appendRefreshButton(Context context, AppDialogPresenter dialog) {
        dialog.appendSingleButton(UiOptionItem.from(
                "Odśwież teraz (zeruje licznik błędów)",
                option -> StPlus.requestManualRefresh()));
    }

    /*
     * Sekcja debugu — do TESTOWANIA NAPELNIANIA feedu bez odinstalowywania aplikacji
     * (zyczenie Usera 2026-09-12). Kasowanie materialow zeruje tez znaczniki kanalow,
     * wiec rotacja od razu po nie wraca i widac caly przebieg od zera.
     */
    private static void appendDebugSection(Context context, AppDialogPresenter dialog) {
        List<OptionItem> items = new ArrayList<>();

        items.add(UiOptionItem.from("Zapomnij ok. 30 filmow (test napelniania)",
                option -> forget(context, 30)));

        items.add(UiOptionItem.from("Zapomnij ok. 200 filmow",
                option -> forget(context, 200)));

        dialog.appendStringsCategory("Debug — testowanie napelniania", items);
    }

    /** @param count ile filmow zapomniec; -1 = wszystko. */
    private static void forget(Context context, int count) {
        int removed = count < 0 ? StPlusStore.forgetAll() : StPlusStore.forgetItems(count);
        StPlusStore.save(context);
        StPlus.onStoreCleared();
        MessageHelpers.showMessage(context, "SmartTube+: zapomniane " + removed
                + " filmow. Odswiezam od nowa...");
        StPlus.requestManualRefresh();
    }

    private static void appendAboutButton(Context context, AppDialogPresenter dialog) {
        dialog.appendLongTextCategory("About SmartTube+", UiOptionItem.from(about()));
    }

    /**
     * Opis projektu PO ANGIELSKU — celowo, bo repozytorium idzie na GitHub i ten
     * tekst ma sie nadawac wprost do README (zyczenie Usera, 2026-09-12).
     */
    private static String about() {
        return "WHAT THIS IS\n"
                + "SmartTube+ is a small fork of SmartTube. It adds a \"Your channels\" row "
                + "on the home screen that works WITHOUT signing in to YouTube.\n\n"
                + "WHY\n"
                + "Signing in is not required to follow channels. SmartTube+ reads the local "
                + "channel list you already have in the app (importable from NewPipe, PocketTube "
                + "or GrayJay) and builds a newest-first feed from it. No account, no tracking, "
                + "no subscriptions stored on Google's side.\n\n"
                + "HOW THE FEED WORKS\n"
                + "Channels are refreshed in rotation: each round takes a few of the least "
                + "recently checked channels, so the app never floods YouTube with a burst of "
                + "requests. Results are merged into a per-channel store, never overwritten "
                + "wholesale, so a channel that fails to answer keeps the items it already had. "
                + "The row is drawn from that store instantly, before any network call.\n\n"
                + "TWO SOURCES\n"
                + "Items come from the channel RSS feed when it answers, and from the channel's "
                + "video tab when it does not. RSS is cheap and carries real publish timestamps; "
                + "the channel tab is heavier but keeps working when RSS refuses. You can force "
                + "either one in the settings above.\n\n"
                + "A NOTE ON RATE LIMITS\n"
                + "Asking YouTube for many channel feeds too quickly gets you HTTP 404 on nearly "
                + "every request - not because the channels are gone, but as throttling. If that "
                + "happens, slow down and wait; it clears. This build paces itself deliberately.\n\n"
                + "GOAL\n"
                + "To show that a private, fast subscription feed can be added to SmartTube with "
                + "a small patch. If this lands upstream, the fork stops being necessary - which "
                + "would be the best outcome.\n\n"
                + "Based on SmartTube by yuliskov. MIT licensed.";
    }
}
