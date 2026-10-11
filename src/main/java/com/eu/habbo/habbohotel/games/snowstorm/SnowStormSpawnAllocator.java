package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded port of the frozen spawn-allocation fixture algorithm. */
public final class SnowStormSpawnAllocator {
    public static final int MAXIMUM_ATTEMPTS = 64;
    public static final int MAXIMUM_PLAYERS = 12;

    private SnowStormSpawnAllocator() {
    }

    public static Allocation allocate(SnowStormMapDefinition map, int teamCount, int count, int seed) {
        if (teamCount < 1 || teamCount > 4) {
            throw new IllegalArgumentException("SnowStorm team count must be between 1 and 4");
        }
        if (count < 1 || count > MAXIMUM_PLAYERS) {
            throw new IllegalArgumentException("SnowStorm spawn count must be between 1 and 12");
        }

        List<Candidate> candidates = candidatesFor(map, teamCount);
        List<Candidate> best = List.of();
        for (int attempt = 0; attempt < MAXIMUM_ATTEMPTS; attempt++) {
            final int currentAttempt = attempt;
            List<Candidate> ordered = new ArrayList<>(candidates);
            ordered.sort((left, right) -> comparePriorityOrder(left, right, seed, currentAttempt));

            List<Candidate> selected = new ArrayList<>(count);
            while (selected.size() < count) {
                Candidate chosen = null;
                for (Candidate candidate : ordered) {
                    if (selected.contains(candidate) || !compatible(candidate, selected)) {
                        continue;
                    }
                    if (chosen == null || selected.isEmpty()
                            || betterFarthest(candidate, chosen, selected, seed, currentAttempt)) {
                        chosen = candidate;
                    }
                    if (selected.isEmpty()) {
                        break;
                    }
                }
                if (chosen == null) {
                    break;
                }
                selected.add(chosen);
            }
            if (selected.size() > best.size()) {
                best = List.copyOf(selected);
            }
            if (selected.size() == count) {
                return new Allocation(attempt, candidates.size(), selected);
            }
        }

        throw new IllegalStateException("SnowStorm map " + map.mapId() + " allocated only "
                + best.size() + "/" + count + " players in " + MAXIMUM_ATTEMPTS + " attempts");
    }

    public static List<Candidate> candidatesFor(SnowStormMapDefinition map, int teamCount) {
        List<SnowStormSpawnCluster> clusters = map.spawnClusters().stream()
                .filter(cluster -> cluster.supportsTeamCount(teamCount))
                .sorted(Comparator.comparingInt(SnowStormSpawnCluster::id))
                .toList();
        List<Candidate> result = new ArrayList<>();
        Set<SnowStormTile> seen = new HashSet<>();
        for (SnowStormSpawnCluster cluster : clusters) {
            for (int y = 0; y < map.height(); y++) {
                for (int x = 0; x < map.width(); x++) {
                    SnowStormTile tile = new SnowStormTile(x, y);
                    if (!map.isWalkable(tile)) {
                        continue;
                    }
                    long dx = (long) x - cluster.centerX();
                    long dy = (long) y - cluster.centerY();
                    if (floorSqrt(dx * dx + dy * dy) > cluster.radius() || !seen.add(tile)) {
                        continue;
                    }
                    result.add(new Candidate(cluster.id(), x, y, cluster.minimumSeparation()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static int comparePriorityOrder(Candidate left, Candidate right, int seed, int attempt) {
        int comparison = Long.compare(priority(right, seed, attempt), priority(left, seed, attempt));
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.clusterId(), right.clusterId());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.y(), right.y());
        return comparison != 0 ? comparison : Integer.compare(left.x(), right.x());
    }

    private static boolean betterFarthest(
            Candidate candidate,
            Candidate current,
            List<Candidate> selected,
            int seed,
            int attempt) {
        long candidateNearest = nearestDistanceSquared(candidate, selected);
        long currentNearest = nearestDistanceSquared(current, selected);
        if (candidateNearest != currentNearest) {
            return candidateNearest > currentNearest;
        }
        long candidatePriority = priority(candidate, seed, attempt);
        long currentPriority = priority(current, seed, attempt);
        if (candidatePriority != currentPriority) {
            return candidatePriority > currentPriority;
        }
        if (candidate.clusterId() != current.clusterId()) {
            return candidate.clusterId() < current.clusterId();
        }
        if (candidate.y() != current.y()) {
            return candidate.y() < current.y();
        }
        return candidate.x() < current.x();
    }

    private static long nearestDistanceSquared(Candidate candidate, List<Candidate> selected) {
        long nearest = Long.MAX_VALUE;
        for (Candidate other : selected) {
            nearest = Math.min(nearest, candidate.tile().distanceSquared(other.tile()));
        }
        return nearest;
    }

    private static boolean compatible(Candidate candidate, List<Candidate> selected) {
        for (Candidate other : selected) {
            int minimum = Math.max(candidate.minimumSeparation(), other.minimumSeparation());
            if (floorSqrt(candidate.tile().distanceSquared(other.tile())) < minimum) {
                return false;
            }
        }
        return true;
    }

    private static int floorSqrt(long value) {
        return (int) Math.sqrt(value);
    }

    private static long priority(Candidate candidate, int seed, int attempt) {
        int value = seed ^ attempt * 0x9E3779B9;
        value ^= candidate.clusterId() * 0x85EBCA6B;
        value ^= candidate.x() * 0xC2B2AE35;
        value ^= candidate.y() * 0x27D4EB2F;
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return Integer.toUnsignedLong(value);
    }

    public record Candidate(int clusterId, int x, int y, int minimumSeparation) {
        public SnowStormTile tile() {
            return new SnowStormTile(x, y);
        }
    }

    public record Allocation(int attempt, int candidateCount, List<Candidate> positions) {
        public Allocation {
            positions = List.copyOf(positions);
        }
    }
}
