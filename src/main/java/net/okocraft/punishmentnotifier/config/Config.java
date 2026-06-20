package net.okocraft.punishmentnotifier.config;

import org.jspecify.annotations.NullUnmarked;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;

@NullUnmarked
@ConfigSerializable
public class Config {

    public static Config loadFrom(Path filepath) throws ConfigurateException {
        return YamlConfigurationLoader.builder().path(filepath).build().load().get(Config.class);
    }

    public Notifications notifications;

    @ConfigSerializable
    public static class Notifications {
        public PunishmentNotification punishment;

        @ConfigSerializable
        public static class PunishmentNotification {
            public String webhookUrl = "";
            public long threadId;
        }

        public AltNotification alt;

        @ConfigSerializable
        public static class AltNotification {
            public String webhookUrl = "";
            public long threadId;
            public boolean onlyBannedAlt = false;
            public boolean onlyStrongPossibility = true;
        }
    }
}
