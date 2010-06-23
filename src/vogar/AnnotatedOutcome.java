/*
 * Copyright (C) 2009 The Android Open Source Project
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

public class AnnotatedOutcome {
    /** sorts outcomes in reverse chronological order */
    private static final Ordering<Outcome> ORDER_BY_DATE = new Ordering<Outcome>() {
        public int compare(Outcome outcome1, Outcome outcome2) {
            return outcome2.getDate().compareTo(outcome1.getDate());
        }
    };
    public static Ordering<AnnotatedOutcome> ORDER_BY_NAME = new Ordering<AnnotatedOutcome>() {
        @Override public int compare(AnnotatedOutcome a, AnnotatedOutcome b) {
            return a.getName().compareTo(b.getName());
        }
    };

    private final Expectation expectation;
    private final Outcome outcome;
    /** the result value of this outcome (kept only so that it need not be repeatedly recomputed) */
    private final ResultValue resultValue;
    /** a list of previous outcomes for the same action, sorted in reverse chronological order */
    private final List<Outcome> previousOutcomes;
    /**
     * a list of previous result values for the same action, sorted in reverse chronological order
     * (this is kept only so they don't have to be repeatedly recomputed from previousOutcomes)
     */
    private final List<ResultValue> previousResultValues;
    /** will be null if not comparing to a tag */
    private final String tagName;
    private final Outcome tagOutcome;
    private final ResultValue tagResultValue;
    /** the last time the result value changed, or null if it's never changed in recorded history */
    private final Date lastChanged;

    AnnotatedOutcome(Outcome outcome, Expectation expectation,
            List<Outcome> previousOutcomes, String tagName, Outcome tagOutcome) {
        this.expectation = expectation;

        this.outcome = outcome;
        this.resultValue = this.outcome.getResultValue(expectation);

        this.previousOutcomes = ORDER_BY_DATE.sortedCopy(previousOutcomes);
        this.previousResultValues = new ArrayList<ResultValue>();
        Date lastChanged = null;
        for (Outcome previousOutcome : this.previousOutcomes) {
            ResultValue previousOutcomeResultValue = previousOutcome.getResultValue(expectation);
            previousResultValues.add(previousOutcomeResultValue);
            // only assign lastChanged the first time
            if (previousOutcomeResultValue != this.resultValue && lastChanged == null) {
                lastChanged = previousOutcome.getDate();
            }
        }
        this.lastChanged = lastChanged;

        this.tagName = tagName;
        this.tagOutcome = tagOutcome;
        this.tagResultValue =
                this.tagOutcome == null ? null : this.tagOutcome.getResultValue(expectation);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getName() {
        return outcome.getName();
    }

    public ResultValue getResultValue() {
        return resultValue;
    }

    public List<ResultValue> getPreviousResultValues() {
        return previousResultValues;
    }

    public ResultValue getMostRecentResultValue() {
        if (previousResultValues.isEmpty()) {
            return null;
        }
        return previousResultValues.get(0);
    }

    public boolean hasTag() {
        return tagOutcome != null;
    }

    public String getTagName() {
        return tagName;
    }

    public ResultValue getTagResultValue() {
        return tagResultValue;
    }

    /**
     * Returns whether the outcome is noteworthy given the result value and previous history.
     */
    public boolean isNoteworthy() {
        return resultValue != ResultValue.OK || recentlyChanged() || changedSinceTag();
    }

    public boolean outcomeChanged() {
        return previousOutcomes.isEmpty() || !outcome.equals(previousOutcomes.get(0));
    }

    /**
     * Returns whether the outcome recently changed in result value.
     */
    private boolean recentlyChanged() {
        if (previousResultValues.isEmpty()) {
            return false;
        }
        return previousResultValues.get(0) != resultValue;
    }

    private boolean changedSinceTag() {
        return tagResultValue != null && tagResultValue != resultValue;
    }

    public Date lastChanged() {
        return lastChanged;
    }
}
