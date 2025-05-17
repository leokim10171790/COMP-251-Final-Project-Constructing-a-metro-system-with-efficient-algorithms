import java.util.*;
import java.lang.Math.*;

public class McMetro {
    protected Track[] tracks;
    protected HashMap<BuildingID, Building> buildingTable = new HashMap<>();
    protected HashMap<BuildingID, Integer> buildingIndex = new HashMap<>();
    protected HashMap<Integer, ArrayList<Edge>> adjacencyList = new HashMap<>(); // Graph
    protected int numBuildings;
    protected Trie trie;

    McMetro(Track[] tracks, Building[] buildings) {
        this.tracks = tracks;
        this.numBuildings = buildings.length;
        this.trie = new Trie();

        // Populate buildingTable, buildingIndex, and add empty lists to adjacencyList
        int index = 0;
        if (buildings != null) {
            for (Building building : buildings) {
                buildingTable.put(building.id(), building);
                buildingIndex.put(building.id(), index);
                adjacencyList.put(index, new ArrayList<>());
                index++;
            }
        }

        // Form a graph accordingly by computing the effective capacity of each track
        if (tracks != null) {
            for (Track track : tracks) {
                // Get the start and end buildings of each track
                Building startBuilding = buildingTable.get(track.startBuildingId());
                Building endBuilding = buildingTable.get(track.endBuildingId());

                // Get the occupants of the start/end buildings
                int occupantsStart = startBuilding.occupants();
                int occupantsEnd = endBuilding.occupants();

                // Get the effective capacity allowed for each individual track
                int effectiveCapacity = Math.min(track.capacity(), Math.min(occupantsStart, occupantsEnd));

                // Get the indices of startBuilding & endBuilding
                int u = buildingIndex.get(track.startBuildingId());
                int v = buildingIndex.get(track.endBuildingId());

                // Special case: there already exists a track from u to v (multiple tracks): testMaxPassenger16
                boolean edgeExists = false;
                for (Edge edge : adjacencyList.get(u)) {
                    if (edge.to == v) {
                        edge.capacity += effectiveCapacity;
                        edgeExists = true;
                        break;
                    }
                }

                // Ordinary case: Single track from u to v
                if (!edgeExists) {
                    adjacencyList.get(u).add(new Edge(v, effectiveCapacity));
                }

            }
        }
    }

    // Edge class definition for the adjacency list
    protected static class Edge {
        int to;
        int capacity;

        Edge(int to, int capacity) {
            this.to = to;
            this.capacity = capacity;
        }
    }

    /*
    Maximum number of passengers that can be transported from start to end
    Basically the same as an ordinary max flow problem but needs to consider
    the effective capacity i.e. the numerator of goodness equation
    Time Complexity: O(V * E^2) since using Edmonds-Karp (BFS)
    */
    int maxPassengers(BuildingID start, BuildingID end) {

        // Edge case: 0 occupants -> testMaxPassenger10 & 11
        if (buildingTable.get(start).occupants() == 0 || buildingTable.get(end).occupants() == 0) {
            return 0;
        }

        int startIndex = buildingIndex.get(start);
        int endIndex = buildingIndex.get(end);

        // Edge case: Self loop -> for testMaxPassenger9
        if (startIndex == endIndex) {
            int selfLoopCapacity = 0;
            for (Edge edge : adjacencyList.get(startIndex)) {
                if (edge.to == endIndex) {
                    selfLoopCapacity += edge.capacity;
                    break;
                }
            }

            for (Edge edge : adjacencyList.get(startIndex)) {
                if (edge.to == startIndex) {
                    edge.capacity = 0;
                    break;
                }
            }

            return selfLoopCapacity;
        }

        return getMaxFlow(startIndex, endIndex);
    }

