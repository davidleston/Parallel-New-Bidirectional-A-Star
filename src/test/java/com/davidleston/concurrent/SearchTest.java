package com.davidleston.concurrent;

import com.google.common.collect.ImmutableMultimap;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicStampedReference;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTest {

    @Test
    void exploringDeadEndPathWhenCompletePathHasBeenFound() {
        new Search<>(
                'a',
                'c',
                new AtomicBoolean(false),
                new HashSet<>() {{
                    add('b');
                    add('c');
                }},
                new AtomicStampedReference<>('b', 2),
                new HashMap<>() {{
                    put('a', new Path<>('a', 1));
                }},
                new HashMap<>() {{
                    Path<Character> cPath = new Path<>('c', 1);
                    put('c', cPath);
                    put('b', cPath.prepend('b', 1, 1, 1));
                }},
                ImmutableMultimap.<Character, Character>builder()
                        .put('a', 'b')
                        .put('b', 'a')
                        .put('b', 'c')
                        .put('c', 'b')
                        .put('a', 'z')
                        .put('z', 'a')
                        .build()::get,
                (a, b) -> 1,
                (a, b) -> 1
        ).run();
    }

    @Test
    void simulateOtherThreadAlreadyFoundPathCosting3AndWhileThisThreadFoundPathCosting2OtherThreadAlreadyFoundPathCosting2() {
        var bestPath = new AtomicStampedReference<>('a', 3);
        new Search<>(
                'a',
                'c',
                new AtomicBoolean(false),
                new HashSet<>() {{
                    add('b');
                    add('c');
                }},
                bestPath,
                new HashMap<>() {
                    {
                        put('a', new Path<>('a', 1));
                    }

                    @Override
                    public Path<Character> put(Character key, Path<Character> value) {
                        if (key == 'z') {
                            // simulate other thread finding a better path just as this thread found a different equally good better path
                            bestPath.set('a', 2);
                        }
                        return super.put(key, value);
                    }
                },
                new HashMap<>() {{
                    var cPath = new Path<>('c', 1);
                    var bPath = cPath.prepend('b', 1, 1, 1);
                    var aPath = bPath.prepend('a', 1, 1, 1);
                    var zPath = cPath.prepend('z', 1, 1, 1);
                    put('c', cPath);
                    put('b', bPath);
                    put('a', aPath);
                    put('z', zPath);
                }},
                ImmutableMultimap.<Character, Character>builder()
                        .put('a', 'b')
                        .put('b', 'a')
                        .put('b', 'c')
                        .put('c', 'b')
                        .put('a', 'z')
                        .put('z', 'a')
                        .put('c', 'z')
                        .put('z', 'c')
                        .build()::get,
                (a, b) -> 1,
                (a, b) -> 1
        ).run();
        assertThat(bestPath.getReference())
                .isEqualTo('a');
        assertThat(bestPath.getStamp())
                .isEqualTo(2);
    }
}