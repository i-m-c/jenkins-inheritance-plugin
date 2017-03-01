/**
 * Copyright (c) 2015-2017, Intel Deutschland GmbH
 * Copyright (c) 2011-2015, Intel Mobile Communications GmbH
 *
 * This file is part of the Inheritance plug-in for Jenkins.
 *
 * The Inheritance plug-in is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation in version 3
 * of the License
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package hudson.plugins.project_inheritance.utils;

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

