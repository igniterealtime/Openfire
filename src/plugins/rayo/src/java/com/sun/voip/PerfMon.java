/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation and distributed hereunder
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this
 * code.
 */

package com.sun.voip;

import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.io.*;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

class PerfObservable extends Observable {
    public void notifyObservers(Object b) {
	setChanged();
	super.notifyObservers(b);
    }
}

class PerfMonCanvas extends Canvas implements Observer {
    int maxElements;
    Vector rateVector = new Vector();
    // Vector highestAllowedVector = new Vector();
    Vector windowSizeVector = new Vector();
    Graphics g;

    PerfMonCanvas(Observable notifier, int width) {
	this.maxElements = width;
	notifier.addObserver(this);
    }

    public void paint() {
	paint(getGraphics());
    }

    public void paint(Graphics g) {
	draw(rateVector, g, Color.blue);
	// draw(highestAllowedVector, g, Color.red);
	draw(windowSizeVector, g, Color.green);
    }

    private void draw(Vector v, Graphics g, Color c) {
	g.setColor(c);

	Dimension d = getSize();

	int height = d.height;

	Point previousPoint = null;

	int size = v.size();

	for (int i = 0; i < size; i++) {
            try {
		Point point1 = previousPoint;
                Point point2 = new Point(i, ((Point)v.elementAt(i)).y);

                if (point1 == null)
                    point1 = point2;

                previousPoint = point2;

                g.drawLine(point1.x, height - point1.y,
                           point2.x, height - point2.y);
            } catch (NoSuchElementException e) {
		break;
            }
	}
    }

    public void addElement(Vector v, int y) {
	while (v.size() >= getSize().width) {
	    v.removeElementAt(0);
	}

	v.addElement(new Point(0, y));
    }

    public void update(Observable o, Object arg) {
	paint(g);
    }

    public void erase() {
	g = getGraphics();
	g.clearRect(0, 0, getSize().width, getSize().height);
    }

}

public class PerfMon extends Frame implements Runnable {
    class DWAdapter extends WindowAdapter {
        public void windowClosing(WindowEvent event) {
            setVisible(false);

	    updater.windowClosed();
        }
    }

    private PerfMonCanvas pmc;
    private Thread dispThread;
    private int height;
    private int width;
    private boolean quit = false;

    private DataUpdater updater;

    private String title;

    public PerfMon(String title, DataUpdater updater, Point location, int width,
	    int height) {

	super(title);

	this.title = title;
	this.updater = updater;
	this.width = width;
	this.height = height;

	if (location != null) {
	    setLocation(location);
	}

	addWindowListener(new DWAdapter());
	setBackground(Color.white);
	setLayout(new BorderLayout());
	pmc = new PerfMonCanvas(new PerfObservable(), width);
	ScrollPane sp = new ScrollPane();
	sp.add("Center", pmc);
	add(sp);
	setSize(width, height);
	setVisible(true);
	//show();

	dispThread = new Thread(this);
	dispThread.setDaemon(true);
	dispThread.start();
    }

    public void stop() {
	quit = true;
	setVisible(false);
    }

    public void run() {
	while (quit == false) {
	    try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
	    }

	    pmc.erase();
		
	    int data = updater.getData();

	    setTitle(title + " " + data);
	    
	    pmc.addElement(pmc.windowSizeVector, data);

	    pmc.paint();
	}
    }

}
