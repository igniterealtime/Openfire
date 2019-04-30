package org.jivesoftware.openfire.filetransfer;

/**
 * An event listener for File Transfer related events.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public interface FileTransferEventListener
{
    /**
     * Invoked when a file transfer is about to start.. The interceptor can either modify the file transfer or
     * throw a FileTransferRejectedException. The file transfer went sent to the interceptor can be in two states, ready
     * and not ready. The not ready state indicates that this event was fired when the file transfer request was sent by
     * the initiator. The ready state indicates that the file transfer is ready to begin, and the channels can be
     * manipulated by the interceptor.
     * <p>
     * It is recommended for the the sake of user experience that when in the not ready state, any processing done on
     * the file transfer should be quick.
     *
     * @param transfer the transfer being intercepted (never null).
     * @param isReady  true if the transfer is ready to commence or false if this is related to the
     *                 initial file transfer request. An exception at this point will cause the transfer to
     *                 not go through.
     * @throws FileTransferRejectedException if the request was rejected
     */
    void fileTransferStart( FileTransfer transfer, boolean isReady ) throws FileTransferRejectedException;

    /**
     * Invoked when a file transfer was completed. Events are generated for events that succeeded, but also for those
     * that failed.
     *
     * @param transfer      the transfer being intercepted (never null).
     * @param wasSuccessful false when an exception was thrown during file transfer, otherwise true.
     */
    void fileTransferComplete( FileTransfer transfer, boolean wasSuccessful );
}