    // Time complexity: O(V*E^2) for dense graph
    private int getMaxFlow(int startIndex, int endIndex) {
        int maxFlow = 0;

        // Initialize residual graph
        HashMap<Integer, List<Edge>> residualGraph = new HashMap<>();
        for (int u : adjacencyList.keySet()) {
            // Copy the vertices of the original graph
            residualGraph.put(u, new ArrayList<>());
            for (Edge edge : adjacencyList.get(u)) {
                // Add forward edge(u, edge.to "= v") to the residual graph
                residualGraph.get(u).add(new Edge(edge.to, edge.capacity));
            }
        }

        int[] parent = new int[numBuildings];
        // While there exists a path from start to end in the graph (i.e., a path to augment found)
        // Loop terminates when there is no more path to augment (i.e., once the maximum flow has been reached.)
        while(bfs(residualGraph, startIndex, endIndex, parent)) { // O(E*V) iterations of BFS
            int bottleNeck = Integer.MAX_VALUE;
            for (int v = endIndex; v != startIndex; v = parent[v]) {
                int u = parent[v];
                // Determine the bottleneck of the path
                for (Edge edge : residualGraph.get(u)) {
                    if (edge.to == v) {
                        bottleNeck = Math.min(bottleNeck, edge.capacity);
                        break;
                    }
                }
            }

            // Update residual capacities in the path using the bottleneck values
            for (int v = endIndex; v != startIndex; v = parent[v]) {
                int u = parent[v];
                for (Edge edge : residualGraph.get(u)) {
                    if (edge.to == v) {
                        edge.capacity -= bottleNeck; // Decrease forward edge
                    }
                }
                for (Edge edge : residualGraph.get(v)) {
                    if (edge.to == u) {
                        edge.capacity += bottleNeck; // Increase back edge
                    }
                }
            }
            maxFlow += bottleNeck; // Add the flow to the overall flow
        }

        return maxFlow;
    }

