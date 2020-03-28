package org.jivesoftware.util;

/**
 * 
 * A <code>Runnable</code> that can be used in <code>OrderedExecutor</code>. It
 * helps in ordering the execution of tasks in sequence, if there are tasks
 * being executed, having the same ordering key.
 * 
 * @author <a href="mailto:renjithalexander@gmail.com">Renjith Alexander</a>
 */
public interface OrderedRunnable extends Runnable {

	/**
	 * The key that is used to group and order tasks. The tasks that share the same
	 * ordering key will be executed in series, in the order that they were
	 * submitted into the <code>OrderedExecutor</code> than in parallel.
	 * 
	 * @return the ordering key for this task.
	 */
	Object getOrderingKey();

}
