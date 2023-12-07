package net.okocraft.punishmentnotifier;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.slf4j.Logger;
import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.event.PostPardonEvent;
import space.arim.libertybans.api.event.PostPunishEvent;
import space.arim.omnibus.OmnibusProvider;
import space.arim.omnibus.events.ListenerPriorities;
import space.arim.omnibus.events.RegisteredListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PunishmentNotifier {

    private final DiscordNotifier discordNotifier = new DiscordNotifier();
    private final PlayerNotifier playerNotifier = new PlayerNotifier(this);

    private final PunishmentListener punishmentListener = new PunishmentListener(this);
    private final List<RegisteredListener> registeredListeners = new ArrayList<>();

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private LibertyBans libertyBans;

    @Inject
    public PunishmentNotifier(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onEnable(ProxyInitializeEvent ignored) {
        this.getProxy().getCommandManager().register("pnreload", new ReloadCommand());

        this.getLogger().info("Loading config.yml...");

        String url;

        try {
            url = this.readWebhookUrl();
        } catch (IOException e) {
            this.getLogger().error("Could not load config.yml", e);
            return;
        }

        this.getLogger().info("Loading LibertyBans API...");
        this.libertyBans = OmnibusProvider.getOmnibus().getRegistry().getProvider(LibertyBans.class).orElseThrow();

        this.getLogger().info("Loading notifications...");
        this.playerNotifier.load();

        if (url.isEmpty()) {
            this.getLogger().warn("No Webhook url has been set.");
        } else {
            this.getLogger().info("Creating the webhook client...");
            this.discordNotifier.start(url);
        }

        this.getLogger().info("Registering listeners...");
        this.registerListeners();

        this.getLogger().info("Successfully enabled!");
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onDisable(ProxyShutdownEvent ignored) {
        this.getProxy().getCommandManager().unregister("pnreload");

        this.getLogger().info("Unregistering listeners...");
        this.unregisterListeners();

        if (this.discordNotifier.isRunning()) {
            this.getLogger().info("Shutting down the webhook client...");
            this.discordNotifier.shutdown();
        }

        this.getLogger().info("Successfully disabled!");
    }

    public ProxyServer getProxy() {
        return this.proxy;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public Path getDataDirectory() {
        return this.dataDirectory;
    }

    public LibertyBans getLibertyBans() {
        return this.libertyBans;
    }

    public DiscordNotifier getDiscordNotifier() {
        return this.discordNotifier;
    }

    public PlayerNotifier getPlayerNotifier() {
        return this.playerNotifier;
    }

    private void registerListeners() {
        this.getProxy().getEventManager().register(this, this.playerNotifier);

        this.registeredListeners.add(this.getLibertyBans().getOmnibus().getEventBus().registerListener(PostPunishEvent.class, ListenerPriorities.NORMAL, this.punishmentListener::onPunish));
        this.registeredListeners.add(this.getLibertyBans().getOmnibus().getEventBus().registerListener(PostPardonEvent.class, ListenerPriorities.NORMAL, this.punishmentListener::onPardon));
    }

    private void unregisterListeners() {
        if (this.libertyBans != null) {
            this.getProxy().getEventManager().unregisterListeners(this);
            this.registeredListeners.forEach(this.getLibertyBans().getOmnibus().getEventBus()::unregisterListener);
        }
    }

    private String readWebhookUrl() throws IOException {
        var path = this.dataDirectory.resolve("config.yml");

        if (!Files.isRegularFile(path)) {
            Files.createDirectories(this.dataDirectory);
            try (var input = this.getClass().getResourceAsStream("config.yml")) {
                Files.copy(Objects.requireNonNull(input, "Could not find config.yml in the jar."), path);
            }
        }

        var config = YAMLConfigurationLoader.builder().setPath(path).build().load();
        return config.getString("discord-webhook-url");
    }

    private class ReloadCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            var sender = invocation.source();

            PunishmentNotifier.this.unregisterListeners();

            String url;

            try {
                url = PunishmentNotifier.this.readWebhookUrl();
            } catch (IOException e) {
                PunishmentNotifier.this.getLogger().error("Could not load config.yml", e);
                sender.sendMessage(Component.text("Could not reload config.yml. Please check the console."));
                return;
            }

            if (url.isEmpty()) {
                PunishmentNotifier.this.getLogger().warn("No Webhook url has been set.");
                sender.sendMessage(Component.text("PunishmentNotifier has been reloaded: No Webhook url has been set."));
            } else {
                PunishmentNotifier.this.discordNotifier.start(url);
            }

            registerListeners();

            sender.sendMessage(Component.text("PunishmentNotifier has been reloaded!"));
        }
    }
}
