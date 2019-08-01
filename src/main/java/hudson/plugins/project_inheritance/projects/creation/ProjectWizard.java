/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2017 Intel Deutschland GmbH
 */
package hudson.plugins.project_inheritance.projects.creation;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.plugins.project_inheritance.projects.InheritableProjectsCategory;
import hudson.plugins.project_inheritance.util.MockItemGroup;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * This class is responsible for adding a "Wizard" functionality to the
 * "New Item" page, that expands on the "Copy from other Job" feature.
 * <p>
 * It basically fulfils the same purpose, with the sole exception that the
 * selection of jobs is limited to an administrator-filtered list of "template"
 * projects.
 * <p>
 * These projects get extended with some short descriptions and maybe additional
 * variable fields. When the user then creates the job, a new job is created
 * by copying the Template and substituting the variables in the XML that is
 * to be copied.
 * 
 * @author mhschroe
 *
 */
public class ProjectWizard extends MockItemGroup<Job<?,?>> {

	public ProjectWizard() {
		//Nothing to do -- not a real project
	}

	
	// === DESCRIPTOR METHODS ===
	
	@Override
	public ProjectWizardDescriptor getDescriptor() {
		return DESCRIPTOR;
	}
	
	@Extension
	public static final ProjectWizardDescriptor DESCRIPTOR = new ProjectWizardDescriptor();
	
	public static class ProjectWizardDescriptor extends TopLevelItemDescriptor {

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicableIn(ItemGroup parent) {
			//Check if there are any templates defined -- if not, no reason to
			//add this option
			return !ProjectCreationEngine.instance.getTemplates().isEmpty();
		}
		
		@SuppressWarnings("rawtypes")
		@Override
		public TopLevelItem newInstance(ItemGroup parent, String name) {
			//Retrieve the template name, if any from the current request
			StaplerRequest req = Stapler.getCurrentRequest();
			if (req == null) { return null; }
			final String templateName;
			try {
				JSONObject form = req.getSubmittedForm();
				if (form == null || !form.has("templateName")) { return null; }
				templateName = form.getString("templateName");
			} catch (ServletException ex) {
				return null;
			}
			
			//Check if the template exists, and retrieve fitting project
			AbstractProject<?, ?> project = null;
			List<ProjectTemplate> templates =
					ProjectCreationEngine.instance.getTemplates();
			for (ProjectTemplate template : templates) {
				if (!template.getName().equals(templateName)) { continue; }
				//Template found
				AbstractProject<?, ?> p = template.getProject();
				if (p == null) { return null; }
				project = p;
				break;
			}
			
			//Use Jenkins default behaviour to copy the job
			try {
				return (TopLevelItem) Jenkins.get().copy(project, name);
			} catch (IOException e) {
				return null;
			}
		}
		
		@Override
		public String getDisplayName() {
			return Messages.ProjectWizard_DisplayName();
		}
		
		@Override
		public String getDescription() {
			//Use super(), because description is set in newInstanceDetail.groovy
			return super.getDescription();
		}
		
		@Override
		public String getCategoryId() {
			return InheritableProjectsCategory.ID;
		}
		
		@Override
		public String getIconClassName() {
			return "icon-wizard-project";
		}
		
		static {
			IconSet.icons.addIcon(new Icon("icon-wizard-project icon-sm", "plugin/project-inheritance/images/16x16/tools-wizard.png", Icon.ICON_SMALL_STYLE));
			IconSet.icons.addIcon(new Icon("icon-wizard-project icon-md", "plugin/project-inheritance/images/24x24/tools-wizard.png", Icon.ICON_MEDIUM_STYLE));
			IconSet.icons.addIcon(new Icon("icon-wizard-project icon-lg", "plugin/project-inheritance/images/32x32/tools-wizard.png", Icon.ICON_LARGE_STYLE));
			IconSet.icons.addIcon(new Icon("icon-wizard-project icon-xlg", "plugin/project-inheritance/images/48x48/tools-wizard.png", Icon.ICON_XLARGE_STYLE));
		}
		
		// === GROOVY METHODS ===
		
		public ListBoxModel doFillNameItems() {
			ListBoxModel lbm = new ListBoxModel();
			
			//Get the global PCE config
			ProjectCreationEngine pce = ProjectCreationEngine.instance;
			for (ProjectTemplate t : pce.getTemplates()) {
				//Check if that template is valid
				if (t.getProject() != null) {
					lbm.add(t.toString(), t.getName());
				}
			}
			
			//Make sure the first one is selected
			if (!lbm.isEmpty()) {
				lbm.get(0).selected = true;
			}
			
			return lbm;
		}
	}
}
