package de.mxro.process;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import de.mxro.process.internal.Engine;

public class Spawn {

    /**
     * Start a new process.
     * 
     * @param command
     * @param listener
     * @param folder
     * @return
     */
    public static XProcess startProcess(final String command, final File folder, final ProcessListener listener) {
        return startProcess(command.split(" "), folder, listener);
    }

    public static XProcess startProcess(final String[] command, final File folder, final ProcessListener listener) {
        return Engine.startProcess(command, listener, folder);
    }

    public static String runCommand(final String command, final File folder) {
        return runCommand(command.split(" "), folder);
    }

    /**
     * Runs a command in a new process and stops the process thereafter.
     * 
     * @param command
     * @param folder
     */
    public static String runCommand(final String[] command, final File folder) {

        final CountDownLatch latch = new CountDownLatch(2);

        final List<Throwable> exceptions = Collections.synchronizedList(new LinkedList<Throwable>());

        final List<String> output = Collections.synchronizedList(new LinkedList<String>());

        latch.countDown();
        final XProcess process = startProcess(command, folder, new ProcessListener() {

            @Override
            public void onProcessQuit(final int returnValue) {
                latch.countDown();
            }

            @Override
            public void onOutputLine(final String line) {
                output.add(line);
            }

            @Override
            public void onErrorLine(final String line) {
                output.add(line);
            }

            @Override
            public void onError(final Throwable t) {
                exceptions.add(t);
            }
        });

        try {
            latch.await();
            // Thread.sleep(300); // just wait for input to gobble in
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (exceptions.size() > 0) {
            throw new RuntimeException(exceptions.get(0));
        }

        final StringBuilder sb = new StringBuilder();
        for (final String line : new ArrayList<String>(output)) {
            sb.append(line + "\n");
        }

        process.destory();
        return sb.toString();
    }

    public interface Callback<Type> {
        public void onDone(Type t);
    }

    /**
     * Runs bash scripts (*.sh) in a UNIX environment.
     * 
     * @param bashScriptFile
     * @return
     */
    public static String runBashScript(final File bashScriptFile) {
        return runCommand("/bin/bash -c " + bashScriptFile.getAbsolutePath(), bashScriptFile.getParentFile());
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    public static String sh(final File folder, final String bashCommand) {

        if (!isWindows()) {
            return runCommand(new String[] { "/bin/bash", "-c", bashCommand }, folder);
        } else {
            return runCommand(new String[] { "cmd.exe", "/C", bashCommand }, folder);
        }
    }

    /**
     * Executes a bash command.
     * 
     * @param bashCommand
     * @return
     */
    public static String sh(final String bashCommand) {
        return sh(null, bashCommand);
    }

}
