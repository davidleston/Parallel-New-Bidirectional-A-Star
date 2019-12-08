package com.davidleston.concurrent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParallelShortestPathFinderTest {
    private final ToIntBiFunction<Character, Character> everythingOneAway = (a, b) -> a == b ? 0 : 1;

    @Test
    void startEqualsEnd() {
        var onlyNode = 'a';
        var shortestPath = new ParallelShortestPathFinder<>(
                everythingOneAway,
                everythingOneAway,
                a -> emptySet())
                .search(onlyNode, onlyNode);
        assertThat(shortestPath)
                .containsExactly(onlyNode);
    }

    @Test
    void noPath() {
        var shortestPath = new ParallelShortestPathFinder<>(
                everythingOneAway,
                everythingOneAway,
                a -> emptySet())
                .search('a', 'b');
        assertThat(shortestPath)
                .isEmpty();
    }

    @Test
    void directPath() {
        var shortestPath = new ParallelShortestPathFinder<>(
                everythingOneAway,
                everythingOneAway,
                forPath("ab"))
                .search('a', 'b');
        assertThat(shortestPath)
                .containsExactly('a', 'b');
    }

    @Test
    void deadEndBranchesEnsuringThreadsTakeTurns() {
        var edges = ImmutableMultimap.<Character, Character>builder()
                .put('a', 'b')
                .put('b', 'a')
                .put('b', 'c')
                .put('c', 'b')
                .put('a', 'z')
                .put('z', 'a')
                .put('x', 'c')
                .put('c', 'x')
                .build();
        var shortestPath = new ParallelShortestPathFinder<>(
                everythingOneAway,
                (a, b) -> (a == 'b' || b == 'b') ? 2 : 1,
                character -> {
                    try {
                        Thread.sleep(0, 1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return edges.get(character);

                })
                .search('a', 'c');
        assertThat(shortestPath)
                .containsExactly('a', 'b', 'c');
    }

    /**
     * Test created for code coverage.
     */
    @Test
    void runnableThrowsException() {
        assertThrows(RuntimeException.class, () -> shortestPath(a -> {
            a.run();
            return new AbstractFuture<>() {
                @Override
                public Object get() throws InterruptedException {
                    throw new InterruptedException();
                }
            };
        }));
    }

    @Test
    void runnableRunToCompletion() {
        ImmutableList<Character> shortestPath = shortestPath(a -> {
            a.run();
            return Futures.immediateFuture(null);
        });
        assertThat(shortestPath)
                .containsExactly('a', 'b', 'c');
    }

    @Test
    void runnableNotRun() {
        ImmutableList<Character> shortestPath = shortestPath(a -> Futures.immediateCancelledFuture());
        assertThat(shortestPath)
                .containsExactly('a', 'b', 'c');
    }

    private ImmutableList<Character> shortestPath(Function<Runnable, Future<?>> executor) {
        return new ParallelShortestPathFinder<>(
                everythingOneAway,
                everythingOneAway,
                forPath("abc"),
                executor)
                .search('a', 'c');
    }

    private Function<Character, Iterable<Character>> forPath(String nodes) {
        var builder = ImmutableMultimap.<Character, Character>builder();
        for (int i = 0; i < nodes.length() - 1; i++) {
            char from = nodes.charAt(i);
            char to = nodes.charAt(i + 1);
            builder.put(from, to);
            builder.put(to, from);
        }
        return builder.build()::get;
    }

    @Test
    void forCoverage() {
        assertThat(
                new Path<>('a', 0)
                        .prepend('b', 0, 0, 0)
                        .toString())
                .isEqualTo("b ← a");
    }
}