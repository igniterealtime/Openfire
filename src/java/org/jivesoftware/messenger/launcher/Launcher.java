package org.jivesoftware.messenger.launcher;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.XMLProperties;
import org.jivesoftware.messenger.JiveGlobals;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import javax.swing.*;


/**
 * Launcher for Jive Messenger.
 *
 * @author Matt Tucker
 */
public class Launcher {
    private Process messengerd = null;
    private String configFile = JiveGlobals.getJiveHome() + File.separator + "config" +
            File.separator + "jive-messenger.xml";
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

        // If setup is being run for the first time and we are using JDK 1.3,
        // copy optional libs into the official lib directory.
        if (isJDK13() && !this.isSetupDone()) {
            try {
                File optDir = new File(JiveGlobals.getJiveHome() + File.separator + "optional" +
                        File.separator + "jdk1.3");
                File libDir = new File(JiveGlobals.getJiveHome() + File.separator + "lib");

                if (optDir.exists() && libDir.exists()) {
                    File[] files = optDir.listFiles(new FileFilter() {
                        public boolean accept(File pathname) {
                            return pathname.getName().endsWith(".jar");
                        }
                    });

                    for (int i = 0; i < files.length; i++) {
                        File source = files[i];
                        File destination = new File(libDir, source.getName());

                        if (!destination.exists()) {
                            InputStream in = null;
                            OutputStream out = null;

                            try {
                                in = new BufferedInputStream(new FileInputStream(source));
                                out = new BufferedOutputStream(new FileOutputStream(destination));

                                byte[] buf = new byte[1024];
                                int len;

                                while ((len = in.read(buf)) >= 0) {
                                    out.write(buf, 0, len);
                                }
                            }
                            catch (Exception e) {
                            }
                            finally {
                                try {
                                    in.close();
                                }
                                catch (Exception e2) {
                                    Log.error(e2);
                                }

                                try {
                                    out.close();
                                }
                                catch (Exception e3) {
                                    Log.error(e3);
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
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
            File exe = null;

            try {
                // Aliases keep their cwd rather than the aliased binDir's cwd on MacOS X
                // so we'll do a search for messengerd rather than relying on it being where
                // we think it will be...
                binDir = new File("").getAbsoluteFile();

                if (!"bin".equals(binDir.getName())) {
                    binDir = new File(binDir, "bin");
                }

                if ("bin".equals(binDir.getName())) {
                    // Windows
                    exe = new File(binDir, "messengerd.exe");

                    if (exe.exists()) {
                        messengerd = Runtime.getRuntime().exec(new String[]{exe.toString()});
                    }
                    else {
                        // MacOS X
                        exe = new File(binDir, "messengerd.app");

                        if (exe.exists()) {
                            messengerd = Runtime.getRuntime().exec(new String[]{
                                "open", exe.toString()
                            });
                        }
                        else {
                            // Unix
                            exe = new File(binDir, "messengerd");

                            if (exe.exists()) {
                                messengerd = Runtime.getRuntime().exec(new String[]{exe.toString()});
                            }
                            else {
                                throw new FileNotFoundException();
                            }
                        }
                    }
                }
            }
            catch (Exception e) {
                // Try one more time using the jar and hope java is on the path
                try {
                    if (binDir != null) {
                        messengerd = Runtime.getRuntime().exec(new String[]{
                            "java", "-jar", new File(binDir, "messengerd.jar").toString()
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

    private boolean isJDK13() {
        try {
            loadClass("java.nio.Buffer");

            return false;
        }
        catch (Exception e) {
        }

        return true;
    }

    Class loadClass(String className) throws ClassNotFoundException {
        Class theClass = null;

        try {
            theClass = Class.forName(className);
        }
        catch (ClassNotFoundException e1) {
            try {
                theClass = Thread.currentThread().getContextClassLoader().loadClass(className);
            }
            catch (ClassNotFoundException e2) {
                theClass = getClass().getClassLoader().loadClass(className);
            }
        }

        return theClass;
    }

    private synchronized boolean isSetupDone() {
        try {
            XMLProperties props = new XMLProperties(configFile);
            String setup = props.getProperty("setup");

            return Boolean.valueOf(setup).booleanValue();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    private synchronized void launchBrowser() {
        try {
            XMLProperties props = new XMLProperties(configFile);
            String port = props.getProperty("embedded-web.port");
            BrowserLauncher.openURL("http://127.0.0.1:" + port + "/index.jsp");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}