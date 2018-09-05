/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

    @Override
    public void dragDropEnd(DragSourceDropEvent DragSourceDropEvent) {
    }

    @Override
    public void dragEnter(DragSourceDragEvent DragSourceDragEvent) {
    }

    @Override
    public void dragExit(DragSourceEvent DragSourceEvent) {
    }

    @Override
    public void dragOver(DragSourceDragEvent DragSourceDragEvent) {
    }

    @Override
    public void dropActionChanged(DragSourceDragEvent DragSourceDragEvent) {
    }

    @Override
    public void dragEnter(DropTargetDragEvent dropTargetDragEvent) {
        dropTargetDragEvent.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
    }

    @Override
    public void dragExit(DropTargetEvent dropTargetEvent) {
    }

    @Override
    public void dragOver(DropTargetDragEvent dropTargetDragEvent) {
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dropTargetDragEvent) {
    }

    @Override
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
        catch (IOException | UnsupportedFlavorException io) {
            io.printStackTrace();
            dropTargetDropEvent.rejectDrop();
        }
    }

    @Override
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
