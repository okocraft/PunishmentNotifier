package net.okocraft.punishmentnotifier.config;

import com.github.siroshun09.configapi.core.node.MapNode;
import com.github.siroshun09.configapi.core.serialization.annotation.Comment;
import com.github.siroshun09.configapi.core.serialization.key.KeyGenerator;
import com.github.siroshun09.configapi.core.serialization.record.RecordSerialization;
import com.github.siroshun09.configapi.format.yaml.YamlFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("UnstableApiUsage")
public record Config(Notifications notifications) {

    private static final RecordSerialization<Config> SERIALIZATION = RecordSerialization.builder(Config.class).keyGenerator(KeyGenerator.CAMEL_TO_KEBAB).build();

    public static Config loadFromYamlFile(Path filepath) throws IOException {
        if (Files.isRegularFile(filepath)) {
            return SERIALIZATION.deserializer().deserialize(YamlFormat.DEFAULT.load(filepath));
        } else {
            var config = SERIALIZATION.deserializer().deserialize(MapNode.empty());
            YamlFormat.COMMENT_PROCESSING.save(SERIALIZATION.serializer().serialize(config), filepath);
            return config;
        }
    }

    public record Notifications(PunishmentNotification punishment) {
    }

    public record PunishmentNotification(
            @Comment("The url of Discord Webhook. Set this value to empty to disable notifications.")
            String webhookUrl
    ) {
    }
}
