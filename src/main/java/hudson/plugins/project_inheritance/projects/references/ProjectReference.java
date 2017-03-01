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
package hudson.plugins.project_inheritance.projects.references;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.util.FormValidation;

public class ProjectReference extends SimpleProjectReference {
	public static class PrioMap {
		public final int parameterPriority;
		public final int buildWrapperPriority;
		public final int builderPriority;
		public final int publisherPriority;
		public final int miscPriority;
		
		public PrioMap(int... prio) {
			if (prio.length != 5) {
				throw new IllegalArgumentException("PrioMap expected 5 values");
			}
			parameterPriority = prio[0];
			buildWrapperPriority = prio[1];
			builderPriority = prio[2];
			publisherPriority = prio[3];
			miscPriority = prio[4];
		}
	}
	
	public final PrioMap prioMap;
	
	@DataBoundConstructor
	public ProjectReference(String targetJob,
			int parameterPriority, int buildWrapperPriority,
			int builderPriority, int publisherPriority, int miscPriority) {
		super(targetJob);
		this.prioMap = new PrioMap(
				parameterPriority, buildWrapperPriority,
				builderPriority, publisherPriority, miscPriority
		);
	}
	
	/**
	 * Usability constructor, in case all priorities are identical
	 */
	public ProjectReference(String targetJob, int priority) {
		this(targetJob, priority, priority, priority, priority, priority);
	}
	
	
	
	
	// === GUI ACCESS METHODS ===
	
	public int getParameterPriority() {
		return this.prioMap.parameterPriority;
	}
	
	public int getBuildWrapperPriority() {
		return this.prioMap.buildWrapperPriority;
	}
	
	public int getBuilderPriority() {
		return this.prioMap.builderPriority;
	}
	
	public int getPublisherPriority() {
		return this.prioMap.publisherPriority;
	}
	
	public int getMiscPriority() {
		return this.prioMap.miscPriority;
	}
	
	
	
	// === COMPARATOR IMPLEMENTATIONS ===
	
	public static class PrioComparator implements Comparator<AbstractProjectReference>  {
		public static enum SELECTOR {
			PARAMETER, BUILD_WRAPPER, BUILDER, PUBLISHER, MISC
		}
		private final SELECTOR sel;
		
		public PrioComparator(SELECTOR sel) {
			this.sel = sel;
		}

		public int compare(AbstractProjectReference o1, AbstractProjectReference o2) {
			return this.getPrio(o1).compareTo(this.getPrio(o2));
		}
		
		private Integer getPrio(AbstractProjectReference ref) {
			if (!(ref instanceof ProjectReference)) {
				return 0;
			}
			ProjectReference pRef = (ProjectReference) ref; 
			switch (sel) {
				case PARAMETER:
					return pRef.getParameterPriority();
				case BUILD_WRAPPER:
					return pRef.getBuildWrapperPriority();
				case BUILDER:
					return pRef.getBuilderPriority();
				case PUBLISHER:
					return pRef.getPublisherPriority();
				case MISC:
					return pRef.getMiscPriority();
				default:
					throw new IllegalArgumentException(
							"Invalid priority selector"
					);
			}
		}
	
		public static int getPriorityFor(AbstractProjectReference ref, SELECTOR sel) {
			if (!(ref instanceof ProjectReference)) {
				return 0;
			}
			ProjectReference pRef = (ProjectReference) ref;
			switch (sel) {
				case BUILD_WRAPPER:
					return pRef.getBuildWrapperPriority();
				case BUILDER:
					return pRef.getBuilderPriority();
				case PARAMETER:
					return pRef.getParameterPriority();
				case PUBLISHER:
					return pRef.getPublisherPriority();
				case MISC:
					return pRef.getMiscPriority();
				default:
					//TODO: Log this error
					return 0;
			}
		}
	
		public static List<AbstractProjectReference> getSortedCopy(
				List<AbstractProjectReference> in, SELECTOR sel) {
			LinkedList<AbstractProjectReference> sortRefs = 
					new LinkedList<AbstractProjectReference>(in);
			Collections.sort(
					sortRefs, new PrioComparator(sel)
			);
			return sortRefs;
		}
	}
	
	
	
	// === DESCRIPTOR MEMBERS AND CLASSES ===

	
	@Extension
	public static class OrderedProjectReferenceDescriptor extends SimpleProjectReferenceDescriptor {
		private static final String PRIO_ERROR =
				"Please enter a valid priority. Negative numbers" +
				" mean execute before, positive numbers (incl. 0) after " +
				" this project's entries.";
		
		@Override
		public String getDisplayName() {
			return Messages.ProjectReference_DisplayName();
		}
		
		
		public static FormValidation isNumber(String val, String errMsg) {
			try {
				Integer.parseInt(val);
				return FormValidation.ok();
			} catch (NumberFormatException ex) {
				return FormValidation.error(errMsg);
			}
		}
		
		public FormValidation doCheckParameterPriority(
				@QueryParameter String parameterPriority) {
			return isNumber(parameterPriority, PRIO_ERROR);
		}
		
		public FormValidation doCheckBuildWrapperPriority(
				@QueryParameter String buildWrapperPriority) {
			return isNumber(buildWrapperPriority, PRIO_ERROR);
		}
		
		public FormValidation doCheckBuilderPriority(
				@QueryParameter String builderPriority) {
			return isNumber(builderPriority, PRIO_ERROR);
		}
		
		public FormValidation doCheckPublisherPriority(
				@QueryParameter String publisherPriority) {
			return isNumber(publisherPriority, PRIO_ERROR);
		}
		
		public FormValidation doCheckMiscPriority(
				@QueryParameter String miscPriority) {
			return isNumber(miscPriority, PRIO_ERROR);
		}
	}
}

