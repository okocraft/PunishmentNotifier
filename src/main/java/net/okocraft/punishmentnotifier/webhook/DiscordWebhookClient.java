package net.okocraft.punishmentnotifier.webhook;

import net.okocraft.punishmentnotifier.PunishmentNotifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class DiscordWebhookClient implements AutoCloseable {

    private final URI uri;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private volatile boolean shutdown = false;

    public DiscordWebhookClient(String url, long threadId) {
        String fullUrl = url + "?wait=true" + (threadId > 0 ? "&thread_id=" + threadId : "");
        this.uri = URI.create(fullUrl);
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Punishment-Notification-Thread"));
        this.httpClient = HttpClient.newHttpClient();
    }

    public void send(WebhookMessage message) {
        if (this.shutdown) {
            return;
        }
        this.postAsync(message.toJson());
    }

    public boolean isShutdown() {
        return this.shutdown;
    }

    @Override
    public void close() {
        this.shutdown = true;
        this.executor.shutdown();
        try {
            this.executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void postAsync(String json) {
        try {
            this.executor.execute(() -> {
                try {
                    var request = HttpRequest.newBuilder(this.uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();
                    var response = this.httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        PunishmentNotifier.LOGGER.error("Discord webhook returned non-2xx status: {}", response.statusCode());
                    }
                } catch (Exception e) {
                    PunishmentNotifier.LOGGER.error("Failed to send Discord webhook", e);
                }
            });
        } catch (RejectedExecutionException ignored) {
        }
    }
}
