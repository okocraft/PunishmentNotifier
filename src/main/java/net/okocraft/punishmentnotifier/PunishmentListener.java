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

        plugin.getDiscordNotifier().sendNewPunishmentLog(p);

        if (p.getType() == PunishmentType.WARNING || p.getType() == PunishmentType.TEMP_WARNING) {
            plugin.getPlayerNotifier().addNotification(p);
        }
    }

    @EventHandler
    public void onRevoke(RevokePunishmentEvent e) {
        Punishment p = e.getPunishment();

        plugin.getDiscordNotifier().sendRevocationLog(p);

        if (p.getType() == PunishmentType.WARNING || p.getType() == PunishmentType.TEMP_WARNING) {
            plugin.getPlayerNotifier().removeNotification(p);
        }
    }
}
