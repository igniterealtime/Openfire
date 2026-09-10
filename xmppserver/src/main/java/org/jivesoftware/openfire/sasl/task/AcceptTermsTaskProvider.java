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
package org.jivesoftware.openfire.sasl.task;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An example {@link Sasl2TaskProvider} that requires a user to accept the current terms of service before their
 * authentication is allowed to complete.
 *
 * This illustrates the simplest shape of a task: one that the server imposes, that is not advertised in stream
 * features, and whose eligibility is a per-user lookup. A peer that does not understand the task can only abort,
 * which is the intent here: the point of the task is that the user cannot get in without performing it.
 *
 * The exchange is:
 * <pre>{@code
 * S: <continue xmlns='urn:xmpp:sasl:2'>
 *      <additional-data>...</additional-data>
 *      <tasks><task>X-EXAMPLE-ACCEPT-TERMS</task></tasks>
 *      <text>The terms of service have changed...</text>
 *    </continue>
 * C: <next xmlns='urn:xmpp:sasl:2' task='X-EXAMPLE-ACCEPT-TERMS'/>
 * S: <task-data xmlns='urn:xmpp:sasl:2'>
 *      <terms xmlns='https://example.org/protocol/terms' version='2026-01'>https://example.org/terms</terms>
 *    </task-data>
 * C: <task-data xmlns='urn:xmpp:sasl:2'>
 *      <accept xmlns='https://example.org/protocol/terms' version='2026-01'/>
 *    </task-data>
 * S: <success xmlns='urn:xmpp:sasl:2'>...</success>
 * }</pre>
 */
public class AcceptTermsTaskProvider implements Sasl2TaskProvider
{
    private static final Logger Log = LoggerFactory.getLogger(AcceptTermsTaskProvider.class);

    public static final String NAMESPACE = "https://example.org/protocol/terms";
    public static final String TASK_NAME = "X-EXAMPLE-ACCEPT-TERMS";

    /** The name of the user property in which the accepted version is recorded. */
    private static final String USER_PROPERTY = "terms.accepted-version";

    private final String currentVersion;
    private final String termsUrl;

    /**
     * Creates a provider that requires acceptance of the given version of the terms of service.
     *
     * @param currentVersion an opaque version identifier for the current terms (cannot be null). Compared for
     *                        exact equality against what a user has previously accepted and against what a peer
     *                        claims to accept; bump this whenever the terms change materially enough that prior
     *                        acceptances should no longer count.
     * @param termsUrl the location at which the peer's user can read the terms of this version (cannot be null).
     */
    public AcceptTermsTaskProvider(@Nonnull final String currentVersion, @Nonnull final String termsUrl)
    {
        this.currentVersion = currentVersion;
        this.termsUrl = termsUrl;
    }

    @Override
    @Nonnull
    public String getIdentifier()
    {
        return "org.example.accept-terms";
    }

    @Override
    @Nonnull
    public Set<String> getTaskNames()
    {
        return Set.of(TASK_NAME);
    }

    @Override
    @Nonnull
    public List<String> getOfferedTasks(@Nonnull final Sasl2TaskContext context)
    {
        final String username = context.getAuthorizationIdentity();
        if (context.getCompletedTaskNames().contains(TASK_NAME)) {
            return List.of();
        }

        final boolean upToDate;
        if (username == null) {
            // Anonymous authentication: there is no account that has recorded an acceptance.
            upToDate = false;
        } else {
            upToDate = getAcceptedVersion(username).filter(currentVersion::equals).isPresent();
        }
        return upToDate ? List.of() : List.of(TASK_NAME);
    }

    @Override
    @Nonnull
    public Optional<String> getContinueText(@Nonnull final Sasl2TaskContext context)
    {
        return Optional.of("The terms of service have changed and must be accepted before you can sign in.");
    }

    @Override
    @Nonnull
    public Sasl2Task createTask(@Nonnull final String taskName, @Nonnull final Sasl2TaskContext context)
    {
        return new AcceptTermsTask(context);
    }

    @Nonnull
    private Optional<String> getAcceptedVersion(@Nonnull final String username)
    {
        try {
            return Optional.ofNullable(UserManager.getInstance().getUser(username).getProperties().get(USER_PROPERTY));
        } catch (final UserNotFoundException e) {
            Log.debug("Unable to determine the accepted terms version of user '{}': the account does not exist.", username, e);
            return Optional.empty();
        } catch (final Exception e) {
            // Sasl2TaskManager treats an exception from getOfferedTasks() as "this provider offers nothing this
            // round", to isolate one misbehaving provider from the rest of the negotiation. Left uncaught, that
            // would silently let the user in without accepting the terms on a transient lookup failure. Since this
            // task is a mandatory gate, an unexpected failure here must be treated the same as "not yet accepted",
            // not swallowed.
            Log.warn("Unable to determine the accepted terms version of user '{}' due to an unexpected error. Assuming the terms have not been accepted.", username, e);
            return Optional.empty();
        }
    }

    private void recordAcceptance(@Nonnull final String username)
    {
        try {
            final User user = UserManager.getInstance().getUser(username);
            user.getProperties().put(USER_PROPERTY, currentVersion);
        } catch (final Exception e) {
            // The acceptance could not be persisted. This is no reason to fail the authentication (as the peer did
            // explicitly accept). Acceptance should be re-offered the next time this user authenticates.
            Log.warn("Unable to record the acceptance of the terms of service by user '{}'.", username, e);
        }
    }

    /**
     * The stateful part: one instance per negotiation in which the peer selected this task.
     */
    private class AcceptTermsTask implements Sasl2Task
    {
        private final Sasl2TaskContext context;

        private AcceptTermsTask(@Nonnull final Sasl2TaskContext context)
        {
            this.context = context;
        }

        @Override
        @Nonnull
        public String getName()
        {
            return TASK_NAME;
        }

        @Override
        @Nonnull
        public Sasl2TaskResult begin(@Nonnull final Element next)
        {
            final Element terms = DocumentHelper.createElement(QName.get("terms", NAMESPACE));
            terms.addAttribute("version", currentVersion);
            terms.setText(termsUrl);
            return Sasl2TaskResult.taskData(terms);
        }

        @Override
        @Nonnull
        public Sasl2TaskResult onTaskData(@Nonnull final Element taskData) throws SaslFailureException
        {
            final Element accept = taskData.element(QName.get("accept", NAMESPACE));
            if (accept == null) {
                throw new SaslFailureException(Failure.NOT_AUTHORIZED, "The terms of service were not accepted.");
            }
            if (!currentVersion.equals(accept.attributeValue("version"))) {
                throw new SaslFailureException(Failure.MALFORMED_REQUEST, "A version of the terms of service was accepted that is not the version that was offered.");
            }
            final String username = context.getAuthorizationIdentity();
            if (username != null) {
                // A non-anonymous session has an account to persist the acceptance against, so it is not asked again on
                // a later connection (until the version changes). An anonymous session has no such account: its
                // acceptance covers only this one negotiation, which is why getOfferedTasks() offers this task on
                // every anonymous authentication rather than trying to remember a prior one.
                recordAcceptance(username);
            }
            return Sasl2TaskResult.completed();
        }
    }
}
