package org.vosk.livesubtitle;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class VoskVoiceRecognizer extends Service implements RecognitionListener {
    public VoskVoiceRecognizer() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private String results;

    /*android.text.TextWatcher tw = new android.text.TextWatcher() {
        public void afterTextChanged(android.text.Editable s) {}
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            translate_api translate = new translate_api();
            if (RECOGNIZING_STATUS.RECOGNIZING) {
                if (MainActivity.voice_text != null) {
                    //get_translation(MainActivity.voice_text.getText(), LANGUAGE.SRC, LANGUAGE.DST);
                    if (MainActivity.voice_text.length() > 0) {
                        MainActivity.textview_debug2.setText(mlkit_status_message);
                    }
                    if (TRANSLATION_TEXT.STRING.length() == 0) {
                        create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                        create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                    } else {
                        create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.VISIBLE);
                        create_overlay_translation_text.overlay_translation_text.setVisibility(View.VISIBLE);
                        create_overlay_translation_text.overlay_translation_text.setText(TRANSLATION_TEXT.STRING);
                        create_overlay_translation_text.overlay_translation_text.setSelection(create_overlay_translation_text.overlay_translation_text.getText().length());
                    }
                }
                translate.setOnTranslationCompleteListener(new translate_api.OnTranslationCompleteListener() {
                    @Override
                    public void onStartTranslation() {}

                    @Override
                    public void onCompleted(String text) {
                        TRANSLATION_TEXT.STRING = text;
                    }

                    @Override
                    public void onError(Exception e) {
                        //Toast.makeText(MainActivity.this, "Unknown error", Toast.LENGTH_SHORT).show();
                    }
                });
                translate.execute(VOICE_TEXT.STRING, LANGUAGE.SRC, LANGUAGE.DST);

            } else {
                if (translator != null) translator.close();
                create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
            }
        }
    };*/

    @Override
    public void onCreate() {
        super.onCreate();
        LibVosk.setLogLevel(LogLevel.INFO);
        //MainActivity.voice_text.addTextChangedListener(tw);
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        if (speechStreamService != null) {
            speechStreamService.stop();
            speechStreamService = null;
        }

        int h;
        if (Objects.equals(LANGUAGE.SRC, "ja") || Objects.equals(LANGUAGE.SRC, "zh-Hans") || Objects.equals(LANGUAGE.SRC, "zh-Hant")) {
            h = 122;
        }
        else {
            h = 109;
        }
        MainActivity.voice_text.setHeight((int) (h * getResources().getDisplayMetrics().density));

        if (Objects.equals(VOSK_MODEL.ISO_CODE, "en-US")) {
            initModel();
        } else {
            initDownloadedModel();
        }

        if (RECOGNIZING_STATUS.RECOGNIZING) {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (VOICE_TEXT.STRING != null) {
                        translate(VOICE_TEXT.STRING, LANGUAGE.SRC, LANGUAGE.DST);
                    }
                }
            },0,1000);
        }
    }

    private void initModel() {
        StorageService.unpack(this, VOSK_MODEL.ISO_CODE, "model", (model) -> {
                    this.model = model;
                    recognizeMicrophone();
                },
                (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
    }

    private void initDownloadedModel() {
        if (VOSK_MODEL.DOWNLOADED) {
            model = new Model(VOSK_MODEL.USED_PATH);
            recognizeMicrophone();
        } else {
            if (create_overlay_mic_button.mic_button != null) create_overlay_mic_button.mic_button.setImageResource(R.drawable.ic_mic_black_off);
            RECOGNIZING_STATUS.RECOGNIZING = false;
            String string_recognizing = "recognizing=" + RECOGNIZING_STATUS.RECOGNIZING;
            MainActivity.textview_recognizing.setText(string_recognizing);
            String hints = "Recognized words";
            MainActivity.voice_text.setHint(hints);
            stopSelf();
            String msg = "You need to download the model first";
            //toast(msg);
            setText(MainActivity.textview_debug, msg);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        if (speechStreamService != null) {
            speechStreamService.stop();
            speechStreamService = null;
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        if (hypothesis != null) {
            results = (((((hypothesis.replace("text", ""))
                    .replace("{", ""))
                    .replace("}", ""))
                    .replace(":", ""))
                    .replace("partial", ""))
                    .replace("\"", "");
        }
        if (RECOGNIZING_STATUS.RECOGNIZING) {
            VOICE_TEXT.STRING = results.toLowerCase(Locale.forLanguageTag(LANGUAGE.SRC));
            MainActivity.voice_text.setText(VOICE_TEXT.STRING);
            MainActivity.voice_text.setSelection(MainActivity.voice_text.getText().length());
        }
        else {
            VOICE_TEXT.STRING = "";
            MainActivity.voice_text.setText("");
        }
    }

    @Override
    public void onResult(String hypothesis) {
        /*if (hypothesis != null) {
            results = (((((hypothesis.replace("text", ""))
                    .replace("{", ""))
                    .replace("}", ""))
                    .replace(":", ""))
                    .replace("partial", ""))
                    .replace("\"", "");
        }
        if (RECOGNIZING_STATUS.RECOGNIZING) {
            VOICE_TEXT.STRING = results.toLowerCase(Locale.forLanguageTag(LANGUAGE.SRC));
            MainActivity.voice_text.setText(VOICE_TEXT.STRING);
            MainActivity.voice_text.setSelection(MainActivity.voice_text.getText().length());
        }
        else {
            VOICE_TEXT.STRING = "";
            MainActivity.voice_text.setText("");
        }*/
    }

    @Override
    public void onFinalResult(String hypothesis) {
        /*if (hypothesis != null) {
            results = (((((hypothesis.replace("text", ""))
                    .replace("{", ""))
                    .replace("}", ""))
                    .replace(":", ""))
                    .replace("partial", ""))
                    .replace("\"", "");
        }

        if (RECOGNIZING_STATUS.RECOGNIZING) {
            VOICE_TEXT.STRING = results.toLowerCase(Locale.forLanguageTag(LANGUAGE.SRC));
            MainActivity.voice_text.setText(VOICE_TEXT.STRING);
            MainActivity.voice_text.setSelection(MainActivity.voice_text.getText().length());
        }
        else {
            VOICE_TEXT.STRING = "";
            MainActivity.voice_text.setText("");
        }*/

        /*if (speechStreamService != null) {
            speechStreamService = null;
        }*/
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
        speechService.startListening(this);
    }

    @Override
    public void onTimeout() {
        speechService.startListening(this);
    }

    private void setErrorState(String message) {
        MainActivity.textview_debug.setText(message);
        if (speechService != null) speechService.startListening(this);
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
            String string_recognizing = "recognizing=" + RECOGNIZING_STATUS.RECOGNIZING;
            MainActivity.textview_recognizing.setText(string_recognizing);
            String string_overlaying = "overlaying=" + OVERLAYING_STATUS.OVERLAYING;
            MainActivity.textview_overlaying.setText(string_overlaying);
        } else {
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    public void translate(String t, String src, String dst) {
        translate_api translate = new translate_api();
        translate.setOnTranslationCompleteListener(new translate_api.OnTranslationCompleteListener() {
            @Override
            public void onStartTranslation() {}

            @Override
            public void onCompleted(String text) {
                TRANSLATION_TEXT.STRING = text;
                if (RECOGNIZING_STATUS.RECOGNIZING) {
                    if (TRANSLATION_TEXT.STRING.length() == 0) {
                        create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                        create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                    } else {
                        create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.VISIBLE);
                        create_overlay_translation_text.overlay_translation_text.setVisibility(View.VISIBLE);
                        create_overlay_translation_text.overlay_translation_text.setText(TRANSLATION_TEXT.STRING);
                        create_overlay_translation_text.overlay_translation_text.setSelection(create_overlay_translation_text.overlay_translation_text.getText().length());
                    }
                } else {
                    create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                    create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                //toast("Unknown error");
                setText(MainActivity.textview_debug, e.getMessage());
            }
        });
        translate.execute(t, src, dst);
    }

    public void gtranslate(String t, String src, String dst) {
        google_translate translate = new google_translate();
        translate.setOnTranslationCompleteListener(new google_translate.OnTranslationCompleteListener() {
            @Override
            public void onStartTranslation() {}

            @Override
            public void onCompleted(String translation) {
                TRANSLATION_TEXT.STRING = translation;
                if (RECOGNIZING_STATUS.RECOGNIZING) {
                    if (TRANSLATION_TEXT.STRING.length() == 0) {
                        create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                        create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                    } else {
                        create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.VISIBLE);
                        create_overlay_translation_text.overlay_translation_text.setVisibility(View.VISIBLE);
                        create_overlay_translation_text.overlay_translation_text.setText(TRANSLATION_TEXT.STRING);
                        create_overlay_translation_text.overlay_translation_text.setSelection(create_overlay_translation_text.overlay_translation_text.getText().length());
                    }
                } else {
                    create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                    create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                //toast("Unknown error");
                setText(MainActivity.textview_debug, e.getMessage());
            }
        });
        translate.execute(t, src, dst);
    }

    private void toast(String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setText(final TextView tv, final String text){
        Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                // Any UI task, example
                tv.setText(text);
            }
        };
        handler.sendEmptyMessage(1);
    }

}
