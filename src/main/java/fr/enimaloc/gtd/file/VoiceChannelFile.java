package fr.enimaloc.gtd.file;

public class VoiceChannelFile extends ChannelFile {
    public VoiceChannelFile() {
        this.type = "voice";
    }

    public int bitrate;
    public int userLimit;
    public String region;
}
