package com.zendrahub.evolve.core.persist;

import com.zendrahub.evolve.core.model.WorldState;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Crash-safe save files. A save writes a temporary file, flushes it to disk,
 * moves the previous save to {@code <name>.bak} and moves the new file into
 * place, so at every moment either the new save, the old save or its backup
 * is intact. Loading falls back to the backup when the main file is missing,
 * unreadable or invalid, and reports what happened.
 */
public final class SaveManager {

    /**
     * @param state      the loaded world, or null when there is no save at all
     * @param fromBackup true when the main file was unusable and the backup was loaded
     * @param warnings   what went wrong on the way, for logs
     */
    public record LoadResult(WorldState state, boolean fromBackup, List<String> warnings) {
    }

    private SaveManager() {
    }

    public static Path backupOf(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    public static void save(Path file, WorldState state) throws IOException {
        byte[] data = StateCodec.toJson(state).getBytes(StandardCharsets.UTF_8);
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
            ch.force(true);
        }
        if (Files.exists(file)) {
            move(file, backupOf(file));
        }
        move(tmp, file);
    }

    public static LoadResult load(Path file) throws IOException {
        Path backup = backupOf(file);
        List<String> warnings = new ArrayList<>();
        if (Files.exists(file)) {
            try {
                return new LoadResult(read(file), false, warnings);
            } catch (IOException | RuntimeException e) {
                warnings.add("save " + file.getFileName() + " is unusable: " + e.getMessage());
            }
        } else if (Files.exists(backup)) {
            warnings.add("save " + file.getFileName() + " is missing; trying the backup");
        } else {
            return new LoadResult(null, false, warnings);
        }
        if (!Files.exists(backup)) {
            throw new IOException(String.join("; ", warnings) + "; no backup exists");
        }
        try {
            return new LoadResult(read(backup), true, warnings);
        } catch (IOException | RuntimeException e) {
            warnings.add("backup " + backup.getFileName() + " is unusable too: " + e.getMessage());
            throw new IOException(String.join("; ", warnings), e);
        }
    }

    private static WorldState read(Path file) throws IOException {
        return StateCodec.fromJson(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
