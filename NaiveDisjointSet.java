import java.util.*;

public class NaiveDisjointSet<T> {
    HashMap<T, T> parentMap = new HashMap<>();
    HashMap<T, Integer> sizeMap = new HashMap<>();

    void add(T element) {
        parentMap.put(element, element);
        sizeMap.put(element, 1);
    }

    // Starting from the input node, goes up to the root,
    // update the parent of each node as the root while coming back down
    // Time-complexity: O(ackermann(n))
    T find(T a) {
        T parent = parentMap.get(a); // parent <- p[a]
        if (parent.equals(a)) { // when a is the root itself
            return a;
        } else {
            parent = find(parent); // Bring down the root from the top to the input node
            parentMap.put(a, parent); // While coming back to a, let each node point to the root directly
            return parent;
        }
    }

    // Union by size
    // Time complexity: Identical to path compression
    void union(T a, T b) {
        T rootA = find(a);
        T rootB = find(b);

        if (rootA.equals(rootB) == false) { // Different roots == Two different trees == Union required
            int sizeA = sizeMap.get(rootA);
            int sizeB = sizeMap.get(rootB);
            if (sizeA < sizeB) { // Tree B is bigger == Attach A under B
                parentMap.put(rootA, rootB);
                sizeMap.put(rootB, sizeA + sizeB);
                sizeMap.remove(rootA);
            } else { // Tree A is bigger or equal size == Attach B under A
                parentMap.put(rootB, rootA);
                sizeMap.put(rootA, sizeB + sizeA);
                sizeMap.remove(rootB);
            }
        }
    }
}
