package org.vosk.livesubtitle;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;

public class GoogleTranslatorClient5 extends AsyncTask<String, String, String> {
    private OnTranslationCompleteListener listener;
    @Override
    protected String doInBackground(String... strings) {
        String[] strArr = (String[]) strings;
        String str = "";
        try {
            String encode = URLEncoder.encode(strArr[0], "utf-8");
            StringBuilder sb = new StringBuilder();
            sb.append("https://clients5.google.com/translate_a/");
            sb.append("single?dj=1&dt=t&dt=sp&dt=ld&dt=bd&client=dict-chrome-ex&sl=");
            sb.append(((String[]) strings)[1]);
            sb.append("&tl=");
            sb.append(((String[]) strings)[2]);
            sb.append("&q=");
            sb.append(encode);
			HttpClient httpClient = new DefaultHttpClient();
            HttpResponse execute = httpClient.execute(new HttpGet(sb.toString()));
            StatusLine statusLine = execute.getStatusLine();
            if (statusLine.getStatusCode() == 200) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                execute.getEntity().writeTo(byteArrayOutputStream);
                String byteArrayOutputStream2 = byteArrayOutputStream.toString();
                byteArrayOutputStream.close();

                JSONObject jo = new JSONObject(byteArrayOutputStream2);
                JSONArray ja_sentences = jo.getJSONArray("sentences");

                StringBuilder translation = new StringBuilder(str);
                for (int i = 0; i < ja_sentences.length(); i++) {
                    JSONObject jo_trans = ja_sentences.getJSONObject(i);
                    String str_trans = jo_trans.getString("trans");
                    translation.append(str_trans);
                }
                return translation.toString();
            }
            execute.getEntity().getContent().close();
			httpClient.getConnectionManager().shutdown();
            throw new IOException(statusLine.getReasonPhrase());
        } catch (Exception e) {
            Log.e("GoogleTranslatorClient5",e.getMessage());
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
