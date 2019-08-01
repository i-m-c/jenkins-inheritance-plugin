/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2017 Intel Deutschland GmbH
 */
package hudson.plugins.project_inheritance.projects.view.scripts;

import java.io.File;

import hudson.plugins.project_inheritance.projects.view.BuildFlowScriptAction;

/**
 * This class encapsulates a script and the metadata surrounding it.
 * 
 * @see BuildFlowScriptAction for how this is turned into a set of real scripts.
 */
public class MetaScript {
	/**
	 * This is the shebang line from the content, all neatly parsed out and
	 * without the leading magic bytes.
	 * <p>
	 * May be emtpy (e.g. for Windows CMD) but should not be null.
	 */
	public final String shebang;
	
	/**
	 * The content of the script file. Line endings must be appropriate for
	 * the target platform.
	 */
	public final String content;
	
	/**
	 * The file path that should be used for the script file.
	 * <p>
	 * This must be a relative path, so that the script archive generator
	 * can properly create the build-flow control files.
	 * <p>
	 * Note that it is not necessary for this path to actually exist.
	 */
	public final File file;
	
	/**
	 * This flag specifies, whether the file ought to be called from a
	 * control file -- or not.
	 * <p>
	 * It is not final, because a script might start out as being callable,
	 * then be "called" by control file and thus turn not callable, to prevent
	 * double-invocation.
	 * <p>
	 * Note that this means that being executable and being callable are not
	 * the same thing. A script can be executed at some point, without being
	 * marked as callable in the end.
	 */
	protected boolean callable;
	
	
	public MetaScript(String shebang, String content, File file) {
		this.shebang = shebang;
		this.content = content;
		this.file = file;
		this.callable = true;
	}
	
	
	public boolean isCallable() {
		return this.callable;
	}
	
	public void setCallable(boolean isCallable) {
		this.callable = isCallable;
	}
}
