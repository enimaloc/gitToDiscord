package fr.enimaloc.gtd.file;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ChannelFileTomlTest {

    private static final TomlMapper MAPPER = new TomlMapper();

    @Test
    void textChannel_roundTrip() throws IOException {
        TextChannelFile original = new TextChannelFile();
        original.id = 111222333444555666L;
        original.name = "general";
        original.position = 2;
        original.parentCategoryId = 999888777666555444L;
        original.positionInCategory = 1;
        original.topic = "General chat";
        original.slowmode = 5;
        original.nsfw = false;

        String toml = MAPPER.writeValueAsString(original);
        TextChannelFile restored = MAPPER.readValue(toml, TextChannelFile.class);

        assertEquals(original.id, restored.id);
        assertEquals(original.name, restored.name);
        assertEquals(original.position, restored.position);
        assertEquals(original.parentCategoryId, restored.parentCategoryId);
        assertEquals(original.topic, restored.topic);
        assertEquals(original.slowmode, restored.slowmode);
        assertEquals(original.nsfw, restored.nsfw);
        assertEquals("text", restored.type);
    }

    @Test
    void voiceChannel_roundTrip() throws IOException {
        VoiceChannelFile original = new VoiceChannelFile();
        original.id = 222333444555666777L;
        original.name = "voice-general";
        original.position = 0;
        original.parentCategoryId = 0L;
        original.positionInCategory = 0;
        original.bitrate = 64000;
        original.userLimit = 10;
        original.region = "auto";

        String toml = MAPPER.writeValueAsString(original);
        VoiceChannelFile restored = MAPPER.readValue(toml, VoiceChannelFile.class);

        assertEquals(original.id, restored.id);
        assertEquals(original.name, restored.name);
        assertEquals(original.bitrate, restored.bitrate);
        assertEquals(original.userLimit, restored.userLimit);
        assertEquals(original.region, restored.region);
        assertEquals("voice", restored.type);
    }

    @Test
    void forumChannel_roundTrip() throws IOException {
        ForumChannelFile original = new ForumChannelFile();
        original.id = 333444555666777888L;
        original.name = "forum";
        original.position = 3;
        original.parentCategoryId = 0L;
        original.positionInCategory = 0;
        original.topic = "Forum topic";
        original.slowmode = 10;
        original.layout = "LIST";
        original.availableTags.add("bug");
        original.availableTags.add("feature");
        original.defaultReactionEmoji = "thumbsup";
        original.defaultSortOrder = "LATEST_ACTIVITY";
        original.tagRequired = true;
        original.defaultThreadSlowmode = 30;

        String toml = MAPPER.writeValueAsString(original);
        ForumChannelFile restored = MAPPER.readValue(toml, ForumChannelFile.class);

        assertEquals(original.id, restored.id);
        assertEquals(original.name, restored.name);
        assertEquals(original.topic, restored.topic);
        assertEquals(original.slowmode, restored.slowmode);
        assertEquals(original.layout, restored.layout);
        assertEquals(original.availableTags, restored.availableTags);
        assertEquals(original.defaultReactionEmoji, restored.defaultReactionEmoji);
        assertEquals(original.defaultSortOrder, restored.defaultSortOrder);
        assertEquals(original.tagRequired, restored.tagRequired);
        assertEquals(original.defaultThreadSlowmode, restored.defaultThreadSlowmode);
        assertEquals("forum", restored.type);
    }

    @Test
    void newsChannel_roundTrip() throws IOException {
        NewsChannelFile original = new NewsChannelFile();
        original.id = 444555666777888999L;
        original.name = "announcements";
        original.position = 0;
        original.parentCategoryId = 0L;
        original.positionInCategory = 0;
        original.topic = "Official announcements";
        original.nsfw = false;

        String toml = MAPPER.writeValueAsString(original);
        NewsChannelFile restored = MAPPER.readValue(toml, NewsChannelFile.class);

        assertEquals(original.id, restored.id);
        assertEquals(original.name, restored.name);
        assertEquals(original.topic, restored.topic);
        assertEquals(original.nsfw, restored.nsfw);
        assertEquals("news", restored.type);
    }

    @Test
    void mediaChannel_roundTrip() throws IOException {
        MediaChannelFile original = new MediaChannelFile();
        original.id = 555666777888999000L;
        original.name = "media";
        original.position = 1;
        original.parentCategoryId = 0L;
        original.positionInCategory = 0;
        original.topic = "Share media";
        original.slowmode = 0;
        original.nsfw = false;
        original.mediaDownloadHidden = true;

        String toml = MAPPER.writeValueAsString(original);
        MediaChannelFile restored = MAPPER.readValue(toml, MediaChannelFile.class);

        assertEquals(original.id, restored.id);
        assertEquals(original.name, restored.name);
        assertEquals(original.nsfw, restored.nsfw);
        assertEquals(original.mediaDownloadHidden, restored.mediaDownloadHidden);
        assertEquals("media", restored.type);
    }
}
