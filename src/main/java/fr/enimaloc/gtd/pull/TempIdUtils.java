package fr.enimaloc.gtd.pull;

public final class TempIdUtils {
    /** IDs Discord valides sont >= 10^15. En-dessous = ID temporaire défini par l'utilisateur. */
    public static final long THRESHOLD = 1_000_000_000_000_000L;

    private TempIdUtils() {}

    /** Retourne true si l'id est un ID temporaire (non-Discord, > 0 et < THRESHOLD). */
    public static boolean isTemp(long id) {
        return id > 0 && id < THRESHOLD;
    }
}
