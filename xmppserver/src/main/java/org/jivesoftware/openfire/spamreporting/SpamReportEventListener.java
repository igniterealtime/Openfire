/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.spamreporting;

import javax.annotation.Nonnull;

/**
 * An event listener that is invoked when Openfire processes a spam report.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public interface SpamReportEventListener
{
    /**
     * Invoked when Openfire received a spam report.
     *
     * @param spamReport the spam report that was received.
     */
    void receivedSpamReport(@Nonnull final SpamReport spamReport);
}
