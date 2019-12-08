Parallel New Bidirectional A* (PNBA*)
=====================================

[![Build Status](https://travis-ci.org/davidleston/Parallel-New-Bidirectional-A-Star.svg?branch=master)](https://travis-ci.org/davidleston/Parallel-New-Bidirectional-A-Star)
[![Coverage Status](https://coveralls.io/repos/github/davidleston/Parallel-New-Bidirectional-A-Star/badge.svg)](https://coveralls.io/github/davidleston/Parallel-New-Bidirectional-A-Star)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

Uses the algorithm detailed in 
[A Parallel Bidirectional Heuristic Search Algorithm (PNBA*)](https://homepages.dcc.ufmg.br/~chaimo/public/ENIA11.pdf)
by Luis Henrique Oliveira Rios and Luiz Chaimowicz
from Departamento de Ciência da Computaçã̃o
 – Universidade Federal de Minas Gerais (UFMG)
 – Belo Horizonte, MG – Brasil.
 
## Example Usage

This example is a solution to Question 22 in Chapter 17 in the book
[Cracking the Coding Interview](http://www.crackingthecodinginterview.com):

```java
import one.util.streamex.IntStreamEx;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static java.util.function.Function.identity; 

public class Q_17_22 {
    ImmutableList<String> transform(String from, String to, String[] dictionary) {
        var wildcards = IntStreamEx.range(from.length())
             .parallel()
             .mapToObj(i -> Arrays.stream(dictionary)
                     .collect(toImmutableListMultimap(
                             word -> removeCharacter(word, i),
                             identity())))
             .collect(toImmutableList());

        Function<String, Iterable<String>> connectedNodes = s -> IntStreamEx.range(from.length())
                 .flatMapToObj(i -> wildcards
                         .get(i)
                         .get(removeCharacter(s, i))
                         .stream());
        return new ParallelShortestPathFinder<>(
                 (a, b) -> 1,
                 this::distance,
                 connectedNodes)
                 .search(from, to);
    }

    private int distance(String a, String b) {
        int count = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) == b.charAt(i)) {
                count++;
            }
        }
        return count;
    }

    private String removeCharacter(String s, int i) {
        return s.substring(0, i) + s.substring(i + 1);
    }
}
```
