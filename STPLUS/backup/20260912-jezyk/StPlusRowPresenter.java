package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.RowPresenter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.stplus.StPlus;
import com.liskovsoft.smartyoutubetv2.common.stplus.StPlusSettings;

import java.lang.ref.WeakReference;

/*
 * >>> STPLUS — SmartTube+ plugin code (plik NASZ, nie istnieje w upstreamie).
 *
 * PRAWA STRONA BELKI NAGLOWKA wiersza "Twoje kanaly": wyblakly opis stanu + zebatka.
 *
 * CZEGO CHCIAL USER (2026-09-12, powtarzane kilka razy):
 *   - zebatka na koncu linii "Twoje kanaly", PO PRAWEJ STRONIE,
 *   - mala i ledwo widoczna, wyrazniejsza dopiero po najechaniu,
 *   - MUSI dac sie kliknac — palcem na tablecie i pilotem na projektorze,
 *   - opis ze skanowania ("173 z 173 kanalow, 2560 filmow") tez po prawej,
 *     wyblakly, mniejszy, "mniej rzucajacy sie w oczy i mniej przeszkadzajacy",
 *   - w gornej belce sa juz inne rzeczy (zegar, logo, odswiezanie) — zebatka tam
 *     NIE moze isc, bo nachodzi na logo. To juz raz bylo i zostalo odrzucone.
 *
 * DLACZEGO TA WERSJA, A NIE POPRZEDNIA:
 * poprzednia doklejala zebatke jako `compound drawable` do TextView naglowka i lapala
 * dotyk przez `setOnTouchListener` po wspolrzednej X. To NIE dzialalo — naglowek wiersza
 * w leanbacku nie dostaje zdarzen dotyku, wiec ikona byla wylacznie obrazkiem
 * (User: "za tym nie da sie jej kurwa kliknac"). Compound drawable nie moze tez
 * dostac fokusu z pilota i zawsze siedzi TUZ ZA tekstem, a nie przy prawej krawedzi.
 *
 * JAK TO JEST ZROBIONE TERAZ (sprawdzone na sprzecie zrzutem uiautomator):
 * belka naglowka wiersza to `lb_row_container_header_dock` — POZIOMY LinearLayout
 * na CALA SZEROKOSC EKRANU (na tablecie zmierzone [0,206][1200,260]), w ktorym siedzi
 * sam naglowek jako wrap_content ([112,206][933,260]). Zostaje wiec wolne miejsce
 * po prawej: dokladamy do tej belki drugie dziecko z `layout_weight = 1`, wyrownane
 * do prawej — a w nim opis i PRAWDZIWY ImageView. Prawdziwy widok znaczy: ma wlasny
 * obszar dotyku, wlasny `OnClickListener` i moze byc `focusable`, czyli da sie na nim
 * stanac pilotem.
 *
 * DRUGA DROGA DO TEGO SAMEGO MENU (celowa nadmiarowosc, nie dublowanie):
 * klikniecie karty stanu w wierszu tez otwiera `StPlusSettings.show()` — hak
 * `isStatusItem` w BrowsePresenter. Gdyby leanback nie chcial oddac fokusu zebatce
 * na ktoryms urzadzeniu, ustawienia i tak pozostaja dostepne z pilota.
 *
 * RECYKLING WIDOKOW: leanback uzywa tych samych ViewHolderow do roznych wierszy,
 * wiec przy KAZDYM `onBindRowViewHolder` jawnie wlaczamy albo wylaczamy nasza belke.
 * Bez tego zebatka potrafi wyladowac przy "Wybrane dla Ciebie".
 * <<< STPLUS
 */
public class StPlusRowPresenter extends CustomListRowPresenter {
    /** Przezroczystosc zebatki, gdy wiersz nie jest zaznaczony (0-255). */
    private static final int ALPHA_IDLE = 80;
    /** Przezroczystosc, gdy wiersz jest zaznaczony albo zebatka ma fokus. */
    private static final int ALPHA_ACTIVE = 255;
    /** Rozmiar ikony w dp — celowo maly, ma nie konkurowac z tytulem. */
    private static final int ICON_DP = 20;
    /** Zapas nad i pod ikona — maly, bo belka naglowka ma tylko 36 dp wysokosci. */
    private static final int ICON_PAD_V_DP = 4;
    /** Zapas po bokach — duzy, bo tam jest miejsce i stad bierze sie pole dotyku. */
    private static final int ICON_PAD_H_DP = 16;
    /** Odstep naszej belki od prawej krawedzi. */
    private static final int BAR_END_PAD_DP = 8;
    /** Wielkosc liter opisu — wyraznie mniejsza niz tytul wiersza. */
    private static final float TEXT_SP = 12f;
    /** Kolor opisu: szary, przygaszony (User: "bardziej wyblakly"). */
    private static final int TEXT_COLOR = Color.parseColor("#7C7C7C");
    /** Podswietlenie zebatki, gdy ma fokus z pilota. */
    private static final int FOCUS_BG = Color.parseColor("#33FFFFFF");

