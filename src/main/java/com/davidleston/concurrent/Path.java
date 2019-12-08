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

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;

final class Path<T> {
    final T value;
    final Path<T> previous;
    final int distanceTravelled;
    final int estimateOfActualTravel;
    final int estimatedDistanceToGoal;

    Path(T value, int estimatedDistanceToGoal) {
        this(value, estimatedDistanceToGoal, null, 0, 0);
    }

    private Path(T value, int estimatedDistanceToGoal, Path<T> previous, int distanceTravelled, int estimateOfActualTravel) {
        this.value = value;
        this.previous = previous;
        this.distanceTravelled = distanceTravelled;
        this.estimateOfActualTravel = estimateOfActualTravel;
        this.estimatedDistanceToGoal = estimatedDistanceToGoal;
    }

    Path<T> prepend(T value, int estimatedDistanceToGoal, int newTotalDistanceTravelled, int estimateOfActualTravel) {
        return new Path<>(value, estimatedDistanceToGoal, this, newTotalDistanceTravelled, estimateOfActualTravel);
    }

    void collectInto(Collection<T> accumulator) {
        var current = this;
        while (current != null) {
            accumulator.add(current.value);
            current = current.previous;
        }
    }

    @VisibleForTesting
    @Override
    public String toString() {
        var builder = new StringBuilder(value.toString());
        var previous = this.previous;
        while (previous != null) {
            builder.append(" ← ");
            builder.append(previous.value);
            previous = previous.previous;
        }
        return builder.toString();
    }
}
