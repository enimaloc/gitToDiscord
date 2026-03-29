package fr.enimaloc.gtd.file;

public class TextChannelFile extends ChannelFile {
    public TextChannelFile() {
        this.type = "text";
    }

    public String topic;
    public int slowmode;
    public boolean nsfw;
}
