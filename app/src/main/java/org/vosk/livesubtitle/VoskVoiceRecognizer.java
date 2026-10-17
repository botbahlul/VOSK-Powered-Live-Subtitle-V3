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
import android.view.Gravity;
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

	public String lastVoiceString = "";
	public String translatedLastVoiceString = "";

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
		MainActivity.full_voice_text.setHeight((int) (h * getResources().getDisplayMetrics().density));

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
					if (VOICE_TEXT.STRING != null && !Objects.equals(VOICE_TEXT.STRING, "")) {
						// =================================================
						// ENDPOINT TEST HAS NOT FINISHED YET
						// =================================================
						if (GOOGLE_TRANSLATE_ENDPOINT == 0) {
							Log.d("GoogleTranslator", "Waiting for endpoint test...");
							return;
						}
						// =================================================
						// ENDPOINT 1 SUCCESS
						// =================================================
						if (GOOGLE_TRANSLATE_ENDPOINT == 1) {
							//Log.d("GoogleTranslator", "Using GoogleTranslate1");
							GoogleTranslate1(VOICE_TEXT.STRING, LANGUAGE.SRC, LANGUAGE.DST);
						}
						// =================================================
						// ENDPOINT 2 SUCCESS
						// =================================================
						else if (GOOGLE_TRANSLATE_ENDPOINT == 2) {
							//Log.d("GoogleTranslator", "Using GoogleTranslate2");
							GoogleTranslate2(VOICE_TEXT.STRING, LANGUAGE.SRC, LANGUAGE.DST);
						}
						// =================================================
						// BOTH ENDPOINT FAILED
						// =================================================
						else if (GOOGLE_TRANSLATE_ENDPOINT == -1) {
							Log.e("GoogleTranslator", "No working Google Translate endpoint");
						}
					}
				}
			};
			timer.schedule(timerTask, 0, 2000);
		} else {
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
			MainActivity.voice_text.setText("");
			MainActivity.voice_text.setHint("Recognized words");
			MainActivity.full_voice_text.setText("");
			MainActivity.full_voice_text.setHint("Recognized sentences");
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
			results = ((((((hypothesis.replace("text", ""))
					.replace("{", ""))
					.replace("}", ""))
					.replace(":", ""))
					.replace(".", ""))
					.replace("partial", ""))
					.replace("\"", "");
        }
        if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
			VOICE_TEXT.STRING = results.toLowerCase(Locale.forLanguageTag(LANGUAGE.SRC));
			MainActivity.voice_text.setText(VOICE_TEXT.STRING);
			MainActivity.voice_text.setSelection(MainActivity.voice_text.getText().length());
        }
    }

    @Override
    public void onResult(String hypothesis) {
		if (hypothesis != null) {
			results = (((((((hypothesis.replace("text", ""))
					.replace("{", ""))
					.replace("}", ""))
					.replace(":", ""))
					.replace(".", ""))
					.replace("\n", ""))
					.replace("partial", ""))
					.replace("\"", "");
		}

		if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
			VOICE_TEXT.STRING = results.toLowerCase(Locale.forLanguageTag(LANGUAGE.SRC));
			if (!VOICE_TEXT.STRING.isEmpty()) {
				lastVoiceString = VOICE_TEXT.STRING;
			}
			if (MainActivity.full_voice_text != null && !lastVoiceString.isEmpty()) {
				appendText(MainActivity.full_voice_text, lastVoiceString);
				MainActivity.full_voice_text.setSelection(MainActivity.full_voice_text.getText().length());
				MainActivity.full_voice_text.setGravity(Gravity.BOTTOM);
				if (lastVoiceString != null && !Objects.equals(lastVoiceString, "")) {
					appendText(MainActivity.full_voice_text, lastVoiceString);
					MainActivity.full_voice_text.setSelection(MainActivity.full_voice_text.getText().length());
					MainActivity.full_voice_text.setGravity(Gravity.BOTTOM);
					if (lastVoiceString != null && !Objects.equals(lastVoiceString, "")) {
						// =================================================
						// ENDPOINT TEST HAS NOT FINISHED YET
						// =================================================
						if (GOOGLE_TRANSLATE_ENDPOINT == 0) {
							Log.d("testGoogleTranslate", "Waiting for endpoint test...");
							return;
						}
						// =================================================
						// ENDPOINT 1 SUCCESS
						// =================================================
						if (GOOGLE_TRANSLATE_ENDPOINT == 1) {
							//Log.d("testGoogleTranslate", "Using FullGoogleTranslate1");
							FullGoogleTranslate1(lastVoiceString, LANGUAGE.SRC, LANGUAGE.DST);
						}
						// =================================================
						// ENDPOINT 2 SUCCESS
						// =================================================
						else if (GOOGLE_TRANSLATE_ENDPOINT == 2) {
							//Log.d("testGoogleTranslate", "Using FullGoogleTranslate2");
							FullGoogleTranslate2(lastVoiceString, LANGUAGE.SRC, LANGUAGE.DST);

						}
						// =================================================
						// BOTH ENDPOINT FAILED
						// =================================================
						else if (GOOGLE_TRANSLATE_ENDPOINT == -1) {
							Log.e("testGoogleTranslate", "No working Google Translate endpoint");
							if (create_overlay_translation_text.overlay_full_translation_text != null) {
								create_overlay_translation_text.overlay_full_translation_text.setVisibility(View.INVISIBLE);
								create_overlay_translation_text.overlay_full_translation_text_container.setVisibility(View.INVISIBLE);
							}
						}
					}
				}
				lastVoiceString = "";
			}
		}
    }

    @Override
    public void onFinalResult(String hypothesis) {
		if (hypothesis != null) {
			results = ((((((hypothesis.replace("text", ""))
					.replace("{", ""))
					.replace("}", ""))
					.replace(":", ""))
					.replace(".", ""))
					.replace("partial", ""))
					.replace("\"", "");
		}

        if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
            VOICE_TEXT.STRING = results.toLowerCase(Locale.forLanguageTag(LANGUAGE.SRC));
            MainActivity.voice_text.setText(VOICE_TEXT.STRING);
            MainActivity.voice_text.setSelection(MainActivity.voice_text.getText().length());
        }

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

	private void appendText(final TextView tv, final String text){
		if (tv != null) {
			new Handler(Looper.getMainLooper()).post(() -> tv.append(text));
		}
	}

	private void testGoogleTranslateEndpoints() {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(() -> {
			boolean SELECT_ENDPOINT_1 = false;
			boolean SELECT_ENDPOINT_2 = false;
			HttpClient httpClient = null;
			// =========================================================
			// TEST ENDPOINT 1
			// https://clients5.google.com/translate_a/t
			// =========================================================
			try {
				String testSentence = URLEncoder.encode("Hello", "UTF-8");
				String url =
						"https://clients5.google.com/translate_a/t" +
								"?client=dict-chrome-ex" +
								"&sl=en" +
								"&tl=id" +
								"&q=" + testSentence;
				Log.d("testGoogleTranslate", "Testing endpoint 1: " + url);
				httpClient = new DefaultHttpClient();
				HttpGet httpget = new HttpGet(url);
				httpget.setHeader(
						"User-Agent",
						"Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
								"AppleWebKit/537.36 (KHTML, like Gecko) " +
								"Chrome/139.0.0.0 Safari/537.36"
				);
				HttpResponse response = httpClient.execute(httpget);
				StatusLine statusLine = response.getStatusLine();
				Log.d("testGoogleTranslate", "Endpoint 1 status code: " + statusLine.getStatusCode());
				if (statusLine.getStatusCode() == 200) {
					if (response.getEntity() != null) {
						ByteArrayOutputStream output = new ByteArrayOutputStream();
						response.getEntity().writeTo(output);
						String responseString = output.toString("UTF-8");
						output.close();
						Log.d("testGoogleTranslate", "Endpoint 1 response: " + responseString);
						JSONArray jsonArray = new JSONArray(responseString);
						if (jsonArray.length() > 0) {
							SELECT_ENDPOINT_1 = true;
						}
					}
				}

			} catch (Exception e) {
				Log.e("testGoogleTranslate", "Endpoint 1 FAILED",e);

			} finally {
				if (httpClient != null) {
					httpClient.getConnectionManager().shutdown();
				}
			}

			// =========================================================
			// TEST ENDPOINT 2
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
				Log.d("testGoogleTranslate", "Testing endpoint 2: " + url);
				httpClient = new DefaultHttpClient();
				HttpGet httpget = new HttpGet(url);
				HttpResponse response = httpClient.execute(httpget);
				StatusLine statusLine = response.getStatusLine();
				Log.d("testGoogleTranslate", "Endpoint 2 status code: " + statusLine.getStatusCode());

				if (statusLine.getStatusCode() == 200) {
					if (response.getEntity() != null) {
						ByteArrayOutputStream output = new ByteArrayOutputStream();
						response.getEntity().writeTo(output);
						String responseString = output.toString("UTF-8");
						output.close();
						Log.d("testGoogleTranslate", "Endpoint 2 response: " +responseString);
						JSONArray jsonArray = new JSONArray(responseString);
						if (jsonArray.length() > 0) {
							SELECT_ENDPOINT_2 = true;
						}
					}
				}

			} catch (Exception e) {
				Log.e("testGoogleTranslate", "Endpoint 2 FAILED", e);

			} finally {
				if (httpClient != null) {
					httpClient.getConnectionManager().shutdown();
				}
			}

			// =========================================================
			// If endpoint 1 success,choose endpoint 1
			// =========================================================
			if (SELECT_ENDPOINT_1) {
				GOOGLE_TRANSLATE_ENDPOINT = 1;
				Log.d("testGoogleTranslate", "SELECTED ENDPOINT = 1");
				executor.shutdown();
				return;
			}

			// =========================================================
			// CHOOSE FINAL ENDPOINT
			// =========================================================
			if (SELECT_ENDPOINT_2) {
				GOOGLE_TRANSLATE_ENDPOINT = 2;
				Log.d("testGoogleTranslate", "SELECTED ENDPOINT = 2");
			} else {
				GOOGLE_TRANSLATE_ENDPOINT = -1;
				Log.e("testGoogleTranslate", "BOTH GOOGLE TRANSLATE ENDPOINTS FAILED");
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
					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					response.getEntity().writeTo(byteArrayOutputStream);
					String responseString = byteArrayOutputStream.toString("UTF-8");
					byteArrayOutputStream.close();
					//Log.d("GoogleTranslate1", "Response: " + responseString);
					JSONArray jsonArray = new JSONArray(responseString);
					for (int i = 0; i < jsonArray.length(); i++) {
						if (!jsonArray.isNull(i)) {
							TRANSLATION.set(TRANSLATION.get()+ jsonArray.getString(i));
						}
					}
					//Log.d("GoogleTranslate1", "TRANSLATION: " + TRANSLATION.get());

				} else {
					//Log.e("GoogleTranslate1", "HTTP " + statusLine.getStatusCode() + ": " + statusLine.getReasonPhrase());
					if (response.getEntity() != null) {
						response.getEntity().getContent().close();
					}
					throw new IOException("http " + statusLine.getStatusCode() + ": " + statusLine.getReasonPhrase());
				}

			} catch (Exception e) {
				Log.e("GoogleTranslate1", "Translation error", e);

			} finally {
				if (httpClient != null) {
					httpClient.getConnectionManager().shutdown();
				}
			}

			handler.post(() -> {
				TRANSLATION_TEXT.STRING = TRANSLATION.toString();
				if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
					if (create_overlay_translation_text.overlay_translation_text_container != null) {
						create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.VISIBLE);
						create_overlay_translation_text.overlay_translation_text_container.setBackgroundColor(Color.parseColor("#80000000"));
						create_overlay_translation_text.overlay_translation_text.setVisibility(View.VISIBLE);
						create_overlay_translation_text.overlay_translation_text.setBackgroundColor(Color.TRANSPARENT);
						create_overlay_translation_text.overlay_translation_text.setTextIsSelectable(true);
						create_overlay_translation_text.overlay_translation_text.setHighlightColor(Color.TRANSPARENT);
						create_overlay_translation_text.overlay_translation_text.setText(TRANSLATION_TEXT.STRING);
						create_overlay_translation_text.overlay_translation_text.setSelection(create_overlay_translation_text.overlay_translation_text.getText().length());
						create_overlay_translation_text.overlay_translation_text.setTextColor(Color.parseColor("#FFFF00"));
						//android.util.Log.d("SubtitleDebug", "textColor=" + Integer.toHexString(create_overlay_translation_text.overlay_translation_text.getCurrentTextColor()) + ", alpha=" + create_overlay_translation_text.overlay_translation_text.getAlpha() + ", layerType=" + create_overlay_translation_text.overlay_translation_text.getLayerType() + ", backgroundAlpha=" + (create_overlay_translation_text.overlay_translation_text.getBackground() != null ? create_overlay_translation_text.overlay_translation_text.getBackground().getAlpha() : -1));
					}
				} else {
					if (create_overlay_translation_text.overlay_translation_text_container != null) {
						create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
						create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
					}
				}
			});
		});
	}

	private String GoogleTranslate2(String SENTENCE, String SRC, String DST) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Handler handler = new Handler(Looper.getMainLooper());
		AtomicReference<String> TRANSLATION = new AtomicReference<>("");
		try {
			SENTENCE = URLEncoder.encode(SENTENCE, "utf-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		String finalSENTENCE = SENTENCE;
		if (RECOGNIZING_STATUS.IS_RECOGNIZING && finalSENTENCE != null) {
			executor.execute(() -> {
				HttpClient httpClient;
				try {
					String url = "https://translate.googleapis.com/translate_a/";
					String params = "single?client=gtx&sl=" + SRC + "&tl=" + DST + "&dt=t&q=" + finalSENTENCE;
					httpClient = new DefaultHttpClient();
					HttpResponse response = httpClient.execute(new HttpGet(url + params));
					ByteArrayOutputStream byteArrayOutputStream;
					StatusLine statusLine = response.getStatusLine();
					JSONArray jsonArray;
					if (statusLine.getStatusCode() == 200) {
						byteArrayOutputStream = new ByteArrayOutputStream();
						response.getEntity().writeTo(byteArrayOutputStream);
						String stringOfByteArrayOutputStream = byteArrayOutputStream.toString();
						try {
							jsonArray = new JSONArray(Objects.requireNonNull(stringOfByteArrayOutputStream)).getJSONArray(0);
							//Log.d("GoogleTranslate2", "jsonArray = " + jsonArray);
							int length = jsonArray.length();
							for (int i = 0; i < length; i++) {
								TRANSLATION.set(TRANSLATION + new JSONArray(Objects.requireNonNull(stringOfByteArrayOutputStream)).getJSONArray(0).getJSONArray(i).get(0).toString());
							}
						}
						catch(Exception e) {
							//Log.e("GoogleTranslate2", e.getMessage());
							e.printStackTrace();
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
					Log.e("GoogleTranslate2", e.getMessage());
					e.printStackTrace();
				}

				handler.post(() -> {
					TRANSLATION_TEXT.STRING = TRANSLATION.toString();
					if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
						if (create_overlay_translation_text.overlay_translation_text_container != null) {
							create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.VISIBLE);
							create_overlay_translation_text.overlay_translation_text_container.setBackgroundColor(Color.parseColor("#80000000"));
							create_overlay_translation_text.overlay_translation_text.setVisibility(View.VISIBLE);
							create_overlay_translation_text.overlay_translation_text.setBackgroundColor(Color.TRANSPARENT);
							create_overlay_translation_text.overlay_translation_text.setTextIsSelectable(true);
							create_overlay_translation_text.overlay_translation_text.setHighlightColor(Color.TRANSPARENT);
							create_overlay_translation_text.overlay_translation_text.setText(TRANSLATION_TEXT.STRING);
							create_overlay_translation_text.overlay_translation_text.setSelection(create_overlay_translation_text.overlay_translation_text.getText().length());
							create_overlay_translation_text.overlay_translation_text.setTextColor(Color.parseColor("#FFFF00"));
							//android.util.Log.d("SubtitleDebug", "textColor=" + Integer.toHexString(create_overlay_translation_text.overlay_translation_text.getCurrentTextColor()) + ", alpha=" + create_overlay_translation_text.overlay_translation_text.getAlpha() + ", layerType=" + create_overlay_translation_text.overlay_translation_text.getLayerType() + ", backgroundAlpha=" + (create_overlay_translation_text.overlay_translation_text.getBackground() != null ? create_overlay_translation_text.overlay_translation_text.getBackground().getAlpha() : -1));
						}
					} else {
						if (create_overlay_translation_text.overlay_translation_text_container != null) {
							create_overlay_translation_text.overlay_translation_text.setVisibility(View.INVISIBLE);
							create_overlay_translation_text.overlay_translation_text_container.setVisibility(View.INVISIBLE);
						}
					}
				});
			});
		}
		else {
			executor.shutdown();
		}
		return TRANSLATION.toString();
	}

	private void FullGoogleTranslate1(String SENTENCE, String SRC, String DST) {
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
					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					response.getEntity().writeTo(byteArrayOutputStream);
					String responseString = byteArrayOutputStream.toString("UTF-8");
					byteArrayOutputStream.close();
					//Log.d("FullGoogleTranslate1", "Response: " + responseString);
					JSONArray jsonArray = new JSONArray(responseString);
					for (int i = 0; i < jsonArray.length(); i++) {
						if (!jsonArray.isNull(i)) {
							TRANSLATION.set(TRANSLATION.get()+ jsonArray.getString(i));
						}
					}
					//Log.d("FullGoogleTranslate1", "TRANSLATION: " + TRANSLATION.get());

				} else {
					//Log.e("FullGoogleTranslate1", "HTTP " + statusLine.getStatusCode() + ": " + statusLine.getReasonPhrase());
					if (response.getEntity() != null) {
						response.getEntity().getContent().close();
					}
					throw new IOException("http " + statusLine.getStatusCode() + ": " + statusLine.getReasonPhrase());
				}

			} catch (Exception e) {
				Log.e("FullGoogleTranslate1", "Translation error", e);

			} finally {
				if (httpClient != null) {
					httpClient.getConnectionManager().shutdown();
				}
			}

			handler.post(() -> {
				translatedLastVoiceString = TRANSLATION.toString();
				if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
					if (create_overlay_translation_text.overlay_full_translation_text_container != null) {
						create_overlay_translation_text.overlay_full_translation_text_container.setVisibility(View.VISIBLE);
						create_overlay_translation_text.overlay_full_translation_text_container.setBackgroundColor(Color.parseColor("#80000000"));
						create_overlay_translation_text.overlay_full_translation_text.setVisibility(View.VISIBLE);
						create_overlay_translation_text.overlay_full_translation_text.setBackgroundColor(Color.TRANSPARENT);
						create_overlay_translation_text.overlay_full_translation_text.setTextIsSelectable(true);
						create_overlay_translation_text.overlay_full_translation_text.setHighlightColor(Color.TRANSPARENT);
						appendText(create_overlay_translation_text.overlay_full_translation_text, "\n" + translatedLastVoiceString);
						create_overlay_translation_text.overlay_full_translation_text.setSelection(create_overlay_translation_text.overlay_full_translation_text.getText().length());
						create_overlay_translation_text.overlay_full_translation_text.setTextColor(Color.parseColor("#FF00FFFF"));
						// 1. Makes Spannable
						Spannable spannableString = new SpannableStringBuilder(translatedLastVoiceString);

						// 2. Validate the index to ensure it does not exceed the text length (prevents crashes)
						int textLength = translatedLastVoiceString.length();
						int selectionEnd = create_overlay_translation_text.overlay_translation_text.getSelectionEnd();
						int safeEnd = Math.max(0, Math.min(selectionEnd, textLength)); // Aman dari IndexOutOfBounds

						// 3. Apply Span using safeEnd
						spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#FFFF00")),
								0,
								safeEnd,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Gunakan flag standar yang valid

						spannableString.setSpan(new BackgroundColorSpan(Color.parseColor("#80000000")),
								0,
								safeEnd,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

						create_overlay_translation_text.overlay_translation_text.setText(spannableString);
						create_overlay_translation_text.overlay_translation_text.setSelection(create_overlay_translation_text.overlay_translation_text.getText().length());
						create_overlay_translation_text.overlay_full_translation_text.setGravity(Gravity.BOTTOM);

						create_overlay_translation_text.overlay_full_translation_text.setGravity(Gravity.BOTTOM);
					}
				} else {
					if (create_overlay_translation_text.overlay_full_translation_text_container != null) {
						create_overlay_translation_text.overlay_full_translation_text.setVisibility(View.INVISIBLE);
						create_overlay_translation_text.overlay_full_translation_text_container.setVisibility(View.INVISIBLE);
					}
				}
			});
		});
	}

	private String FullGoogleTranslate2(String SENTENCE, String SRC, String DST) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Handler handler = new Handler(Looper.getMainLooper());
		AtomicReference<String> TRANSLATION = new AtomicReference<>("");
		try {
			SENTENCE = URLEncoder.encode(SENTENCE, "utf-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		String finalSENTENCE = SENTENCE;
		if (RECOGNIZING_STATUS.IS_RECOGNIZING && finalSENTENCE != null) {
			executor.execute(() -> {
				HttpClient httpClient;
				try {
					String url = "https://translate.googleapis.com/translate_a/";
					String params = "single?client=gtx&sl=" + SRC + "&tl=" + DST + "&dt=t&q=" + finalSENTENCE;
					httpClient = new DefaultHttpClient();
					HttpResponse response = httpClient.execute(new HttpGet(url + params));
					ByteArrayOutputStream byteArrayOutputStream;
					StatusLine statusLine = response.getStatusLine();
					JSONArray jsonArray;
					if (statusLine.getStatusCode() == 200) {
						byteArrayOutputStream = new ByteArrayOutputStream();
						response.getEntity().writeTo(byteArrayOutputStream);
						String stringOfByteArrayOutputStream = byteArrayOutputStream.toString();
						try {
							jsonArray = new JSONArray(Objects.requireNonNull(stringOfByteArrayOutputStream)).getJSONArray(0);
							//Log.d("FullGoogleTranslate2", "jsonArray = " + jsonArray);
							int length = jsonArray.length();
							for (int i = 0; i < length; i++) {
								TRANSLATION.set(TRANSLATION + new JSONArray(Objects.requireNonNull(stringOfByteArrayOutputStream)).getJSONArray(0).getJSONArray(i).get(0).toString());
							}
						}
						catch(Exception e) {
							//Log.e("FullGoogleTranslate2", e.getMessage());
							e.printStackTrace();
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
					Log.e("FullGoogleTranslate2", e.getMessage());
					e.printStackTrace();
				}

				handler.post(() -> {
					translatedLastVoiceString = TRANSLATION.toString();
					if (RECOGNIZING_STATUS.IS_RECOGNIZING) {
						if (create_overlay_translation_text.overlay_full_translation_text_container != null) {
							create_overlay_translation_text.overlay_full_translation_text_container.setVisibility(View.VISIBLE);
							create_overlay_translation_text.overlay_full_translation_text_container.setBackgroundColor(Color.parseColor("#80000000"));
							create_overlay_translation_text.overlay_full_translation_text.setVisibility(View.VISIBLE);
							create_overlay_translation_text.overlay_full_translation_text.setBackgroundColor(Color.TRANSPARENT);
							create_overlay_translation_text.overlay_full_translation_text.setTextIsSelectable(true);
							create_overlay_translation_text.overlay_full_translation_text.setHighlightColor(Color.TRANSPARENT);
							appendText(create_overlay_translation_text.overlay_full_translation_text, "\n" + translatedLastVoiceString);
							create_overlay_translation_text.overlay_full_translation_text.setSelection(create_overlay_translation_text.overlay_full_translation_text.getText().length());
							create_overlay_translation_text.overlay_full_translation_text.setTextColor(Color.parseColor("#FF00FFFF"));
							// 1. Makes Spannable
							Spannable spannableString = new SpannableStringBuilder(translatedLastVoiceString);

							// 2. Validate the index to ensure it does not exceed the text length (prevents crashes)
							int textLength = translatedLastVoiceString.length();
							int selectionEnd = create_overlay_translation_text.overlay_translation_text.getSelectionEnd();
							int safeEnd = Math.max(0, Math.min(selectionEnd, textLength)); // Aman dari IndexOutOfBounds

							// 3. Apply Span using safeEnd
							spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#FFFF00")),
									0,
									safeEnd,
									Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); // Gunakan flag standar yang valid

							spannableString.setSpan(new BackgroundColorSpan(Color.parseColor("#80000000")),
									0,
									safeEnd,
									Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

							create_overlay_translation_text.overlay_translation_text.setText(spannableString);
							create_overlay_translation_text.overlay_translation_text.setSelection(create_overlay_translation_text.overlay_translation_text.getText().length());
							create_overlay_translation_text.overlay_full_translation_text.setGravity(Gravity.BOTTOM);

							create_overlay_translation_text.overlay_full_translation_text.setGravity(Gravity.BOTTOM);
						}
					} else {
						if (create_overlay_translation_text.overlay_full_translation_text_container != null) {
							create_overlay_translation_text.overlay_full_translation_text.setVisibility(View.INVISIBLE);
							create_overlay_translation_text.overlay_full_translation_text_container.setVisibility(View.INVISIBLE);
						}
					}
				});
			});
		}
		else {
			executor.shutdown();
		}
		return TRANSLATION.toString();
	}

}
