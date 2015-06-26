package hudson.plugins.project_inheritance.projects;

import hudson.Extension;

import hudson.model.Item;
import hudson.model.listeners.ItemListener ;

import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;

@Extension
public class ItemOperations extends ItemListener {
	public static final ItemOperations INSTANCE = new ItemOperations();


	public void xonRenamed(Item item, String oldName, String newName) {
		System.out.println("ITEM Renombrado " + oldName + " " + newName + " " + item.getFullName());
		if (item instanceof InheritanceProject) {
			System.out.println("Es un InheritanceProject");
		}
	}

	@Override
	public void onLocationChanged(Item item, String oldFullName, String newFullName) {
		System.out.println("ITEM cambiado " + oldFullName + " " + newFullName + " " + item.getFullName());
		if (item instanceof InheritanceProject) {
			System.out.println("Es un InheritanceProject");
		}


		InheritanceProject inherited = (InheritanceProject) item;
		InheritanceProject.clearBuffers(inherited);
		//And then fixing all named references
		for (InheritanceProject p : inherited.getProjectsMap().values()) {
			for (AbstractProjectReference ref : p.getParentReferences()) {
System.out.println("ParentReference " + ref.getName());
				if (ref.getName().equals(oldFullName)) {
					ref.switchProject(inherited);
				}
			}
			for (AbstractProjectReference ref : p.compatibleProjects) {
System.out.println("compatibleProjects " + ref.getName());
				if (ref.getName().equals(oldFullName)) {
					ref.switchProject(inherited);
				}
			}
			try {
				item.save();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
	}
}