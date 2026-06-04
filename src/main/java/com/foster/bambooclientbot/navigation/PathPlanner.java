package com.foster.bambooclientbot.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PathPlanner {
    private static final int MAX_SEARCH_NODES = 4096;
    private static final int MAX_DISTANCE_FROM_START = 96;
    private static final int[][] CARDINAL_DIRECTIONS = {
            {0, -1},
            {1, 0},
            {0, 1},
            {-1, 0}
    };

    private final NavigationGrid navigationGrid;

    public PathPlanner(NavigationGrid navigationGrid) {
        this.navigationGrid = navigationGrid;
    }

    public PathPlanResult plan(World world, BlockPos start, BlockPos target) {
        if (world == null || start == null || target == null) {
            return PathPlanResult.notFound("invalid_coordinates");
        }

        if (!withinSearchBounds(start, target)) {
            return PathPlanResult.notFound("search_limit_reached");
        }

        if (navigationGrid.evaluate(world, start) != Walkability.WALKABLE
                || navigationGrid.evaluate(world, target) != Walkability.WALKABLE) {
            return PathPlanResult.notFound("path_not_found");
        }

        PriorityQueue<SearchNode> open = new PriorityQueue<>(
                Comparator.comparingDouble(SearchNode::estimatedTotalCost)
                        .thenComparingDouble(SearchNode::cost)
                        .thenComparingInt(SearchNode::sequence)
        );
        Map<BlockPos, Double> bestCosts = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        int sequence = 0;
        int searchedNodes = 0;

        bestCosts.put(start, 0.0);
        open.add(new SearchNode(start, 0.0, heuristic(start, target), sequence++));

        while (!open.isEmpty()) {
            if (searchedNodes >= MAX_SEARCH_NODES) {
                return PathPlanResult.notFound("search_limit_reached");
            }

            SearchNode current = open.poll();
            Double bestKnownCost = bestCosts.get(current.position());

            if (bestKnownCost == null || current.cost() > bestKnownCost) {
                continue;
            }

            searchedNodes++;

            if (current.position().equals(target)) {
                List<BlockPos> path = reconstructPath(cameFrom, target);
                return PathPlanResult.found(path.size(), pathLength(path));
            }

            for (BlockPos neighbor : neighbors(world, start, current.position())) {
                double nextCost = current.cost() + movementCost(current.position(), neighbor);
                double previousCost = bestCosts.getOrDefault(neighbor, Double.POSITIVE_INFINITY);

                if (nextCost >= previousCost) {
                    continue;
                }

                bestCosts.put(neighbor, nextCost);
                cameFrom.put(neighbor, current.position());
                open.add(new SearchNode(neighbor, nextCost, nextCost + heuristic(neighbor, target), sequence++));
            }
        }

        return PathPlanResult.notFound("path_not_found");
    }

    private List<BlockPos> neighbors(World world, BlockPos start, BlockPos position) {
        List<BlockPos> neighbors = new ArrayList<>();

        for (int[] direction : CARDINAL_DIRECTIONS) {
            BlockPos base = position.add(direction[0], 0, direction[1]);
            addIfWalkable(world, start, neighbors, base);
            addIfWalkable(world, start, neighbors, base.up());
            addIfWalkable(world, start, neighbors, base.down());
        }

        return neighbors;
    }

    private void addIfWalkable(World world, BlockPos start, List<BlockPos> neighbors, BlockPos candidate) {
        if (!withinSearchBounds(start, candidate)) {
            return;
        }

        if (navigationGrid.evaluate(world, candidate) == Walkability.WALKABLE) {
            neighbors.add(candidate);
        }
    }

    private boolean withinSearchBounds(BlockPos start, BlockPos position) {
        return Math.abs(position.getX() - start.getX()) <= MAX_DISTANCE_FROM_START
                && Math.abs(position.getY() - start.getY()) <= MAX_DISTANCE_FROM_START
                && Math.abs(position.getZ() - start.getZ()) <= MAX_DISTANCE_FROM_START;
    }

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos target) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = target;

        while (current != null) {
            path.add(current);
            current = cameFrom.get(current);
        }

        return path.reversed();
    }

    private double pathLength(List<BlockPos> path) {
        double length = 0.0;

        for (int index = 1; index < path.size(); index++) {
            length += movementCost(path.get(index - 1), path.get(index));
        }

        return length;
    }

    private double movementCost(BlockPos from, BlockPos to) {
        int deltaX = to.getX() - from.getX();
        int deltaY = to.getY() - from.getY();
        int deltaZ = to.getZ() - from.getZ();
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    private double heuristic(BlockPos from, BlockPos to) {
        return movementCost(from, to);
    }

    private record SearchNode(BlockPos position, double cost, double estimatedTotalCost, int sequence) {
    }
}