    private static final String TAG_BAR = "stplus_header_bar";
    private static final String TAG_SUMMARY = "stplus_header_summary";
    private static final String TAG_GEAR = "stplus_header_gear";

    /** Widok opisu, ktory jest teraz na ekranie (moze go nie byc — stad WeakReference). */
    private static WeakReference<TextView> sSummaryView = new WeakReference<>(null);
    private static boolean sSummaryListenerSet;

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);

        try {
            bindHeaderBar(holder, isOurRow(holder, item));
        } catch (Exception e) {
            // Ozdoba nie ma prawa wywrocic listy — w najgorszym razie nie ma zebatki.
            e.printStackTrace();
        }
    }

    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {
        super.onSelectLevelChanged(holder);

        try {
            View bar = findBar(holder);
            if (bar != null && bar.getVisibility() == View.VISIBLE) {
                setGearAlpha(bar, holder.getSelectLevel() > 0.5f ? ALPHA_ACTIVE : ALPHA_IDLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------ czyj wiersz ---

    /**
     * Czy to NASZ wiersz "Twoje kanaly".
     *
     * Pewny sygnal to ID grupy w pierwszej pozycji wiersza — tytul moze sie kiedys
     * zmienic albo zostac przetlumaczony, a ID jest stale. Tytul zostaje jako zapas
     * na wypadek, gdyby wiersz byl chwilowo pusty.
     */
    private boolean isOurRow(RowPresenter.ViewHolder holder, Object item) {
        if (!(item instanceof ListRow)) {
            return false;
        }

        ListRow row = (ListRow) item;

        ObjectAdapter adapter = row.getAdapter();
        if (adapter != null && adapter.size() > 0) {
            Object first = adapter.get(0);
            if (first instanceof Video) {
                VideoGroup group = ((Video) first).getGroup();
                if (group != null) {
                    return group.getId() == StPlus.SUBS_ROW_ID;
                }
            }
        }

        if (row.getHeaderItem() == null || holder == null || holder.view == null) {
            return false;
        }

        String ours = holder.view.getContext().getString(
                com.liskovsoft.smartyoutubetv2.common.R.string.stplus_subscriptions_row);
        return ours.equals(row.getHeaderItem().getName());
    }

    // ------------------------------------------------------------------ belka ---

    private void bindHeaderBar(RowPresenter.ViewHolder holder, boolean ours) {
        ViewGroup dock = headerDock(holder);
        if (dock == null) {
            return;
        }

        View bar = dock.findViewWithTag(TAG_BAR);

        if (!ours) {
            // Widoki sa recyklowane — bez tego zebatka wedruje do cudzych wierszy.
            if (bar != null) {
                bar.setVisibility(View.GONE);
            }
            return;
        }

        if (bar == null) {
            bar = createBar(dock);
            if (bar == null) {
                return;
            }
            dock.addView(bar);
        }

        bar.setVisibility(View.VISIBLE);

        TextView summary = bar.findViewWithTag(TAG_SUMMARY);
        if (summary != null) {
            sSummaryView = new WeakReference<>(summary);
            applySummary(summary, StPlus.summaryText());
        }

        setGearAlpha(bar, holder.getSelectLevel() > 0.5f ? ALPHA_ACTIVE : ALPHA_IDLE);
        ensureSummaryListener();
    }

    /**
     * Belka naglowka: poziomy LinearLayout na cala szerokosc wiersza, w ktorym leanback
     * trzyma sam naglowek. Nasz pasek dokladamy jako kolejne dziecko.
     */
    private ViewGroup headerDock(RowPresenter.ViewHolder holder) {
        if (holder == null || holder.getHeaderViewHolder() == null) {
            return null;
        }
        View header = holder.getHeaderViewHolder().view;
        if (header == null || !(header.getParent() instanceof LinearLayout)) {
            return null;
        }
        return (ViewGroup) header.getParent();
    }

    private View createBar(ViewGroup dock) {
        final float d = dock.getResources().getDisplayMetrics().density;

        /*
         * `onRequestFocusInDescendants` -> false to NIE jest sztuczka, tylko dokladnie
         * ten mechanizm, ktory rozdziela dwa rozne rodzaje "dania fokusu":
         *
         *  - requestFocus() z gory (tak okno wybiera, co ma fokus PO OTWARCIU) schodzi
         *    w dol przez onRequestFocusInDescendants i bierze pierwszy focusowalny widok
         *    w drzewie. Belka naglowka jest pierwszym dzieckiem wiersza, wiec bez tego
         *    zebatka zabiera fokus zaraz po starcie aplikacji — sprawdzone na tablecie
         *    (pierwszy build mial ja podswietlona od razu po wejsciu na ekran glowny).
         *  - focusSearch (D-pad, czyli pilot) idzie przez FocusFinder i addFocusables,
         *    ktorych to w ogole nie dotyczy.
         *
         * Efekt: po starcie fokus zostaje tam, gdzie ma byc — na kafelkach — a pilotem
         * nadal da sie na zebatke wejsc.
         */
        LinearLayout bar = new LinearLayout(dock.getContext()) {
            @Override
            protected boolean onRequestFocusInDescendants(int direction,
                                                          android.graphics.Rect previouslyFocusedRect) {
                return false;
            }
        };
        bar.setTag(TAG_BAR);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, (int) (BAR_END_PAD_DP * d), 0);
        // Waga 1 przy szerokosci 0 = "zabierz cale wolne miejsce po naglowku",
        // czyli pasek konczy sie dokladnie przy prawej krawedzi wiersza.
        // Wysokosc WRAP_CONTENT, nie MATCH_PARENT: belka naglowka sama jest
        // wrap_content, wiec dziecko z MATCH_PARENT dostaloby wysokosc calego
        // dostepnego miejsca i rozpchnelo naglowek na pol ekranu.
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView summary = new TextView(dock.getContext());
        summary.setTag(TAG_SUMMARY);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        summary.setTextColor(TEXT_COLOR);
        summary.setSingleLine(true);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        summary.setVisibility(View.GONE);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textParams.rightMargin = (int) (10 * d);
        summary.setLayoutParams(textParams);
        bar.addView(summary);

        ImageView gear = new ImageView(dock.getContext());
        gear.setTag(TAG_GEAR);
        Drawable icon = ContextCompat.getDrawable(dock.getContext(),
                com.liskovsoft.smartyoutubetv2.common.R.drawable.icon_settings);
        if (icon == null) {
            return null;
        }
        gear.setImageDrawable(icon.mutate());
        gear.setImageAlpha(ALPHA_IDLE);
        gear.setScaleType(ImageView.ScaleType.FIT_CENTER);
        /*
         * Zapas wokol ikony jest ASYMETRYCZNY i to jest celowe.
         *
         * Belka naglowka ma na obu urzadzeniach 54 px wysokosci (zmierzone zrzutem
         * uiautomator przy gestosci 240, czyli 36 dp). Kwadratowe pole dotyku 44 dp
         * bylo by WYZSZE od belki i podnioslo caly wiersz — a wiersza nie wolno
         * przesuwac. Dlatego w pionie zapas jest maly (ikona 20 dp + 2 x 4 dp = 28 dp,
         * czyli 42 px — miesci sie w 54 px), a pole dotyku powieksza sie w POZIOMIE,
         * gdzie miejsca jest pod dostatkiem: 20 + 2 x 16 = 52 dp szerokosci.
         */
        int padV = (int) (ICON_PAD_V_DP * d);
        int padH = (int) (ICON_PAD_H_DP * d);
        gear.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(
                (int) ((ICON_DP + 2 * ICON_PAD_H_DP) * d),
                (int) ((ICON_DP + 2 * ICON_PAD_V_DP) * d));
        gearParams.gravity = Gravity.CENTER_VERTICAL;
        gear.setLayoutParams(gearParams);

        // Palec: zwykly klik na widoku o rozsadnym polu (ikona 20 dp + 12 dp zapasu).
        gear.setClickable(true);
        gear.setOnClickListener(v -> StPlusSettings.show(v.getContext()));

        // Pilot: zebatka musi umiec przyjac fokus, inaczej nie ma jak do niej dojsc.
        // focusableInTouchMode zostaje WYLACZONE — na tablecie fokus po dotyku tylko
        // przeszkadza, a klikniecie i tak dziala.
        gear.setFocusable(true);
        gear.setOnFocusChangeListener((v, hasFocus) -> {
            ((ImageView) v).setImageAlpha(hasFocus ? ALPHA_ACTIVE : ALPHA_IDLE);
            v.setBackgroundColor(hasFocus ? FOCUS_BG : Color.TRANSPARENT);
        });

        bar.addView(gear);
        return bar;
    }

    private View findBar(RowPresenter.ViewHolder holder) {
        ViewGroup dock = headerDock(holder);
        return dock != null ? dock.findViewWithTag(TAG_BAR) : null;
    }

    private static void setGearAlpha(View bar, int alpha) {
        View gear = bar.findViewWithTag(TAG_GEAR);
        if (gear instanceof ImageView && !gear.isFocused()) {
            ((ImageView) gear).setImageAlpha(alpha);
        }
    }

    private static void applySummary(TextView view, String text) {
        if (text == null || text.isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Opis ma sie odswiezac w trakcie skanowania, a nie tylko przy przerysowaniu wiersza.
     * `common` nie widzi tego modulu, wiec to my zglaszamy sie po dane (ten sam wzorzec
     * co RefreshListener dla kolka w belce). Wolanie przychodzi na watku glownym.
     */
    private static void ensureSummaryListener() {
        if (sSummaryListenerSet) {
            return;
        }
        sSummaryListenerSet = true;
        StPlus.setSummaryListener(text -> {
            TextView view = sSummaryView.get();
            if (view != null) {
                applySummary(view, text);
            }
        });
    }
}
