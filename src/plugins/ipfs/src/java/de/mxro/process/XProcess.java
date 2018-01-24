package de.mxro.process;

/**
 * A wrapper for {@link java.lang.Process}.
 * 
 * @author <a href="http://www.mxro.de/">Max Rohde</a>
 * 
 */
public interface XProcess {

	/**
	 * Call to push the specified {@link String} into the started processes
	 * input.
	 * 
	 * @param line
	 */
	public void sendLine(String line);

	/**
	 * Try to destroy the process
	 */
	public void destory();

}
