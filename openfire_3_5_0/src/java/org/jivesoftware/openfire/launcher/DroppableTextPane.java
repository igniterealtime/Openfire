/**
 * $RCSfile$
 * $Revision: 1298 $
 * $Date: 2005-04-24 14:22:45 -0300 (Sun, 24 Apr 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.launcher;

import javax.swing.*;
import java.awt.dnd.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.List;
import java.util.Iterator;
import java.io.File;
import java.io.IOException;

/**
 * A droppable text pane allows for DnD of file objects from the OS onto the actual
 * pane via <code>File</code>.
 *
 * @author Matt Tucker
 */
public abstract class DroppableTextPane extends JTextPane implements DropTargetListener,
        DragSourceListener, DragGestureListener
 {
    private DragSource dragSource = DragSource.getDefaultDragSource();

    /**
     * Creates a droppable text pane.
     */
    public DroppableTextPane() {
        new DropTarget(this, this);
        dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, this);
    }

    public void dragDropEnd(DragSourceDropEvent DragSourceDropEvent) {
    }

    public void dragEnter(DragSourceDragEvent DragSourceDragEvent) {
    }

    public void dragExit(DragSourceEvent DragSourceEvent) {
    }

    public void dragOver(DragSourceDragEvent DragSourceDragEvent) {
    }

    public void dropActionChanged(DragSourceDragEvent DragSourceDragEvent) {
    }

    public void dragEnter(DropTargetDragEvent dropTargetDragEvent) {
        dropTargetDragEvent.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
    }

    public void dragExit(DropTargetEvent dropTargetEvent) {
    }

    public void dragOver(DropTargetDragEvent dropTargetDragEvent) {
    }

    public void dropActionChanged(DropTargetDragEvent dropTargetDragEvent) {
    }

    public void drop(DropTargetDropEvent dropTargetDropEvent) {
        try {
            Transferable transferable = dropTargetDropEvent.getTransferable();
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dropTargetDropEvent.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                List fileList = (List) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                Iterator iterator = fileList.iterator();
                while (iterator.hasNext()) {
                    File file = (File) iterator.next();
                    if (file.isFile()) {
                        fileDropped(file);
                    }

                    if (file.isDirectory()) {
                        directoryDropped(file);
                    }
                }
                dropTargetDropEvent.getDropTargetContext().dropComplete(true);
            }
            else {
                dropTargetDropEvent.rejectDrop();
            }
        }
        catch (IOException io) {
            io.printStackTrace();
            dropTargetDropEvent.rejectDrop();
        }
        catch (UnsupportedFlavorException ufe) {
            ufe.printStackTrace();
            dropTargetDropEvent.rejectDrop();
        }
    }

    public void dragGestureRecognized(DragGestureEvent dragGestureEvent) {

    }

    /**
     * Notified when a file has been dropped onto the frame.
     *
     * @param file the file that has been dropped.
     */
    public void fileDropped(File file){

    }

    /**
     * Notified when a directory has been dropped onto the frame.
     *
     * @param file the directory that has been dropped.
     */
    public void directoryDropped(File file){

    }
}