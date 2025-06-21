package net.okocraft.punishmentnotifier.notifier;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import net.okocraft.punishmentnotifier.PunishmentNotifier;
import net.okocraft.punishmentnotifier.config.Config;
import net.okocraft.punishmentnotifier.data.MapDataFile;
import net.okocraft.punishmentnotifier.util.Reflections;
import net.okocraft.punishmentnotifier.util.UUIDParser;
import org.jetbrains.annotations.NotNull;
import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.NetworkAddress;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.PunishmentType;
import space.arim.libertybans.api.user.AltAccount;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class AltNotifier {

    private final LibertyBans libertyBans;
    private final MapDataFile<UUID, String> dataFile;
    private final BiConsumer<Runnable, Duration> asyncExecutor;
    private final Config.AltNotification config;
    private final WebhookClient webhook;
    private final Map<UUID, String> notifiedUuids = new ConcurrentHashMap<>();

    public AltNotifier(@NotNull Config.AltNotification config, WebhookClient webhook, LibertyBans libertyBans, Path dataDirectory, BiConsumer<Runnable, Duration> asyncExecutor) {
        this.config = config;
        this.webhook = webhook;
        this.libertyBans = libertyBans;
        this.dataFile = new MapDataFile<>(
                dataDirectory.resolve("notified-alts.dat"),
                UUID::toString, Function.identity(),
                UUIDParser::parse, Function.identity()
        );
        this.asyncExecutor = asyncExecutor;
    }

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
        List<? extends AltAccount> alts = this.detectAlts(uuid, address);

        if (alts.isEmpty()) {
            return;
        }

        var description = new StringBuilder();
        var processedUuids = new HashSet<UUID>();
        boolean bannedAltDetected = false;

        for (int i = 0, size = alts.size(); i < size; i++) {
            var alt = alts.get(i);

            if (!processedUuids.add(alt.uuid())) {
                continue;
            }

            var info = this.toAccountInfo(alt);

            if (!info.banned() && (this.config.onlyBannedAlt() || (this.config.onlyStrongPossibility() && info.merePossibility()))) {
                continue;
            }

            if (i != 0) {
                description.append(System.lineSeparator());
            }

            description.append("- `").append(info.name()).append('`');

            if (info.banned()) {
                description.append(" [**BANNED**]");
                bannedAltDetected = true;
            }

            if (info.merePossibility()) {
                description.append(" (Mere possibility)");
            }
        }

        if (!bannedAltDetected) {
            if (this.hasNotified(uuid, address)) {
                return;
            } else {
                this.asyncExecutor.accept(this::save, null);
            }
        }

        this.webhook.send(new WebhookEmbedBuilder()
                .setColor(0x000000)
                .setTimestamp(Instant.now())
                .setTitle(new WebhookEmbed.EmbedTitle(alts.size() + " Alt Account(s) Detected: " + name, null)).setDescription(description.toString())
                .build());
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

    private List<? extends AltAccount> detectAlts(UUID uuid, InetAddress address) {
        return this.libertyBans.getAccountSupervisor()
                .detectAlts(uuid, NetworkAddress.of(address))
                .punishmentTypes(PunishmentType.BAN)
                .build().detect().join();
    }

    private AccountInfo toAccountInfo(AltAccount alt) {
        return new AccountInfo(
                alt.latestUsername().orElseGet(alt.uuid()::toString),
                this.isBanned(alt),
                !Reflections.isNormalDetection(alt)
        );
    }

    private boolean isBanned(AltAccount alt) {
        return this.libertyBans.getSelector()
                .selectionBuilder()
                .type(PunishmentType.BAN)
                .selectActiveOnly()
                .victim(PlayerVictim.of(alt.uuid()))
                .build()
                .getFirstSpecificPunishment()
                .thenApplyAsync(Optional::isPresent)
                .toCompletableFuture()
                .join();
    }

    private boolean hasNotified(UUID uuid, InetAddress address) {
        var raw = new String(Base64.getEncoder().encode(address.getAddress()), StandardCharsets.UTF_8);
        return raw.equals(this.notifiedUuids.putIfAbsent(uuid, raw));
    }

    private record AccountInfo(String name, boolean banned, boolean merePossibility) {
    }
}
