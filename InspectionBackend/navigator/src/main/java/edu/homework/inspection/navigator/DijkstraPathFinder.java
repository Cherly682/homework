package edu.homework.inspection.navigator;

import edu.homework.inspection.common.Point;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class DijkstraPathFinder implements PathFinder {
    @Override
    public List<Point> findPath(Point start, Point end, GridMap map) {
        if (start.equals(end)) {
            return Collections.emptyList();
        }
        PriorityQueue<NodeCost> open = new PriorityQueue<>(Comparator.comparingInt(NodeCost::cost));
        Map<Point, Point> cameFrom = new HashMap<>();
        Map<Point, Integer> cost = new HashMap<>();

        cost.put(start, 0);
        open.add(new NodeCost(start, 0));

        while (!open.isEmpty()) {
            NodeCost currentScore = open.poll();
            Point current = currentScore.point;
            if (currentScore.cost > cost.get(current)) {
                continue;
            }
            if (current.equals(end)) {
                return PathUtils.reconstruct(cameFrom, start, end);
            }
            for (Point next : map.neighbors(current)) {
                int newCost = cost.get(current) + map.moveCost(next);
                if (!cost.containsKey(next) || newCost < cost.get(next)) {
                    cost.put(next, newCost);
                    cameFrom.put(next, current);
                    open.add(new NodeCost(next, newCost));
                }
            }
        }
        return Collections.emptyList();
    }

    private static final class NodeCost {
        private final Point point;
        private final int cost;

        private NodeCost(Point point, int cost) {
            this.point = point;
            this.cost = cost;
        }

        private int cost() {
            return cost;
        }
    }
}
