package org.vosk.livesubtitle;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;

public class GoogleTranslator extends AsyncTask<String, String, String> {
    private OnTranslationCompleteListener listener;
    @Override
    protected String doInBackground(String... strings) {
        String str = "";
        try {
            String SENTENCE = URLEncoder.encode(((String[]) strings)[0], "utf-8");
            String SRC = ((String[]) strings)[1];
            String DST = ((String[]) strings)[2];
            String sb = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" + SRC +
                    "&tl=" + DST +
                    "&dt=t&q=" + SENTENCE;
            HttpResponse response = new DefaultHttpClient().execute(new HttpGet(sb));
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() == 200) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                response.getEntity().writeTo(byteArrayOutputStream);
                String stringOfByteArrayOutputStream = byteArrayOutputStream.toString();
                byteArrayOutputStream.close();
                JSONArray jSONArray = new JSONArray(stringOfByteArrayOutputStream).getJSONArray(0);
                StringBuilder TRANSLATION = new StringBuilder(str);
                for (int i = 0; i < jSONArray.length(); i++) {
                    JSONArray jSONArray2 = jSONArray.getJSONArray(i);
                    TRANSLATION.append(jSONArray2.get(0).toString());
                }
                return TRANSLATION.toString();
            }
            response.getEntity().getContent().close();
            throw new IOException(statusLine.getReasonPhrase());
        } catch (Exception e) {
            Log.e("GoogleTranslator",e.getMessage());
            listener.onError(e);
            return str;
        }
    }
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        listener.onStartTranslation();
    }
    @Override
    protected void onPostExecute(String text) {
        listener.onCompleted(text);
    }
    public interface OnTranslationCompleteListener{
        void onStartTranslation();
        void onCompleted(String text);
        void onError(Exception e);
    }
    public void setOnTranslationCompleteListener(OnTranslationCompleteListener listener){
        this.listener=listener;
    }
}
