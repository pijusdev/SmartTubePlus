package com.liskovsoft.smartyoutubetv2.common.stplus;

import android.content.Context;

import java.util.Locale;

/*
 * >>> STPLUS — SmartTube+ plugin code (plik NASZ, nie istnieje w upstreamie).
 *
 * Jezyk NASZYCH tekstow: polski albo angielski.
 *
 * DLACZEGO OSOBNY MECHANIZM, A NIE `res/values-en/strings.xml`:
 * zasoby Androida ida za jezykiem SYSTEMU, a User chce PRZELACZNIK w naszym menu
 * (2026-09-12): "przydaloby sie, zebysmy mieli tez przelacznik tego naszego menu
 * miedzy polskim a reszta swiata, czyli mamy jezyk polski w tych ustawieniach albo
 * reszta swiata, czyli po angielsku to nasze menu bedzie". Ma dzialac niezaleznie od
 * jezyka systemu i bez restartu aplikacji — a zasoby tego nie potrafia bez kombinowania
 * z `Configuration` i przeladowaniem aktywnosci.
 *
 * Dodatkowy powod: projekt idzie na GitHub. Angielska wersja naszych tekstow ma byc
 * w JEDNYM miejscu, obok polskiej, zeby dalo sie ja przeczytac i poprawic bez szukania
 * po plikach zasobow.
 *
 * Uzycie: `StPlusText.t("po polsku", "in English")`.
 * <<< STPLUS
 */
public final class StPlusText {
    /** Jezyk wg systemu — polski system = polskie teksty. */
    public static final int LANG_AUTO = 0;
    public static final int LANG_PL = 1;
    public static final int LANG_EN = 2;

    private static Boolean sEnglish;

    private StPlusText() {
    }

    /** Ustawienie z menu. Wolane przez StPlusSettings przy starcie i przy zmianie. */
    static void apply(Context context, int lang) {
        switch (lang) {
            case LANG_PL:
                sEnglish = false;
                break;
            case LANG_EN:
                sEnglish = true;
                break;
            case LANG_AUTO:
            default:
                sEnglish = !isPolishSystem(context);
                break;
        }
    }

    public static boolean isEnglish() {
        return sEnglish != null && sEnglish;
    }

    /** Wybor tekstu. Krotka nazwa, bo wywolan jest kilkadziesiat. */
    public static String t(String pl, String en) {
        return isEnglish() ? en : pl;
    }

    private static boolean isPolishSystem(Context context) {
        try {
            Locale locale = context != null
                    ? context.getResources().getConfiguration().locale
                    : Locale.getDefault();
            return locale != null && "pl".equalsIgnoreCase(locale.getLanguage());
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------- teksty wspolne ---

    /** Naglowek naszego wiersza. */
    public static String rowTitle() {
        return t("Twoje kanały", "Your channels");
    }

    /** Kafelek-zaslepka na koncu wiersza. */
    public static String loadingMore() {
        return t("Ładowanie…", "Loading…");
    }

    public static String counts(int withData, int all, int items) {
        return t(withData + " z " + all + " kanałów, " + items + " filmów",
                withData + " of " + all + " channels, " + items + " videos");
    }

    public static String cardLoading() {
        return t("wczytuję zapisane materiały...", "loading saved items...");
    }

    public static String cardNoSubs() {
        return t("brak subskrypcji — zaimportuj listę kanałów (np. z NewPipe)",
                "no subscriptions — import a channel list (e.g. from NewPipe)");
    }

    public static String cardNotScanned() {
        return t("jeszcze nie skanowałem", "not scanned yet");
    }

    public static String scanning() {
        return t("skanuję...", "scanning...");
    }

    public static String storeCleared() {
        return t("magazyn wyczyszczony — skanuję od nowa", "store cleared — scanning again");
    }

    /** Meldunek z rundy: „ostatnie skanowanie 15:39: 10 kanałów, 8 z materiałami, 120 filmów”. */
    public static String roundInfo(String clock, int channels, int withItems, int items, int silent) {
        StringBuilder sb = new StringBuilder();
        if (isEnglish()) {
            sb.append("last scan ").append(clock).append(": ")
                    .append(channels).append(" channels, ")
                    .append(withItems).append(" with items, ")
                    .append(items).append(" videos");
            if (silent > 0) {
                sb.append(", ").append(silent).append(" no answer");
            }
        } else {
            sb.append("ostatnie skanowanie ").append(clock).append(": ")
                    .append(channels).append(" kanałów, ")
                    .append(withItems).append(" z materiałami, ")
                    .append(items).append(" filmów");
            if (silent > 0) {
                sb.append(", ").append(silent).append(" bez odpowiedzi");
            }
        }
        return sb.toString();
    }

    public static String roundError(String message) {
        return t("błąd — " + message, "error — " + message);
    }

    public static String sourceLabel(boolean rss) {
        if (rss) {
            return t(" • źródło: RSS", " • source: RSS");
        }
        return t(" • źródło: zakładki kanału", " • source: channel tab");
    }

    public static String codesLabel() {
        return t(" • odpowiedzi RSS: ", " • RSS responses: ");
    }

    // --------------------------------------------------- opis stanu (naglowek) ---

    /** Trwa obieg: „skanuję… 40 z 173 kanałów, +12 nowych”. */
    public static String summaryScanning(int done, int all, int added) {
        return t("skanuję… " + done + " z " + all + " kanałów, +" + added + " nowych",
                "scanning… " + done + " of " + all + " channels, +" + added + " new");
    }

    /**
     * Obieg stoi: „2560 filmów · skan 16:19, +45 · następny 17:19”.
     *
     * @param nextClock godzina nastepnego obiegu albo null, gdy tylko recznie
     */
    public static String summaryIdle(int items, String lastClock, int added, String nextClock) {
        StringBuilder sb = new StringBuilder();
        if (isEnglish()) {
            sb.append(items).append(" videos");
            if (lastClock != null) {
                sb.append(" · scan ").append(lastClock).append(", +").append(added);
            }
            sb.append(nextClock != null ? " · next " + nextClock : " · manual only");
        } else {
            sb.append(items).append(" filmów");
            if (lastClock != null) {
                sb.append(" · skan ").append(lastClock).append(", +").append(added);
            }
            sb.append(nextClock != null ? " · następny " + nextClock : " · tylko ręcznie");
        }
        return sb.toString();
    }

    // ------------------------------------------------------- czas publikacji ---

    public static String timeJustNow() {
        return t("zaraz", "just now");
    }

    public static String timeMinutes(long n) {
        return t(n + " min temu", n + " min ago");
    }

    public static String timeHours(long n) {
        return t(n + " godz. temu", n + " h ago");
    }

    public static String timeDays(long n) {
        return t(n + " dni temu", n + " days ago");
    }

    public static String timeMonths(long n) {
        return t(n + " mies. temu", n + " months ago");
    }
}
