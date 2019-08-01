/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2017 Intel Deutschland GmbH
 */
package hudson.plugins.project_inheritance.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ParameterizedProjectReference;
import hudson.plugins.project_inheritance.projects.references.SimpleProjectReference;
import jenkins.model.Jenkins;

public class XmlProject {
	public final InheritanceProject project;
	public final String xmlFile;
	
	public XmlProject(String name) throws IOException {
		this(name, null);
	}
	
	public XmlProject(String name, String xmlFile) throws IOException {
		this.xmlFile = xmlFile;
		//Create the project -- optionally from the given XML
		Jenkins j = Jenkins.get();
		project = (InheritanceProject) j.createProject(
				InheritanceProject.DESCRIPTOR, name
		);
	}
	
	public void loadFromXml() throws IOException {
		if (xmlFile != null) {
			Source s = new StreamSource(new FileInputStream(new File(xmlFile)));
			project.updateByXml(s);
		}
	}
	
	public void setParameter(InheritableStringParameterDefinition pd) throws IOException {
		ParametersDefinitionProperty pdp = this.project.getProperty(
				ParametersDefinitionProperty.class, IMode.LOCAL_ONLY
		);
		if (pdp == null) {
			pdp = new ParametersDefinitionProperty(pd);
			this.project.addProperty(pdp);
		} else {
			//Create a new parameter defs list
			List<ParameterDefinition> defs =
					new LinkedList<ParameterDefinition>();
			//Add the new definition
			defs.add(pd);
			//And back-fill the existing ones
			for (ParameterDefinition oDef : pdp.getParameterDefinitions()) {
				if (oDef.getName().equals(pd.getName())) { continue; }
				defs.add(oDef);
			}
			//Drop the old property and add a shiny new one
			this.project.removeProperty(ParametersDefinitionProperty.class);
			this.project.addProperty(
					new ParametersDefinitionProperty(defs)
			);
		}
	}
	
	public void setParameter(String name, String value) throws IOException {
		this.setParameter(new InheritableStringParameterDefinition(
				name, value
		));
	}
	
	public void addParent(String pName, String variance, ParameterDefinition... defs) {
		AbstractProjectReference ref;
		if (defs != null && defs.length > 0) {
			ref = new ParameterizedProjectReference(pName, variance, Arrays.asList(defs));
		} else {
			ref = new SimpleProjectReference(pName);
		}
		this.project.addParentReference(ref, false);
	}
	
	public boolean dropParent(String pName) {
		return this.project.removeParentReference(pName);
	}
}
