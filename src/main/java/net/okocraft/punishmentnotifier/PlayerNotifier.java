package net.okocraft.punishmentnotifier;

import com.github.siroshun09.configapi.yaml.YamlConfiguration;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.utils.Punishment;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

public class PlayerNotifier implements Listener {

    private final PunishmentNotifier plugin;
    private final YamlConfiguration yaml;

    public PlayerNotifier(PunishmentNotifier plugin) {
        this.plugin = plugin;
        this.yaml = YamlConfiguration.create(plugin.getDataFolder().toPath().resolve("data.yml"));

        try {
            yaml.load();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load data.yml", e);
        }
    }

    @EventHandler
    public void onConnect(PostLoginEvent e) {
        ProxiedPlayer player = e.getPlayer();
        String strUuid = player.getUniqueId().toString().replace("-", "");

        if (!yaml.getKeyList().contains(strUuid)) {
            return;
        }

        int id = yaml.getInteger(strUuid, -1);
        if (id != -1) {
            PunishmentManager pm = PunishmentManager.get();
            Punishment p = pm.getPunishment(id);

            if (p != null) {
                Stream.of(
                                "&8[&c処罰&8]&c あなたは警告されています:",
                                "&7理由: &r" + p.getReason(),
                                "&7処罰者: &b" + p.getOperator(),
                                "&7累積回数: &b" + pm.getCurrentWarns(strUuid) + "回"
                        )
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
        try {
            yaml.save();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save to data.yml", e);
        }
    }
}
