package net.okocraft.punishmentnotifier.listener;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import com.velocitypowered.api.proxy.ProxyServer;
import net.okocraft.punishmentnotifier.config.Config;
import net.okocraft.punishmentnotifier.notifier.PlayerNotifier;
import org.jetbrains.annotations.NotNull;
import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.Operator;
import space.arim.libertybans.api.PlayerOperator;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.PunishmentType;
import space.arim.libertybans.api.event.PostPardonEvent;
import space.arim.libertybans.api.event.PostPunishEvent;
import space.arim.libertybans.api.punish.Punishment;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class PunishmentListener {


    private final ProxyServer proxy;
    private final LibertyBans libertyBans;
    private final PlayerNotifier playerNotifier;
    private final Config.PunishmentNotification config;

    private WebhookClient webhook;

    public PunishmentListener(ProxyServer proxy, LibertyBans libertyBans, PlayerNotifier playerNotifier, Config.PunishmentNotification config) {
        this.proxy = proxy;
        this.libertyBans = libertyBans;
        this.playerNotifier = playerNotifier;
        this.config = config;
    }

    public void onPunish(PostPunishEvent event) {
        var punishment = event.getPunishment();

        if (!(punishment.getVictim() instanceof PlayerVictim player)) {
            return;
        }

        if (this.proxy.getPlayer(player.getUUID()).isEmpty()) {
            this.playerNotifier.addNotification(punishment);
        }

        var playerName = this.libertyBans.getUserResolver().lookupName(player.getUUID()).join().orElse(player.getUUID().toString());

        var operatorName = this.resolveOperatorName(punishment.getOperator());

        var duration = this.formatDuration(punishment);

        this.sendPunishLog(punishment, playerName, operatorName, duration);
    }

    public void onPardon(PostPardonEvent event) {
        var punishment = event.getPunishment();

        if (!(punishment.getVictim() instanceof PlayerVictim player)) {
            return;
        }

        this.playerNotifier.removeNotification(punishment);

        var playerName = this.libertyBans.getUserResolver().lookupName(player.getUUID()).join().orElse(player.getUUID().toString());

        var operatorName = this.resolveOperatorName(punishment.getOperator());

        this.sendPardonLog(punishment, playerName, operatorName);
    }

    private String formatDuration(@NotNull Punishment punishment) {
        return this.libertyBans.getFormatter().formatDuration(Duration.between(
                Instant.now().truncatedTo(ChronoUnit.SECONDS),
                punishment.getEndDate().truncatedTo(ChronoUnit.SECONDS)
        ));
    }

    private String resolveOperatorName(@NotNull Operator operator) {
        if (operator instanceof PlayerOperator player) {
            return this.libertyBans.getUserResolver().lookupName(player.getUUID()).join().orElse("Unknown");
        } else {
            return "Console";
        }
    }

    private void sendPunishLog(Punishment p, String playerName, String operatorName, String formattedDuration) {
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

    private void sendPardonLog(Punishment p, String playerName, String operatorName) {
        if (!this.isRunning()) {
            return;
        }

        String log = "[`" + playerName + "`] **UN" + p.getType().name() + "** " + p.getReason() + " #" + p.getIdentifier() + " (By " + operatorName + ")";
        this.webhook.send(log);
    }

    public boolean isRunning() {
        return webhook != null && !webhook.isShutdown();
    }

    public void startWebhookIfEnabled() {
        this.shutdownWebhookIfRunning();

        var url = this.config.webhookUrl();

        if (!url.isEmpty()) {
            this.webhook = new WebhookClientBuilder(url)
                    .setThreadFactory(r -> new Thread(r, "Punishment-Notification-Thread"))
                    .setWait(true).build();
        }
    }

    public void shutdownWebhookIfRunning() {
        if (this.isRunning()) {
            this.webhook.close();
        }
    }

    private static String capitalize(PunishmentType type) {
        var name = type.name();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ENGLISH);
    }
}
