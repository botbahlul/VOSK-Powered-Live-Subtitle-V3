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
import org.apache.http.client.HttpClient;
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
	private volatile int GOOGLE_TRANSLATE_ENDPOINT = 0;
	// 0 = not tested yet
	// 1 = use GoogleTranslate1
	// 2 = use GoogleTranslate2
	// -1 = both endpoint failed

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

			// =========================================================
			// ENDPOINT TEST
			// =========================================================
			GOOGLE_TRANSLATE_ENDPOINT = 0;

			testGoogleTranslateEndpoints();

			// =========================================================
			// TIMER TRANSLATION
			// =========================================================
			timer = new Timer();

			timerTask = new TimerTask() {

				@Override
				public void run() {

					if (VOICE_TEXT.STRING != null &&
							!Objects.equals(VOICE_TEXT.STRING, "")) {

						// =================================================
						// ENDPOINT TEST HAS NOT FINISHED YET
						// =================================================
						if (GOOGLE_TRANSLATE_ENDPOINT == 0) {

							Log.d(
									"GoogleTranslator",
									"Waiting for endpoint test..."
							);

							return;
						}


						// =================================================
						// ENDPOINT 1 SUCCESS
						// =================================================
						if (GOOGLE_TRANSLATE_ENDPOINT == 1) {

							Log.d(
									"GoogleTranslator",
									"Using GoogleTranslate1"
							);

							GoogleTranslate1(
									VOICE_TEXT.STRING,
									LANGUAGE.SRC,
									LANGUAGE.DST
							);

						}


						// =================================================
						// ENDPOINT 2 SUCCESS
						// =================================================
						else if (GOOGLE_TRANSLATE_ENDPOINT == 2) {

							Log.d(
									"GoogleTranslator",
									"Using GoogleTranslate2"
							);

							GoogleTranslate2(
									VOICE_TEXT.STRING,
									LANGUAGE.SRC,
									LANGUAGE.DST
							);

						}


						// =================================================
						// BOTH ENDPOINT FAILED
						// =================================================
						else if (GOOGLE_TRANSLATE_ENDPOINT == -1) {

							Log.e(
									"GoogleTranslator",
									"No working Google Translate endpoint"
							);
						}
					}
				}
			};

			timer.schedule(
					timerTask,
					0,
					2000
			);
		}
		else {

			if (timerTask != null) {
				timerTask.cancel();
			}

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
            setText(MainActivity.textview_output_messages, msg);
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
            //if (results != null && !results.equals("") && !results.isEmpty()) {
                VOICE_TEXT.STRING = results.toLowerCase(Locale.forLanguageTag(LANGUAGE.SRC));
                MainActivity.voice_text.setText(VOICE_TEXT.STRING);
                MainActivity.voice_text.setSelection(MainActivity.voice_text.getText().length());
            //}
            //else {
                //VOICE_TEXT.STRING = "";
                //MainActivity.voice_text.setText("");
            //}
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

	private void testGoogleTranslateEndpoints() {

		ExecutorService executor = Executors.newSingleThreadExecutor();

		executor.execute(() -> {

			boolean endpoint1OK = false;
			boolean endpoint2OK = false;

			HttpClient httpClient = null;

			// =========================================================
			// TEST ENDPOINT 1
			// https://translate.googleapis.com/translate_a/single
			// =========================================================
			try {

				String testSentence = URLEncoder.encode("Hello", "UTF-8");

				String url =
						"https://translate.googleapis.com/translate_a/" +
								"single?client=gtx" +
								"&sl=en" +
								"&tl=id" +
								"&dt=t" +
								"&q=" + testSentence;

				Log.d(
						"GoogleTranslator",
						"Testing endpoint 1: " + url
				);

				httpClient = new DefaultHttpClient();

				HttpGet httpget = new HttpGet(url);

				HttpResponse response =
						httpClient.execute(httpget);

				StatusLine statusLine =
						response.getStatusLine();

				Log.d(
						"GoogleTranslator",
						"Endpoint 1 HTTP: " +
								statusLine.getStatusCode()
				);

				if (statusLine.getStatusCode() == 200) {

					if (response.getEntity() != null) {

						ByteArrayOutputStream output =
								new ByteArrayOutputStream();

						response.getEntity()
								.writeTo(output);

						String responseString =
								output.toString("UTF-8");

						output.close();

						Log.d(
								"GoogleTranslator",
								"Endpoint 1 response: " +
										responseString
						);

						// Pastikan response memang JSON array
						JSONArray jsonArray =
								new JSONArray(responseString);

						if (jsonArray.length() > 0) {
							endpoint1OK = true;
						}
					}
				}

			} catch (Exception e) {

				Log.e(
						"GoogleTranslator",
						"Endpoint 1 FAILED",
						e
				);

			} finally {

				if (httpClient != null) {
					httpClient
							.getConnectionManager()
							.shutdown();
				}
			}


			// =========================================================
			// If endpoint 1 success,choose endpoint 1
			// =========================================================
			if (endpoint1OK) {

				GOOGLE_TRANSLATE_ENDPOINT = 1;

				Log.d(
						"GoogleTranslator",
						"SELECTED ENDPOINT = 1"
				);

				executor.shutdown();
				return;
			}


			// =========================================================
			// TEST ENDPOINT 2
			// https://clients5.google.com/translate_a/t
			// =========================================================
			try {

				String testSentence =
						URLEncoder.encode("Hello", "UTF-8");

				String url =
						"https://clients5.google.com/translate_a/t" +
								"?client=dict-chrome-ex" +
								"&sl=en" +
								"&tl=id" +
								"&q=" + testSentence;

				Log.d(
						"GoogleTranslator",
						"Testing endpoint 2: " + url
				);

				httpClient = new DefaultHttpClient();

				HttpGet httpget =
						new HttpGet(url);

				httpget.setHeader(
						"User-Agent",
						"Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
								"AppleWebKit/537.36 (KHTML, like Gecko) " +
								"Chrome/139.0.0.0 Safari/537.36"
				);

				HttpResponse response =
						httpClient.execute(httpget);

				StatusLine statusLine =
						response.getStatusLine();

				Log.d(
						"GoogleTranslator",
						"Endpoint 2 HTTP: " +
								statusLine.getStatusCode()
				);

				if (statusLine.getStatusCode() == 200) {

					if (response.getEntity() != null) {

						ByteArrayOutputStream output =
								new ByteArrayOutputStream();

						response.getEntity()
								.writeTo(output);

						String responseString =
								output.toString("UTF-8");

						output.close();

						Log.d(
								"GoogleTranslator",
								"Endpoint 2 response: " +
										responseString
						);

						JSONArray jsonArray =
								new JSONArray(responseString);

						if (jsonArray.length() > 0) {
							endpoint2OK = true;
						}
					}
				}

			} catch (Exception e) {

				Log.e(
						"GoogleTranslator",
						"Endpoint 2 FAILED",
						e
				);

			} finally {

				if (httpClient != null) {
					httpClient
							.getConnectionManager()
							.shutdown();
				}
			}


			// =========================================================
			// CHOOSE FINAL ENDPOINT
			// =========================================================

			if (endpoint2OK) {

				GOOGLE_TRANSLATE_ENDPOINT = 2;

				Log.d(
						"GoogleTranslator",
						"SELECTED ENDPOINT = 2"
				);

			} else {

				GOOGLE_TRANSLATE_ENDPOINT = -1;

				Log.e(
						"GoogleTranslator",
						"BOTH GOOGLE TRANSLATE ENDPOINTS FAILED"
				);
			}

			executor.shutdown();
		});
	}

    private void GoogleTranslate1(String SENTENCE, String SRC, String DST) {
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
            HttpClient httpClient;
            try {
                String url = "https://translate.googleapis.com/translate_a/";
                String params = "single?client=gtx&sl=" + SRC + "&tl=" + DST + "&dt=t&q=" + finalSENTENCE;
                httpClient = new DefaultHttpClient();
                HttpGet httpget = new HttpGet(url + params);
                HttpResponse response = httpClient.execute(httpget);
                ByteArrayOutputStream byteArrayOutputStream;
                StatusLine statusLine = response.getStatusLine();
                JSONArray jsonArray, sentence;

                if (statusLine.getStatusCode() == 200) {
                    byteArrayOutputStream = new ByteArrayOutputStream();
                    response.getEntity().writeTo(byteArrayOutputStream);
                    String stringOfByteArrayOutputStream = byteArrayOutputStream.toString();
                    jsonArray = new JSONArray(stringOfByteArrayOutputStream);

                    int sentenceLength = jsonArray.getJSONArray(0).length();

                    sentence = jsonArray.getJSONArray(0);
                    for (int i = 0; i<sentenceLength; i++) {
                        TRANSLATION.set(TRANSLATION + sentence.getJSONArray(i).get(0).toString());
                    }

                } else {
                    response.getEntity().getContent().close();
                    httpClient.getConnectionManager().shutdown();
                    throw new IOException(statusLine.getReasonPhrase());
                }
                byteArrayOutputStream.close();
                httpClient.getConnectionManager().shutdown();
            }

            catch (Exception e) {
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

	private void GoogleTranslate2(String SENTENCE, String SRC, String DST) {

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

			HttpClient httpClient = null;

			try {
				String url = "https://clients5.google.com/translate_a/t";
				String params =
						"?client=dict-chrome-ex"
								+ "&sl=" + SRC
								+ "&tl=" + DST
								+ "&q=" + finalSENTENCE;

				httpClient = new DefaultHttpClient();
				HttpGet httpget = new HttpGet(url + params);

				httpget.setHeader(
						"User-Agent",
						"Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
								"AppleWebKit/537.36 (KHTML, like Gecko) " +
								"Chrome/139.0.0.0 Safari/537.36"
				);

				HttpResponse response = httpClient.execute(httpget);
				StatusLine statusLine = response.getStatusLine();

				if (statusLine.getStatusCode() == 200) {

					ByteArrayOutputStream
							byteArrayOutputStream =
							new ByteArrayOutputStream();

					response.getEntity()
							.writeTo(byteArrayOutputStream);

					String responseString =
							byteArrayOutputStream
									.toString("UTF-8");

					byteArrayOutputStream.close();

					Log.d(
							"GoogleTranslator",
							"Response: " + responseString
					);

					JSONArray jsonArray = new JSONArray(responseString);

					for (int i = 0;
						 i < jsonArray.length();
						 i++) {

						if (!jsonArray.isNull(i)) {

							TRANSLATION.set(
									TRANSLATION.get()
											+ jsonArray
											.getString(i)
							);
						}
					}

					Log.d(
							"GoogleTranslator",
							"TRANSLATION: "
									+ TRANSLATION.get()
					);

				} else {

					Log.e(
							"GoogleTranslator",
							"HTTP "
									+ statusLine.getStatusCode()
									+ ": "
									+ statusLine.getReasonPhrase()
					);

					if (response.getEntity() != null) {

						response.getEntity()
								.getContent()
								.close();
					}

					throw new IOException(
							"HTTP "
									+ statusLine.getStatusCode()
									+ ": "
									+ statusLine.getReasonPhrase()
					);
				}

			} catch (Exception e) {

				Log.e(
						"GoogleTranslator",
						"Translation error",
						e
				);

			} finally {

				if (httpClient != null) {

					httpClient
							.getConnectionManager()
							.shutdown();
				}
			}

			handler.post(() -> {

				TRANSLATION_TEXT.STRING = TRANSLATION.toString();

				Log.d(
						"GoogleTranslator",
						"TRANSLATION_TEXT.STRING: "
								+ TRANSLATION_TEXT.STRING
				);

				if (RECOGNIZING_STATUS.IS_RECOGNIZING) {

					if (TRANSLATION_TEXT.STRING.length() == 0) {

						create_overlay_translation_text
								.overlay_translation_text
								.setVisibility(
										View.INVISIBLE
								);

						create_overlay_translation_text
								.overlay_translation_text_container
								.setVisibility(
										View.INVISIBLE
								);

					}

					else {

						create_overlay_translation_text
								.overlay_translation_text_container
								.setVisibility(
										View.VISIBLE
								);

						create_overlay_translation_text
								.overlay_translation_text_container
								.setBackgroundColor(
										Color.TRANSPARENT
								);

						create_overlay_translation_text
								.overlay_translation_text
								.setVisibility(
										View.VISIBLE
								);

						create_overlay_translation_text
								.overlay_translation_text
								.setBackgroundColor(
										Color.TRANSPARENT
								);

						create_overlay_translation_text
								.overlay_translation_text
								.setTextIsSelectable(
										true
								);

						create_overlay_translation_text
								.overlay_translation_text
								.setText(
										TRANSLATION_TEXT.STRING
								);

						create_overlay_translation_text
								.overlay_translation_text
								.setSelection(
										create_overlay_translation_text
												.overlay_translation_text
												.getText()
												.length()
								);

						Spannable spannableString =
								new SpannableStringBuilder(
										TRANSLATION_TEXT.STRING
								);

						int selectionEnd =
								create_overlay_translation_text
										.overlay_translation_text
										.getSelectionEnd();

						spannableString.setSpan(
								new ForegroundColorSpan(
										Color.YELLOW
								),
								0,
								selectionEnd,
								0
						);

						spannableString.setSpan(
								new BackgroundColorSpan(
										Color.parseColor(
												"#80000000"
										)
								),
								0,
								selectionEnd,
								0
						);

						create_overlay_translation_text
								.overlay_translation_text
								.setText(
										spannableString
								);

						create_overlay_translation_text
								.overlay_translation_text
								.setSelection(
										create_overlay_translation_text
												.overlay_translation_text
												.getText()
												.length()
								);
					}

				} else {

					create_overlay_translation_text
							.overlay_translation_text
							.setVisibility(View.INVISIBLE);

					create_overlay_translation_text
							.overlay_translation_text_container
							.setVisibility(View.INVISIBLE);
				}
			});
		});
	}

}
