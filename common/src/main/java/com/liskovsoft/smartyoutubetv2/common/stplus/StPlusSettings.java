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
 * Nasze wlasne, male menu ustawien — otwierane zebatka przy naglowku wiersza
 * "Twoje kanaly" (zyczenie Usera, 2026-09-12): "na koncu tej linii po prawej, gornej
 * (...) bedzie mala zebatka. Jak ja klikujemy, to bedzie pojawiac nasze menu jakies
 * takie male z naszymi ustawieniami."
 *
 * Teksty ida przez StPlusText — menu ma przelacznik POLSKI / ANGIELSKI, bo projekt
 * idzie na GitHub i ma byc uzywalny poza Polska.
 *
 * Ustawienia trzymamy we wlasnym pliku prefs "stplus" (nie ruszamy GeneralData
 * upstreama — to by byl hak w cudzej klasie i przepadlby przy aktualizacji).
 * <<< STPLUS
 */
public final class StPlusSettings {
    private static final String PREFS = "stplus";
    private static final String KEY_DEBUG = "stplus_debug_line";
    private static final String KEY_STATUS_CARD = "stplus_status_card";
    private static final String KEY_FEED_SOURCE = "stplus_feed_source";
    private static final String KEY_REFRESH_EVERY = "stplus_refresh_every_min";
    private static final String KEY_SPEED = "stplus_speed";
    private static final String KEY_SHORTS = "stplus_show_shorts";
    private static final String KEY_LANG = "stplus_lang";

    private static Boolean sDebugLine;
    private static Boolean sStatusCard;
    private static Integer sFeedSource;
    private static Integer sRefreshEveryMin;
    private static Integer sSpeed;
    private static Boolean sShowShorts;
    private static Integer sLang;

    private StPlusSettings() {
    }

    // ------------------------------------------------------------- stan ---

