package com.lijinmu.pairlink;

import android.content.Context;
import org.json.JSONObject;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okhttp3.dnsoverhttps.DnsOverHttps;

final class Net {
    private static volatile OkHttpClient client;
    static OkHttpClient client(Context c) {
        if (client != null) return client;
        synchronized (Net.class) {
            if (client == null) {
                OkHttpClient bootstrap = new OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build();
                try {
                    DnsOverHttps doh = new DnsOverHttps.Builder().client(bootstrap)
                        .url(HttpUrl.get("https://cloudflare-dns.com/dns-query"))
                        .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
                        .includeIPv6(false).build();
                    client = bootstrap.newBuilder().dns(host -> {
                        try { return doh.lookup(host); } catch (Exception e) { return Dns.SYSTEM.lookup(host); }
                    }).build();
                } catch (Exception e) { client = bootstrap; }
            }
        }
        return client;
    }
    static JSONObject request(Context c, String base, String path, JSONObject body) throws Exception {
        Request.Builder r = new Request.Builder().url(base + path);
        if (body != null) r.post(RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8")));
        try (Response response = client(c).newCall(r.build()).execute()) {
            String text = response.body() == null ? "{}" : response.body().string();
            JSONObject result = new JSONObject(text);
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code() + ": " + result.optString("error", text));
            return result;
        }
    }
}
