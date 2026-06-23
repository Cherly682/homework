package edu.homework.inspection.navigator;

import edu.homework.inspection.common.Point;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class TargetSelector {

    /**
     * BFS 搜索距离起点最近的未探索非障碍格子。
     * 移除随机选择和距离过滤，改为直接返回最近目标，避免多车协作时的随机游走问题。
     *
     * @param start 搜索起点
     * @param map   地图信息（障碍物、已探索状态）
     * @return 最近的未探索格子；若全部已探索则返回起点
     */
    public Point chooseTarget(Point start, GridMap map) {
        Queue<Point> queue = new ArrayDeque<>();
        Set<Point> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Point current = queue.poll();
            if (!map.isBlocked(current) && !map.isExplored(current)) {
                return current;
            }
            for (Point next : map.neighbors(current)) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return start;
    }
}
