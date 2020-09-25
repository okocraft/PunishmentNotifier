package net.okocraft.punishmentnotifier;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import me.leoko.advancedban.utils.Punishment;

public class DiscordNotifier {

    private WebhookClient webhook;

    public DiscordNotifier(String url) {
        webhook = new WebhookClientBuilder(url)
                .setThreadFactory(r -> new Thread(r, "Punishment-Notification-Thread"))
                .setWait(true).build();
    }

    public void sendNewPunishmentLog(Punishment p) {
        String log;

        if (p.getDuration(true).equalsIgnoreCase("permanent")) {
            // [被処罰者] タイプ 理由 #ID (By 処罰者)
            log = "[`" + p.getName() + "`] " + p.getType().getName() + " " + p.getReason() +
                    " #" + p.getId() + " (By " + p.getOperator() + ")";
        } else {
            // [被処罰者] タイプ 期間 理由 #ID (By 処罰者)
            log = "[`" + p.getName() + "`] " + p.getType().getName() + " " + p.getDuration(true)
                    + " " + p.getReason() + " #" + p.getId() + " (By " + p.getOperator() + ")";
        }

        webhook.send(log);
    }

    public void sendRevocationLog(Punishment p) {
        String log = "[`" + p.getName() + "`] **UN" + p.getType().getName().toUpperCase() + "** " +
                p.getReason() + " #" + p.getId() + " (By " + p.getOperator() + ")";

        webhook.send(log);
    }

    public void restart(String url) {
        shutdown();

        webhook = new WebhookClientBuilder(url)
                .setThreadFactory(r -> new Thread(r, "Punishment-Notification-Thread"))
                .setWait(true).build();
    }

    public void shutdown() {
        if (!webhook.isShutdown()) {
            webhook.close();
        }
    }
}
