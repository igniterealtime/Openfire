package org.jivesoftware.messenger.user;

/**
 * A UserProvider to be used in conjunction with
 * {@link org.jivesoftware.messenger.auth.NativeAuthProvider NativeAuthProvider}, which
 * authenticates using OS-level authentication. New user accounts will automatically be
 * created as needed (upon successful initial authentication) and are subsequently treated as
 * read-only. To enable this provider, edit the XML config file file and set:
 *
 * <pre>
 * &lt;provider&gt;
 *     &lt;auth&gt;
 *         &lt;className&gt;org.jivesoftware.messenger.auth.NativeAuthProvider&lt;/className&gt;
 *     &lt;/auth&gt;
 *     &lt;user&gt;
 *         &lt;className&gt;org.jivesoftware.messenger.user.NativeUserProvider&lt;/className&gt;
 *     &lt;/user&gt;
 * &lt;/provider&gt;
 * </pre>
 *
 * @see org.jivesoftware.messenger.auth.NativeAuthProvider NativeAuthProvider
 *
 * @author Matt Tucker
 */
public class NativeUserProvider extends DefaultUserProvider {

    public boolean isReadOnly() {
        return true;
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }
}
