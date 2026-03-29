package fr.enimaloc.gtd.file;

import java.util.ArrayList;
import java.util.List;

public class ForumChannelFile extends ChannelFile {
    public ForumChannelFile() {
        this.type = "forum";
    }

    public String topic;
    public List<String> availableTags = new ArrayList<>();
    public int slowmode;
    public String layout;
    public String defaultReactionEmoji;
    public String defaultSortOrder;
    public boolean tagRequired;
    public int defaultThreadSlowmode;
}
