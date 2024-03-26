package net.okocraft.punishmentnotifier;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.punish.Punishment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;

public class PlayerNotifier {

    private static final long[] EMPTY_IDS = new long[0];
    private static final long INVALID_ID = Long.MIN_VALUE;

    private final Map<UUID, long[]> warnMap = new HashMap<>();
    private final StampedLock lock = new StampedLock();
    private final LibertyBans libertyBans;
    private final Path dataDirectory;
    private final BiConsumer<Runnable, Duration> asyncExecutor;

    public PlayerNotifier(LibertyBans libertyBans, Path dataDirectory, BiConsumer<Runnable, Duration> asyncExecutor) {
        this.libertyBans = libertyBans;
        this.dataDirectory = dataDirectory;
        this.asyncExecutor = asyncExecutor;
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
            long[] array = this.warnMap.get(uuid);
            long[] newArray = array != null ? Arrays.copyOf(array, array.length + 1) : new long[1];
            newArray[newArray.length - 1] = p.getIdentifier();

            this.warnMap.put(uuid, newArray);
            snapshot = Map.copyOf(this.warnMap);
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
            long[] array = this.warnMap.get(uuid);

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

            this.warnMap.put(uuid, newArray);
            snapshot = Map.copyOf(this.warnMap);
        } finally {
            this.lock.unlockWrite(stamp);
        }

        this.saveAsync(snapshot);
    }

    private long[] pickPunishmentIds(UUID uuid) {
        {
            long stamp = this.lock.tryOptimisticRead();
            if (!this.warnMap.containsKey(uuid) && this.lock.validate(stamp)) {
                return EMPTY_IDS;
            }
        }

        long stamp = this.lock.writeLock();

        long[] ids;
        Map<UUID, long[]> snapshot;

        try {
            ids = this.warnMap.remove(uuid);
            snapshot = Map.copyOf(this.warnMap);
        } finally {
            this.lock.unlockWrite(stamp);
        }

        this.saveAsync(snapshot);

        return Objects.requireNonNullElse(ids, EMPTY_IDS);
    }

    public void load() {
        var path = this.dataDirectory.resolve("data.dat");

        if (Files.isRegularFile(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                reader.lines().forEach(this::processLine);
            } catch (IOException e) {
                PunishmentNotifier.LOGGER.error("Could not load data.dat", e);
            }
        }
    }

    private void processLine(String line) {
        var elements = line.split("=", 2);

        if (elements.length != 2) {
            PunishmentNotifier.LOGGER.error("Invalid line: " + line);
            return;
        }

        UUID uuid;

        try {
            uuid = UUID.fromString(elements[0]);
        } catch (IllegalArgumentException e) {
            PunishmentNotifier.LOGGER.error("Invalid line: " + line);
            return;
        }

        var strIds = elements[1].split(",");
        long[] ids = new long[strIds.length];

        for (int i = 0; i < strIds.length; i++) {
            try {
                ids[i] = Long.parseLong(strIds[i]);
            } catch (NumberFormatException e) {
                ids[i] = INVALID_ID;
            }
        }

        this.warnMap.put(uuid, ids);
    }

    private void saveAsync(Map<UUID, long[]> snapshot) {
        this.asyncExecutor.accept(() -> this.save(snapshot), null);
    }

    private synchronized void save(Map<UUID, long[]> snapshot) {
        try (var writer = Files.newBufferedWriter(
                this.dataDirectory.resolve("data.dat"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writeWarnMap(snapshot, writer);
        } catch (IOException e) {
            PunishmentNotifier.LOGGER.error("Could not save data.dat", e);
        }
    }

    private static void writeWarnMap(Map<UUID, long[]> snapshot, BufferedWriter writer) throws IOException {
        for (var entry : snapshot.entrySet()) {
            long[] value = entry.getValue();

            if (value.length == 0) {
                continue;
            }

            int idCount = 0;

            for (long id : value) {
                if (id != INVALID_ID) {
                    if (idCount++ == 0) {
                        writer.write(entry.getKey().toString());
                        writer.write('=');
                    } else {
                        writer.write(',');
                    }
                    writer.write(Long.toString(id));
                }
            }

            writer.newLine();
        }
    }
}
