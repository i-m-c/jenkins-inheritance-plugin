/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2015 Intel Deutschland GmbH
 * Copyright (c) 2015 Intel Mobile Communications GmbH
 */
package hudson.plugins.project_inheritance.projects.causes;

import java.util.Objects;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import hudson.model.Run;
import hudson.model.Cause.UserIdCause;
import hudson.util.XStream2;
import jenkins.model.Jenkins;

/**
 * Wrapper around {@link UserIdCause} that behaves exactly like it and
 * merely serves as a marker for whether or not a run started by this cause
 * should be considered a reference run or not.
 * <p>
 * As such, the only method it overwrites is the getUserName() method, to
 * signify that it has been run as a reference, without having to alter the
 * other messages and descriptions.
 * 
 * @author Martin Schroeder
 */
public class ReferenceUserIdCause extends UserIdCause {
	public ReferenceUserIdCause() {
		super();
	}
	
	@Initializer(before = InitMilestone.PLUGINS_PREPARED)
	public static void compatibilityAlias() {
		XStream2[] xs = { Jenkins.XSTREAM2, Run.XSTREAM2, Items.XSTREAM2 };
		for (XStream2 x : xs) {
			x.addCompatibilityAlias("hudson.plugins.job_dependency.projects.causes.ReferenceUserIdCause",
					ReferenceUserIdCause.class);
		}
	}

	/**
	 * Get the unique username string that identifies the build was a reference
	 * cause
	 */
	public String getUserName() {
		String orig = super.getUserName();
		return String.format("%s (Reference)", orig);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		//Multiply by random prime number to provide uniqueness against
		//UserIdCause
		return 23 * Objects.hash(this.getUserId());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		return o instanceof ReferenceUserIdCause && Objects.equals(this.getUserId(), ((ReferenceUserIdCause) o).getUserId());
	}
}
