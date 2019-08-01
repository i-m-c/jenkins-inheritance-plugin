/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2017 Intel Deutschland GmbH
 */
package hudson.plugins.project_inheritance.projects.creation;

import java.io.Serializable;
import java.lang.ref.WeakReference;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class ProjectTemplate implements Describable<ProjectTemplate>, Serializable {
	private static final long serialVersionUID = 7940442135531816851L;
	
	private final String name;
	private final String shortDescription;
	
	private transient WeakReference<AbstractProject<?, ?>> project;
	
	@DataBoundConstructor
	public ProjectTemplate(String name, String shortDescription) {
		this.name = name;
		this.shortDescription = shortDescription;
	}
	
	@Override
	public String toString() {
		return String.format("%s - %s", name, shortDescription);
	}
	
	public AbstractProject<?, ?> getProject() {
		AbstractProject<?, ?> p;
		if (project != null) {
			p = project.get();
			if (p != null) { return p; }
		}
		p = Jenkins.get().getItemByFullName(name, AbstractProject.class);
		if (p == null) { return null; }
		
		project = new WeakReference<AbstractProject<?,?>>(p);
		return p;
	}
	
	
	public String getName() {
		return this.name;
	}
	
	public String getShortDescription() {
		return this.shortDescription;
	}
	
	
	
	// === DESCRIPTOR CLASS AND FIELDS ===
	
	@Override
	public Descriptor<ProjectTemplate> getDescriptor() {
		return DESCRIPTOR;
	}

	@Extension
	public static final ProjectTemplateDescriptor DESCRIPTOR =
			new ProjectTemplateDescriptor();
	
	public static class ProjectTemplateDescriptor extends Descriptor<ProjectTemplate> {
		
		public ListBoxModel doFillNameItems() {
			ListBoxModel lbm = new ListBoxModel();
			
			for (AbstractProject<?,?> p : Jenkins.get().getItems(AbstractProject.class)) {
				lbm.add(p.getFullName(), p.getFullName());
			}
			
			return lbm;
		}
	}
}
