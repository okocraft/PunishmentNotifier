package net.okocraft.punishmentnotifier;

import com.github.siroshun09.configapi.yaml.YamlConfiguration;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.IOException;
import java.util.logging.Level;

public class PunishmentNotifier extends Plugin {

    private final YamlConfiguration config = YamlConfiguration.create(getDataFolder().toPath().resolve("config.yml"));
    private DiscordNotifier discordNotifier;
    private PlayerNotifier playerNotifier;

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand());

        getLogger().info("Loading config.yml...");

        try {
            config.load();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not load config.yml", e);
            return;
        }

        String url = config.getString("discord-webhook-url");

        if (url.isEmpty()) {
            getLogger().warning("No Webhook url has been set.");
            return;
        }

        getLogger().info("Creating webhook client...");
        discordNotifier = new DiscordNotifier(config.getString("discord-webhook-url"));

        getLogger().info("Registering listeners...");
        registerListeners();

        getLogger().info("Successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (discordNotifier != null) {
            discordNotifier.shutdown();
        }

        unregisterListeners();
        getProxy().getPluginManager().unregisterCommands(this);

        getLogger().info("Successfully disabled!");
    }

    public DiscordNotifier getDiscordNotifier() {
        return discordNotifier;
    }

    public PlayerNotifier getPlayerNotifier() {
        return playerNotifier;
    }

    private void registerListeners() {
        playerNotifier = new PlayerNotifier(this);

        getProxy().getPluginManager().registerListener(this, playerNotifier);
        getProxy().getPluginManager().registerListener(this, new PunishmentListener(this));
    }

    private void unregisterListeners() {
        getProxy().getPluginManager().unregisterListeners(this);
    }

    private class ReloadCommand extends Command {

        public ReloadCommand() {
            super("pnreload", "punishmentnotifier.reload");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            sender.sendMessage(TextComponent.fromLegacyText("Reloading PunishmentNotifier..."));

            unregisterListeners();

            try {
                config.reload();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not reload config.yml", e);
                sender.sendMessage(TextComponent.fromLegacyText("Could not reload config.yml. Please check the console."));
                return;
            }

            String url = config.getString("discord-webhook-url");

            if (url.isEmpty()) {
                getLogger().warning("No Webhook url has been set.");
                return;
            }

            discordNotifier.restart(url);
            registerListeners();
        }
    }
}
