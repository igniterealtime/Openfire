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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The outcome of a server-to-server connection test: one result per check, in the order of {@link S2SCheck}.
 *
 * Instances are created by {@link S2SChecklistBuilder} and cannot be modified.
 *
 * @author Matthew Vivian
 */
public final class S2SChecklist
{
    private final List<CheckResult> results;
    private final Map<S2SCheck, CheckResult> byCheck;

    S2SChecklist(final List<CheckResult> results)
    {
        this.results = List.copyOf(results);
        this.byCheck = new EnumMap<>(S2SCheck.class);
        for (final CheckResult result : this.results) {
            byCheck.put(result.check(), result);
        }
        if (byCheck.size() != S2SCheck.values().length || this.results.size() != S2SCheck.values().length) {
            throw new IllegalArgumentException("A checklist needs exactly one result for each check.");
        }
    }

    /**
     * All results, in the order of {@link S2SCheck}.
     *
     * @return an unmodifiable list of 11 results.
     */
    public List<CheckResult> getResults()
    {
        return results;
    }

    /**
     * The result of one check.
     *
     * @param check the check.
     * @return its result, never null.
     */
    public CheckResult get(final S2SCheck check)
    {
        return byCheck.get(check);
    }

    /**
     * The earliest failed check, which is where an administrator should start looking.
     *
     * @return the first result with status {@link CheckStatus#FAIL}, or empty when no check failed.
     */
    public Optional<CheckResult> getFirstFailure()
    {
        return results.stream().filter(result -> result.status() == CheckStatus.FAIL).findFirst();
    }
}
