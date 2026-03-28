package fr.enimaloc.gtd.archive;

import fr.enimaloc.gtd.asset.AssetDownloader;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessageArchiver {

    private final Path gitRoot;

    public MessageArchiver(Path gitRoot) {
        this.gitRoot = gitRoot;
    }

    /**
     * Archive tous les channels du serveur.
     * @return map channel-name → nombre de messages archivés
     */
    public Map<String, Integer> archive(Guild guild) throws IOException {
        Map<String, Integer> result = new LinkedHashMap<>();

        List<GuildMessageChannel> channels = new ArrayList<>();
        channels.addAll(guild.getTextChannels());
        channels.addAll(guild.getNewsChannels());
        for (ForumChannel fc : guild.getForumChannels()) {
            channels.addAll(fc.getThreadChannels());
        }
        for (MediaChannel mc : guild.getMediaChannels()) {
            channels.addAll(mc.getThreadChannels());
        }

        for (GuildMessageChannel channel : channels) {
            int count = archiveChannel(channel);
            result.put(channel.getName() + " (" + channel.getIdLong() + ")", count);
        }
        return result;
    }

    private int archiveChannel(GuildMessageChannel channel) throws IOException {
        List<MessageRecord> records = new ArrayList<>();

        // Première page depuis le début
        MessageHistory history = channel.getHistoryFromBeginning(100).complete();
        List<Message> batch = history.getRetrievedHistory();

        while (!batch.isEmpty()) {
            // getRetrievedHistory() retourne du plus récent au plus ancien — on inverse
            for (int i = batch.size() - 1; i >= 0; i--) {
                records.add(toRecord(batch.get(i)));
            }
            if (batch.size() < 100) break; // dernière page

            // Page suivante : messages après le plus récent de ce batch
            String newestId = batch.get(0).getId();
            history = channel.getHistoryAfter(newestId, 100).complete();
            batch = history.getRetrievedHistory();
        }

        Path dest = gitRoot.resolve("messages/" + channel.getIdLong() + ".msg");
        MsgPackZstdWriter.write(records, dest);
        return records.size();
    }

    private MessageRecord toRecord(Message msg) {
        MessageRecord r = new MessageRecord();
        r.id = msg.getIdLong();
        r.authorId = msg.getAuthor().getIdLong();
        r.authorName = msg.getAuthor().getName();
        r.content = msg.getContentRaw();
        r.timestamp = msg.getTimeCreated().toInstant().toEpochMilli();
        r.editedTimestamp = msg.getTimeEdited() != null
                ? msg.getTimeEdited().toInstant().toEpochMilli() : 0L;

        msg.getReactions().forEach(reaction -> {
            MessageRecord.ReactionRecord rr = new MessageRecord.ReactionRecord();
            rr.emoji = reaction.getEmoji().getName();
            rr.count = reaction.getCount();
            r.reactions.add(rr);
        });

        msg.getEmbeds().forEach(embed -> {
            MessageRecord.EmbedRecord er = new MessageRecord.EmbedRecord();
            er.title = embed.getTitle();
            er.description = embed.getDescription();
            er.url = embed.getUrl();
            r.embeds.add(er);
        });

        Path attachDir = gitRoot.resolve("messages/" + msg.getChannel().getIdLong() + "/attachments");
        msg.getAttachments().forEach(attachment -> {
            MessageRecord.AttachmentRecord ar = new MessageRecord.AttachmentRecord();
            ar.filename = attachment.getFileName();
            ar.url = attachment.getUrl();

            if (attachment.isImage()) {
                Path dest = attachDir.resolve(msg.getIdLong() + "_" + attachment.getFileName() + ".webp");
                Path written = AssetDownloader.download(attachment.getUrl(), dest);
                ar.localPath = written != null ? gitRoot.relativize(written).toString() : null;
            } else {
                Path dest = attachDir.resolve(msg.getIdLong() + "_" + attachment.getFileName());
                try {
                    Files.createDirectories(dest.getParent());
                    try (var in = URI.create(attachment.getUrl()).toURL().openStream()) {
                        byte[] bytes = in.readAllBytes();
                        Files.write(dest, bytes);
                        ar.localPath = gitRoot.relativize(dest).toString();
                    }
                } catch (IOException e) {
                    System.err.println("[MessageArchiver] Impossible de télécharger " + attachment.getUrl() + " : " + e.getMessage());
                    ar.localPath = null;
                }
            }
            r.attachments.add(ar);
        });

        return r;
    }
}
