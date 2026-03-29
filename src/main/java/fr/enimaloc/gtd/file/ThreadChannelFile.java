package fr.enimaloc.gtd.file;

import java.util.ArrayList;
import java.util.List;

public class ThreadChannelFile {
    public static final String FILE_PATH = "threads/%d/%d.toml";

    public long id;
    public String type = "thread";
    public String name;
    public long parentChannelId;
    public boolean archived;
    public boolean locked;
    public int autoArchiveDurationMinutes;
    public List<Long> memberIds = new ArrayList<>();
    public boolean pinned;
    public boolean invitable;
    public List<String> appliedTags = new ArrayList<>();
    public long ownerId;
    public boolean _public;
}
