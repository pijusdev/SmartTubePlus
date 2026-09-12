package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.RowPresenter;

import com.liskovsoft.smartyoutubetv2.common.stplus.StPlus;
import com.liskovsoft.smartyoutubetv2.common.stplus.StPlusSettings;

/*
 * >>> STPLUS — SmartTube+ plugin code (plik NASZ, nie istnieje w upstreamie).
 *
 * Zebatka DOKLEJONA DO KONCA NAGLOWKA wiersza "Twoje kanaly" — dokladnie tam,
 * gdzie chcial User (2026-09-12, powtorzone dwa razy): "na koncu tej linii po prawej,
 * gornej, z naszej kanaly bedzie mala zebatka (...) ma byc mala i najlepiej, zeby byla
 * wyblakla, dopiero jak najedziemy cos jest bardziej widoczna".
 *
 * DLACZEGO TAK, A NIE IKONA W ROGU EKRANU:
 * pierwsza wersja sadzila zebatke w prawym gornym rogu, obok odswiezania — i wchodzila
 * na logo SmartTube. Poza tym to nie bylo to, o co chodzilo: zebatka ma nalezec do
 * WIERSZA, a nie do belki.
 *
 * JAK TO JEST ZROBIONE:
 * naglowek wiersza w leanbacku to zwykly TextView (RowHeaderView), wiec zebatka idzie
 * jako `compound drawable` po prawej stronie tekstu. Dzieki temu siedzi zawsze dokladnie
 * na koncu napisu "Twoje kanaly", bez kombinowania z ukladem i bez ryzyka, ze zaslowni
 * cokolwiek innego.
 *
 * Przezroczystosc: 90/255 gdy wiersz nie jest zaznaczony, pelna gdy jest — czyli
 * "wyblakla, a po najechaniu bardziej widoczna".
 *
 * Klikanie: dotykiem — w prawy skraj naglowka (obszar ikony). Na pilocie dotyku nie ma,
 * dlatego DRUGA droga do tego samego menu jest klikniecie karty statusu w wierszu
 * (StPlus.isStatusItem -> StPlusSettings.show). Jedno menu, dwa wejscia.
 * <<< STPLUS
 */
public class StPlusRowPresenter extends CustomListRowPresenter {
    /** Przezroczystosc ikony, gdy wiersz nie jest zaznaczony (0-255). */
    private static final int ALPHA_IDLE = 90;
    /** Rozmiar ikony w dp — celowo maly, ma nie konkurowac z tytulem. */
    private static final int ICON_DP = 18;

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);

        try {
            attachGear(holder);
        } catch (Exception e) {
            // Ozdoba nie ma prawa wywrocic listy — w najgorszym razie nie ma zebatki.
            e.printStackTrace();
        }
    }

    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {
        super.onSelectLevelChanged(holder);

        try {
            TextView title = findOurHeader(holder);
            if (title != null) {
                setIconAlpha(title, holder.getSelectLevel() > 0.5f ? 255 : ALPHA_IDLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void attachGear(RowPresenter.ViewHolder holder) {
        TextView title = findOurHeader(holder);
        if (title == null) {
            return;
        }

        float d = title.getResources().getDisplayMetrics().density;
        int size = (int) (ICON_DP * d);

        Drawable gear = ContextCompat.getDrawable(title.getContext(),
                com.liskovsoft.smartyoutubetv2.common.R.drawable.icon_settings);
        if (gear == null) {
            return;
        }
        gear = gear.mutate();
        gear.setBounds(0, 0, size, size);
        gear.setAlpha(ALPHA_IDLE);

        title.setCompoundDrawables(null, null, gear, null);
        title.setCompoundDrawablePadding((int) (6 * d));

        title.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return false;
            }
            // Reagujemy tylko na prawy skraj naglowka, czyli na sama ikone.
            int hot = size + (int) (16 * d);
            if (event.getX() >= v.getWidth() - hot) {
                StPlusSettings.show(v.getContext());
                return true;
            }
            return false;
        });
    }

    /**
     * TextView naglowka, ale TYLKO dla naszego wiersza "Twoje kanaly".
     *
     * Widok naglowka nie zawsze JEST TextView — bywa kontenerem, w ktorym TextView
     * siedzi glebiej (zalezy od layoutu naglowka). Pierwsza wersja sprawdzala tylko
     * `view instanceof TextView` i przez to zebatka nie pojawiala sie w ogole
     * (User: "calkowicie zniknela zebatka"). Dlatego szukamy w glab.
     */
    private TextView findOurHeader(RowPresenter.ViewHolder holder) {
        if (holder == null || holder.getHeaderViewHolder() == null) {
            return null;
        }

        String ours = holder.view.getContext().getString(
                com.liskovsoft.smartyoutubetv2.common.R.string.stplus_subscriptions_row);

        TextView found = findTitle(holder.getHeaderViewHolder().view, ours);
        if (found == null) {
            // Naglowki wierszy moga byc zupelnie osobnym widokiem — szukamy tez
            // w calym wierszu, zeby nie polegac na jednej sciezce leanbacka.
            found = findTitle(holder.view, ours);
        }
        return found;
    }

    private TextView findTitle(View view, String ours) {
        if (view == null) {
            return null;
        }

        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            return text != null && text.toString().startsWith(ours) ? (TextView) view : null;
        }

        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTitle(group.getChildAt(i), ours);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private void setIconAlpha(TextView title, int alpha) {
        Drawable[] drawables = title.getCompoundDrawables();
        if (drawables.length > 2 && drawables[2] != null) {
            drawables[2].setAlpha(alpha);
            title.invalidate();
        }
    }
}
