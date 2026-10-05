package org.vosk.livesubtitle;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class VoskVoiceRecognizer extends Service implements RecognitionListener {
    public VoskVoiceRecognizer() {}

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private Translator translator;
    private TranslatorOptions options;
    private DownloadConditions conditions;
    private String results;
    private String mlkit_status_message;
    private Timer timer = new Timer();
    private TimerTask timerTask;

    @Override
    public void onCreate() {
        super.onCreate();
        LibVosk.setLogLevel(LogLevel.INFO);
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        if (speechStreamService != null) {
            speechStreamService.stop();
            speechStreamService = null;
        }
        if (translator != null) {
            translator.close();
        }

        int h;
        if (Objects.equals(LANGUAGE.SRC, "ja") || Objects.equals(LANGUAGE.SRC, "zh-Hans") || Objects.equals(LANGUAGE.SRC, "zh-Hant")) {
            h = 122;
        }
        else {
            h = 109;
        }
        MainActivity.voice_text.setHeight((int) (h * getResources().getDisplayMetrics().density));

        RECOGNIZING_STATUS.STRING = "RECOGNIZING_STATUS.IS_RECOGNIZING = " + RECOGNIZING_STATUS.IS_RECOGNIZING;
        setText(MainActivity.textview_recognizing, RECOGNIZING_STATUS.STRING);
        OVERLAYING_STATUS.STRING = "OVERLAYING_STATUS.IS_OVERLAYING = " + OVERLAYING_STATUS.IS_OVERLAYING;
        setText(MainActivity.textview_overlaying, OVERLAYING_STATUS.STRING);

        options = new TranslatorOptions.Builder()
                .setSourceLanguage(LANGUAGE.SRC)
                .setTargetLanguage(LANGUAGE.DST)
                .build();
        translator = Translation.getClient(options);
        conditions = new DownloadConditions.Builder().build();

        if (!MLKIT_DICTIONARY.READY) {
            mlkit_status_message = "Downloading MLKIT dictionary, please be patient";
            setText(MainActivity.textview_output_messages, mlkit_status_message);

            translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener(unused -> {
                        MLKIT_DICTIONARY.READY = true;
                        mlkit_status_message = "MLKIT dictionary is ready";
                        setText(MainActivity.textview_mlkit_status, mlkit_status_message);
                    })
                    .addOnFailureListener(e -> {});
        }
        else {
            new Handler(Looper.getMainLooper()).post(() -> {
                mlkit_status_message = "MLKIT dictionary download completed";
                setText(MainActivity.textview_output_messages, mlkit_status_message);
                mlkit_status_message = "MLKIT dictionary is ready";
                setText(MainActivity.textview_mlkit_status, mlkit_status_message);
            });
        }

        if (Objects.equals(VOSK_MODEL.ISO_CODE, "en-US")) {
            initModel();
        } else {
            initDownloadedModel();
        }

        if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
            Handler handler = new Handler(Looper.getMainLooper());
            Executor executor = Executors.newSingleThreadExecutor();
            timer = new Timer();
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    if (VOICE_TEXT.STRING != null && MLKIT_DICTIONARY.READY) {
                        executor.execute(() -> translator.translate(VOICE_TEXT.STRING).addOnSuccessListener(s -> TRANSLATION_TEXT.STRING = s.toLowerCase(Locale.forLanguageTag(LANGUAGE.DST))).addOnFailureListener(e -> {}));
                        handler.post(() -> {
                            if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
                                if (TRANSLATION_TEXT.STRING.length() == 0) {
                                    create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                                    create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                                } else {
                                    create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.VISIBLE);
                                    create_overlay_translation_text.overlay_translation_text_container.setBackgroundColor(Color.TRANSPARENT);
                                    create_overlay_translation_text.overlay_translation_text.setVisibility(View.VISIBLE);
                                    create_overlay_translation_text.overlay_translation_text.setBackgroundColor(Color.TRANSPARENT);
                                    create_overlay_translation_text.overlay_translation_text.setTextIsSelectable(true);
                                    create_overlay_translation_text.overlay_translation_text.setText(TRANSLATION_TEXT.STRING);
                                    create_overlay_translation_text.overlay_translation_text.setSelection(create_overlay_translation_text.overlay_translation_text.getText().length());
                                    Spannable spannableString = new SpannableStringBuilder(TRANSLATION_TEXT.STRING);
                                    spannableString.setSpan(new ForegroundColorSpan(Color.YELLOW),
                                            0,
                                            create_overlay_translation_text.overlay_translation_text.getSelectionEnd(),
                                            0);
                                    spannableString.setSpan(new BackgroundColorSpan(Color.parseColor("#80000000")),
                                            0,
                                            create_overlay_translation_text.overlay_translation_text.getSelectionEnd(),
                                            0);
                                    create_overlay_translation_text.overlay_translation_text.setText(spannableString);
                                    create_overlay_translation_text.overlay_translation_text.setSelection(create_overlay_translation_text.overlay_translation_text.getText().length());
                                }
                            } else {
                                VOICE_TEXT.STRING = "";
                                TRANSLATION_TEXT.STRING = "";
                                create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                                create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                            }
                        });
                    }
                    else {
                        TRANSLATION_TEXT.STRING = "";
                        create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                        create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                    }
                }
            };
            timer.schedule(timerTask,0,1000);
        }
        else {
            if (timerTask != null) timerTask.cancel();
            if (timer != null) {
                timer.cancel();
                timer.purge();
            }
            stopSelf();
        }

    }

    private void initModel() {
        StorageService.unpack(this, VOSK_MODEL.ISO_CODE, "model", (model) -> {
            this.model = model;
            recognizeMicrophone();
        }, (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
    }

    private void initDownloadedModel() {
        if (new File(VOSK_MODEL.EXTRACTED_PATH + VOSK_MODEL.ISO_CODE).exists()) {
            model = new Model(VOSK_MODEL.USED_PATH);
            recognizeMicrophone();
        } else {
            if (create_overlay_mic_button.mic_button != null) create_overlay_mic_button.mic_button.setImageResource(R.drawable.ic_mic_black_off);
            RECOGNIZING_STATUS.IS_RECOGNIZING = false;
            RECOGNIZING_STATUS.STRING = "RECOGNIZING_STATUS.IS_RECOGNIZING = " + RECOGNIZING_STATUS.IS_RECOGNIZING;
            MainActivity.textview_recognizing.setText(RECOGNIZING_STATUS.STRING);
            String hints = "Recognized words";
            MainActivity.voice_text.setHint(hints);
            stopSelf();
            String string_msg = "You need to download the model first";
            setText(MainActivity.textview_output_messages,string_msg);
            //toast(msg);
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
        if (timerTask != null) timerTask.cancel();
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        if (translator != null) {
            translator.close();
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
        if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
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
        if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
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

        if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
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
        setText(MainActivity.textview_output_messages, message);
        if (speechService != null) speechService.startListening(this);
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
            RECOGNIZING_STATUS.STRING = "RECOGNIZING_STATUS.IS_RECOGNIZING = " + RECOGNIZING_STATUS.IS_RECOGNIZING;
            MainActivity.textview_recognizing.setText(RECOGNIZING_STATUS.STRING);
            OVERLAYING_STATUS.STRING = "OVERLAYING_STATUS.IS_OVERLAYING = " + OVERLAYING_STATUS.IS_OVERLAYING;
            MainActivity.textview_overlaying.setText(OVERLAYING_STATUS.STRING);
        } else {
            MainActivity.textview_output_messages.setText("");
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    public void setText(final TextView tv, final String text){
        new Handler(Looper.getMainLooper()).post(() -> tv.setText(text));
    }

    /*private void toast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }*/

}
