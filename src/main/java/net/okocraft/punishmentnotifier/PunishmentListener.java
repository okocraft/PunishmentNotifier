package net.okocraft.punishmentnotifier;

import org.jetbrains.annotations.NotNull;
import space.arim.libertybans.api.Operator;
import space.arim.libertybans.api.PlayerOperator;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.event.PostPardonEvent;
import space.arim.libertybans.api.event.PostPunishEvent;
import space.arim.libertybans.api.punish.Punishment;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class PunishmentListener {

    private final PunishmentNotifier plugin;

    public PunishmentListener(PunishmentNotifier plugin) {
        this.plugin = plugin;
    }

    public void onPunish(PostPunishEvent event) {
        var punishment = event.getPunishment();

        if (!(punishment.getVictim() instanceof PlayerVictim player)) {
            return;
        }

        if (this.plugin.getProxy().getPlayer(player.getUUID()).isEmpty()) {
            this.plugin.getPlayerNotifier().addNotification(punishment);
        }

        var playerName = this.plugin.getLibertyBans().getUserResolver().lookupName(player.getUUID()).join().orElse(player.getUUID().toString());

        var operatorName = this.resolveOperatorName(punishment.getOperator());

        var duration = this.formatDuration(punishment);

        this.plugin.getDiscordNotifier().sendPunishLog(punishment, playerName, operatorName, duration);
    }

    public void onPardon(PostPardonEvent event) {
        var punishment = event.getPunishment();

        if (!(punishment.getVictim() instanceof PlayerVictim player)) {
            return;
        }

        this.plugin.getPlayerNotifier().removeNotification(punishment);

        var playerName = this.plugin.getLibertyBans().getUserResolver().lookupName(player.getUUID()).join().orElse(player.getUUID().toString());

        var operatorName = this.resolveOperatorName(punishment.getOperator());

        this.plugin.getDiscordNotifier().sendPardonLog(punishment, playerName, operatorName);
    }

    private String formatDuration(@NotNull Punishment punishment) {
        return this.plugin.getLibertyBans().getFormatter().formatDuration(Duration.between(
                Instant.now().truncatedTo(ChronoUnit.SECONDS),
                punishment.getEndDate().truncatedTo(ChronoUnit.SECONDS)
        ));
    }

    private String resolveOperatorName(@NotNull Operator operator) {
        if (operator instanceof PlayerOperator player) {
            return this.plugin.getLibertyBans().getUserResolver().lookupName(player.getUUID()).join().orElse("Unknown");
        } else {
            return "Console";
        }
    }
}
