package net.okocraft.punishmentnotifier.config;

import dev.siroshun.configapi.core.node.MapNode;
import dev.siroshun.configapi.format.yaml.YamlFormat;
import dev.siroshun.configapi.serialization.record.RecordSerialization;
import dev.siroshun.serialization.annotation.Comment;
import dev.siroshun.serialization.annotation.DefaultBoolean;
import dev.siroshun.serialization.core.key.KeyGenerator;

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

    public record Notifications(PunishmentNotification punishment, AltNotification alt) {
    }

    public record PunishmentNotification(
            @Comment("The url of Discord Webhook. Set this value to empty to disable notifications.")
            String webhookUrl,
            @Comment("The id of the thread. If you are using forum channel, you need to set this id.")
            long threadId
    ) {
    }

    public record AltNotification(
            @Comment("The url of Discord Webhook. Set this value to empty to disable notifications.")
            String webhookUrl,
            @Comment("The id of the thread. If you are using forum channel, you need to set this id.")
            long threadId,
            @Comment("If set to true, only banned alts will be notified.")
            @DefaultBoolean(false) boolean onlyBannedAlt,
            @Comment("If set to true, only same IP alts will be notified.")
            @DefaultBoolean(true) boolean onlyStrongPossibility
    ) {
    }
}
