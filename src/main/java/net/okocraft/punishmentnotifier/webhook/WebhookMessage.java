package net.okocraft.punishmentnotifier.webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@NullMarked
public sealed interface WebhookMessage {

    String toJson();

    record Text(String content) implements WebhookMessage {
        @Override
        public String toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("content", this.content);
            return obj.toString();
        }
    }

    record Embed(@Nullable String mention,
                 int color,
                 Instant timestamp,
                 String title,
                 String description) implements WebhookMessage {
        @Override
        public String toJson() {
            JsonObject embed = new JsonObject();
            embed.addProperty("color", this.color);
            embed.addProperty("timestamp", this.timestamp.toString());
            embed.addProperty("title", this.title);
            embed.addProperty("description", this.description);

            JsonArray embeds = new JsonArray();
            embeds.add(embed);

            JsonObject root = new JsonObject();
            if (this.mention != null) {
                root.addProperty("content", this.mention);
            }
            root.add("embeds", embeds);
            return root.toString();
        }
    }
}
