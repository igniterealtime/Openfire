package de.mxro.process.internal;

public interface StreamListener {

	public void onOutputLine(String line);

	public void onClosed();

	public void onError(Throwable t);

}
