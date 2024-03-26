package net.okocraft.punishmentnotifier.data;

import net.okocraft.punishmentnotifier.PunishmentNotifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public record MapDataFile<K, V>(Path filepath,
                                Function<K, String> keyToString, Function<V, String> valueToString,
                                Function<String, K> stringToKey, Function<String, V> stringToValue) {

    private static final char SEPARATOR = ':';
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile(String.valueOf(SEPARATOR), Pattern.LITERAL);

    public void load(Map<K, V> map) throws IOException {
        if (Files.isRegularFile(this.filepath)) {
            try (var reader = Files.newBufferedReader(this.filepath)) {
                reader.lines().forEach(line -> this.processLine(line, map));
            }
        }
    }

    private void processLine(String line, Map<K, V> map) {
        var elements = SEPARATOR_PATTERN.split(line, 2);

        if (elements.length != 2) {
            PunishmentNotifier.LOGGER.error("Invalid line in {}: {}", this.filepath.getFileName(), line);
            return;
        }

        K key = this.stringToKey.apply(elements[0]);
        V value = this.stringToValue.apply(elements[1]);

        if (key != null && value != null) {
            map.put(key, value);
        }
    }

    public void save(Map<K, V> map) throws IOException {
        var parent = this.filepath.getParent();

        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }

        try (var writer = Files.newBufferedWriter(
                this.filepath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            for (var entry : map.entrySet()) {
                writer.write(this.keyToString.apply(entry.getKey()));
                writer.write(SEPARATOR);
                writer.write(this.valueToString.apply(entry.getValue()));
                writer.newLine();
            }
        }
    }
}
