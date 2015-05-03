package utils;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import org.apache.commons.io.output.NullOutputStream;

import hudson.console.ConsoleNote;
import hudson.model.TaskListener;

public class DummyListener implements TaskListener {
	private static final long serialVersionUID = -1506720486825690288L;

	private static final PrintWriter nullWriter = new PrintWriter(new NullOutputStream());
	
	public DummyListener() { }

	@Override
	public PrintStream getLogger() {
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void annotate(ConsoleNote ann) throws IOException {}

	@Override
	public void hyperlink(String url, String text) throws IOException {}

	@Override
	public PrintWriter error(String msg) {
		return nullWriter;
	}

	@Override
	public PrintWriter error(String format, Object... args) {
		return nullWriter;
	}

	@Override
	public PrintWriter fatalError(String msg) {
		return nullWriter;
	}

	@Override
	public PrintWriter fatalError(String format, Object... args) {
		return nullWriter;
	}

}

