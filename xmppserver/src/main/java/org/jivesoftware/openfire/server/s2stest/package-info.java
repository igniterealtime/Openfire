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

/**
 * The checklist shown by the admin console's server-to-server connection test: the 11 checks, and the rules that turn
 * what was recorded during a connection attempt into a status for each check. The records come from
 * {@link org.jivesoftware.openfire.server.diagnostics}.
 *
 * Classes in this package hold no reference to server singletons, so the rules can be tested on their own.
 */
package org.jivesoftware.openfire.server.s2stest;
