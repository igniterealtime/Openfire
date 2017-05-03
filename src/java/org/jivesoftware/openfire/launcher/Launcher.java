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

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Graphical launcher for Openfire.
 *
 * @author Matt Tucker
 */
public class Launcher {

    private String appName;
    private File binDir;
    private Process openfired;
    private File configFile;
    private JPanel toolbar = new JPanel();

    private ImageIcon offIcon;
    private ImageIcon onIcon;
    private TrayIcon trayIcon;
    private JFrame frame;
    private JPanel cardPanel = new JPanel();
    private CardLayout cardLayout = new CardLayout();

    private JTextPane pane;
    private boolean freshStart = true;

    /**
     * Creates a new Launcher object.
     */
    public Launcher() throws AWTException {
        // Initialize the SystemTray now (to avoid a bug!)
        SystemTray tray = null;
        try {
            tray = SystemTray.getSystemTray();
        }
        catch (Throwable e) {
            // Log to System error instead of standard error log.
            System.err.println("Error loading system tray library, system tray support disabled.");
        }

        // Use the native look and feel.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        if (System.getProperty("app.name") != null) {
            appName = System.getProperty("app.name");
        }
        else {
            appName = "Openfire";
        }

        binDir = new File("").getAbsoluteFile();
        // See if the appdir property is set. If so, use it to find the executable.
        if (System.getProperty("appdir") != null) {
            binDir = new File(System.getProperty("appdir"));
        }

        configFile = new File(new File(binDir.getParent(), "conf"), "openfire.xml");

        frame = new DroppableFrame() {
            @Override
			public void fileDropped(File file) {
                String fileName = file.getName();
                if (fileName.endsWith(".jar") || fileName.endsWith(".war")) {
                    installPlugin(file);
                }
            }
        };

        frame.setTitle(appName);

        ImageIcon splash;
        JPanel mainPanel = new JPanel();
        JLabel splashLabel = null;

        cardPanel.setLayout(cardLayout);

