import dev.zwazel.internal.game.tank.Tank;
import dev.zwazel.internal.game.tank.TankConfig;
import dev.zwazel.internal.game.utils.Graph;
import dev.zwazel.internal.game.utils.Node;

import java.util.*;

public class AStar {
    // Add obstacle height threshold (adjust as needed)
    private static final float OBSTACLE_HEIGHT_THRESHOLD = 0.2f;
    private static final double CORNER_BUFFER = 1;

    public static LinkedList<Node> findPath(Graph graph, Node start, Node target) {
        // Create data structures
        PriorityQueue<Node> openSet = new PriorityQueue<>(
                Comparator.comparingDouble(node -> getFCost(node, target))
        );
        Set<Node> closedSet = new HashSet<>();
        Map<Node, Node> cameFrom = new HashMap<>();
        Map<Node, Double> gScore = new HashMap<>();
        Map<Node, Double> fScore = new HashMap<>();

        // Initialize all nodes
        for (Node[] row : graph.getNodes()) {
            for (Node node : row) {
                gScore.put(node, Double.MAX_VALUE);
                fScore.put(node, Double.MAX_VALUE);
            }
        }

        // Initialize start node
        gScore.put(start, 0.0);
        fScore.put(start, heuristic(start, target));
        openSet.add(start);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.equals(target)) {
                return reconstructPath(cameFrom, current);
            }

            closedSet.add(current);

            for (Node neighbor : current.getNeighbours()) {
                // Skip if neighbor is obstacle or in closed set
                if (isObstacle(neighbor, current)) {
                    continue;
                }

                // Check if moving diagonally through a blocked corner
                if (isDiagonalMove(current, neighbor) &&
                        isBlockedCorner(graph, current, neighbor)) {
                    continue;
                }

                double tentativeGScore = gScore.get(current) + getMovementCost(current, neighbor);

                if (tentativeGScore < gScore.get(neighbor)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeGScore);
                    fScore.put(neighbor, tentativeGScore + heuristic(neighbor, target));

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }

        return new LinkedList<>();
    }

    private static boolean isDiagonalMove(Node a, Node b) {
        return a.getX() != b.getX() && a.getY() != b.getY();
    }

    private static boolean isBlockedCorner(Graph graph, Node current, Node neighbor) {
        // For diagonal moves, check the two adjacent cards
        int x1 = current.getX();
        int y1 = current.getY();
        int x2 = neighbor.getX();
        int y2 = neighbor.getY();

        // Get the two "corner" nodes
        Node corner1 = graph.getNode(x1, y2);
        Node corner2 = graph.getNode(x2, y1);

        return isObstacle(corner1, current) || isObstacle(corner2, current);
    }

    private static double getMovementCost(Node a, Node b) {
        // Base distance
        double distance = distanceBetween(a, b);

        // Add penalty for moving near obstacles
        double obstaclePenalty = 0;
        if (isNearObstacle(a, a) || isNearObstacle(b, a)) {
            obstaclePenalty = CORNER_BUFFER * 10;
        }

        // Add height difference penalty
        double heightPenalty = Math.abs(a.getHeight() - b.getHeight()) * 2.0;

        return distance + heightPenalty + obstaclePenalty;
    }

    private static boolean isNearObstacle(Node node, Node current) {
        // Check if any immediate neighbor is an obstacle
        for (Node neighbor : node.getNeighbours()) {
            if (isObstacle(neighbor, current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isObstacle(Node node, Node current) {
        // Consider node an obstacle if its height exceeds threshold
        return node.getHeight() > OBSTACLE_HEIGHT_THRESHOLD + current.getHeight() || node.getHeight() < current.getHeight() - OBSTACLE_HEIGHT_THRESHOLD;
    }

    private static LinkedList<Node> reconstructPath(Map<Node, Node> cameFrom, Node current) {
        LinkedList<Node> path = new LinkedList<>();
        path.addFirst(current);

        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.addFirst(current);
        }

        return path;
    }

    private static double distanceBetween(Node a, Node b) {
        // Add height difference penalty if you want paths to avoid steep areas
        double heightPenalty = Math.abs(a.getHeight() - b.getHeight()) * 2.0;
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        return Math.sqrt(dx * dx + dy * dy) + heightPenalty;
    }

    private static double heuristic(Node node, Node target) {
        // Manhattan distance
        return Math.abs(node.getX() - target.getX()) + Math.abs(node.getY() - target.getY());
    }

    private static double getFCost(Node node, Node target) {
        return heuristic(node, target);
    }
}