/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2017 Intel Deutschland GmbH
 */
package hudson.plugins.project_inheritance.projects;

import hudson.Extension;
import jenkins.model.item_category.ItemCategory;

/**
 * Designed for projects which derive from {@link InheritanceProject} class
 * and offer features to inherit properties between classes.
 *
 * @since 2.1
 */
@Extension(ordinal=500)
public class InheritableProjectsCategory extends ItemCategory {
	public static final String ID = "inheritable-projects";
	
	@Override
	public String getId() {
		return ID;
	}
	
	@Override
	public String getDisplayName() {
		return Messages.InheritanceProjectsCategory_DisplayName();
	}
	
	@Override
	public String getDescription() {
		return Messages.InheritanceProjectsCategory_Description();
	}
	
	@Override
	public int getMinToShow() {
		return 1;
	}
}