        // Set the icon.
        try {
            splash = new ImageIcon(getClass().getClassLoader().getResource("splash.gif"));
            splashLabel = new JLabel("", splash, JLabel.CENTER);

            onIcon = new ImageIcon(getClass().getClassLoader().getResource("openfire_on-16x16.gif"));
            offIcon = new ImageIcon(getClass().getClassLoader().getResource("openfire_off-16x16.gif"));
            frame.setIconImage(offIcon.getImage());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        mainPanel.setLayout(new BorderLayout());
        cardPanel.setBackground(Color.white);

        // Add buttons
        final JButton startButton = new JButton("Start");
        startButton.setActionCommand("Start");

        final JButton stopButton = new JButton("Stop");
        stopButton.setActionCommand("Stop");

        final JButton browserButton = new JButton("Launch Admin");
        browserButton.setActionCommand("Launch Admin");

        final JButton quitButton = new JButton("Quit");
        quitButton.setActionCommand("Quit");

        toolbar.setLayout(new GridBagLayout());
        toolbar.add(startButton, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
        toolbar.add(stopButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
        toolbar.add(browserButton, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
        toolbar.add(quitButton, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));

        mainPanel.add(cardPanel, BorderLayout.CENTER);
        mainPanel.add(toolbar, BorderLayout.SOUTH);

        // create the main menu of the system tray icon
        PopupMenu menu = new PopupMenu(appName + " Menu");

        final MenuItem showMenuItem = new MenuItem("Hide");
        showMenuItem.setActionCommand("Hide/Show");
        menu.add(showMenuItem);

        final MenuItem startMenuItem = new MenuItem("Start");
        startMenuItem.setActionCommand("Start");
        menu.add(startMenuItem);

        final MenuItem stopMenuItem = new MenuItem("Stop");
        stopMenuItem.setActionCommand("Stop");
        menu.add(stopMenuItem);

        final MenuItem browserMenuItem = new MenuItem("Launch Admin");
        browserMenuItem.setActionCommand("Launch Admin");
        menu.add(browserMenuItem);

        menu.addSeparator();

        final MenuItem quitMenuItem = new MenuItem("Quit");
        quitMenuItem.setActionCommand("Quit");
        menu.add(quitMenuItem);

        browserButton.setEnabled(false);
        stopButton.setEnabled(false);
        browserMenuItem.setEnabled(false);
        stopMenuItem.setEnabled(false);

        ActionListener actionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ("Start".equals(e.getActionCommand())) {
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    // Adjust button and menu items.
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    startMenuItem.setEnabled(false);
                    stopMenuItem.setEnabled(true);

                    // Startup Application
                    startApplication();

                    // Change to the "on" icon.
                    frame.setIconImage(onIcon.getImage());
                    trayIcon.setImage(onIcon.getImage());

                    // Start a thread to enable the admin button after 8 seconds.
                    Thread thread = new Thread() {
                        @Override
						public void run() {
                            try {
                                sleep(8000);
                            }
                            catch (InterruptedException ie) {
                                // Ignore.
                            }
                            // Enable the Launch Admin button/menu item only if the
                            // server has started.
                            if (stopButton.isEnabled()) {
                                browserButton.setEnabled(true);
                                browserMenuItem.setEnabled(true);
                                frame.setCursor(Cursor.getDefaultCursor());
                            }
                        }
                    };
                    thread.start();
                }
                else if ("Stop".equals(e.getActionCommand())) {
                    stopApplication();
                    // Change to the "off" button.
                    frame.setIconImage(offIcon.getImage());
                    trayIcon.setImage(offIcon.getImage());
                    // Adjust buttons and menu items.
                    frame.setCursor(Cursor.getDefaultCursor());
                    browserButton.setEnabled(false);
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    browserMenuItem.setEnabled(false);
                    startMenuItem.setEnabled(true);
                    stopMenuItem.setEnabled(false);
                }
                else if ("Launch Admin".equals(e.getActionCommand())) {
                    launchBrowser();
                } else if ("Quit".equals(e.getActionCommand())) {
                    stopApplication();
                    System.exit(0);
                }
                else if ("Hide/Show".equals(e.getActionCommand()) || "PressAction".equals(e.getActionCommand())) {
                    toggleVisibility(showMenuItem);
                }
            }
        };

        // Register a listener for the radio buttons.
        startButton.addActionListener(actionListener);
        stopButton.addActionListener(actionListener);
        browserButton.addActionListener(actionListener);
        quitButton.addActionListener(actionListener);

        // Register a listener for the menu items.
        quitMenuItem.addActionListener(actionListener);
        browserMenuItem.addActionListener(actionListener);
        stopMenuItem.addActionListener(actionListener);
        startMenuItem.addActionListener(actionListener);
        showMenuItem.addActionListener(actionListener);

        // Set the system tray icon with the menu
        trayIcon = new TrayIcon(offIcon.getImage(), appName, menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(actionListener);
        trayIcon.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Left click
                if (e.getButton() == 1) {
                    toggleVisibility(showMenuItem);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
        if (tray != null) {
            tray.add(trayIcon);
        }

        frame.addWindowListener(new WindowAdapter() {
            @Override
			public void windowClosing(WindowEvent e) {
                stopApplication();
                System.exit(0);
            }

            @Override
			public void windowIconified(WindowEvent e) {
                // Make the window disappear when minimized
                frame.setVisible(false);
                showMenuItem.setLabel("Show");
            }
        });

        cardPanel.add("main", splashLabel);
        frame.getContentPane().add(mainPanel, BorderLayout.CENTER);
        frame.pack();

        frame.setSize(400, 300);
        frame.setResizable(true);

        GraphicUtils.centerWindowOnScreen(frame);

        frame.setVisible(true);

        // Setup command area
        final ImageIcon icon = new ImageIcon(getClass().getClassLoader().getResource("splash2.gif"));
        pane = new DroppableTextPane() {
            @Override
			public void paintComponent(Graphics g) {
                final Dimension size = pane.getSize();

                int x = (size.width - icon.getIconWidth()) / 2;
                int y = (size.height - icon.getIconHeight()) / 2;
                //  Approach 1: Dispaly image at at full size
                g.setColor(Color.white);
                g.fillRect(0, 0, size.width, size.height);
                g.drawImage(icon.getImage(), x, y, null);


                setOpaque(false);
                super.paintComponent(g);
            }

            @Override
			public void fileDropped(File file) {
                String fileName = file.getName();
                if (fileName.endsWith(".jar") || fileName.endsWith(".war")) {
                    installPlugin(file);
                }
            }
        };

        pane.setEditable(false);

        final JPanel bevelPanel = new JPanel();
        bevelPanel.setBackground(Color.white);
        bevelPanel.setLayout(new BorderLayout());
        bevelPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        bevelPanel.add(new JScrollPane(pane), BorderLayout.CENTER);
        cardPanel.add("running", bevelPanel);

        // Start the app.
        startButton.doClick();
    }

    /**
     * Creates a new GUI launcher instance.
     */
    public static void main(String[] args) throws AWTException {
        new Launcher();
    }

    private void toggleVisibility(MenuItem showMenuItem) {
        // Hide/Unhide the window if the user clicked in the system tray icon or
        // selected the menu option
        if (frame.isVisible()) {
            frame.setVisible(false);
            showMenuItem.setLabel("Show");
        } else {
            frame.setVisible(true);
            frame.setState(Frame.NORMAL);
            showMenuItem.setLabel("Hide");
        }
    }

    private synchronized void startApplication() {
        if (openfired == null) {
            try {
                File windowsExe = new File(binDir, "openfired.exe");
                File unixExe = new File(binDir, "openfired");
                if (windowsExe.exists()) {
                    openfired = Runtime.getRuntime().exec(new String[]{windowsExe.toString()});
                }
                else if (unixExe.exists()) {
                    openfired = Runtime.getRuntime().exec(new String[]{unixExe.toString()});
                }
                else {
                    throw new FileNotFoundException();
                }
            }
            catch (Exception e) {
                // Try one more time using the jar and hope java is on the path
                try {
                    File libDir = new File(binDir.getParentFile(), "lib").getAbsoluteFile();
                    openfired = Runtime.getRuntime().exec(new String[]{
                        "java", "-jar", new File(libDir, "startup.jar").toString()
                    });
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            "Launcher could not start,\n" + appName,
                            "File not found", JOptionPane.ERROR_MESSAGE);
                }
            }

            final SimpleAttributeSet styles = new SimpleAttributeSet();
            SwingWorker<String, Void> inputWorker = new SwingWorker<String, Void>() {
                @Override
				public String doInBackground() {
                    if (openfired != null) {
                        // Get the input stream and read from it
                        try (InputStream in = openfired.getInputStream()) {
                            int c;
                            while ((c = in.read()) != -1) {
                                try {
                                    StyleConstants.setFontFamily(styles, "courier new");
                                    pane.getDocument().insertString(pane.getDocument().getLength(),
                                            "" + (char)c, styles);
                                }
                                catch (BadLocationException e) {
                                    // Ignore.
                                }
                            }
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    return "ok";
                }
            };
            inputWorker.execute();


            SwingWorker<String, Void> errorWorker = new SwingWorker<String, Void>() {
                @Override
				public String doInBackground() {
                    if (openfired != null) {
                        // Get the input stream and read from it
                        try (InputStream in = openfired.getErrorStream()) {
                            int c;
                            while ((c = in.read()) != -1) {
                                try {
                                    StyleConstants.setForeground(styles, Color.red);
                                    pane.getDocument().insertString(pane.getDocument().getLength(), "" + (char)c, styles);
                                }
                                catch (BadLocationException e) {
                                    // Ignore.
                                }
                            }
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    return "ok";
                }
            };
            errorWorker.execute();

            if (freshStart) {
                try {
                    Thread.sleep(1000);
                    cardLayout.show(cardPanel, "running");
                }
                catch (Exception ex) {
                    // Ignore.
                }
                freshStart = false;
            }
            else {
                 // Clear Text
                pane.setText("");
                cardLayout.show(cardPanel, "running");
            }

        }
    }

    private synchronized void stopApplication() {
        if (openfired != null) {
            try {
            	// attempt to perform a graceful shutdown by sending
            	// an "exit" command to the process (via stdin)
                try (Writer out = new OutputStreamWriter(
                        new BufferedOutputStream(openfired.getOutputStream()))) {
                    out.write("exit\n");
                }
                final Thread waiting = Thread.currentThread();
            	Thread waiter = new Thread() {
            		@Override
            		public void run() {
                        try {
                        	// wait for the openfire server to stop
                        	openfired.waitFor();
                        	waiting.interrupt();
                        }
                        catch (InterruptedException ie) { /* ignore */ }
            		}
            	};
            	waiter.start();
            	try {
            		// wait for a maximum of ten seconds
            		Thread.sleep(10000);
            		waiter.interrupt();
            		openfired.destroy();
            	}
            	catch (InterruptedException ie) { /* ignore */ }
                cardLayout.show(cardPanel, "main");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        openfired = null;
    }

    private synchronized void launchBrowser() {
        try {
            // Note, we use standard DOM to read in the XML. This is necessary so that
            // Launcher has fewer dependencies.
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document document = factory.newDocumentBuilder().parse(configFile);
            Element rootElement = document.getDocumentElement();
            Element adminElement = (Element)rootElement.getElementsByTagName("adminConsole").item(0);
            String port = "-1";
            String securePort = "-1";
            Element portElement = (Element)adminElement.getElementsByTagName("port").item(0);
            if (portElement != null) {
                port = portElement.getTextContent();
            }
            Element securePortElement = (Element)adminElement.getElementsByTagName("securePort").item(0);
            if (securePortElement != null) {
                securePort = securePortElement.getTextContent();
            }
            if ("-1".equals(port)) {
                Desktop.getDesktop().browse(URI.create("https://127.0.0.1:" + securePort + "/index.html"));
            } else {
                Desktop.getDesktop().browse(URI.create("http://127.0.0.1:" + port + "/index.html"));
            }
        }
        catch (Exception e) {
            // Make sure to print the exception
            e.printStackTrace(System.out);
            JOptionPane.showMessageDialog(new JFrame(), configFile + " " + e.getMessage());
        }
    }

    private void installPlugin(final File plugin) {
        final JDialog dialog = new JDialog(frame, "Installing Plugin", true);
        dialog.getContentPane().setLayout(new BorderLayout());
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setString("Installing Plugin.  Please wait...");
        bar.setStringPainted(true);
        dialog.getContentPane().add(bar, BorderLayout.CENTER);
        dialog.pack();
        dialog.setSize(225, 55);

        final SwingWorker<File, Void> installerThread = new SwingWorker<File, Void>() {
            @Override
			public File doInBackground() {
                File pluginsDir = new File(binDir.getParentFile(), "plugins");
                String tempName = plugin.getName() + ".part";
                File tempPluginsFile = new File(pluginsDir, tempName);

                File realPluginsFile = new File(pluginsDir, plugin.getName());

                // Copy Plugin into Dir.
                try {
                    // Just for fun. Show no matter what for two seconds.
                    Thread.sleep(2000);

                    copy(plugin.toURI().toURL(), tempPluginsFile);

                    // If successfull, rename to real plugin name.
                    tempPluginsFile.renameTo(realPluginsFile);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return realPluginsFile;
            }

            @Override
			public void done() {
                dialog.setVisible(false);
            }
        };

        // Start installation
        installerThread.execute();

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private static void copy(URL src, File dst) throws IOException {
        try (InputStream in = src.openStream()) {
            try (OutputStream out = new FileOutputStream(dst)) {
                dst.mkdirs();
                copy(in, out);
            }
        }
    }

    /**
     * Common code for copy routines.  By convention, the streams are
     * closed in the same method in which they were opened.  Thus,
     * this method does not close the streams when the copying is done.
     */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        while (true) {
            int bytesRead = in.read(buffer);
            if (bytesRead < 0) {
                break;
            }
            out.write(buffer, 0, bytesRead);
        }
    }
}