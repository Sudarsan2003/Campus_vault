package org.example.cvbackend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Asks the Python service for an image embedding. A failure returns empty and never breaks the caller. */
@Service
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final URI endpoint;

    public EmbeddingClient(@Value("${app.ai.url}") String baseUrl) {
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/embed");
    }

    public Optional<float[]> embed(byte[] image, String contentType) {
        try {
            HttpRequest req = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(image))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Image matching service answered {}", res.statusCode());
                return Optional.empty();
            }
            return Optional.of(parse(res.body()));
        } catch (IOException e) {
            log.warn("Image matching service unavailable: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Could not read the embedding: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public static String toText(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 9);
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.toString();
    }

    public static float[] parse(String text) {
        String[] parts = text.trim().split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Float.parseFloat(parts[i]);
        return v;
    }

    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / Math.sqrt(na * nb);
    }
}