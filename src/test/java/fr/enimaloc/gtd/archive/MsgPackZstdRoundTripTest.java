package fr.enimaloc.gtd.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MsgPackZstdRoundTripTest {

    @Test
    void roundTripEmptyList(@TempDir Path dir) throws IOException {
        Path dest = dir.resolve("empty.msg");
        MsgPackZstdWriter.write(List.of(), dest);
        List<MessageRecord> result = MsgPackZstdReader.read(dest);
        assertTrue(result.isEmpty());
    }

    @Test
    void roundTripFullRecord(@TempDir Path dir) throws IOException {
        MessageRecord r = new MessageRecord();
        r.id = 123456789L;
        r.authorId = 987654321L;
        r.authorName = "testUser";
        r.content = "Hello world";
        r.timestamp = 1711574400000L;
        r.editedTimestamp = 0L;

        MessageRecord.ReactionRecord reaction = new MessageRecord.ReactionRecord();
        reaction.emoji = "👍";
        reaction.count = 3;
        r.reactions.add(reaction);

        MessageRecord.EmbedRecord embed = new MessageRecord.EmbedRecord();
        embed.title = "My Title";
        embed.description = null; // test null string
        embed.url = "https://example.com";
        r.embeds.add(embed);

        MessageRecord.AttachmentRecord att = new MessageRecord.AttachmentRecord();
        att.filename = "image.png";
        att.url = "https://cdn.discordapp.com/attachments/1/2/image.png";
        att.localPath = "messages/1/attachments/2_image.webp";
        r.attachments.add(att);

        Path dest = dir.resolve("record.msg");
        MsgPackZstdWriter.write(List.of(r), dest);
        List<MessageRecord> result = MsgPackZstdReader.read(dest);

        assertEquals(1, result.size());
        MessageRecord got = result.get(0);
        assertEquals(r.id, got.id);
        assertEquals(r.authorId, got.authorId);
        assertEquals(r.authorName, got.authorName);
        assertEquals(r.content, got.content);
        assertEquals(r.timestamp, got.timestamp);
        assertEquals(r.editedTimestamp, got.editedTimestamp);

        assertEquals(1, got.reactions.size());
        assertEquals("👍", got.reactions.get(0).emoji);
        assertEquals(3, got.reactions.get(0).count);

        assertEquals(1, got.embeds.size());
        assertEquals("My Title", got.embeds.get(0).title);
        assertNull(got.embeds.get(0).description);
        assertEquals("https://example.com", got.embeds.get(0).url);

        assertEquals(1, got.attachments.size());
        assertEquals("image.png", got.attachments.get(0).filename);
        assertEquals("messages/1/attachments/2_image.webp", got.attachments.get(0).localPath);
    }

    @Test
    void multipleRecords(@TempDir Path dir) throws IOException {
        MessageRecord r1 = new MessageRecord();
        r1.id = 1L;
        r1.authorName = "alice";
        r1.content = "first";

        MessageRecord r2 = new MessageRecord();
        r2.id = 2L;
        r2.authorName = "bob";
        r2.content = "second";

        Path dest = dir.resolve("multi.msg");
        MsgPackZstdWriter.write(List.of(r1, r2), dest);
        List<MessageRecord> result = MsgPackZstdReader.read(dest);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id);
        assertEquals(2L, result.get(1).id);
        assertEquals("alice", result.get(0).authorName);
        assertEquals("bob", result.get(1).authorName);
    }
}
