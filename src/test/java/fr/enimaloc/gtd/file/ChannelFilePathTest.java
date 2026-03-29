package fr.enimaloc.gtd.file;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChannelFilePathTest {

    @Test
    void roleFilePath_formatsWithId() {
        String path = RoleFile.FILE_PATH.formatted(123456789012345678L);
        assertEquals("roles/123456789012345678.toml", path);
    }

    @Test
    void categoryFilePath_formatsWithId() {
        String path = CategoryFile.FILE_PATH.formatted(987654321098765432L);
        assertEquals("categories/987654321098765432.toml", path);
    }

    @Test
    void channelFilePath_formatsWithId() {
        String path = ChannelFile.FILE_PATH.formatted(111222333444555666L);
        assertEquals("channels/111222333444555666.toml", path);
    }

    @Test
    void threadFilePath_formatsWithParentIdAndThreadId() {
        String path = ThreadChannelFile.FILE_PATH.formatted(555666777888999000L, 100200300400500600L);
        assertEquals("threads/555666777888999000/100200300400500600.toml", path);
    }

    @Test
    void emoteFilePath_formatsWithId() {
        String path = EmoteFile.FILE_PATH.formatted(222333444555666777L);
        assertEquals("emojis/222333444555666777.toml", path);
    }

    @Test
    void stickerFilePath_formatsWithId() {
        String path = StickerFile.FILE_PATH.formatted(333444555666777888L);
        assertEquals("stickers/333444555666777888.toml", path);
    }
}
