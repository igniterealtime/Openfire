/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.server.s2stest;

import java.util.Objects;

/**
 * The one-line summary shown above the checklist: a failure (naming the first failed check), warnings (counted), steps
 * not observed, or everything passed, in that order of precedence.
 *
 * @param kind the kind of summary.
 * @param failedCheck the first failed check, for a failure; otherwise null.
 * @param warnings the number of checks with a warning.
 * @author Matthew Vivian
 */
public record ChecklistSummary(Kind kind, S2SCheck failedCheck, int warnings)
{
    public enum Kind
    {
        FAILED("failed"),
        WARNINGS("warnings"),
        NOT_OBSERVED("not_observed"),
        PASSED("passed");

        private final String keySegment;

        Kind(final String keySegment)
        {
            this.keySegment = keySegment;
        }

        /**
         * The i18n key of this summary's message. Its parameters are: the domain; for a failure, the number of the
         * failed check; for warnings, the number of warnings.
         *
         * @return the resource bundle key, never null.
         */
        public String messageKey()
        {
            return "server2server.settings.testing.summary." + keySegment;
        }
    }

    public ChecklistSummary
    {
        Objects.requireNonNull(kind, "kind");
    }

    public static ChecklistSummary of(final S2SChecklist checklist)
    {
        final int warnings = (int) checklist.getResults().stream().filter(result -> result.status() == CheckStatus.WARNING).count();
        final var firstFailure = checklist.getFirstFailure();
        if (firstFailure.isPresent()) {
            return new ChecklistSummary(Kind.FAILED, firstFailure.get().check(), warnings);
        }
        if (warnings > 0) {
            return new ChecklistSummary(Kind.WARNINGS, null, warnings);
        }
        final boolean anyNotObserved = checklist.getResults().stream().anyMatch(result -> result.status() == CheckStatus.NOT_OBSERVED);
        return new ChecklistSummary(anyNotObserved ? Kind.NOT_OBSERVED : Kind.PASSED, null, 0);
    }
}
