package com.iras;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileCleanup {
    private final JdbcTemplate database;
    private final Path storage;
    public FileCleanup(JdbcTemplate database, @Value("${iras.storage}") String storage) {
        this.database = database;
        this.storage = Path.of(storage).toAbsolutePath().normalize();
    }
    public void enqueue(String filename) {
        database.update("INSERT INTO file_cleanup(file_path) VALUES (?) ON CONFLICT DO NOTHING", filename);
    }
    public boolean remove(String filename) {
        Path path = storage.resolve(filename).normalize();
        if (!storage.equals(path.getParent())) return false;
        try {
            Files.deleteIfExists(path);
            database.update("DELETE FROM file_cleanup WHERE file_path=?", filename);
            return true;
        } catch (Exception error) {
            database.update("UPDATE file_cleanup SET attempts=attempts+1 WHERE file_path=?", filename);
            return false;
        }
    }
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void retry() {
        database.queryForList("SELECT file_path FROM file_cleanup ORDER BY created_at LIMIT 100", String.class).forEach(this::remove);
    }
}