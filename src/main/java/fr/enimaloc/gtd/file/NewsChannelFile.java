package fr.enimaloc.gtd.file;

public class NewsChannelFile extends ChannelFile {
    public NewsChannelFile() {
        this.type = "news";
    }

    public String topic;
    public boolean nsfw;
}
