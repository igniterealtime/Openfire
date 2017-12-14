package org.ifsoft.nodejs.openfire;

import java.net.*;
import java.io.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeThread implements Runnable {

    private static final Logger Log = LoggerFactory.getLogger(NodeThread.class);

    private Thread thread = null;
    private Process nodeProcess = null;
    private BufferedReader input = null;
    private BufferedReader error = null;


    public NodeThread()
    {

    }


    public void start(String path, File dir) {

        stopThread();

        try {
            nodeProcess = Runtime.getRuntime().exec(path, null, dir);
            Log.info("Started Node");

            input = new BufferedReader (new InputStreamReader(nodeProcess.getInputStream()));
            error = new BufferedReader (new InputStreamReader(nodeProcess.getErrorStream()));
            Log.info("Started Node Console Reader");

        } catch (Exception e) {
            Log.info("Started Node exception " + e);
        }


        // All ok: start a receiver thread
        thread = new Thread(this);
        thread.start();
    }

    public void run() {
        Log.info("Start run()");

        // Get events while we're alive.
        while (thread != null && thread.isAlive()) {

            try {

                String line = input.readLine();

                while (line != null) {
                    Log.info(line);
                    line = input.readLine();
                }

                line = error.readLine();

                while (line != null) {
                    Log.error(line);
                    line = error.readLine();
                }

                Thread.sleep(500);

            } catch (Throwable t) {

            }

        }
    }

    public void stop() {

        Log.info("Stopped Node");

        nodeProcess.destroy();
        stopThread();
    }

    public void stopThread() {
        Log.info("In stopThread()");

        // Keep a reference such that we can kill it from here.
        Thread targetThread = thread;

        thread = null;

        // This should stop the main loop for this thread.
        // Killing a thread on a blcing read is tricky.
        // See also http://gee.cs.oswego.edu/dl/cpj/cancel.html
        if ((targetThread != null) && targetThread.isAlive()) {

            targetThread.interrupt();

            try {

                // Wait for it to die
                targetThread.join(500);
            }
            catch (InterruptedException ignore) {
            }

            // If current thread refuses to die,
            // take more rigorous methods.
            if (targetThread.isAlive()) {

                // Not preferred but may be needed
                // to stop during a blocking read.
                targetThread.stop();

                // Wait for it to die
                try {
                    targetThread.join(500);
                }
                catch (InterruptedException ignore) {
                }
            }

            Log.info("Stopped thread alive=" + targetThread.isAlive());

        }
    }

}
