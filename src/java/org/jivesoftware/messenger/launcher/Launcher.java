/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.launcher;

import org.jdesktop.jdic.tray.SystemTray;
import org.jdesktop.jdic.tray.TrayIcon;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.WebManager;
import org.jivesoftware.util.XMLProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Graphical launcher for Jive Messenger.
 *
 * @author Matt Tucker
 */
public class Launcher {

    private Process messengerd;
    private String configFile = JiveGlobals.getHomeDirectory() + File.separator + "conf" + File.separator + "jive-messenger.xml";
    private JPanel toolbar = new JPanel();

    private ImageIcon offIcon;
    private ImageIcon onIcon;
    private TrayIcon trayIcon;
    private JFrame frame;

    /**
     * Creates a new Launcher object.
     */
    public Launcher() {
        // Initialize the SystemTray now (to avoid a bug!)
        final SystemTray tray = SystemTray.getDefaultSystemTray();
        // Use the native look and feel.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        String title = "Jive Messenger";
        frame = new DroppableFrame() {
            public void fileDropped(File file) {
                String fileName = file.getName();
                if (fileName.endsWith(".jar") || fileName.endsWith(".war")) {
                    installPlugin(file);
                }
            }
        };

        frame.setTitle(title);

        ImageIcon splash = null;
        JLabel splashLabel = null;

        // Set the icon.
        try {
            splash = new ImageIcon(getClass().getClassLoader().getResource("splash.gif"));
            splashLabel = new JLabel("", splash, JLabel.LEFT);

            onIcon = new ImageIcon(getClass().getClassLoader().getResource("messenger_on-16x16.gif"));
            offIcon = new ImageIcon(getClass().getClassLoader().getResource("messenger_off-16x16.gif"));
            frame.setIconImage(offIcon.getImage());
        }
        catch (Exception e) {
        }

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());

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
        toolbar.add(startButton, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
        toolbar.add(stopButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
        toolbar.add(browserButton, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
        toolbar.add(quitButton, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));

        if (splashLabel != null) {
            mainPanel.add(splashLabel, BorderLayout.CENTER);
        }

        mainPanel.add(toolbar, BorderLayout.SOUTH);

        // create the main menu of the system tray icon
        JPopupMenu menu = new JPopupMenu("Messenger Menu");

        final JMenuItem showMenuItem = new JMenuItem("Hide");
        showMenuItem.setActionCommand("Hide/Show");
        menu.add(showMenuItem);

        final JMenuItem startMenuItem = new JMenuItem("Start");
        startMenuItem.setActionCommand("Start");
        menu.add(startMenuItem);

        final JMenuItem stopMenuItem = new JMenuItem("Stop");
        stopMenuItem.setActionCommand("Stop");
        menu.add(stopMenuItem);

        final JMenuItem browserMenuItem = new JMenuItem("Launch Admin");
        browserMenuItem.setActionCommand("Launch Admin");
        menu.add(browserMenuItem);

        menu.addSeparator();

        final JMenuItem quitMenuItem = new JMenuItem("Quit");
        quitMenuItem.setActionCommand("Quit");
        menu.add(quitMenuItem);

        browserButton.setEnabled(false);
        stopButton.setEnabled(false);
        browserMenuItem.setEnabled(false);
        stopMenuItem.setEnabled(false);

        ActionListener actionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if ("Start".equals(e.getActionCommand())) {
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    // Adjust button and menu items.
                    startApplication();
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    startMenuItem.setEnabled(false);
                    stopMenuItem.setEnabled(true);
                    // Change to the "on" icon.
                    frame.setIconImage(onIcon.getImage());
                    trayIcon.setIcon(onIcon);
                    // Start a thread to enable the admin button after 8 seconds.
                    Thread thread = new Thread() {
                        public void run() {
                            try {
                                sleep(8000);
                            }
                            catch (Exception e) {
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
                    trayIcon.setIcon(offIcon);
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
                }
                else if ("Quit".equals(e.getActionCommand())) {
                    stopApplication();
                    System.exit(0);
                }
                else if ("Hide/Show".equals(e.getActionCommand()) || "PressAction".equals(e.getActionCommand())) {
                    // Hide/Unhide the window if the user clicked in the system tray icon or
                    // selected the menu option
                    if (frame.isVisible()) {
                        frame.setVisible(false);
                        frame.setState(Frame.ICONIFIED);
                        showMenuItem.setText("Show");
                    }
                    else {
                        frame.setVisible(true);
                        frame.setState(Frame.NORMAL);
                        showMenuItem.setText("Hide");
                    }
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
        trayIcon = new TrayIcon(offIcon, "Jive Messenger", menu);
        trayIcon.setIconAutoSize(true);
        trayIcon.addActionListener(actionListener);

        tray.addTrayIcon(trayIcon);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                stopApplication();
                System.exit(0);
            }

            public void windowIconified(WindowEvent e) {
                // Make the window disappear when minimized
                frame.setVisible(false);
                showMenuItem.setText("Show");
            }
        });

        frame.getContentPane().add(mainPanel);
        frame.pack();
        // frame.setSize(539,418);
        frame.setResizable(false);

        GraphicUtils.centerWindowOnScreen(frame);

        frame.setVisible(true);

        // Start the app.
        startButton.doClick();
    }

    /**
     * Creates a new GUI launcher instance.
     */
    public static void main(String[] args) {
        new Launcher();
    }

    private synchronized void startApplication() {
        if (messengerd == null) {
            try {
                File windowsExe = new File(new File("").getAbsoluteFile(), "messengerd.exe");
                File unixExe = new File(new File("").getAbsoluteFile(), "messengerd");
                if (windowsExe.exists()) {
                    messengerd = Runtime.getRuntime().exec(new String[]{windowsExe.toString()});
                }
                else if (unixExe.exists()) {
                    messengerd = Runtime.getRuntime().exec(new String[]{unixExe.toString()});
                }
                else {
                    throw new FileNotFoundException();
                }
            }
            catch (Exception e) {
                // Try one more time using the jar and hope java is on the path
                try {
                    File libDir = new File("../lib").getAbsoluteFile();
                    messengerd = Runtime.getRuntime().exec(new String[]{
                        "java", "-jar", new File(libDir, "startup.jar").toString()
                    });
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            "Launcher could not start,\nJive Messenger",
                            "File not found", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private synchronized void stopApplication() {
        if (messengerd != null) {
            try {
                messengerd.destroy();
                messengerd.waitFor();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        messengerd = null;
    }

    private synchronized void launchBrowser() {
        try {
            XMLProperties props = new XMLProperties(configFile);
            String port = props.getProperty("adminConsole.port");
            BrowserLauncher.openURL("http://127.0.0.1:" + port + "/index.html");
        }
        catch (Exception e) {
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

        final SwingWorker installerThread = new SwingWorker() {
            public Object construct() {
                File pluginsDir = new File(JiveGlobals.getHomeDirectory(), "plugins");
                String tempName = plugin.getName() + ".part";
                File tempPluginsFile = new File(pluginsDir, tempName);

                File realPluginsFile = new File(pluginsDir, plugin.getName());

                // Copy Plugin into Dir.
                try {
                    // Just for fun. Show no matter what for two seconds.
                    Thread.sleep(2000);

                    WebManager.copy(plugin.toURL(), tempPluginsFile);

                    // If successfull, rename to real plugin name.
                    tempPluginsFile.renameTo(realPluginsFile);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return realPluginsFile;
            }

            public void finished() {
                dialog.setVisible(false);
            }
        };

        // Start installation
        installerThread.start();

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
}

