package org.jivesoftware.openfire.vcard;

import org.dom4j.Element;
import org.jivesoftware.util.Base64;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Image resizing utility methods.
 */
public class PhotoResizer
{
    private static final Logger Log = LoggerFactory.getLogger( PhotoResizer.class );

    // Property that, when 'true' causes avatars that are being loaded from backend storage to be resized, prior to be
    // processed and send to entities.
    public static final String PROPERTY_RESIZE_ON_LOAD = "avatar.resize.enable-on-load";
    public static final boolean PROPERTY_RESIZE_ON_LOAD_DEFAULT = true;

    // Property that, when 'true' causes avatars that are being stored in backend storage to be resized.
    public static final String PROPERTY_RESIZE_ON_CREATE = "avatar.resize.enable-on-create";
    public static final boolean PROPERTY_RESIZE_ON_CREATE_DEFAULT = false;

    // Property that controls the target dimension, in pixels.
    public static final String PROPERTY_TARGETDIMENSION = "avatar.resize.targetdimension";
    public static final int PROPERTY_TARGETDIMENSION_DEFAULT = 96;

    public static void resizeAvatar( final Element vCardElement )
    {
        if ( vCardElement == null )
        {
            return;
        }

        // XPath didn't work?
        if ( vCardElement.element( "PHOTO" ) == null )
        {
            return;
        }

        if ( vCardElement.element( "PHOTO" ).element( "BINVAL" ) == null || vCardElement.element( "PHOTO" ).element( "TYPE" ) == null )
        {
            return;
        }

        final Element element = vCardElement.element( "PHOTO" ).element( "BINVAL" );
        if ( element.getTextTrim() == null || element.getTextTrim().isEmpty() )
        {
            return;
        }

        // Get a writer (check if we can generate a new image for the type of the original).
        final String type = vCardElement.element( "PHOTO" ).element( "TYPE" ).getTextTrim();
        final Iterator it = ImageIO.getImageWritersByMIMEType( type );
        if ( !it.hasNext() )
        {
            Log.debug( "Cannot resize avatar. No writers available for MIME type {}.", type );
            return;
        }
        final ImageWriter iw = (ImageWriter) it.next();

        // Extract the original avatar from the VCard.
        final byte[] original = Base64.decode( element.getTextTrim() );

        // Crop and shrink, if needed.
        final int targetDimension = JiveGlobals.getIntProperty( PROPERTY_TARGETDIMENSION, PROPERTY_TARGETDIMENSION_DEFAULT );
        final byte[] resized = cropAndShrink( original, targetDimension, iw );

        // If a resized image was created, replace to original avatar in the VCard.
        if ( resized != null )
        {
            Log.debug( "Replacing original avatar in vcard with a resized variant." );
            vCardElement.element( "PHOTO" ).element( "BINVAL" ).setText( Base64.encodeBytes( resized ) );
        }
    }

    public static byte[] cropAndShrink( final byte[] bytes, final int targetDimension, final ImageWriter iw )
    {
        Log.debug( "Original image size: {} bytes.", bytes.length );

        BufferedImage avatar;
        try ( final ByteArrayInputStream stream = new ByteArrayInputStream( bytes ) )
        {
            avatar = ImageIO.read( stream );

            if ( avatar.getWidth() <= targetDimension && avatar.getHeight() <= targetDimension )
            {
                Log.debug( "Original image dimension ({}x{}) is within acceptable bounds ({}x{}). No need to resize.", new Object[] { avatar.getWidth(), avatar.getHeight(), targetDimension, targetDimension });
                return null;
            }
        }
        catch ( IOException | RuntimeException ex )
        {
            Log.warn( "Failed to resize avatar. An unexpected exception occurred while reading the original image.", ex );
            return null;
        }

        /* We're going to be resizing, let's crop the image so that it's square and figure out the new starting size. */
        Log.debug( "Original image is " + avatar.getWidth() + "x" + avatar.getHeight() + " pixels" );

        final int targetWidth, targetHeight;

        if ( avatar.getHeight() == avatar.getWidth() )
        {
            Log.debug( "Original image is already square ({}x{})", avatar.getWidth(), avatar.getHeight() );
            targetWidth = targetHeight = avatar.getWidth();
        }
        else
        {
            final int x, y;
            if ( avatar.getHeight() > avatar.getWidth() )
            {
                Log.debug( "Original image is taller ({}) than wide ({}).", avatar.getHeight(), avatar.getWidth() );
                x = 0;
                y = ( avatar.getHeight() - avatar.getWidth() ) / 2;
                targetWidth = targetHeight = avatar.getWidth();
            }
            else
            {
                Log.debug( "Original image is wider ({}) than tall ({}).", avatar.getWidth(), avatar.getHeight() );
                x = ( avatar.getWidth() - avatar.getHeight() ) / 2;
                y = 0;
                targetWidth = targetHeight = avatar.getHeight();
            }
            // pull out a square image, centered.
            avatar = avatar.getSubimage( x, y, targetWidth, targetHeight );
        }

        /* Let's crop/scale the image as necessary out the new dimensions. */
        final BufferedImage resizedAvatar = new BufferedImage( targetDimension, targetDimension, avatar.getType() );

        final AffineTransform scale = AffineTransform.getScaleInstance( (double) targetDimension / (double) targetWidth, (double) targetDimension / (double) targetHeight );
        final Graphics2D g = resizedAvatar.createGraphics();
        g.drawRenderedImage( avatar, scale );
        Log.debug( "Resized image is {}x{}.", resizedAvatar.getWidth(), resizedAvatar.getHeight() );

        /* Now we have to dump the new jpeg, png, etc. to a byte array */
        try ( final ByteArrayOutputStream bostream = new ByteArrayOutputStream();
              final ImageOutputStream iostream = new MemoryCacheImageOutputStream( bostream ) )
        {
            iw.setOutput( iostream );
            iw.write( resizedAvatar );
            final byte[] data = bostream.toByteArray();

            Log.debug( "Resized image size: {} bytes.", data.length );

            return data;
        }
        catch ( IOException | RuntimeException ex )
        {
            Log.warn( "Failed to resize avatar. An unexpected exception occurred while writing the resized image.", ex );
            return null;
        }
    }
}
