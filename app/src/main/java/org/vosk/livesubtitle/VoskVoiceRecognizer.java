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
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

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
    private String results;
    Timer timer = new Timer();
    TimerTask timerTask;
    /*android.text.TextWatcher tw = new android.text.TextWatcher() {
        public void afterTextChanged(android.text.Editable s) {}
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            GoogleTranslator translate = new GoogleTranslator();
            if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
                if (MainActivity.voice_text != null) {
                    //get_translation(MainActivity.voice_text.getText(), LANGUAGE.SRC, LANGUAGE.DST);
                    if (MainActivity.voice_text.length() > 0) {
                        MainActivity.textview_output_messages2.setText(mlkit_status_message);
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
                translate.setOnTranslationCompleteListener(new GoogleTranslator.OnTranslationCompleteListener() {
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

        if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
            timer = new Timer();
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    if (VOICE_TEXT.STRING != null) {
                        //translate(VOICE_TEXT.STRING, LANGUAGE.SRC, LANGUAGE.DST);
                        GoogleTranslate(VOICE_TEXT.STRING, LANGUAGE.SRC, LANGUAGE.DST);
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
            String msg = "You have to download the model first";
            //toast(msg);
            setText(MainActivity.textview_output_messages, msg);
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
        MainActivity.textview_output_messages.setText(message);
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

    private void GoogleTranslate(String SENTENCE, String SRC, String DST) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicReference<String> TRANSLATION = new AtomicReference<>("");
        try {
            SENTENCE = URLEncoder.encode(SENTENCE, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        String finalSENTENCE = SENTENCE;
        executor.execute(() -> {
            try {
                String url = "https://translate.googleapis.com/translate_a/";
                String params = "single?client=gtx&sl=" + SRC + "&tl=" + DST + "&dt=t&q=" + finalSENTENCE;
                HttpResponse response = new DefaultHttpClient().execute(new HttpGet(url+params));
                StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() == 200) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    response.getEntity().writeTo(byteArrayOutputStream);
                    String stringOfByteArrayOutputStream = byteArrayOutputStream.toString();
                    byteArrayOutputStream.close();
                    JSONArray jSONArray = new JSONArray(stringOfByteArrayOutputStream).getJSONArray(0);
                    for (int i = 0; i < jSONArray.length(); i++) {
                        JSONArray jSONArray2 = jSONArray.getJSONArray(i);
                        TRANSLATION.set(TRANSLATION + jSONArray2.get(0).toString());
                    }

                }
                else {
                    response.getEntity().getContent().close();
                    throw new IOException(statusLine.getReasonPhrase());
                }
            } catch (Exception e) {
                Log.e("GoogleTranslator",e.getMessage());
                e.printStackTrace();
            }

            handler.post(() -> {
                TRANSLATION_TEXT.STRING = TRANSLATION.toString();
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
                    create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                    create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                }
            });
        });
    }

    /*public void gtranslate1(String t, String src, String dst) {
        GoogleTranslator translate = new GoogleTranslator();
        translate.setOnTranslationCompleteListener(new GoogleTranslator.OnTranslationCompleteListener() {
            @Override
            public void onStartTranslation() {}

            @Override
            public void onCompleted(String text) {
                TRANSLATION_TEXT.STRING = text;
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
                    create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
                    create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                //toast("Unknown error");
                setText(MainActivity.textview_output_messages, e.getMessage());
            }
        });
        translate.execute(t, src, dst);
    }*/

    /*public void gtranslate2(String t, String src, String dst) {
        GoogleTranslatorClient5 translate = new GoogleTranslatorClient5();
        translate.setOnTranslationCompleteListener(new GoogleTranslatorClient5.OnTranslationCompleteListener() {
            @Override
            public void onStartTranslation() {}

            @Override
            public void onCompleted(String translation) {
                TRANSLATION_TEXT.STRING = translation;
                if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
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
                setText(MainActivity.textview_output_messages, e.getMessage());
            }
        });
        translate.execute(t, src, dst);
    }*/

    /*private void toast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }*/

}
