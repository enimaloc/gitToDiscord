package fr.enimaloc.gtd.archive;

import com.github.luben.zstd.ZstdOutputStream;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MsgPackZstdWriter {

    private static final int ZSTD_LEVEL = 19;

    public static void write(List<MessageRecord> records, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZstdOutputStream zos = new ZstdOutputStream(baos, ZSTD_LEVEL);
             MessagePacker packer = MessagePack.newDefaultPacker(zos)) {
            packer.packArrayHeader(records.size());
            for (MessageRecord r : records) {
                packRecord(packer, r);
            }
        }
        Files.write(destination, baos.toByteArray());
    }

    private static void packRecord(MessagePacker p, MessageRecord r) throws IOException {
        p.packArrayHeader(9);
        p.packLong(r.id);
        p.packLong(r.authorId);
        packString(p, r.authorName);
        packString(p, r.content);
        p.packLong(r.timestamp);
        p.packLong(r.editedTimestamp);

        p.packArrayHeader(r.reactions.size());
        for (MessageRecord.ReactionRecord rr : r.reactions) {
            p.packArrayHeader(2);
            packString(p, rr.emoji);
            p.packInt(rr.count);
        }

        p.packArrayHeader(r.embeds.size());
        for (MessageRecord.EmbedRecord er : r.embeds) {
            p.packArrayHeader(3);
            packString(p, er.title);
            packString(p, er.description);
            packString(p, er.url);
        }

        p.packArrayHeader(r.attachments.size());
        for (MessageRecord.AttachmentRecord ar : r.attachments) {
            p.packArrayHeader(3);
            packString(p, ar.filename);
            packString(p, ar.url);
            packString(p, ar.localPath);
        }
    }

    private static void packString(MessagePacker p, String s) throws IOException {
        if (s == null) p.packNil();
        else p.packString(s);
    }
}
