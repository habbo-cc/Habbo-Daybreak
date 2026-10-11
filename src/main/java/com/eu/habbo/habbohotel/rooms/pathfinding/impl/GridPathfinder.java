package com.eu.habbo.habbohotel.rooms.pathfinding.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/** Shared A* pathfinding rules for room-like grids which are not backed by a loaded {@code Room}. */
public final class GridPathfinder {
    private static final int[][] DIRECTIONS = {{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};

    public interface Grid {
        int width();
        int height();
        boolean isWalkable(int x, int y);
        boolean isOccupied(int x, int y);
        double floorHeight(int x, int y);
    }

    public record Step(int x, int y) {}

    private record Node(int x, int y, int g, int f, long order) implements Comparable<Node> {
        @Override public int compareTo(Node other) {
            int result = Integer.compare(this.f, other.f);
            return result == 0 ? Long.compare(this.order, other.order) : result;
        }
    }

    private GridPathfinder() {
        throw new IllegalStateException("Utility class");
    }

    public static List<Step> findPath(Grid grid, int startX, int startY, int targetX, int targetY,
                                      double maximumStepHeight, boolean allowFalling,
                                      boolean retroStyleDiagonals) {
        return findPath(grid, startX, startY, targetX, targetY, maximumStepHeight, allowFalling,
                retroStyleDiagonals, false);
    }

    /** Finds the same deterministic route as {@link #findPath}, without materializing its tail. */
    public static Step findNextStep(Grid grid, int startX, int startY, int targetX, int targetY,
                                    double maximumStepHeight, boolean allowFalling,
                                    boolean retroStyleDiagonals) {
        List<Step> result = findPath(grid, startX, startY, targetX, targetY, maximumStepHeight,
                allowFalling, retroStyleDiagonals, true);
        return result.isEmpty() ? null : result.getFirst();
    }

    private static List<Step> findPath(
            Grid grid,
            int startX,
            int startY,
            int targetX,
            int targetY,
            double maximumStepHeight,
            boolean allowFalling,
            boolean retroStyleDiagonals,
            boolean firstStepOnly) {
        if (!inside(grid, startX, startY) || !inside(grid, targetX, targetY)
                || (startX == targetX && startY == targetY)
                || !grid.isWalkable(targetX, targetY) || grid.isOccupied(targetX, targetY)) {
            return List.of();
        }

        int[][] costs = new int[grid.width()][grid.height()];
        int[][] previousX = new int[grid.width()][grid.height()];
        int[][] previousY = new int[grid.width()][grid.height()];
        boolean[][] closed = new boolean[grid.width()][grid.height()];
        for (int x = 0; x < grid.width(); x++) {
            Arrays.fill(costs[x], Integer.MAX_VALUE);
            Arrays.fill(previousX[x], -1);
            Arrays.fill(previousY[x], -1);
        }

        PriorityQueue<Node> open = new PriorityQueue<>();
        long order = 0;
        costs[startX][startY] = 0;
        open.add(new Node(startX, startY, 0, heuristic(startX, startY, targetX, targetY), order++));

        while (!open.isEmpty()) {
            Node current = open.poll();
            if (closed[current.x][current.y] || current.g != costs[current.x][current.y]) continue;
            if (current.x == targetX && current.y == targetY) {
                return firstStepOnly
                        ? List.of(traceFirst(startX, startY, targetX, targetY, previousX, previousY))
                        : trace(startX, startY, targetX, targetY, previousX, previousY);
            }
            closed[current.x][current.y] = true;

            for (int[] direction : DIRECTIONS) {
                int x = current.x + direction[0];
                int y = current.y + direction[1];
                if (!inside(grid, x, y) || closed[x][y] || !grid.isWalkable(x, y)
                        || grid.isOccupied(x, y) || invalidHeight(grid, current.x, current.y, x, y,
                        maximumStepHeight, allowFalling)) continue;

                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                if (diagonal && !retroStyleDiagonals
                        && !grid.isWalkable(x, current.y) && !grid.isWalkable(current.x, y)) continue;

                int nextCost = current.g + (diagonal
                        ? PathfinderConstants.DIAGONAL_MOVEMENT_COST
                        : PathfinderConstants.BASIC_MOVEMENT_COST);
                if (nextCost >= costs[x][y]) continue;
                costs[x][y] = nextCost;
                previousX[x][y] = current.x;
                previousY[x][y] = current.y;
                open.add(new Node(x, y, nextCost, nextCost + heuristic(x, y, targetX, targetY), order++));
            }
        }
        return List.of();
    }

    private static boolean invalidHeight(Grid grid, int fromX, int fromY, int toX, int toY,
                                         double maximumStepHeight, boolean allowFalling) {
        double difference = grid.floorHeight(toX, toY) - grid.floorHeight(fromX, fromY);
        return difference > maximumStepHeight || (!allowFalling && difference < -maximumStepHeight);
    }

    private static int heuristic(int x, int y, int targetX, int targetY) {
        int dx = Math.abs(targetX - x);
        int dy = Math.abs(targetY - y);
        int diagonal = Math.min(dx, dy);
        return diagonal * PathfinderConstants.DIAGONAL_MOVEMENT_COST
                + (Math.max(dx, dy) - diagonal) * PathfinderConstants.BASIC_MOVEMENT_COST;
    }

    private static List<Step> trace(int startX, int startY, int targetX, int targetY,
                                    int[][] previousX, int[][] previousY) {
        ArrayDeque<Step> path = new ArrayDeque<>();
        int x = targetX;
        int y = targetY;
        while (x != startX || y != startY) {
            path.addFirst(new Step(x, y));
            int oldX = x;
            x = previousX[x][y];
            y = previousY[oldX][y];
            if (x < 0 || y < 0) return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(path));
    }

    private static Step traceFirst(int startX, int startY, int targetX, int targetY,
                                   int[][] previousX, int[][] previousY) {
        int x = targetX;
        int y = targetY;
        while (previousX[x][y] != startX || previousY[x][y] != startY) {
            int oldX = x;
            x = previousX[x][y];
            y = previousY[oldX][y];
            if (x < 0 || y < 0) throw new IllegalStateException("Broken path predecessor chain");
        }
        return new Step(x, y);
    }

    private static boolean inside(Grid grid, int x, int y) {
        return x >= 0 && y >= 0 && x < grid.width() && y < grid.height();
    }
}
