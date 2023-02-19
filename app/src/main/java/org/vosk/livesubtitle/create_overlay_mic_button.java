package org.vosk.livesubtitle;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;

public class create_overlay_mic_button extends Service{

    public create_overlay_mic_button() {}

    private GlobalOverlay mGlobalOverlay_mic_button;
    @SuppressLint("StaticFieldLeak")
    public static ImageView mic_button;
    private final String hints = "Recognized words";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        create_mic_button();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mGlobalOverlay_mic_button != null) {
            mGlobalOverlay_mic_button.removeOverlayView(mic_button);
        }
        if (IS_OVER_REMOVEVIEW.IS_OVER) {
            stop_vosk_voice_recognizer();
            stop_create_overlay_translation_text();
            RECOGNIZING_STATUS.IS_RECOGNIZING = false;
            RECOGNIZING_STATUS.STRING = "RECOGNIZING_STATUS.IS_RECOGNIZING = " + RECOGNIZING_STATUS.IS_RECOGNIZING;
            MainActivity.textview_recognizing.setText(RECOGNIZING_STATUS.STRING);
            OVERLAYING_STATUS.IS_OVERLAYING = false;
            OVERLAYING_STATUS.STRING = "OVERLAYING_STATUS.IS_OVERLAYING = " + OVERLAYING_STATUS.IS_OVERLAYING;
            MainActivity.textview_overlaying.setText(OVERLAYING_STATUS.STRING);
            MainActivity.textview_debug.setText("");
            VOICE_TEXT.STRING = "";
            TRANSLATION_TEXT.STRING = "";
            MainActivity.voice_text.setText("");
            MainActivity.voice_text.setHint(hints);

            NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager.isNotificationPolicyAccessGranted()) {
                MainActivity.audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
            }
            else {
                Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }

            if (create_overlay_translation_text.overlay_translation_text != null) {
                create_overlay_translation_text.overlay_translation_text.setText("");
                create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
            }
            if (create_overlay_mic_button.mic_button != null) {
                create_overlay_mic_button.mic_button.setVisibility(View.INVISIBLE);
            }
        }
        MainActivity.textview_debug.setText("");
        VOICE_TEXT.STRING = "";
        TRANSLATION_TEXT.STRING = "";
        MainActivity.voice_text.setText("");
        String string_recognizing = "recognizing=" + RECOGNIZING_STATUS.IS_RECOGNIZING;
        MainActivity.textview_recognizing.setText(string_recognizing);
        String string_overlaying = "overlaying=" + OVERLAYING_STATUS.IS_OVERLAYING;
        MainActivity.textview_overlaying.setText(string_overlaying);
        MainActivity.voice_text.setHint(hints);
    }

    private void create_mic_button() {
        mGlobalOverlay_mic_button = new GlobalOverlay(this);
        mic_button = new ImageView(this);
        if (!RECOGNIZING_STATUS.IS_RECOGNIZING) {
            mic_button.setImageResource(R.drawable.ic_mic_black_off);
        } else {
            mic_button.setImageResource(R.drawable.ic_mic_black_on);
        }
        mic_button.setBackgroundColor(Color.parseColor("#80000000"));
        mGlobalOverlay_mic_button.addOverlayView(mic_button,
                96,
                96,
                0,
                0,
                //new View.OnClickListener() {
                v -> {
                    RECOGNIZING_STATUS.IS_RECOGNIZING = !RECOGNIZING_STATUS.IS_RECOGNIZING;
                    String string_recognizing = "recognizing=" + RECOGNIZING_STATUS.IS_RECOGNIZING;
                    MainActivity.textview_recognizing.setText(string_recognizing);
                    if (!RECOGNIZING_STATUS.IS_RECOGNIZING) {
                        stop_vosk_voice_recognizer();
                        mic_button.setImageResource(R.drawable.ic_mic_black_off);
                        MainActivity.textview_debug.setText("");
                        VOICE_TEXT.STRING = "";
                        TRANSLATION_TEXT.STRING = "";
                        MainActivity.voice_text.setText("");
                        MainActivity.voice_text.setHint(hints);

                        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (notificationManager.isNotificationPolicyAccessGranted()) {
                            MainActivity.audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, MainActivity.mStreamVolume, AudioManager.ADJUST_SAME);
                            //MainActivity.audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, (int) Double.parseDouble(String.valueOf((long) (MainActivity.audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) / 2))), 0);
                        }
                        else {
                            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }

                        if (create_overlay_translation_text.overlay_translation_text != null) {
                            create_overlay_translation_text.overlay_translation_text.setText("");
                            create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                            create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                        }
                    } else {
                        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (notificationManager.isNotificationPolicyAccessGranted()) {
                            MainActivity.audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                        }
                        else {
                            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }

                        start_vosk_voice_recognizer();
                        mic_button.setImageResource(R.drawable.ic_mic_black_on);
                        MainActivity.textview_debug.setText(R.string.say_something);
                        if (TRANSLATION_TEXT.STRING.length() == 0) {
                            if (create_overlay_translation_text.overlay_translation_text != null) {
                                create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                                create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                            }
                        } else {
                            if (create_overlay_translation_text.overlay_translation_text != null) {
                                create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.VISIBLE);
                                create_overlay_translation_text.overlay_translation_text.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                },
                /*new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        return false;
                    }
                },*/
                v -> false,
                /*new GlobalOverlay.OnRemoveOverlayListener() {
                    @Override
                    public void onRemoveOverlay(View mic_button, boolean isRemovedByUser) {
                        //toast("onRemoveOverlay");
                        stopSelf();
                    }
                });*/
                (mic_button, isRemovedByUser) -> {
                    //toast("onRemoveOverlay");
                    stopSelf();
                });
    }

    private void start_vosk_voice_recognizer() {
        Intent i = new Intent(this, VoskVoiceRecognizer.class);
        startService(i);
    }

    private void stop_vosk_voice_recognizer() {
        stopService(new Intent(this, VoskVoiceRecognizer.class));
    }

    private void stop_create_overlay_translation_text() {
        stopService(new Intent(this, create_overlay_translation_text.class));
    }

    /*private void toast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }*/

}
