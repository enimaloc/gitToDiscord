package fr.enimaloc.gtd.file;

public class MediaChannelFile extends ChannelFile {
    public MediaChannelFile() {
        this.type = "media";
    }

    public String topic;
    public int slowmode;
    public boolean nsfw;
    public boolean mediaDownloadHidden;
}
