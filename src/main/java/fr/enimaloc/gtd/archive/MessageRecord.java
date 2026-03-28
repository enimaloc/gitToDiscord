package fr.enimaloc.gtd.archive;

import java.util.ArrayList;
import java.util.List;

public class MessageRecord {
    public long id;
    public long authorId;
    public String authorName;
    public String content;
    public long timestamp;        // epoch ms
    public long editedTimestamp;  // 0 si non édité
    public List<ReactionRecord> reactions = new ArrayList<>();
    public List<EmbedRecord> embeds = new ArrayList<>();
    public List<AttachmentRecord> attachments = new ArrayList<>();

    public static class ReactionRecord {
        public String emoji;
        public int count;
    }

    public static class EmbedRecord {
        public String title;
        public String description;
        public String url;
    }

    public static class AttachmentRecord {
        public String filename;
        public String url;
        public String localPath; // chemin relatif à gitRoot, null si non téléchargé
    }
}
