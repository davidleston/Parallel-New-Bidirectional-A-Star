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

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.reverse;

/**
 * Finds the shortest path between two nodes in a graph.
 *
 * Assumes edges are bidirectional and have identical, non-negative distance in both directions of travel.
 *
 * Uses the algorithm described in
 * <a href="https://homepages.dcc.ufmg.br/~chaimo/public/ENIA11.pdf">PNBA*: A Parallel Bidirectional Heuristic Search Algorithm</a>
 *
 * @param <T> graph node type, must correctly implement hashCode and equals
 */
public final class ParallelShortestPathFinder<T> {
    private final ToIntBiFunction<T, T> knownDistanceBetweenAdjacentNodes;
    private final ToIntBiFunction<T, T> estimatedDistance;
    private final Function<T, Iterable<T>> connectedNodes;
    private final Function<Runnable, Future<?>> executor;

    /**
     * Functions must be thread-safe and do not need to support null arguments.
     *
     * Will use one thread from the common ForkJoin pool.
     *
     * @param knownDistanceBetweenAdjacentNodes provides the known distance between two adjacent nodes, returns zero if and only if nodes are equal
     * @param estimatedDistance provides an estimated distance (e.g. "as the crow flies") between two nodes that may be adjacent, returns zero when nodes are equal
     * @param connectedNodes provides all the nodes connected to a given node
     * @throws NullPointerException if any arguments are null
     */
    public ParallelShortestPathFinder(
            ToIntBiFunction<T, T> knownDistanceBetweenAdjacentNodes,
            ToIntBiFunction<T, T> estimatedDistance,
            Function<T, Iterable<T>> connectedNodes) {
        this(knownDistanceBetweenAdjacentNodes, estimatedDistance, connectedNodes,
                a -> ForkJoinPool.commonPool().submit(a));
    }

    /**
     * Functions must be thread-safe and do not need to support null arguments.
     *
     * @param knownDistanceBetweenAdjacentNodes provides the known distance between two adjacent nodes, returns zero if and only if nodes are equal
     * @param estimatedDistance provides an estimated distance between two nodes that may be adjacent, returns zero when nodes are equal
     * @param connectedNodes provides all the nodes connected to a given node
     * @param executor Executes two {@link Runnable}s and waits fo the runnables to complete before returning
     * @throws NullPointerException if any arguments are null
     */
    public ParallelShortestPathFinder(
            ToIntBiFunction<T, T> knownDistanceBetweenAdjacentNodes,
            ToIntBiFunction<T, T> estimatedDistance,
            Function<T, Iterable<T>> connectedNodes,
            Function<Runnable, Future<?>> executor) {
        this.knownDistanceBetweenAdjacentNodes = checkNotNull(knownDistanceBetweenAdjacentNodes);
        this.estimatedDistance = checkNotNull(estimatedDistance);
        this.connectedNodes = checkNotNull(connectedNodes);
        this.executor = checkNotNull(executor);
    }

    /**
     * For correct behavior when the graph is modified between executions of this method
     * the functions passed to the constructor must reflect the modified changes.
     *
     * Behavior is undefined if graph is concurrently modified.
     *
     * @param start first node in path
     * @param end last node in path
     * @return shortest path including the terminal nodes or an empty list if no path was found
     * @throws NullPointerException if either any encountered node is null or {@code connectedNodes} function returns null
     * @throws ArithmeticException if distances overflow {@code int}
     */
    public ImmutableList<T> search(T start, T end) {
        int d = estimatedDistance.applyAsInt(start, end);
        boolean startAndEndEqual = d == 0;
        if (startAndEndEqual) {
            return ImmutableList.of(start);
        }

        var finished = new AtomicBoolean(false);
        var visited = ConcurrentHashMap.<T>newKeySet();
        var bestCompletePath = new AtomicStampedReference<T>(null, Integer.MAX_VALUE);

        // the 1 suffix = modified by forward search; 2 suffix = modified by backward search
        var paths1 = new ConcurrentHashMap<T, Path<T>>();
        var paths2 = new ConcurrentHashMap<T, Path<T>>();

        paths1.put(start, new Path<>(start, d));
        paths2.put(end, new Path<>(end, d));

        var result1 = new ArrayList<T>();
        Future<?> future = executor.apply(
                () -> {
                    new Search<>(start, end, finished, visited, bestCompletePath, paths1, paths2,
                            connectedNodes, knownDistanceBetweenAdjacentNodes, estimatedDistance)
                            .run();
                    var commonNode = bestCompletePath.getReference();
                    if (commonNode != null) {
                        // skip first element as it will also be in the matching paths2.get(commonNode)
                        Path<T> previous = paths1.get(commonNode).previous;
                        if (previous != null) {
                            previous.collectInto(result1);
                        }
                        reverse(result1);
                    }
                });

        var result2 = new ArrayList<T>();
        new Search<>(end, start, finished, visited, bestCompletePath, paths2, paths1,
                connectedNodes, knownDistanceBetweenAdjacentNodes, estimatedDistance)
                .run();
        var commonNode = bestCompletePath.getReference();
        if (commonNode == null) {
            future.cancel(true);
            return ImmutableList.of();
        }

        Path<T> p = paths2.get(commonNode);
        p.collectInto(result2);
        boolean thisThreadFoundFullPath = p.value.equals(start);
        if (thisThreadFoundFullPath) {
            future.cancel(true);
        } else {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        //noinspection UnstableApiUsage
        return ImmutableList.<T>builderWithExpectedSize(paths1.size() + paths2.size())
                .addAll(result1)
                .addAll(result2)
                .build();
    }
}
