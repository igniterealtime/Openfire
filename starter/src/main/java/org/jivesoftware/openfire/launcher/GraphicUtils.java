/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import java.awt.*;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.Hashtable;
import javax.swing.*;

/**
 * <code>GraphicsUtils</code> class defines common user-interface related utility
 * functions.
 */
public final class GraphicUtils {
    private static final Insets HIGHLIGHT_INSETS = new Insets(1, 1, 1, 1);
    public static final Color SELECTION_COLOR = new java.awt.Color(166, 202, 240);
    public static final Color TOOLTIP_COLOR = new java.awt.Color(166, 202, 240);

    protected final static Component component = new Component() {
    };
    protected final static MediaTracker tracker = new MediaTracker(component);

    private static Hashtable<String, Image> imageCache = new Hashtable<>();

    private GraphicUtils() {
    }


    /**
     * Sets the location of the specified window so that it is centered on screen.
     *
     * @param window The window to be centered.
     */
    public static void centerWindowOnScreen(Window window) {
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        final Dimension size = window.getSize();

        if (size.height > screenSize.height) {
            size.height = screenSize.height;
        }

        if (size.width > screenSize.width) {
            size.width = screenSize.width;
        }

        window.setLocation((screenSize.width - size.width) / 2,
                (screenSize.height - size.height) / 2);
    }

    /**
     * Draws a single-line highlight border rectangle.
     *
     * @param g         The graphics context to use for drawing.
     * @param x         The left edge of the border.
     * @param y         The top edge of the border.
     * @param width     The width of the border.
     * @param height    The height of the border.
     * @param raised    <code>true</code> if the border is to be drawn raised,
     *                  <code>false</code> if lowered.
     * @param shadow    The shadow color for the border.
     * @param highlight The highlight color for the border.
     * @see javax.swing.border.EtchedBorder
     * @see javax.swing.plaf.basic.BasicGraphicsUtils#drawEtchedRect
     */
    public static void drawHighlightBorder(Graphics g, int x, int y,
                                           int width, int height, boolean raised,
                                           Color shadow, Color highlight) {
        final Color oldColor = g.getColor();
        g.translate(x, y);

        g.setColor(raised ? highlight : shadow);
        g.drawLine(0, 0, width - 2, 0);
        g.drawLine(0, 1, 0, height - 2);

        g.setColor(raised ? shadow : highlight);
        g.drawLine(width - 1, 0, width - 1, height - 1);
        g.drawLine(0, height - 1, width - 2, height - 1);

        g.translate(-x, -y);
        g.setColor(oldColor);
    }

    /**
     * Return the amount of space taken up by a highlight border drawn by
     * <code>drawHighlightBorder()</code>.
     *
     * @return The <code>Insets</code> needed for the highlight border.
     * @see #drawHighlightBorder
     */
    public static Insets getHighlightBorderInsets() {
        return HIGHLIGHT_INSETS;
    }

    public static ImageIcon createImageIcon(Image image) {
        if (image == null) {
            return null;
        }

        synchronized (tracker) {
            tracker.addImage(image, 0);
            try {
                tracker.waitForID(0, 0);
            } catch (InterruptedException e) {
                System.out.println("INTERRUPTED while loading Image");
            }
            tracker.removeImage(image, 0);
        }

        return new ImageIcon(image);
    }

    /**
     * Returns a point where the given popup menu should be shown. The
     * point is calculated by adjusting the X and Y coordinates from the
     * given mouse event so that the popup menu will not be clipped by
     * the screen boundaries.
     *
     * @param popup the popup menu
     * @param event the mouse event
     * @return the point where the popup menu should be shown
     */
    public static Point getPopupMenuShowPoint(JPopupMenu popup, MouseEvent event) {
        Component source = (Component) event.getSource();
        Point topLeftSource = source.getLocationOnScreen();
        Point ptRet = getPopupMenuShowPoint(popup,
                topLeftSource.x + event.getX(),
                topLeftSource.y + event.getY());
        ptRet.translate(-topLeftSource.x, -topLeftSource.y);
        return ptRet;
    }

