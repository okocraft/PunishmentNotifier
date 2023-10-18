package net.okocraft.punishmentnotifier;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import space.arim.libertybans.api.PunishmentType;
import space.arim.libertybans.api.punish.Punishment;

import java.util.Locale;

public class DiscordNotifier {

    private WebhookClient webhook;

    public void sendPunishLog(Punishment p, String playerName, String operatorName, String formattedDuration) {
        if (!this.isRunning()) {
            return;
        }

        String log;

        if (p.isPermanent()) {
            // [被処罰者] タイプ 理由 #ID (By 処罰者)
            log = "[`" + playerName + "`] " + capitalize(p.getType()) + " " + p.getReason() +
                    " #" + p.getIdentifier() + " (By " + operatorName + ")";
        } else {
            // [被処罰者] タイプ 期間 理由 #ID (By 処罰者)
            log = "[`" + playerName + "`] " + capitalize(p.getType()) + " " + formattedDuration
                    + " " + p.getReason() + " #" + p.getIdentifier() + " (By " + operatorName + ")";
        }

        this.webhook.send(log);
    }

    public void sendPardonLog(Punishment p, String playerName, String operatorName) {
        if (!this.isRunning()) {
            return;
        }

        String log = "[`" + playerName + "`] **UN" + p.getType().name() + "** " + p.getReason() + " #" + p.getIdentifier() + " (By " + operatorName + ")";
        this.webhook.send(log);
    }

    public boolean isRunning() {
        return webhook != null && !webhook.isShutdown();
    }

    public void start(String url) {
        shutdown();

        this.webhook = new WebhookClientBuilder(url)
                .setThreadFactory(r -> new Thread(r, "Punishment-Notification-Thread"))
                .setWait(true).build();
    }

    public void shutdown() {
        if (this.isRunning()) {
            this.webhook.close();
        }
    }

    private static String capitalize(PunishmentType type) {
        var name = type.name();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ENGLISH);
    }
}
