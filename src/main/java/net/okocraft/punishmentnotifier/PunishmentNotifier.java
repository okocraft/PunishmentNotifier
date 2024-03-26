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
import net.okocraft.punishmentnotifier.config.Config;
import org.slf4j.Logger;
import org.slf4j.helpers.SubstituteLogger;
import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.event.PostPardonEvent;
import space.arim.libertybans.api.event.PostPunishEvent;
import space.arim.omnibus.OmnibusProvider;
import space.arim.omnibus.events.Event;
import space.arim.omnibus.events.EventConsumer;
import space.arim.omnibus.events.ListenerPriorities;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class PunishmentNotifier {

    public static final Logger LOGGER = new SubstituteLogger("PunishmentNotifier", null, true);

    private final List<Runnable> onShutdown = new ArrayList<>();

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final BiConsumer<Runnable, Duration> asyncExecutor;

    private Config config;
    private LibertyBans libertyBans;

    @Inject
    public PunishmentNotifier(Logger logger, ProxyServer proxy, @DataDirectory Path dataDirectory) {
        ((SubstituteLogger) LOGGER).setDelegate(logger);

        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.asyncExecutor = (task, delay) -> {
            var builder = this.proxy.getScheduler().buildTask(this, task);
            if (delay != null) {
                builder.delay(delay);
            }
            builder.schedule();
        };
    }

    @Subscribe(order = PostOrder.LAST)
    public void onEnable(ProxyInitializeEvent ignored) {
        this.proxy.getCommandManager().register("pnreload", new ReloadCommand());

        LOGGER.info("Loading config.yml...");

        try {
            this.config = Config.loadFromYamlFile(this.dataDirectory.resolve("config.yml"));
        } catch (IOException e) {
            LOGGER.error("Could not load config.yml", e);
            return;
        }

        LOGGER.info("Loading LibertyBans API...");
        this.libertyBans = OmnibusProvider.getOmnibus().getRegistry().getProvider(LibertyBans.class).orElseThrow();

        LOGGER.info("Registering listeners...");
        var playerNotifier = this.createAndRegisterPlayerNotifier();
        this.registerPunishmentListener(playerNotifier);

        LOGGER.info("Successfully enabled!");
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onDisable(ProxyShutdownEvent ignored) {
        this.proxy.getCommandManager().unregister("pnreload");

        this.onShutdown.forEach(Runnable::run);
        this.onShutdown.clear();

        LOGGER.info("Successfully disabled!");
    }

    private PlayerNotifier createAndRegisterPlayerNotifier() {
        var notifier = new PlayerNotifier(this.libertyBans, this.dataDirectory, this.asyncExecutor);

        LOGGER.info("Loading notifications...");
        notifier.load();

        this.proxy.getEventManager().register(this, notifier);

        return notifier;
    }

    private void registerPunishmentListener(PlayerNotifier playerNotifier) {
        var listener = new PunishmentListener(this.proxy, this.libertyBans, playerNotifier, this.config.notifications().punishment());

        listener.startWebhookIfEnabled();

        this.registerListener(PostPunishEvent.class, listener::onPunish);
        this.registerListener(PostPardonEvent.class, listener::onPardon);
    }

    private <E extends Event> void registerListener(Class<E> clazz, EventConsumer<E> consumer) {
        var registered = this.libertyBans.getOmnibus().getEventBus().registerListener(clazz, ListenerPriorities.NORMAL, consumer);
        this.onShutdown.add(() -> this.libertyBans.getOmnibus().getEventBus().unregisterListener(registered));
    }

    private class ReloadCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            var sender = invocation.source();
            var plugin = PunishmentNotifier.this;

            plugin.onShutdown.forEach(Runnable::run);
            plugin.onShutdown.clear();

            try {
                plugin.config = Config.loadFromYamlFile(plugin.dataDirectory.resolve("config.yml"));
            } catch (IOException e) {
                PunishmentNotifier.LOGGER.error("Could not load config.yml", e);
                sender.sendMessage(Component.text("Could not reload config.yml. Please check the console."));
                return;
            }

            var playerNotifier = plugin.createAndRegisterPlayerNotifier();
            plugin.registerPunishmentListener(playerNotifier);

            sender.sendMessage(Component.text("PunishmentNotifier has been reloaded!"));
        }
    }
}
