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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import javax.swing.*;
import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.util.XMLProperties;

/**
 * Launcher for Jive Messenger.
 *
 * @author Matt Tucker
 */
public class Launcher {

    private Process messengerd = null;
    private String configFile = JiveGlobals.getMessengerHome() + File.separator + "conf" + File.separator + "jive-messenger.xml";
    private JPanel toolbar = new JPanel();

    /**
     * Creates a new Launcher object.
     */
    public Launcher() {
        // Use the native look and feel.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        String title = "Jive Messenger Server Launcher";
        final JFrame frame = new JFrame(title);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                stopApplication();
                System.exit(0);
            }
        });

        ImageIcon splash = null;
        JLabel splashLabel = null;

        // Set the icon.
        try {
            ImageIcon icon = new ImageIcon(getClass().getClassLoader().getResource("messenger-16x16.gif"));
            splash = new ImageIcon(getClass().getClassLoader().getResource("splash.gif"));
            splashLabel = new JLabel("", splash, JLabel.LEFT);
            frame.setIconImage(icon.getImage());
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
        browserButton.setEnabled(false);


        ActionListener actionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (e.getActionCommand().equals("Start")) {
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    startApplication();
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    Thread thread = new Thread() {
                        public void run() {
                            try {
                                sleep(8000);
                            }
                            catch (Exception e) {
                            }

                            browserButton.setEnabled(true);

                            frame.setCursor(Cursor.getDefaultCursor());
                        }
                    };

                    thread.start();
                }
                else if (e.getActionCommand().equals("Stop")) {
                    stopApplication();
                    browserButton.setEnabled(false);
                    startButton.setEnabled(true);
                    stopButton.setEnabled(true);
                }
                else if (e.getActionCommand().equals("Launch Admin")) {
                    launchBrowser();
                }
                else if (e.getActionCommand().equals("Quit")) {
                    stopApplication();
                    System.exit(0);
                }
            }
        };

        // Register a listener for the radio buttons.
        startButton.addActionListener(actionListener);
        stopButton.addActionListener(actionListener);
        browserButton.addActionListener(actionListener);
        quitButton.addActionListener(actionListener);

        frame.getContentPane().add(mainPanel);
        frame.pack();
        // frame.setSize(539,418);
        frame.setResizable(false);

        GraphicUtils.centerWindowOnScreen(frame);

        frame.setVisible(true);
    }

    /**
     * DOCUMENT ME!
     *
     * @param args DOCUMENT ME!
     */
    public static void main(String[] args) {
        new Launcher();
    }

    private synchronized void startApplication() {
        if (messengerd == null) {
            File binDir = null;
            File libDir = null;
            File homeDir = null;
            File exe = null;

            try {
                // Aliases keep their cwd rather than the aliased binDir's cwd on MacOS X
                // so we'll do a search for messengerd rather than relying on it being where
                // we think it will be...
                binDir = new File("").getAbsoluteFile();
                libDir = new File("../lib").getAbsoluteFile();
                homeDir = binDir.getParentFile();


                if (libDir.exists()) {
                    messengerd = Runtime.getRuntime().exec(new String[]{"java", "-jar", new File(libDir, "startup.jar").toString()});
                }
                else {
                    // MacOS X
                    exe = new File(homeDir, "messenger.app");

                    if (exe.exists()) {
                        messengerd = Runtime.getRuntime().exec(new String[]{
                            "open", exe.toString()
                        });
                    }
                    else {
                        // Unix
                        exe = new File(homeDir, "messenger");

                        if (exe.exists()) {
                            messengerd = Runtime.getRuntime().exec(new String[]{exe.toString()});
                        }
                        else {
                            throw new FileNotFoundException();
                        }
                    }
                }
            }
            catch (Exception e) {
                // Try one more time using the jar and hope java is on the path
                try {
                    if (binDir != null) {
                        messengerd = Runtime.getRuntime().exec(new String[]{
                            "java", "-jar", new File(libDir, "startup.jar").toString()
                        });
                    }
                    else {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(null,
                                "Launcher could not locate messengerd,\nthe Jive Messenger server daemon executable",
                                "File not found", JOptionPane.ERROR_MESSAGE);
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            "Launcher could not locate messengerd,\nthe Jive Messenger server daemon executable",
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
            String port = props.getProperty("embedded-web.port");
            BrowserLauncher.openURL("http://127.0.0.1:" + port + "/index.html");
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(new JFrame(), configFile + " " + e.getMessage());
        }
    }
}

