package com.dynrinth.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HttpUtil {

    private HttpUtil() {}

    public static final class Response {
        private final int statusCode;
        private final String body;

        public Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int statusCode() {
            return statusCode;
        }

        public String body() {
            return body;
        }
    }

    public static Response get(String url, String userAgent, String accept,
                               int connectTimeoutMillis, int readTimeoutMillis) throws IOException {
        HttpURLConnection connection = open(url, userAgent, accept, connectTimeoutMillis, readTimeoutMillis);
        try {
            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream != null ? new String(readAll(stream), StandardCharsets.UTF_8) : "";
            return new Response(statusCode, body);
        } finally {
            connection.disconnect();
        }
    }

    public static void downloadToFile(String url, Path dest, String userAgent,
                                      int connectTimeoutMillis, int readTimeoutMillis) throws IOException {
        HttpURLConnection connection = open(url, userAgent, null, connectTimeoutMillis, readTimeoutMillis);
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + statusCode + " downloading " + url);
            }

            Path parent = dest.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(dest)) {
                copy(input, output);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String userAgent, String accept,
                                          int connectTimeoutMillis, int readTimeoutMillis) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setInstanceFollowRedirects(true);
        if (userAgent != null && !userAgent.isEmpty()) connection.setRequestProperty("User-Agent", userAgent);
        if (accept != null && !accept.isEmpty()) connection.setRequestProperty("Accept", accept);
        return connection;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
    }
}
