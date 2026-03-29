package fr.enimaloc.gtd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerGitConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void gitConfig_roundtrip_preservesGitToken() throws IOException {
        java.io.File configFile = tempDir.resolve("config.toml").toFile();
        Server.GitConfig config = new Server.GitConfig();
        config.branch = "develop";
        config.gitToken = "ghp_testtoken123";
        GTD.MAPPER.writer().writeValue(configFile, config);

        Server.GitConfig loaded = GTD.MAPPER.readValue(configFile, Server.GitConfig.class);

        assertEquals("develop", loaded.branch);
        assertEquals("ghp_testtoken123", loaded.gitToken);
    }

    @Test
    void gitConfig_missingToken_deserializesAsNull() throws IOException {
        java.io.File configFile = tempDir.resolve("config.toml").toFile();
        // Simulate an existing config.toml without gitToken (backward compat)
        Files.writeString(configFile.toPath(), "branch = 'main'\n");

        Server.GitConfig loaded = GTD.MAPPER.readValue(configFile, Server.GitConfig.class);

        assertEquals("main", loaded.branch);
        assertNull(loaded.gitToken);
    }
}
