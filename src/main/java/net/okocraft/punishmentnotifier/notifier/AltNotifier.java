package net.okocraft.punishmentnotifier.notifier;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import net.okocraft.punishmentnotifier.PunishmentNotifier;
import net.okocraft.punishmentnotifier.data.MapDataFile;
import net.okocraft.punishmentnotifier.util.Reflections;
import net.okocraft.punishmentnotifier.util.UUIDParser;
import space.arim.libertybans.api.NetworkAddress;
import space.arim.libertybans.api.PunishmentType;
import space.arim.libertybans.api.user.AccountSupervisor;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class AltNotifier {

    private final AccountSupervisor supervisor;
    private final MapDataFile<UUID, String> dataFile;
    private final BiConsumer<Runnable, Duration> asyncExecutor;
    private final WebhookClient webhook;
    private final Map<UUID, String> notifiedUuids = new ConcurrentHashMap<>();

    public AltNotifier(WebhookClient webhook, AccountSupervisor supervisor, Path dataDirectory, BiConsumer<Runnable, Duration> asyncExecutor) {
        this.webhook = webhook;
        this.supervisor = supervisor;
        this.dataFile = new MapDataFile<>(
                dataDirectory.resolve("notified-alts.dat"),
                UUID::toString, Function.identity(),
                UUIDParser::parse, Function.identity()
        );
        this.asyncExecutor = asyncExecutor;
    }

    @SuppressWarnings("UnstableApiUsage")
    @Subscribe
    public void onConnect(ServerPostConnectEvent e) {
        if (e.getPreviousServer() != null) {
            return;
        }

        var player = e.getPlayer();

        var uuid = player.getUniqueId();
        var name = player.getUsername();
        var address = player.getRemoteAddress().getAddress();

        this.asyncExecutor.accept(() -> this.findAlts(uuid, name, address), null);
    }

    private void findAlts(UUID uuid, String name, InetAddress address) {
        var raw = new String(Base64.getEncoder().encode(address.getAddress()), StandardCharsets.UTF_8);

        if (raw.equals(this.notifiedUuids.putIfAbsent(uuid, raw))) {
            return;
        }

        var counter = new AtomicInteger();
        var builder = new WebhookEmbedBuilder().setColor(0x000000).setTimestamp(Instant.now());
        var description = new StringBuilder();

        this.supervisor.detectAlts(uuid, NetworkAddress.of(address))
                .punishmentTypes(PunishmentType.BAN)
                .build().detect().join()
                .stream()
                .filter(Reflections::isNormalDetection)
                .map(account -> account.latestUsername().orElseGet(account.uuid()::toString))
                .distinct()
                .forEach(account -> {
                    if (counter.getAndIncrement() != 0) {
                        description.append(System.lineSeparator());
                    }
                    description.append("- `").append(account).append('`');
                });

        if (counter.get() == 0) {
            return;
        }

        builder.setTitle(new WebhookEmbed.EmbedTitle(counter.get() + " Alt Account(s) Detected: " + name, null)).setDescription(description.toString());

        this.webhook.send(builder.build());
        this.asyncExecutor.accept(this::save, null);
    }

    public void load() {
        try {
            this.dataFile.load(this.notifiedUuids);
        } catch (IOException e) {
            PunishmentNotifier.LOGGER.error("Could not load notified-alts.dat", e);
        }
    }

    private synchronized void save() {
        try {
            this.dataFile.save(Map.copyOf(this.notifiedUuids));
        } catch (IOException e) {
            PunishmentNotifier.LOGGER.error("Could not save notified-alts.dat", e);
        }
    }
}
