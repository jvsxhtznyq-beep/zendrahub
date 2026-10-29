package com.zendrahub.evolve.core.model;

import java.util.List;
import java.util.Map;

/**
 * A strategic choice with its full justification: the utility score of every
 * option and the human-readable factors behind them. Kept so players and tests
 * can see why a settlement acted as it did.
 *
 * @param causeEventId the event that prompted the re-evaluation, or -1
 */
public record Decision(int day, Strategy chosen, Map<Strategy, Double> scores, List<String> reasons,
                       long causeEventId) {
}
