package net.okocraft.punishmentnotifier;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import com.github.siroshun09.configapi.bungee.BungeeConfig;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;

public class PunishmentNotifier extends Plugin {

    private BungeeConfig config;
    private WebhookClient webhook;
    private DiscordNotifier discordNotifier;
    private PlayerNotifier playerNotifier;

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand());

        getLogger().info("Loading config.yml...");
        config = new BungeeConfig(this, "config.yml", true);

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
        discordNotifier.shutdown();

        unregisterListeners();
        getProxy().getPluginManager().unregisterCommands(this);
        getLogger().info("Successfully disabled!");
    }

    public WebhookClient getWebhook() {
        return webhook;
    }

    public DiscordNotifier getDiscordNotifier() {
        return discordNotifier;
    }

    public PlayerNotifier getPlayerNotifier() {
        return playerNotifier;
    }

    private void createWebhook(String url) {
        if (url.isEmpty()) {
            getLogger().warning("No Webhook url has been set.");
            return;
        }

        webhook = new WebhookClientBuilder(url)
                .setThreadFactory(r -> new Thread(r, "Punishment-Notification-Thread"))
                .setWait(true).build();
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

            config.reload();

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
