package io.github.veliahmedov.dbvault.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

//HTTP communication
final class WebhookNotifier implements Notifier {
    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // to send HTTP requests, connectTimeout limits time to establish connection.
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final URI url;
    private final boolean failureOnly;      // notifyOn failure mode only

    WebhookNotifier(URI url, boolean failureOnly) {
        this.url = url;
        this.failureOnly = failureOnly;
    }

    @Override
    public void send(boolean success, String message) {
        if (success && failureOnly) return;
        try {
            // {"text": "..."} is accepted by Slack-compatible webhooks
            // Map.of("text", "Backup completed") becomes {   "text": "Backup completed"   }  json
            String json = MAPPER.writeValueAsString(Map.of("text", message));
            // build HTTP request
            HttpRequest request = HttpRequest.newBuilder(url)                 //destination
                    .timeout(Duration.ofSeconds(10))                          // limit req duration
                    .header("Content-Type", "application/json")  //json content
                    .POST(HttpRequest.BodyPublishers.ofString(json))          // send HTTP POST and JSON string as req body
                    .build();
            // send request and wait for response, BodyHandlers.discarding return only status code not response body
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            // 300 (redirect), 400 (bad and auth), 500 (server error) return log warn
            if (status >= 300) log.warn("Webhook returned HTTP {}", status);
        } catch (IOException | RuntimeException e) {
            // backup can success but webhook.site can be unavailable which can mark backup even success as failure
            log.warn("Webhook notification failed ({})", e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            // thread interrupted after exception catch revert back to non-interrupt, we make it interrupt again
            Thread.currentThread().interrupt();
        }
    }
}
// for dbvault backup -s s3
