package fr.enimaloc.gtd.archive;

import com.github.luben.zstd.ZstdInputStream;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MsgPackZstdReader {

    public static List<MessageRecord> read(Path source) throws IOException {
        byte[] compressed = Files.readAllBytes(source);
        try (ZstdInputStream zis = new ZstdInputStream(new ByteArrayInputStream(compressed));
             MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(zis)) {
            int count = unpacker.unpackArrayHeader();
            List<MessageRecord> records = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                records.add(unpackRecord(unpacker));
            }
            return records;
        }
    }

    private static MessageRecord unpackRecord(MessageUnpacker u) throws IOException {
        int fieldCount = u.unpackArrayHeader();
        if (fieldCount != 9) {
            throw new IOException("Unexpected record field count: " + fieldCount + " (expected 9)");
        }
        MessageRecord r = new MessageRecord();
        r.id = u.unpackLong();
        r.authorId = u.unpackLong();
        r.authorName = unpackString(u);
        r.content = unpackString(u);
        r.timestamp = u.unpackLong();
        r.editedTimestamp = u.unpackLong();

        int reactionCount = u.unpackArrayHeader();
        for (int i = 0; i < reactionCount; i++) {
            u.unpackArrayHeader(); // 2
            MessageRecord.ReactionRecord rr = new MessageRecord.ReactionRecord();
            rr.emoji = unpackString(u);
            rr.count = u.unpackInt();
            r.reactions.add(rr);
        }

        int embedCount = u.unpackArrayHeader();
        for (int i = 0; i < embedCount; i++) {
            u.unpackArrayHeader(); // 3
            MessageRecord.EmbedRecord er = new MessageRecord.EmbedRecord();
            er.title = unpackString(u);
            er.description = unpackString(u);
            er.url = unpackString(u);
            r.embeds.add(er);
        }

        int attachCount = u.unpackArrayHeader();
        for (int i = 0; i < attachCount; i++) {
            u.unpackArrayHeader(); // 3
            MessageRecord.AttachmentRecord ar = new MessageRecord.AttachmentRecord();
            ar.filename = unpackString(u);
            ar.url = unpackString(u);
            ar.localPath = unpackString(u);
            r.attachments.add(ar);
        }
        return r;
    }

    private static String unpackString(MessageUnpacker u) throws IOException {
        if (u.getNextFormat() == MessageFormat.NIL) {
            u.unpackNil();
            return null;
        }
        return u.unpackString();
    }
}
