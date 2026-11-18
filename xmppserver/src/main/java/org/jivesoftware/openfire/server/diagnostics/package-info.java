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
 * Records what happens during each outbound server-to-server connection attempt: the DNS answer, the TCP connection,
 * the XMPP stream, TLS, the remote certificate and authentication, as the connection code decides each step.
 *
 * Every outbound attempt is recorded, and the latest attempt per domain pair is kept, so that diagnostics such as the
 * admin console's S2S connection test and the failed-session details page can say which step failed and why. A failure
 * while recording never changes how a connection proceeds. See ADR-007.
 */
package org.jivesoftware.openfire.server.diagnostics;
