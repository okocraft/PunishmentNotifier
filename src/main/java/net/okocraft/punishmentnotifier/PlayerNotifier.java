package net.okocraft.punishmentnotifier;

import com.github.siroshun09.configapi.bungee.BungeeYaml;
import com.github.siroshun09.configapi.common.Yaml;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.utils.Punishment;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.List;

public class PlayerNotifier implements Listener {

    private final PunishmentNotifier plugin;
    private final Yaml yaml;

    public PlayerNotifier(PunishmentNotifier plugin) {
        this.plugin = plugin;
        this.yaml = new BungeeYaml(plugin.getDataFolder().toPath().resolve("data.yml"));
    }

    @EventHandler
    public void onConnect(PostLoginEvent e) {
        ProxiedPlayer player = e.getPlayer();
        String strUuid = player.getUniqueId().toString().replace("-", "");

        if (!yaml.getKeys().contains(strUuid)) {
            return;
        }

        int id = yaml.getInt(strUuid, -1);
        if (id != -1) {
            PunishmentManager pm = PunishmentManager.get();
            Punishment p = pm.getPunishment(id);

            if (p != null) {
                List.of(
                        "&8[&c処罰&8]&c あなたは警告されています:",
                        "&7理由: &r" + p.getReason(),
                        "&7処罰者: &b" + p.getOperator(),
                        "&7累積回数: &b" + pm.getCurrentWarns(strUuid) + "回"
                ).stream()
                        .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                        .map(TextComponent::fromLegacyText)
                        .forEach(player::sendMessage);
            }
        }

        yaml.set(strUuid, null);
        save();
    }

    public void addNotification(Punishment p) {
        yaml.set(p.getUuid(), p.getId());
        save();
    }

    public void removeNotification(Punishment p) {
        yaml.set(p.getUuid(), null);
        save();
    }

    private void save() {
        if (!yaml.save()) {
            plugin.getLogger().warning("Could not save data.yml.");
        }
    }
}
