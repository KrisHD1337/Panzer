import dev.zwazel.internal.game.map.MapDefinition;
import dev.zwazel.internal.game.transform.Vec3;
import dev.zwazel.internal.game.utils.Graph;
import dev.zwazel.internal.game.utils.Node;
import dev.zwazel.internal.game.utils.NodeComparator;

import java.util.*;

public class AStar {
    public LinkedList<Node> findPath(MapDefinition mapDefinition, Graph graph, Vec3 startWorldPos, Vec3 goalWorldPos) {
        Vec3 startTile = mapDefinition.getClosestTileFromWorld(startWorldPos);
        Vec3 goalTile = mapDefinition.getClosestTileFromWorld(goalWorldPos);

        int startX = (int) startTile.getX();
        int startY = (int) startTile.getZ();
        int goalX = (int) goalTile.getX();
        int goalY = (int) goalTile.getZ();

        System.out.println("Start Tile: " + startX + "," + startY);
        System.out.println("Goal Tile: " + goalX + "," + goalY);
        System.out.println("Map Size: Width = " + mapDefinition.width() + ", Depth = " + mapDefinition.depth());

        PriorityQueue<Node> openSet = new PriorityQueue<>(new NodeComparator());
        Set<Node> closedSet = new HashSet<>();
        LinkedList<Node> path = new LinkedList<>();

        Node startNode = graph.getNode(startX, startY);
        Node goalNode = graph.getNode(goalX, goalY);
        startNode.setCost(0);
        openSet.add(startNode);

         while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            path.add(current);

            if (current.equals(goalNode)) {
                return path;
            }

            closedSet.add(current);

            for (Node neighbor : current.getNeighbours()) {
                if (closedSet.contains(neighbor)) continue;

                double moveCost = current.getCost() + getMovementCost(current, neighbor);

                if (moveCost < neighbor.getCost()) {
                    neighbor.setCost(moveCost);
                    neighbor.setParent(current);

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }
        return new LinkedList<>();
    }

    private double getMovementCost(Node from, Node to) {
        double baseCost = 1.0;
        double heightDifference = Math.abs(from.getHeight() - to.getHeight());

        return baseCost + heightDifference;
    }
}