package net.okocraft.punishmentnotifier;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.okocraft.punishmentnotifier.data.MapDataFile;
import net.okocraft.punishmentnotifier.util.UUIDParser;
import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.punish.Punishment;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public class PlayerNotifier {

    private static final long[] EMPTY_IDS = new long[0];
    private static final long INVALID_ID = Long.MIN_VALUE;
    private static final char DELIMITER = ',';
    private static final Pattern DELIMITER_PATTERN = Pattern.compile(String.valueOf(DELIMITER), Pattern.LITERAL);

    private final StampedLock lock = new StampedLock();
    private final LibertyBans libertyBans;
    private final MapDataFile<UUID, long[]> dataFile;
    private final BiConsumer<Runnable, Duration> asyncExecutor;
    private final Map<UUID, long[]> notifications = new HashMap<>();

    public PlayerNotifier(LibertyBans libertyBans, Path dataDirectory, BiConsumer<Runnable, Duration> asyncExecutor) {
        this.libertyBans = libertyBans;
        this.dataFile = new MapDataFile<>(
                dataDirectory.resolve("player-notifications.dat"),
                UUID::toString, PlayerNotifier::idsToString,
                UUIDParser::parse, PlayerNotifier::stringToIds
        );
        this.asyncExecutor = asyncExecutor;
    }

    private static String idsToString(long[] ids) {
        var builder = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            long id = ids[i];
            if (id != INVALID_ID) {
                if (i != 0) {
                    builder.append(DELIMITER);
                }
                builder.append(id);
            }
        }
        return builder.toString();
    }

    private static long[] stringToIds(String str) {
        var strIds = DELIMITER_PATTERN.split(str);
        long[] ids = new long[strIds.length];
        for (int i = 0; i < strIds.length; i++) {
            try {
                ids[i] = Long.parseLong(strIds[i]);
            } catch (NumberFormatException e) {
                ids[i] = INVALID_ID;
            }
        }
        return ids;
    }

    @SuppressWarnings("UnstableApiUsage")
    @Subscribe()
    public void onConnect(ServerPostConnectEvent e) {
        if (e.getPreviousServer() != null) {
            return;
        }

        long[] ids = this.pickPunishmentIds(e.getPlayer().getUniqueId());

        if (ids.length != 0) {
            this.asyncExecutor.accept(() -> this.notifyPunishments(e.getPlayer(), ids), Duration.ofSeconds(3));
        }
    }

    private void notifyPunishments(Player player, long[] ids) {
        for (long id : ids) {
            if (id == INVALID_ID) {
                continue;
            }

            this.libertyBans.getSelector()
                    .getActivePunishmentById(id)
                    .thenApply(punishment -> {
                        if (punishment.isEmpty()) {
                            return null;
                        }

                        try {
                            return Reflections.getPunishmentMessage(this.libertyBans.getFormatter(), punishment.get());
                        } catch (Throwable ex) {
                            PunishmentNotifier.LOGGER.error("An exception occurred while getting a punishment message.", ex);
                            return null;
                        }
                    })
                    .thenAcceptAsync(future -> {
                        if (future != null) {
                            future.thenAcceptAsync(player::sendMessage);
                        }
                    });
        }
    }

    public void addNotification(Punishment p) {
        if (!(p.getVictim() instanceof PlayerVictim player)) {
            return;
        }

        var uuid = player.getUUID();

        long stamp = this.lock.writeLock();

        Map<UUID, long[]> snapshot;

        try {
            long[] array = this.notifications.get(uuid);
            long[] newArray = array != null ? Arrays.copyOf(array, array.length + 1) : new long[1];
            newArray[newArray.length - 1] = p.getIdentifier();

            this.notifications.put(uuid, newArray);
            snapshot = Map.copyOf(this.notifications);
        } finally {
            this.lock.unlockWrite(stamp);
        }

        this.saveAsync(snapshot);
    }

    public void removeNotification(Punishment p) {
        if (!(p.getVictim() instanceof PlayerVictim player)) {
            return;
        }

        var uuid = player.getUUID();

        long stamp = this.lock.writeLock();

        Map<UUID, long[]> snapshot;

        try {
            long[] array = this.notifications.get(uuid);

            if (array == null) {
                return;
            }

            int index = -1;

            for (int i = 0; i < array.length; i++) {
                long id = array[i];

                if (id == p.getIdentifier()) {
                    index = i;
                    break;
                }
            }

            if (index == -1) {
                return;
            }

            long[] newArray = new long[array.length - 1];
            boolean removed = false;

            for (int i = 0; i < array.length; i++) {
                if (removed) {
                    newArray[i - 1] = array[i];
                } else {
                    if (i == index) {
                        removed = true;
                    } else {
                        newArray[i] = array[i];
                    }
                }
            }

            this.notifications.put(uuid, newArray);
            snapshot = Map.copyOf(this.notifications);
        } finally {
            this.lock.unlockWrite(stamp);
        }

        this.saveAsync(snapshot);
    }

    private long[] pickPunishmentIds(UUID uuid) {
        {
            long stamp = this.lock.tryOptimisticRead();
            if (!this.notifications.containsKey(uuid) && this.lock.validate(stamp)) {
                return EMPTY_IDS;
            }
        }

        long stamp = this.lock.writeLock();

        long[] ids;
        Map<UUID, long[]> snapshot;

        try {
            ids = this.notifications.remove(uuid);
            snapshot = Map.copyOf(this.notifications);
        } finally {
            this.lock.unlockWrite(stamp);
        }

        this.saveAsync(snapshot);

        return Objects.requireNonNullElse(ids, EMPTY_IDS);
    }

    public void load() {
        try {
            this.dataFile.load(this.notifications);
        } catch (IOException e) {
            PunishmentNotifier.LOGGER.error("Could not load player-notifications.dat", e);
        }
    }

    private void saveAsync(Map<UUID, long[]> snapshot) {
        this.asyncExecutor.accept(() -> this.save(snapshot), null);
    }

    private synchronized void save(Map<UUID, long[]> snapshot) {
        try {
            this.dataFile.save(snapshot);
        } catch (IOException e) {
            PunishmentNotifier.LOGGER.error("Could not save player-notifications.dat", e);
        }
    }
}