    /**
     * Returns a point where the given popup menu should be shown. The
     * point is calculated by adjusting the X and Y coordinates so that
     * the popup menu will not be clipped by the screen boundaries.
     *
     * @param popup the popup menu
     * @param x     the x position in screen coordinate
     * @param y     the y position in screen coordinates
     * @return the point where the popup menu should be shown in screen
     *         coordinates
     */
    public static Point getPopupMenuShowPoint(JPopupMenu popup, int x, int y) {
        Dimension sizeMenu = popup.getPreferredSize();
        Point bottomRightMenu = new Point(x + sizeMenu.width, y + sizeMenu.height);

        Rectangle[] screensBounds = getScreenBounds();
        int n = screensBounds.length;
        for (int i = 0; i < n; i++) {
            Rectangle screenBounds = screensBounds[i];
            if (screenBounds.x <= x && x <= (screenBounds.x + screenBounds.width)) {
                Dimension sizeScreen = screenBounds.getSize();
                sizeScreen.height -= 32;  // Hack to help prevent menu being clipped by Windows/Linux Taskbar.

                int xOffset = 0;
                if (bottomRightMenu.x > (screenBounds.x + sizeScreen.width))
                    xOffset = -sizeMenu.width;

                int yOffset = 0;
                if (bottomRightMenu.y > (screenBounds.y + sizeScreen.height))
                    yOffset = sizeScreen.height - bottomRightMenu.y;

                return new Point(x + xOffset, y + yOffset);
            }
        }

        return new Point(x, y); // ? that would mean that the top left point was not on any screen.
    }

    /**
     * Centers the window over a component (usually another window).
     * The window must already have been sized.
     * @param window the window to center
     * @param over the component over which to center it
     */
    public static void centerWindowOnComponent(Window window, Component over) {
        if ((over == null) || !over.isShowing()) {
            centerWindowOnScreen(window);
            return;
        }


        Point parentLocation = over.getLocationOnScreen();
        Dimension parentSize = over.getSize();
        Dimension size = window.getSize();

        // Center it.
        int x = parentLocation.x + (parentSize.width - size.width) / 2;
        int y = parentLocation.y + (parentSize.height - size.height) / 2;

        // Now, make sure it's onscreen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        // This doesn't actually work on the Mac, where the screen
        // doesn't necessarily start at 0,0
        if (x + size.width > screenSize.width)
            x = screenSize.width - size.width;

        if (x < 0)
            x = 0;

        if (y + size.height > screenSize.height)
            y = screenSize.height - size.height;

        if (y < 0)
            y = 0;

        window.setLocation(x, y);
    }

