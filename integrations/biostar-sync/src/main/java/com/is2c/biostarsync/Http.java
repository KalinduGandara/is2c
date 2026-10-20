package com.is2c.biostarsync;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Small JSON-over-HTTP helper shared by the BioStar and HRM clients. */
final class Http {

    /** A failed call, with a message fit for the UI. */
    static class CallException extends RuntimeException {
        final int status;

        CallException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient client;
    private final JsonMapper json;
    private final String baseUrl;

    Http(String baseUrl, boolean trustAllCerts, JsonMapper json) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.json = json;
        HttpClient.Builder b = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15));
        if (trustAllCerts) {
            b.sslContext(trustAll());
        }
        this.client = b.build();
    }

    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(TIMEOUT)
                .header("Accept", "application/json");
    }

    HttpRequest.BodyPublisher body(Object value) {
        return HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value));
    }

    /** Sends the request; returns the response, or throws {@link CallException} on a non-2xx status. */
    HttpResponse<String> send(HttpRequest request) {
        HttpResponse<String> r;
        try {
            r = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new CallException(0, "cannot reach " + request.uri().getHost() + ": " + describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CallException(0, "interrupted");
        }
        if (r.statusCode() / 100 != 2) {
            throw new CallException(r.statusCode(), request.method() + " " + request.uri().getPath() + " failed with HTTP "
                    + r.statusCode() + errorDetail(r.body()));
        }
        return r;
    }

    JsonNode sendJson(HttpRequest request) {
        String body = send(request).body();
        return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    /** Pulls the human-readable part out of a BioStar or problem+json error body. */
    private String errorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode n = json.readTree(body);
            String msg = n.path("Response").path("message").asString(n.path("detail").asString(""));
            JsonNode errors = n.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                StringBuilder sb = new StringBuilder(msg);
                errors.forEach(e -> sb.append(sb.isEmpty() ? "" : "; ").append(e.path("field").asString())
                        .append(' ').append(e.path("message").asString()));
                msg = sb.toString();
            }
            return msg.isBlank() ? "" : ": " + msg;
        } catch (RuntimeException e) {
            return ": " + body.substring(0, Math.min(200, body.length()));
        }
    }

    private static String describe(IOException e) {
        String m = e.getMessage();
        if (m == null && e.getCause() != null) {
            m = e.getCause().getMessage();
        }
        if (e instanceof javax.net.ssl.SSLHandshakeException) {
            return "TLS certificate not trusted (" + m + "); for BioStar's self-signed certificate tick \"Trust the server's certificate\"";
        }
        return m == null ? e.getClass().getSimpleName() : m;
    }

    /** Accepts any server certificate and host name; only used when the user opts in for BioStar. */
    private static SSLContext trustAll() {
        X509ExtendedTrustManager tm = new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[] { tm }, null);
            return ctx;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