    /**
     * Tryb debugowania = SZCZEGOLY TECHNICZNE: opis przy prawej krawedzi naglowka
     * oraz rozszerzony meldunek na karcie stanu (co zrobila ostatnia runda, z jakiego
     * zrodla, jakie kody odpowiedzi).
     *
     * UWAGA: to NIE jest przelacznik samej karty — karta ma wlasny (KEY_STATUS_CARD).
     * Byly razem do 2026-09-12 i User slusznie to zgloszil: wylaczyl debugowanie
     * i zniknela mu tez ikona SmartTube+ z poczatku wiersza, ktora lubi.
     */
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
        StPlus.onCardSettingsChanged();
    }

    /**
     * Karta stanu na poczatku wiersza (ta z ikona SmartTube+).
     *
     * Domyslnie WLACZONA. User (2026-09-12): "nie wiedzialem, ze ta ikona smart tube
     * zniknie jak wylaczamy debugowanie. To zrob do tego osobny przelacznik, bo ta
     * ikona pierwsza ze smart tube jest spoko."
     *
     * Uwaga: przy pustym wierszu karta pojawia sie ZAWSZE, niezaleznie od tego
     * przelacznika — inaczej leanback nie zrobilby wiersza w ogole, a wiersz
     * "Twoje kanaly" ma byc widoczny zawsze.
     */
    public static boolean isStatusCard(Context context) {
        if (sStatusCard == null && context != null) {
            sStatusCard = prefs(context).getBoolean(KEY_STATUS_CARD, true);
        }
        return sStatusCard != null ? sStatusCard : true;
    }

    public static void setStatusCard(Context context, boolean enabled) {
        sStatusCard = enabled;
        if (context != null) {
            prefs(context).edit().putBoolean(KEY_STATUS_CARD, enabled).apply();
        }
        StPlus.onCardSettingsChanged();
    }

    /** Jezyk NASZYCH tekstow — patrz StPlusText. */
    public static int getLang(Context context) {
        if (sLang == null && context != null) {
            sLang = prefs(context).getInt(KEY_LANG, StPlusText.LANG_AUTO);
        }
        return sLang != null ? sLang : StPlusText.LANG_AUTO;
    }

    public static void setLang(Context context, int lang) {
        sLang = lang;
        if (context != null) {
            prefs(context).edit().putInt(KEY_LANG, lang).apply();
        }
        StPlusText.apply(context, lang);
        StPlus.onCardSettingsChanged();
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
     * PATRZ OPIS W MENU — filtr dziala tylko wtedy, gdy materialy przyszly ze sciezki
     * "zakladki kanalu". Kanal RSS YouTube'a nie niesie ani znacznika shorta, ani czasu
     * trwania, wiec przy zrodle RSS nie ma czego filtrowac. To jest ograniczenie danych,
     * nie usterka przelacznika — i jest napisane wprost w menu, zeby nikt sie nie zastanawial.
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
        StPlusText.apply(context, getLang(context));
        isDebugLine(context);
        isStatusCard(context);
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
        appendStatusCardSwitch(context, dialog);
        appendDebugSwitch(context, dialog);
        appendScheduleCategory(context, dialog);
        appendSpeedCategory(context, dialog);
        appendFeedSourceCategory(context, dialog);
        appendLanguageCategory(context, dialog);
        appendRefreshButton(context, dialog);
        appendDebugSection(context, dialog);
        appendAboutButton(context, dialog);

        dialog.showDialog("SmartTube+");
    }

    /*
     * Opis mowi PRAWDE o ograniczeniu, zamiast udawac, ze filtr dziala zawsze.
     * User zglosil (2026-09-12): "dlaczego dalej pojawiaja mi sie shorty w pierwszej
     * linii, jak mam je wylaczone" — i mial racje, bo z RSS-a nie ma czego rozpoznac.
     * Jego polecenie: "trzeba to zaznaczyc w ustawieniach naszych, zeby bylo wiadomo,
     * ze jest taki problem".
     */
    private static void appendShortsSwitch(Context context, AppDialogPresenter dialog) {
        dialog.appendSingleSwitch(UiOptionItem.from(
                StPlusText.t(
                        "Pokazuj shorty w wierszu\n"
                                + "UWAGA: przy źródle RSS filtr prawie nie działa. Kanał RSS YouTube'a\n"
                                + "nie podaje ani znacznika shorta, ani czasu trwania — rozpoznajemy je\n"
                                + "tylko po tagu #shorts w tytule, a autorzy rzadko go wpisują.\n"
                                + "Pełne odsianie shortów działa przy źródle \"tylko zakładki kanału\".",
                        "Show Shorts in the row\n"
                                + "NOTE: with the RSS source this filter barely works. YouTube's channel RSS\n"
                                + "carries neither a Shorts flag nor a duration, so we can only match the\n"
                                + "#shorts tag in the title, which authors rarely add.\n"
                                + "Reliable filtering needs the \"channel tab only\" source."),
                option -> {
                    setShowShorts(context, option.isSelected());
                    StPlus.onFilterChanged();
                },
                isShowShorts(context)));
    }

    private static void appendStatusCardSwitch(Context context, AppDialogPresenter dialog) {
        dialog.appendSingleSwitch(UiOptionItem.from(
                StPlusText.t(
                        "Karta SmartTube+ na początku wiersza (stan feedu)\n"
                                + "Przy pustym wierszu pokazuje się zawsze — to ona sprawia,\n"
                                + "że wiersz \"Twoje kanały\" jest widoczny, zanim cokolwiek pobierzemy.",
                        "SmartTube+ card at the start of the row (feed status)\n"
                                + "It always shows when the row is empty — it is what keeps\n"
                                + "\"Your channels\" visible before anything is fetched."),
                option -> setStatusCard(context, option.isSelected()),
                isStatusCard(context)));
    }

    private static void appendDebugSwitch(Context context, AppDialogPresenter dialog) {
        dialog.appendSingleSwitch(UiOptionItem.from(
                StPlusText.t(
                        "Tryb debugowania (szczegóły techniczne)\n"
                                + "Opis stanu przy prawej krawędzi nagłówka + na karcie: co zrobiła\n"
                                + "ostatnia runda, z jakiego źródła i jakie były kody odpowiedzi.\n"
                                + "Czas publikacji pod filmami jest ZAWSZE, niezależnie od tego przełącznika.",
                        "Debug mode (technical details)\n"
                                + "Status line at the right edge of the header + on the card: what the last\n"
                                + "round did, which source it used and what response codes came back.\n"
                                + "Publish time under videos is ALWAYS shown, regardless of this switch."),
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
        String[] labels = StPlusText.isEnglish()
                ? new String[] {
                    "Manually only (refresh button)",
                    "On start, at most every 30 min",
                    "On start, at most every 1 h",
                    "On start, at most every 3 h",
                    "On start, at most every 6 h"
                }
                : new String[] {
                    "Tylko ręcznie (przycisk odśwież)",
                    "Przy starcie i nie częściej niż co 30 min",
                    "Przy starcie i nie częściej niż co 1 h",
                    "Przy starcie i nie częściej niż co 3 h",
                    "Przy starcie i nie częściej niż co 6 h"
                };

        for (int i = 0; i < minutes.length; i++) {
            final int value = minutes[i];
            items.add(UiOptionItem.from(labels[i],
                    option -> setRefreshEveryMin(context, value),
                    current == value));
        }

        dialog.appendRadioCategory(StPlusText.t(
                "Kiedy odświeżać (jeden obieg = wszystkie kanały)",
                "When to refresh (one cycle = all channels)"), items);
    }

    /*
     * PRZY ILU KANALACH — to musi byc napisane przy kazdej liczbie.
     *
     * User (2026-09-12): "w tych ustawieniach zapisales tez czas pobierania w odniesieniu
     * do naszych kanalow (...) trzeba bylo napisac, ze to przy okolo 200 subskrypcjach".
     * Czas pelnego obiegu zalezy wprost od liczby kanalow, wiec sama liczba minut bez
     * podanej podstawy nic nie znaczy.
     */
    private static final String BASE_PL = " (przy ok. 200 kanałach)";
    private static final String BASE_EN = " (with about 200 channels)";

    private static void appendSpeedCategory(Context context, AppDialogPresenter dialog) {
        List<OptionItem> items = new ArrayList<>();
        int current = getSpeed(context);

        items.add(UiOptionItem.from(StPlusText.t(
                "Oszczędna — ok. 35 kanałów/min, pełny obieg ok. 6 min" + BASE_PL + "\n"
                        + "Najmniej obciąża słaby sprzęt.",
                "Gentle — about 35 channels/min, full cycle about 6 min" + BASE_EN + "\n"
                        + "Lightest on weak hardware."),
                option -> setSpeed(context, SPEED_GENTLE), current == SPEED_GENTLE));

        items.add(UiOptionItem.from(StPlusText.t(
                "Normalna — ok. 75 kanałów/min, pełny obieg ok. 2,5 min" + BASE_PL + "\n"
                        + "Zalecana.",
                "Normal — about 75 channels/min, full cycle about 2.5 min" + BASE_EN + "\n"
                        + "Recommended."),
                option -> setSpeed(context, SPEED_NORMAL), current == SPEED_NORMAL));

        items.add(UiOptionItem.from(StPlusText.t(
                "Szybka — ok. 150 kanałów/min, pełny obieg ok. 1,5 min" + BASE_PL + "\n"
                        + "Mocniej obciąża procesor.",
                "Fast — about 150 channels/min, full cycle about 1.5 min" + BASE_EN + "\n"
                        + "Heavier on the CPU."),
                option -> setSpeed(context, SPEED_FAST), current == SPEED_FAST));

        dialog.appendRadioCategory(StPlusText.t(
                "Prędkość pobierania (tempo zmierzone na tablecie)",
                "Download pace (measured on a tablet)"), items);
    }

    private static void appendFeedSourceCategory(Context context, AppDialogPresenter dialog) {
        List<OptionItem> items = new ArrayList<>();
        int current = getFeedSource(context);

        // Liczby przy opcjach sa z POMIARU na tablecie (SM-T580, armv7), nie z sufitu.
        // RSS: zmierzone 2026-09-12. Zakladki kanalu: jeszcze nie zmierzone na pelnym
        // obiegu — dlatego napisane wprost "szacunkowo", zeby nikt tego nie wzial za fakt.
        items.add(UiOptionItem.from(StPlusText.t(
                "Automatycznie — ok. 75 kanałów/min, pełna lista w ok. 2,5 min" + BASE_PL + "\n"
                        + "Najpierw RSS, a gdy odmówi, zakładki kanału. Zalecane.\n"
                        + "Shortów w materiałach z RSS nie da się odfiltrować.",
                "Automatic — about 75 channels/min, full list in about 2.5 min" + BASE_EN + "\n"
                        + "RSS first, channel tab when RSS refuses. Recommended.\n"
                        + "Shorts cannot be filtered out of RSS items."),
                option -> setFeedSource(context, RssOptions.SOURCE_AUTO),
                current == RssOptions.SOURCE_AUTO));

        items.add(UiOptionItem.from(StPlusText.t(
                "Tylko RSS — ok. 75 kanałów/min, pełna lista w ok. 2,5 min" + BASE_PL + "\n"
                        + "Najtańsze, z dokładną datą publikacji. Po 15 filmów z kanału.\n"
                        + "Gdy YouTube zacznie odmawiać (błąd 404), lista stoi w miejscu.\n"
                        + "Filtr shortów tu NIE działa — RSS nie podaje ani znacznika, ani długości.",
                "RSS only — about 75 channels/min, full list in about 2.5 min" + BASE_EN + "\n"
                        + "Cheapest, with exact publish dates. Up to 15 videos per channel.\n"
                        + "When YouTube starts refusing (HTTP 404) the list stops growing.\n"
                        + "The Shorts filter does NOT work here — RSS gives no flag and no duration."),
                option -> setFeedSource(context, RssOptions.SOURCE_RSS),
                current == RssOptions.SOURCE_RSS));

        items.add(UiOptionItem.from(StPlusText.t(
                "Tylko zakładki kanału — szacunkowo ok. 20-25 kanałów/min" + BASE_PL + "\n"
                        + "Wolniejsze i cięższe (duży JSON zamiast małego XML), za to nie\n"
                        + "zależy od RSS. Czas publikacji tylko przybliżony.\n"
                        + "JEDYNE źródło, przy którym filtr shortów działa naprawdę.",
                "Channel tab only — roughly 20-25 channels/min" + BASE_EN + "\n"
                        + "Slower and heavier (big JSON instead of small XML), but independent\n"
                        + "of RSS. Publish time is approximate only.\n"
                        + "The ONLY source where the Shorts filter really works."),
                option -> setFeedSource(context, RssOptions.SOURCE_BROWSE),
                current == RssOptions.SOURCE_BROWSE));

        dialog.appendRadioCategory(StPlusText.t(
                "Skąd pobierać materiały kanałów",
                "Where to fetch channel items from"), items);
    }

    private static void appendLanguageCategory(Context context, AppDialogPresenter dialog) {
        List<OptionItem> items = new ArrayList<>();
        int current = getLang(context);

        items.add(UiOptionItem.from(StPlusText.t(
                "Jak w systemie", "Follow the system"),
                option -> setLang(context, StPlusText.LANG_AUTO),
                current == StPlusText.LANG_AUTO));

        items.add(UiOptionItem.from("Polski",
                option -> setLang(context, StPlusText.LANG_PL),
                current == StPlusText.LANG_PL));

        items.add(UiOptionItem.from("English",
                option -> setLang(context, StPlusText.LANG_EN),
                current == StPlusText.LANG_EN));

        dialog.appendRadioCategory(StPlusText.t(
                "Język tekstów SmartTube+ (to menu, karta stanu, nagłówek wiersza)",
                "SmartTube+ language (this menu, status card, row header)"), items);
    }

    private static void appendRefreshButton(Context context, AppDialogPresenter dialog) {
        dialog.appendSingleButton(UiOptionItem.from(
                StPlusText.t("Odśwież teraz (zeruje licznik błędów)",
                        "Refresh now (resets the error counter)"),
                option -> StPlus.requestManualRefresh()));
    }

    /*
     * Sekcja debugu — do TESTOWANIA NAPELNIANIA feedu bez odinstalowywania aplikacji
     * (zyczenie Usera 2026-09-12). Kasowanie materialow zeruje tez znaczniki kanalow,
     * wiec rotacja od razu po nie wraca i widac caly przebieg od zera.
     */
    private static void appendDebugSection(Context context, AppDialogPresenter dialog) {
        List<OptionItem> items = new ArrayList<>();

        items.add(UiOptionItem.from(StPlusText.t(
                "Zapomnij ok. 30 filmów (test napełniania)",
                "Forget about 30 videos (refill test)"),
                option -> forget(context, 30)));

        items.add(UiOptionItem.from(StPlusText.t(
                "Zapomnij ok. 200 filmów", "Forget about 200 videos"),
                option -> forget(context, 200)));

        dialog.appendStringsCategory(StPlusText.t(
                "Debug — testowanie napełniania", "Debug — refill testing"), items);
    }

    /** @param count ile filmow zapomniec; -1 = wszystko. */
    private static void forget(Context context, int count) {
        int removed = count < 0 ? StPlusStore.forgetAll() : StPlusStore.forgetItems(count);
        StPlusStore.save(context);
        StPlus.onStoreCleared();
        MessageHelpers.showMessage(context, StPlusText.t(
                "SmartTube+: zapomniane " + removed + " filmów. Odświeżam od nowa...",
                "SmartTube+: forgot " + removed + " videos. Refreshing..."));
        StPlus.requestManualRefresh();
    }

    private static void appendAboutButton(Context context, AppDialogPresenter dialog) {
        dialog.appendLongTextCategory("About SmartTube+", UiOptionItem.from(about()));
    }

    /**
     * Opis projektu PO ANGIELSKU — celowo, niezaleznie od przelacznika jezyka.
     * Repozytorium idzie na GitHub i ten tekst ma sie nadawac wprost do README
     * (zyczenie Usera, 2026-09-12).
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
                + "A NOTE ON SHORTS\n"
                + "The channel RSS feed carries no Shorts marker and no duration, so items that "
                + "arrive over RSS cannot be reliably told apart from regular videos. The Shorts "
                + "filter therefore only works fully on the channel-tab source. This is a limit "
                + "of the data, not a bug in the switch.\n\n"
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