    /**
     * @param c the component
     * @return returns true if the component of one of its child has the focus
     */
    public static boolean isAncestorOfFocusedComponent(Component c) {
        if (c.hasFocus()) {
            return true;
        } else {
            if (c instanceof Container) {
                Container cont = (Container) c;
                int n = cont.getComponentCount();
                for (int i = 0; i < n; i++) {
                    Component child = cont.getComponent(i);
                    if (isAncestorOfFocusedComponent(child))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the first component in the tree of <code>c</code> that can accept
     * the focus.
     *
     * @param c       the root of the component hierarchy to search
     * @param deepest if <code>deepest</code> is true the method will return the first and deepest component that can accept the
     *                focus.  For example, if both a child and its parent are focusable and <code>deepest</code> is true, the child is
     *                returned.
     * @return the first component that can accept focus
     * @see #focusComponentOrChild
     */
    public static Component getFocusableComponentOrChild(Component c, boolean deepest) {
        if (c != null && c.isEnabled() && c.isVisible()) {
            if (c instanceof Container) {
                Container cont = (Container) c;

                if (deepest == false) { // first one is a good one
                    if (c instanceof JComponent) {
                        JComponent jc = (JComponent) c;
                        if (jc.isRequestFocusEnabled()) {
                            return jc;
                        }
                    }
                }

                int n = cont.getComponentCount();
                for (int i = 0; i < n; i++) {
                    Component child = cont.getComponent(i);
                    Component focused = getFocusableComponentOrChild(child, deepest);
                    if (focused != null) {
                        return focused;
                    }
                }

                if (c instanceof JComponent) {
                    if (deepest == true) {
                        JComponent jc = (JComponent) c;
                        if (jc.isRequestFocusEnabled()) {
                            return jc;
                        }
                    }
                } else {
                    return c;
                }
            }
        }

        return null;
    }

    /**
     * Puts the focus on the first component in the tree of <code>c</code> that
     * can accept the focus.
     * @param c the component
     * @return the first component that can accept focus
     * @see #getFocusableComponentOrChild
     */
    public static Component focusComponentOrChild(Component c) {
        return focusComponentOrChild(c, false);
    }

    /**
     * Puts the focus on the first component in the tree of <code>c</code> that
     * can accept the focus.
     *
     * @param c       the root of the component hierarchy to search
     * @param deepest if <code>deepest</code> is true the method will focus the first and deepest component that can
     *                accept the focus.
     *                For example, if both a child and its parent are focusable and <code>deepest</code> is true, the child is focused.
     * @return the component which has focus
     * @see #getFocusableComponentOrChild
     */
    public static Component focusComponentOrChild(Component c, boolean deepest) {
        final Component focusable = getFocusableComponentOrChild(c, deepest);
        if (focusable != null) {
            focusable.requestFocus();
        }
        return focusable;
    }

    /**
     * Loads an {@link Image} named <code>imageName</code> as a resource
     * relative to the Class <code>cls</code>.  If the <code>Image</code> can
     * not be loaded, then <code>null</code> is returned.  Images loaded here
     * will be added to an internal cache based upon the full {@link URL} to
     * their location.
     * <p>
     * <em>This method replaces legacy code from JDeveloper 3.x and earlier.</em></p>
     *
     * @param imageName the name of the image
     * @param cls the class relative to the which the image resource should be obtained
     * @return the image
     *
     * @see Class#getResource(String)
     * @see Toolkit#createImage(URL)
     */
    public static Image loadFromResource(String imageName, Class cls) {
        try {
            final URL url = cls.getResource(imageName);

            if (url == null) {
                return null;
            }

            Image image = imageCache.get(url.toString());

            if (image == null) {
                image = Toolkit.getDefaultToolkit().createImage(url);
                imageCache.put(url.toString(), image);
            }

            return image;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static Rectangle[] getScreenBounds() {
        GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final GraphicsDevice[] screenDevices = graphicsEnvironment.getScreenDevices();
        Rectangle[] screenBounds = new Rectangle[screenDevices.length];
        for (int i = 0; i < screenDevices.length; i++) {
            GraphicsDevice screenDevice = screenDevices[i];
            final GraphicsConfiguration defaultConfiguration = screenDevice.getDefaultConfiguration();
            screenBounds[i] = defaultConfiguration.getBounds();
        }

        return screenBounds;
    }

    public static final void makeSameSize(JComponent[] comps) {
        if (comps.length == 0) {
            return;
        }

        int max = 0;
        for (int i = 0; i < comps.length; i++) {
            int w = comps[i].getPreferredSize().width;
            max = Math.max(w, max);
        }

        Dimension dim = new Dimension(max, comps[0].getPreferredSize().height);
        for (int i = 0; i < comps.length; i++) {
            comps[i].setPreferredSize(dim);
        }
    }

    /**
     * Return the hexidecimal color from a java.awt.Color
     *
     * @param c the colour
     * @return hexadecimal string
     */
    public static final String toHTMLColor(Color c) {
        int color = c.getRGB();
        color |= 0xff000000;
        String s = Integer.toHexString(color);
        return s.substring(2);
    }

    public static final String createToolTip(String text, int width) {
        final String htmlColor = toHTMLColor(TOOLTIP_COLOR);
        final String toolTip = "<html><table width=" + width + " bgColor=" + htmlColor + "><tr><td><b>" + text + "</b></td></tr></table></table>";
        return toolTip;
    }

    public static final String createToolTip(String text) {
        final String htmlColor = toHTMLColor(TOOLTIP_COLOR);
        final String toolTip = "<html><table  bgColor=" + htmlColor + "><tr><td><b>" + text + "</b></td></tr></table></table>";
        return toolTip;
    }
}