    /*
    Find the shortest paths to augment.
    Update parent[] with endBuilding's ancestors along the path.
    Time complexity: O(E) where E is # of edges (or effective tracks)
    E is likely to be less than actual # of tracks if there are multiple tracks between two buildings
    BFS implementation inspired from https://www.w3schools.com/dsa/dsa_algo_graphs_edmondskarp.php
     */
    private boolean bfs(HashMap<Integer, List<Edge>> residualGraph, int startIndex, int endIndex, int[] parent) {
        boolean[] visited = new boolean[numBuildings];
        Queue<Integer> queue = new LinkedList<>();

        queue.add(startIndex);
        visited[startIndex] = true;
        parent[startIndex] = -1;

        while (!queue.isEmpty()) {
            int u = queue.poll(); // dequeue

            // Get the edges connecting to neighbouring buildings
            for (Edge edge : residualGraph.get(u)) {
                int v = edge.to;
                if (!visited[v] && edge.capacity > 0) {
                    queue.add(v);
                    parent[v] = u;
                    visited[v] = true;
                    // The path properly reaches the end building: valid path
                    if (v == endIndex) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Returns a list of trackIDs that connect to every building maximizing total network capacity taking cost into account
    TrackID[] bestMetroSystem() {
        /*
        Theme: Use MST (Kruskal) with respect to capacity to cost ratio
        Sort tracks by "goodness" (i.e. floor(min capacity/cost))
        Time complexity: O(E log E)
         */
        Arrays.sort(tracks, (a,b) -> { //
            /*
                Sorting idea inspired from
               -> https://stackoverflow.com/questions/21970719/java-arrays-sort-with-lambda-expression
               -> https://www.geeksforgeeks.org/java-integer-compare-method/
            */
            // Goodness of track A
            int capacityA = Math.min(a.capacity(),
                    Math.min(buildingTable.get(a.startBuildingId()).occupants(), buildingTable.get(a.endBuildingId()).occupants()));
            int goodnessA = capacityA / a.cost();

            // Goodness of track B
            int capacityB = Math.min(b.capacity(),
                    Math.min(buildingTable.get(b.startBuildingId()).occupants(), buildingTable.get(b.endBuildingId()).occupants()));
            int goodnessB = capacityB / b.cost();

            // Sort by goodness (decreasing order)
            if (goodnessA != goodnessB) {
                return Integer.compare(goodnessB, goodnessA); // then track a comes first
            } else if (a.cost() != b.cost()) {
                // Goodness ties -> sort by cost (increasing)
                return Integer.compare(a.cost(), b.cost()); //
            }
            // If even the costs are ties, sort by capacity (decreasing)
            return Integer.compare(b.capacity(), a.capacity());

            // Added these extra sorting conditions due to my obsession
        });

        // Initialize a disjoint set for Kruskal MST
        NaiveDisjointSet<BuildingID> ds = new NaiveDisjointSet<>();
        for (BuildingID id : buildingTable.keySet()) {
            ds.add(id);
        }

        // Kruskal's MST
        List<TrackID> mst = new ArrayList<>();
        for (Track track : tracks) {
            BuildingID start = track.startBuildingId();
            BuildingID end = track.endBuildingId();

            // Check that the buildings are in separate disjoint sets,
            // and won't create a cycle
            if (!ds.find(start).equals(ds.find(end))) {
                mst.add(track.id());
                ds.union(start, end);
            }

            // Stop when |E| = |V| - 1
            if (mst.size() == numBuildings - 1) {
                break;
            }
        }

        return mst.toArray(new TrackID[0]); // automatically change to the size of mst internally
    }

    private static class Trie {
        private TrieNode root;

        Trie() {
            this.root = new TrieNode();
        }

        private static class TrieNode {
            // TreeMap stores children in alphabetical order, so no sorting needed later
            TreeMap<Character, TrieNode> children = new TreeMap<>();
            boolean nameEnd = false;
            String fixedName = null;
        }

        // Source: Final project tutorial
        // Helper class for BFS traversal in searchForPassengers
        private static class Entry {
            TrieNode node;
            String pathUntil;

            public Entry(TrieNode node, String pathUntil) {
                this.node = node;
                this.pathUntil = pathUntil; // The word formed so far
            }
        }

        // Inspired from https://www.youtube.com/watch?v=oobqoCJlHA0
        // and https://www.geeksforgeeks.org/introduction-to-trie-data-structure-and-algorithm-tutorials/
        // Time complexity: O(L) where L is the length of the name
        void addPassenger(String name) {
            // Convert to lowercase for consistent Trie traversal
            String lowerCaseName = name.toLowerCase();
            TrieNode current = root;

            // Trie traversal
            for (char c : lowerCaseName.toCharArray()) {
                current.children.putIfAbsent(c, new TrieNode());
                current = current.children.get(c);
            }
            // Mark as the end of the name -> Will be used when searching for the name
            current.nameEnd = true;
            // Capitalize the name, and store it at the position of the last character of the name.
            current.fixedName = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
        }

        // Time-complexity: O(N * L) in the very worst case where N is # of names and L is average length of names
        ArrayList<String> searchForPassengers(String prefix) {
            ArrayList<String> result = new ArrayList<>();

            // Empty prefix (invalid)
            if (prefix == null || prefix.isEmpty()) return result;

            String lowerCasePrefix = prefix.toLowerCase();
            TrieNode current = root;

            for (char c : lowerCasePrefix.toCharArray()) {
                // No names contain the prefix
                if (!current.children.containsKey(c)) return result;

                // Move to the next TrieNode
                current = current.children.get(c);
            } // Reached the end of the prefix

            // BFS to collect all the matching names from this point
            Queue<Entry> queue = new LinkedList<>();
            queue.add(new Entry(current, lowerCasePrefix));

            // Very similar to the BFS in maxPassengers, but traverse from the end of prefix to ends of matching names
            // Only traverse a selected "branch" (or stream)
            while (!queue.isEmpty()) {
                Entry entry = queue.poll();
                TrieNode node = entry.node;
                String currentWord = entry.pathUntil;

                // Reached the end of a name -> Add the properly capitalized name to the result
                if (node.nameEnd) {
                    result.add(node.fixedName);
                }

                // Add the child nodes to the queue that are to be traversed later
                // If the current node is the end of a name, the below line is an empty set, so no iteration
                for (Map.Entry<Character, TrieNode> child : node.children.entrySet()) {
                    // String concatenation, and move to the next child.
                    queue.add(new Entry(child.getValue(), currentWord + child.getKey()));
                }
            }

            return result;
        }
    }

    // Adds a passenger to the system
    void addPassenger(String name) {
        trie.addPassenger(name);
    }

    // Do not change this
    void addPassengers(String[] names) {
        for (String s : names) {
            addPassenger(s);
        }
    }

    // Returns all passengers in the system whose names start with firstLetters
    ArrayList<String> searchForPassengers(String prefix) {
        return trie.searchForPassengers(prefix);
    }


    // Typical interval scheduling problem
    // Time complexity: O(n log n) since schedules are not sorted
    static int hireTicketCheckers(int[][] schedules) {
        // Sort by finishing time (Source: Final project tutorial)
        Arrays.sort(schedules, Comparator.comparingInt((int[] e) -> e[1]));
        int count = 0;
        int lastFin = Integer.MIN_VALUE;

        for (int[] schedule : schedules) {
            int start = schedule[0];
            int fin = schedule[1];

            if (start >= lastFin) {
                count++;
                lastFin = fin;
            }
        }
        return count;
    }
}
