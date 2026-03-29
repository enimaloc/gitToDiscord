package fr.enimaloc.gtd.pull;

import fr.enimaloc.gtd.file.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PullReconciler {

    public ReconcileResult<RoleFile> reconcileRoles(Map<Long, String> existingIdToName, List<RoleFile> desired) {
        return reconcile(desired, existingIdToName, f -> f.id, f -> f.name);
    }

    public ReconcileResult<CategoryFile> reconcileCategories(Map<Long, String> existingIdToName, List<CategoryFile> desired) {
        return reconcile(desired, existingIdToName, f -> f.id, f -> f.name);
    }

    public ReconcileResult<ChannelFile> reconcileChannels(Map<Long, String> existingIdToName, List<ChannelFile> desired) {
        return reconcile(desired, existingIdToName, f -> f.id, f -> f.name);
    }

    private <T> ReconcileResult<T> reconcile(List<T> desired, Map<Long, String> existingIdToName,
            Function<T, Long> idGetter, Function<T, String> nameGetter) {
        List<T> toCreate = new ArrayList<>();
        List<ReconcileResult.UpdateEntry<T>> toUpdate = new ArrayList<>();
        Set<Long> matchedIds = new HashSet<>();

        // Build name→id map (first occurrence wins for duplicates)
        Map<String, Long> nameToId = new LinkedHashMap<>();
        existingIdToName.forEach((id, name) -> nameToId.putIfAbsent(name, id));

        for (T file : desired) {
            long id = idGetter.apply(file);
            String name = nameGetter.apply(file);
            if (id != 0 && existingIdToName.containsKey(id)) {
                matchedIds.add(id);
                toUpdate.add(new ReconcileResult.UpdateEntry<>(id, file));
            } else if (nameToId.containsKey(name)) {
                long matched = nameToId.get(name);
                matchedIds.add(matched);
                toUpdate.add(new ReconcileResult.UpdateEntry<>(matched, file));
            } else {
                toCreate.add(file);
            }
        }

        List<Long> toDelete = existingIdToName.keySet().stream()
            .filter(id -> !matchedIds.contains(id))
            .toList();

        return ReconcileResult.of(toCreate, toUpdate, toDelete);
    }
}
