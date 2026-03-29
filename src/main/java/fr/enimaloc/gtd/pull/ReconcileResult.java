package fr.enimaloc.gtd.pull;

import java.util.List;

public record ReconcileResult<T>(List<T> toCreate, List<ReconcileResult.UpdateEntry<T>> toUpdate, List<Long> toDelete) {

    public record UpdateEntry<T>(long discordId, T file) {}

    public static <T> ReconcileResult<T> of(List<T> create, List<UpdateEntry<T>> update, List<Long> delete) {
        return new ReconcileResult<>(create, update, delete);
    }
}
