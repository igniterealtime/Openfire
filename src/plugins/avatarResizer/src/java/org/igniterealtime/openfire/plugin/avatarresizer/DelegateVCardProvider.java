package org.igniterealtime.openfire.plugin.avatarresizer;

import org.dom4j.Element;
import org.jivesoftware.openfire.vcard.VCardProvider;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.NotFoundException;

/**
 * A vCard Provider that delegates to another provider, applying image resizing on the results from the delegate.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DelegateVCardProvider implements VCardProvider
{
    private VCardProvider delegate;

    public DelegateVCardProvider()
    {
    }

    public VCardProvider getDelegate()
    {
        return delegate;
    }

    public void setDelegate( VCardProvider delegate )
    {
        if ( delegate == null )
        {
            throw new IllegalArgumentException( "Argument 'delegate' cannot be null." );
        }

        if ( delegate instanceof DelegateVCardProvider )
        {
            throw new IllegalArgumentException( "Argument 'delegate' cannot be an instance of DelegateVCardProvider." );
        }

        this.delegate = delegate;
    }

    @Override
    public Element loadVCard( String username )
    {
        final Element element = delegate.loadVCard( username );
        Resizer.resizeAvatar( element );
        return element;
    }

    @Override
    public Element createVCard( String username, Element vCardElement ) throws AlreadyExistsException
    {
        final Element element = delegate.createVCard( username, vCardElement );
        Resizer.resizeAvatar( element );
        return element;
    }

    @Override
    public Element updateVCard( String username, Element vCardElement ) throws NotFoundException
    {
        final Element element = delegate.updateVCard( username, vCardElement );
        Resizer.resizeAvatar( element );
        return element;
    }

    @Override
    public void deleteVCard( String username )
    {
        delegate.deleteVCard( username );
    }

    @Override
    public boolean isReadOnly()
    {
        return delegate.isReadOnly();
    }
}
