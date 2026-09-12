package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageCardView;

/*
 * >>> STPLUS — SmartTube+ plugin code (plik NASZ, nie istnieje w upstreamie).
 *
 * Wyglad karty stanu feedu (tej pierwszej w wierszu "Twoje kanaly").
 *
 * DLACZEGO (User, 2026-09-12): "ta pierwsza karta, ta biala z tym debugiem (...) jest
 * za jasna i razi w oczy. Nie wiem po co ona ma czarny tekst na bialym tle i po chuj
 * ten szary u gory (...) zamiast tego szarego moge wstawic ikonke SmartTube, albo
 * napisac debug czy cos na tym szarym, tylko ze na czarnym".
 *
 * Karta jest zwyklym kafelkiem filmu, tylko bez okladki — dlatego Glide wstawial jej
 * szara zaslepke, a pole tekstowe brало jasne tlo motywu. Tutaj nadpisujemy jedno
 * i drugie: ciemne tlo, jasny tekst, ikona aplikacji zamiast zaslepki.
 *
 * Plik lezy w module `smarttubetv`, a nie w `stplus` w `common`, bo siega po zasoby
 * i widoki UI telewizyjnego — `common` ich nie widzi (sprawdzone, build sie wywala).
 *
 * Nic tu nie liczymy i nie pobieramy — to wylacznie warstwa wygladu.
 * <<< STPLUS
 */
public final class StPlusStatusCard {
    /** Tlo pola tekstowego — ciemne, zeby nie razilo w ciemnym pokoju. */
    private static final int BG = Color.parseColor("#1B1B1B");
    /** Tlo obszaru obrazka — ciemniejsze, zeby karta miala ksztalt. */
    private static final int BG_IMAGE = Color.parseColor("#101010");
    private static final int TEXT_MAIN = Color.parseColor("#E8E8E8");
    private static final int TEXT_SECOND = Color.parseColor("#9E9E9E");

    private StPlusStatusCard() {
    }

    /**
     * @param width  szerokosc obrazka karty — TAKA SAMA jak w zwyklych kafelkach
     * @param height wysokosc obrazka karty — j.w.
     *
     * Wymiary sa parametrem, a nie stala, bo upstream liczy je z ustawien
     * (`VideoCardPresenter.updateDimensions`) i zmieniaja sie wraz z rozmiarem kart.
     * Bez ich ustawienia karta stanu jest WYZSZA od pozostalych: hak w prezenterze
     * wychodzi z metody przed `setMainImageDimensions`, a nasza ikona jest kwadratowa,
     * wiec ImageView rozciaga sie do jej proporcji.
     */
    public static void decorate(ComplexImageCardView cardView, int width, int height) {
        if (cardView == null) {
            return;
        }

        try {
            cardView.setMainImageDimensions(width, height);

            // Zadnej zaslepki Glide'a — wlasna ikona na ciemnym tle.
            ImageView image = cardView.getMainImageView();
            if (image != null) {
                image.setBackgroundColor(BG_IMAGE);
                image.setImageResource(R.mipmap.app_icon);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int pad = (int) (18 * cardView.getResources().getDisplayMetrics().density);
                image.setPadding(pad, pad, pad, pad);
            }

            View infoField = cardView.findViewById(R.id.info_field);
            if (infoField != null) {
                infoField.setBackgroundColor(BG);
            }

            TextView title = cardView.findViewById(R.id.title_text);
            if (title != null) {
                title.setTextColor(TEXT_MAIN);
            }

            TextView content = cardView.findViewById(R.id.content_text);
            if (content != null) {
                content.setTextColor(TEXT_SECOND);
            }

            // Meldunek jest dluzszy niz tytul filmu — dajemy mu wiecej wierszy.
            cardView.setTitleLinesNum(1);
            cardView.setContentLinesNum(3);
            cardView.setBadgeText(null);
            cardView.setProgress(0);
        } catch (Exception e) {
            // Wyglad nie ma prawa wywrocic listy.
            e.printStackTrace();
        }
    }
}
