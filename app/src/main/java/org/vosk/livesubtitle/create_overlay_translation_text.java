package org.vosk.livesubtitle;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import java.util.Objects;

public class create_overlay_translation_text extends Service {
    public create_overlay_translation_text() {}

    @SuppressLint("StaticFieldLeak")
    public static GlobalOverlay mGlobalOverlay_overlay_translation_text;
    @SuppressLint("StaticFieldLeak")
    public static View overlay_translation_text_container;
    @SuppressLint("StaticFieldLeak")
    public static EditText overlay_translation_text;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void onCreate() {
        super.onCreate();
        create_voice_text();
        if (TRANSLATION_TEXT.STRING !=null && TRANSLATION_TEXT.STRING.length() != 0) {
            overlay_translation_text.setText(TRANSLATION_TEXT.STRING);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlay_translation_text != null) {
            mGlobalOverlay_overlay_translation_text.removeOverlayView(overlay_translation_text);
        }
    }

    @SuppressLint("InflateParams")
    private void create_voice_text() {
        mGlobalOverlay_overlay_translation_text = new GlobalOverlay(this);
        LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        overlay_translation_text_container = layoutInflater.inflate(R.layout.overlay_translation_text_container, null);
        overlay_translation_text_container.setMinimumWidth((int) (0.85* DISPLAY_METRIC.DISPLAY_WIDTH));
        overlay_translation_text_container.setBackgroundColor(Color.parseColor("#00000000"));
        overlay_translation_text_container.setVisibility(View.INVISIBLE);
        overlay_translation_text = overlay_translation_text_container.findViewById(R.id.overlay_translation_text);
        overlay_translation_text.setWidth(overlay_translation_text_container.getWidth());
        overlay_translation_text.setBackgroundColor(Color.parseColor("#80000000"));
        overlay_translation_text.setTextColor(Color.YELLOW);
        overlay_translation_text.setVisibility(View.INVISIBLE);
        if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
            if (TRANSLATION_TEXT.STRING.length() == 0) {
                overlay_translation_text.setVisibility(View.INVISIBLE);
                overlay_translation_text_container.setVisibility(View.INVISIBLE);
            } else {
                overlay_translation_text.setVisibility(View.VISIBLE);
                overlay_translation_text_container.setVisibility(View.VISIBLE);
            }
        } else {
            overlay_translation_text.setVisibility(View.INVISIBLE);
            overlay_translation_text_container.setVisibility(View.INVISIBLE);
        }
        int h;
        if (Objects.equals(LANGUAGE.DST, "ja") || Objects.equals(LANGUAGE.DST, "zh-Hans") || Objects.equals(LANGUAGE.DST, "zh-Hant")) {
            h = 75;
        }
        else {
            h = 62;
        }
        mGlobalOverlay_overlay_translation_text.addOverlayView(overlay_translation_text_container,
                (int) (0.85* DISPLAY_METRIC.DISPLAY_WIDTH),
                (int) (h * getResources().getDisplayMetrics().density),
                0,
                (int) (0.3* DISPLAY_METRIC.DISPLAY_HEIGHT),
                v -> {
                    //toast("onClick");
                },
                v -> {
                    //toast("onLongClick not implemented yet");
                    return false;
                },
                (view, isRemovedByUser) -> {
                    //toast("onRemoveOverlay");
                    stopSelf();
                });
    }

    /*private void toast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }*/

}
