/*
Copyright 2019 David Leston

This file is part of Parallel New Bidirectional A*.

Parallel New Bidirectional A* is free software: you can redistribute it and/or modify it under the terms of the
GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License,
or (at your option) any later version.

Parallel New Bidirectional A* is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along with Parallel New Bidirectional A*.
If not, see <https://www.gnu.org/licenses/>.
 */

package com.davidleston.concurrent;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

import static java.lang.Math.addExact;
import static java.util.Comparator.comparingInt;

/**
 * the 1 suffix = modified by this search; 2 suffix = modified by other search
 */
final class Search<T> implements Runnable {
    private final T start;
    private final T end;
    private final AtomicBoolean finished;
    private final Set<T> visited;
    private final AtomicStampedReference<T> bestCompletePath;
    private final PriorityQueue<T> nodesToVisit;
    private final Map<T, Path<T>> paths1;
    private final Map<T, Path<T>> paths2;
    private final Function<T, Iterable<T>> connectedNodes;
    private final ToIntBiFunction<T, T> knownDistanceBetweenAdjacentNodes;
    private final ToIntBiFunction<T, T> estimatedDistance;

    Search(T start,
           T end,
           AtomicBoolean finished,
           Set<T> visited,
           AtomicStampedReference<T> bestCompletePath,
           Map<T, Path<T>> paths1,
           Map<T, Path<T>> paths2,
           Function<T, Iterable<T>> connectedNodes,
           ToIntBiFunction<T, T> knownDistanceBetweenAdjacentNodes,
           ToIntBiFunction<T, T> estimatedDistance) {
        this.start = start;
        this.end = end;
        this.finished = finished;
        this.visited = visited;
        this.bestCompletePath = bestCompletePath;
        this.nodesToVisit = new PriorityQueue<>(comparingInt(x -> paths1.get(x).distanceTravelled));
        this.paths1 = paths1;
        this.paths2 = paths2;
        this.connectedNodes = connectedNodes;
        this.knownDistanceBetweenAdjacentNodes = knownDistanceBetweenAdjacentNodes;
        this.estimatedDistance = estimatedDistance;
    }

    @Override
    public void run() {
        var x = start;
        while (!finished.get()) {
            if (!visited(x)) {
                visit(x);
            }
            if (nodesToVisit.isEmpty()) {
                finished.set(true);
            } else {
                x = nodesToVisit.poll();
            }
        }
    }

    private void visit(T x) {
        var xPath = paths1.get(x);
        if (shouldExpand(xPath)) {
            for (var y : connectedNodes(x)) {
                if (!visited(y)) {
                    var yPathOld = paths1.get(y);
                    var newTotalDistanceToY = addExact(xPath.distanceTravelled, knownDistanceBetweenAdjacentNodes(x, y));
                    if (yPathOld == null || newTotalDistanceToY < yPathOld.distanceTravelled) {
                        Path<T> betterPathToY = xPath.prepend(
                                y,
                                yPathOld == null ? estimatedDistanceToGoal(y) : yPathOld.estimatedDistanceToGoal,
                                newTotalDistanceToY,
                                yPathOld == null ? estimateOfActualTravel(y) : yPathOld.estimateOfActualTravel);
                        paths1.put(y, betterPathToY);
                        nodesToVisit.remove(y);
                        nodesToVisit.add(y);
                        updateBestCompletePath(betterPathToY);
                    }
                }
            }
        }
        visited.add(x);
    }

    private boolean shouldExpand(Path<T> xPath) {
        return bestCompletePath.getReference() == null
                || addExact(xPath.distanceTravelled, xPath.estimatedDistanceToGoal) < bestCompleteDistance();
    }

    private void updateBestCompletePath(Path<T> path) {
        var success = false;
        T newCommonNode = path.value;
        while (!success) {
            var oldCommonNode = bestCompletePath.getReference();
            var oldDistance = bestCompleteDistance();
            var connectingPath = paths2.get(newCommonNode);
            if (connectingPath == null) {
                return;
            }
            var newDistance = addExact(path.distanceTravelled, connectingPath.distanceTravelled);
            if (newDistance >= oldDistance) {
                return;
            }
            success = bestCompletePath.compareAndSet(oldCommonNode, newCommonNode, oldDistance, newDistance);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean visited(T node) {
        return visited.contains(node);
    }

    private Iterable<T> connectedNodes(T x) {
        return connectedNodes.apply(x);
    }

    private int knownDistanceBetweenAdjacentNodes(T x, T y) {
        return knownDistanceBetweenAdjacentNodes.applyAsInt(x, y);
    }

    private int estimateOfActualTravel(T y) {
        return estimatedDistance.applyAsInt(start, y);
    }

    private int estimatedDistanceToGoal(T y) {
        return estimatedDistance.applyAsInt(y, end);
    }

    private int bestCompleteDistance() {
        return bestCompletePath.getStamp();
    }
}
