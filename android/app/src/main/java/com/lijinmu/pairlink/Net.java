package com.lijinmu.pairlink;

import android.content.Context;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

final class Net {
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build();

    static OkHttpClient client() { return client; }
    static JSONObject request(Context c, String base, String path, JSONObject body) throws Exception {
        String apiKey = Config.apiKey(c);
        if (apiKey.isEmpty()) throw new Exception("请先填写并保存 API 密钥");
        Request.Builder r = new Request.Builder().url(base + path).header("X-API-Key", apiKey);
        if (body != null) r.post(RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8")));
        try (Response response = client().newCall(r.build()).execute()) {
            String text = response.body() == null ? "{}" : response.body().string();
            JSONObject result = new JSONObject(text);
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code() + ": " + result.optString("error", text));
            return result;
        }
    }
}
