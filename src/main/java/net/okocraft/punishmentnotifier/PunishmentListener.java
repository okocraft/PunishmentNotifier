package net.okocraft.punishmentnotifier;

import me.leoko.advancedban.bungee.event.PunishmentEvent;
import me.leoko.advancedban.bungee.event.RevokePunishmentEvent;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.PunishmentType;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PunishmentListener implements Listener {

    private final PunishmentNotifier plugin;

    public PunishmentListener(PunishmentNotifier plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPunishment(PunishmentEvent e) {
        Punishment p = e.getPunishment();
        plugin.getWebhook().send(getPunishmentLog(p));

        if (p.getType() == PunishmentType.WARNING) {
            plugin.getPlayerNotifier().addNotification(p);
        }
    }

    @EventHandler
    public void onRevoke(RevokePunishmentEvent e) {
        Punishment p = e.getPunishment();
        plugin.getWebhook().send(getRevocationLog(p));

        if (p.getType() == PunishmentType.WARNING) {
            plugin.getPlayerNotifier().removeNotification(p);
        }
    }

    private String getPunishmentLog(Punishment p) {
        if (p.getDuration(true).equalsIgnoreCase("permanent")) {
            // [被処罰者] タイプ 理由 #ID (By 処罰者)
            return "[`" + p.getName() + "`] " + p.getType().getName() + " " + p.getReason() +
                    " #" + p.getId() + "(By " + p.getOperator() + ")";
        } else {
            // [被処罰者] タイプ 期間 理由 #ID (By 処罰者)
            return "[`" + p.getName() + "`] " + p.getType().getName() + " " + p.getDuration(true)
                    + " " + p.getReason() + " #" + p.getId() + "(By " + p.getOperator() + ")";
        }
    }

    private String getRevocationLog(Punishment p) {
        // [被処罰者] **UNタイプ** 理由 #ID (By 処罰者)
        return "[`" + p.getName() + "`] **UN" + p.getType().getName().toUpperCase() + "** " +
                p.getReason() + " #" + p.getId() + " (By " + p.getOperator() + ")";
    }
}
