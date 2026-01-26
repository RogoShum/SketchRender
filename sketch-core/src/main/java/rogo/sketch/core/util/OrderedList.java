package rogo.sketch.core.util;

import java.util.*;

public class OrderedList<T> {
    public interface AddCallback<T> {
        void onAdded(T element, OrderRequirement<T> requirement);
    }

    public static class PendingElement<T> {
        final T element;
        final OrderRequirement<T> requirement;
        String failReason;
        AddCallback<T> callback;

        PendingElement(T element, OrderRequirement<T> requirement, String failReason, AddCallback<T> callback) {
            this.element = element;
            this.requirement = requirement;
            this.failReason = failReason;
            this.callback = callback;
        }
    }

    private final Map<T, OrderRequirement<T>> elementRequirements = new LinkedHashMap<>();
    private final List<T> orderedList = new ArrayList<>();
    private final boolean throwOnSortFail;
    private final List<PendingElement<T>> pendingElements = new ArrayList<>();
    private final Map<T, Integer> elementIndexMap = new HashMap<>();

    public OrderedList(boolean throwOnSortFail) {
        this.throwOnSortFail = throwOnSortFail;
    }

    public boolean add(T element, OrderRequirement<T> requirement) {
        return add(element, requirement, null);
    }

    public boolean add(T element, OrderRequirement<T> requirement, AddCallback<T> callback) {
        String failReason = null;
        if (!canAddNow(requirement)) {
            failReason = "Missing required elements: " + requirement.getRequiredElements();
            pendingElements.add(new PendingElement<>(element, requirement, failReason, callback));
            return false;
        }
        boolean success = addInternal(element, requirement);
        if (success && callback != null) callback.onAdded(element, requirement);
        processPending();
        return success;
    }

    private boolean addInternal(T element, OrderRequirement<T> requirement) {
        elementRequirements.put(element, requirement);
        if (!sort()) {
            if (requirement.isFollowDefault()) {
                orderedList.clear();
                orderedList.addAll(elementRequirements.keySet());
            } else if (throwOnSortFail) {
                elementRequirements.remove(element);
                throw new IllegalStateException("Cannot resolve order for element: " + element);
            } else {
                elementRequirements.remove(element);
                pendingElements.add(new PendingElement<>(element, requirement, "No suitable insertion position found (topological sort failed)", null));
                return false;
            }
        }
        // Remove all pending attempts for this element after success
        pendingElements.removeIf(p -> p.element.equals(element));
        return true;
    }

    private boolean canAddNow(OrderRequirement<T> requirement) {
        for (T req : requirement.getRequiredElements()) {
            if (!elementRequirements.containsKey(req)) {
                return false;
            }
        }
        return true;
    }

    private void processPending() {
        boolean addedAny = false;
        Iterator<PendingElement<T>> it = pendingElements.iterator();
        while (it.hasNext()) {
            PendingElement<T> pending = it.next();
            if (canAddNow(pending.requirement)) {
                boolean success = addInternal(pending.element, pending.requirement);
                if (success) {
                    if (pending.callback != null) pending.callback.onAdded(pending.element, pending.requirement);
                    it.remove();
                    addedAny = true;
                } else {
                    pending.failReason = "No suitable insertion position found (topological sort failed)";
                }
            }
        }
        if (addedAny) processPending();
    }

    public List<T> getOrderedList() {
        return new ArrayList<>(orderedList);
    }

    public List<PendingElement<T>> getPendingElements() {
        return new ArrayList<>(pendingElements);
    }

    public int getIndex(T element) {
        Integer idx = elementIndexMap.get(element);
        return idx == null ? -1 : idx;
    }

    private boolean sort() {
        Map<T, Set<T>> graph = new HashMap<>();
        Map<T, Integer> inDegree = new HashMap<>();
        for (T elem : elementRequirements.keySet()) {
            graph.put(elem, new HashSet<>());
            inDegree.put(elem, 0);
        }
        for (Map.Entry<T, OrderRequirement<T>> entry : elementRequirements.entrySet()) {
            T elem = entry.getKey();
            OrderRequirement<T> req = entry.getValue();
            for (T after : req.getMustPrecede()) {
                if (graph.containsKey(after)) {
                    graph.get(elem).add(after);
                    inDegree.put(after, inDegree.get(after) + 1);
                }
            }
            for (T before : req.getMustFollow()) {
                if (graph.containsKey(before)) {
                    graph.get(before).add(elem);
                    inDegree.put(elem, inDegree.get(elem) + 1);
                }
            }
        }
        Queue<T> queue = new LinkedList<>();
        for (Map.Entry<T, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }
        List<T> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            T curr = queue.poll();
            result.add(curr);
            for (T next : graph.get(curr)) {
                inDegree.put(next, inDegree.get(next) - 1);
                if (inDegree.get(next) == 0) queue.add(next);
            }
        }
        if (result.size() != elementRequirements.size()) {
            return false;
        }
        orderedList.clear();
        orderedList.addAll(result);
        // update index map
        elementIndexMap.clear();
        for (int i = 0; i < orderedList.size(); i++) {
            elementIndexMap.put(orderedList.get(i), i);
        }
        return true;
    }
}