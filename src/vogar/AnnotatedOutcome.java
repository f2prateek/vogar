/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vogar;

import com.google.common.collect.Ordering;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Contains an outcome for a test, along with some metadata pertaining to the history of this test,
 * including a list of previous outcomes, an outcome corresponding to the tag Vogar is being run
 * with, if applicable, and the expectation for this test, so that result value information is
 * available.
 */
public final class AnnotatedOutcome {
    /** sorts outcomes in reverse chronological order */
    private static final Ordering<Outcome> ORDER_BY_DATE = new Ordering<Outcome>() {
        public int compare(Outcome outcome1, Outcome outcome2) {
            return outcome1.getDate().compareTo(outcome2.getDate());
        }
    };
    public static Ordering<AnnotatedOutcome> ORDER_BY_NAME = new Ordering<AnnotatedOutcome>() {
        @Override public int compare(AnnotatedOutcome a, AnnotatedOutcome b) {
            return a.getName().compareTo(b.getName());
        }
    };

    private final Expectation expectation;
    private final Outcome outcome;
    /** a list of previous outcomes for the same action, sorted in chronological order */
    private final List<Outcome> previousOutcomes;
    /** will be null if not comparing to a tag */
    private final String tagName;
    private final Outcome tagOutcome;

    AnnotatedOutcome(Outcome outcome, Expectation expectation,
            List<Outcome> previousOutcomes, String tagName, Outcome tagOutcome) {
        this.expectation = expectation;
        this.outcome = outcome;
        this.previousOutcomes = ORDER_BY_DATE.sortedCopy(previousOutcomes);
        this.tagName = tagName;
        this.tagOutcome = tagOutcome;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getName() {
        return outcome.getName();
    }

    public ResultValue getResultValue() {
        return outcome.getResultValue(expectation);
    }

    public List<ResultValue> getPreviousResultValues() {
        List<ResultValue> previousResultValues = new ArrayList<ResultValue>();
        for (Outcome previousOutcome : previousOutcomes) {
            previousResultValues.add(previousOutcome.getResultValue(expectation));
        }
        return previousResultValues;
    }

    /**
     * Returns the most recent result value of a run of this test (before the current run).
     */
    public ResultValue getMostRecentResultValue(ResultValue defaultValue) {
        List<ResultValue> previousResultValues = getPreviousResultValues();
        return previousResultValues.isEmpty() ?
                defaultValue :
                previousResultValues.get(previousResultValues.size() - 1);
    }

    public boolean hasTag() {
        return tagOutcome != null;
    }

    public String getTagName() {
        return tagName;
    }

    public ResultValue getTagResultValue() {
        return tagOutcome == null ? null : tagOutcome.getResultValue(expectation);
    }

    /**
     * Returns true if the outcome is noteworthy given the result value and previous history.
     */
    public boolean isNoteworthy() {
        return getResultValue() != ResultValue.OK || recentlyChanged() || changedSinceTag();
    }

    public boolean outcomeChanged() {
        return previousOutcomes.isEmpty()
                || !outcome.equals(previousOutcomes.get(previousOutcomes.size() - 1));
    }

    /**
     * Returns true if the outcome recently changed in result value.
     */
    private boolean recentlyChanged() {
        List<ResultValue> previousResultValues = getPreviousResultValues();
        if (previousResultValues.isEmpty()) {
            return false;
        }
        return previousResultValues.get(previousResultValues.size() - 1) != getResultValue();
    }

    private boolean changedSinceTag() {
        ResultValue tagResultValue = getTagResultValue();
        return tagResultValue != null && tagResultValue != getResultValue();
    }

    /**
     * Returns the last time the result value changed, or null if it's never changed in recorded
     * history.
     */
    public Date lastChanged() {
        Date lastChanged = null;
        ResultValue resultValue = getResultValue();
        for (Outcome previousOutcome : previousOutcomes) {
            if (previousOutcome.getResultValue(expectation) != resultValue) {
                lastChanged = previousOutcome.getDate();
            }
        }
        return lastChanged;
    }
}
