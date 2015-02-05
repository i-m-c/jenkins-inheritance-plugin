package test;

import static org.junit.Assert.assertNotEquals;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.XmlFile;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.model.Computer;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine.CreationClass;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine.CreationMating;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition.IModes;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterReferenceDefinition;
import hudson.plugins.project_inheritance.projects.references.SimpleProjectReference;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference.ProjectReferenceDescriptor;
import hudson.plugins.project_inheritance.projects.references.ParameterizedProjectReference;
import hudson.plugins.project_inheritance.projects.references.filters.MatingReferenceFilter;
import hudson.remoting.VirtualChannel;
import hudson.slaves.SlaveComputer;
import hudson.util.VersionNumber;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import jenkins.model.Jenkins;
import junit.framework.TestCase;

import org.apache.commons.lang.SystemUtils;
import org.jvnet.hudson.test.HudsonTestCase;

import utils.DummyListener;

public class TestInheritanceMain extends HudsonTestCase {
	private static final Logger log = Logger.getLogger(
			TestInheritanceMain.class.toString()
	);
	
	
	protected static class XmlProject {
		public final InheritanceProject project;
		public final String xmlFile;
		
		public XmlProject(String name) throws IOException {
			this(name, null);
		}
		
		public XmlProject(String name, String xmlFile) throws IOException {
			this.xmlFile = xmlFile;
			//Create the project -- optionally from the given XML
			Jenkins j = Jenkins.getInstance();
			project = (InheritanceProject) j.createProject(
					InheritanceProject.DESCRIPTOR, name
			);
		}
		
		public void loadFromXml() throws IOException {
			if (xmlFile == null) {
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
				List<ParameterDefinition> defs = pdp.getParameterDefinitions();
				Iterator<ParameterDefinition> iter = defs.iterator();
				//Remove definitions that are in the way
				while (iter.hasNext()) {
					ParameterDefinition param = iter.next();
					if (param.getName().equals(pd.getName())) {
						iter.remove();
					}
				}
				defs.add(pd);
			}
			this.project.removeProperty(ParametersDefinitionProperty.class);
			this.project.addProperty(pdp);
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
	
	
	public TestInheritanceMain() {
		super();
	}
	
	public TestInheritanceMain(String name) {
		super(name);
	}
	
	private void printInfo(String info) {
		log.info("[testInheritanceMain] " + info);
	}

	private InheritanceBuild buildAndLookForString(InheritanceProject project,
			String stringToFind) throws InterruptedException,
			ExecutionException, IOException {
		InheritanceBuild ib2 = project.scheduleBuild2(0).get();
		List<String> log = ib2.getLog(20);
		boolean parameterFound = false;
		for (String line : log) {
			if (line.equals(stringToFind)) {
				parameterFound = true;
				break;
			}
		}
		assertTrue("String "+stringToFind+" not found in the build", parameterFound);
		return ib2;
	}

	private void purgeSlaves() {
		List<Computer> disconnectingComputers = new ArrayList<Computer>();
		List<VirtualChannel> closingChannels = new ArrayList<VirtualChannel>();
		for (Computer computer : jenkins.getComputers()) {
			if (!(computer instanceof SlaveComputer)) {
				continue;
			}
			// disconnect slaves.
			// retrieve the channel before disconnecting.
			// even a computer gets offline, channel delays to close.
			if (!computer.isOffline()) {
				VirtualChannel ch = computer.getChannel();
				computer.disconnect(null);
				disconnectingComputers.add(computer);
				closingChannels.add(ch);
			}
		}

		try {
			// Wait for all computers disconnected and all channels closed.
			for (Computer computer : disconnectingComputers) {
				computer.waitUntilOffline();
			}
			for (VirtualChannel ch : closingChannels) {
				ch.join();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	
	// === TEST PREPARATION ===
	
	@Override
	protected void tearDown() throws Exception {
		if (!canRunTests()) {
			super.tearDown();
			return;
		}
		printInfo("tearDown()");
		purgeSlaves();
		super.tearDown();
	}

	@Override
	protected void setUp() throws Exception {
		if (!canRunTests()) {
			super.setUp();
			return;
		}
		printInfo("setUp()");
		//setPluginManager(null);
		super.setUp();
	}

	protected boolean canRunTests() {
		VersionNumber v = Jenkins.getVersion();
		if (v.isOlderThan(new VersionNumber("1.520"))) {
			//On Windows, these test cases fail during tearDown() in Jenkins < 1.520
			if (SystemUtils.IS_OS_WINDOWS) {
				return false;
			}
		}
		return true;
	}
	
	// === TEST EXECUTION ===
	
	/**
	 * This test uses two projects, where one inherits properties from another
	 * and verifies, that the general set-up of this is sane.
	 * 
	 * @throws IOException
	 * @throws ServletException
	 */
	public void testGeneralProperties() throws IOException, ServletException {
		printInfo("testGeneralProperties()");
		
		if (!canRunTests()) {
			printInfo("Test is skipped, due to incompatibility with OS/Jenkins");
			return;
		}
		
		//Creating a parent and child project
		XmlProject parent = new XmlProject("parent");
		XmlProject child = new XmlProject("child");
		
		//Attach one parameter to the parent
		parent.setParameter("param1", "parent");
		//And link the child to the parent
		child.addParent(parent.project.getFullName(), null);
		
		printInfo("Testing project comparison...");
		assertNotEquals(
				"Project comparison failed",
				child.project.compareTo(parent.project), 0
		);
		
		printInfo("Testing direct attribute changing (e.g. abstract)...");
		assertFalse("Abstract property does not default to false", parent.project.isAbstract);
		parent.project.isAbstract = true;
		assertTrue("Abstract property is not changed correctly", parent.project.isAbstract);
		
		//Test if renaming keeps references intact
		printInfo("Testing renaming of projects...");
		parent.project.renameTo("parent-renamed");
		TopLevelItem it = Jenkins.getInstance().getItem("parent-renamed");
		assertEquals("Project renaming failed", it, parent.project);
		
		for (TopLevelItem item : Jenkins.getInstance().getItems()) {
			if (!(item instanceof InheritanceProject)) {
				continue;
			}
			InheritanceProject p = (InheritanceProject) item;
			for (AbstractProjectReference ref : p.getParentReferences()) {
				assertEquals(
						"References not updated properly when renaming",
						"parent-renamed", ref.getName()
				);
			}
			for (AbstractProjectReference ref : p.getCompatibleProjects()) {
				assertEquals(
						"References not updated properly when renaming",
						"parent-renamed", ref.getName()
				);
			}
		}
		//Checking identity of the renamed parent
		assertTrue(
				"Renaming caused reference inconsistency",
				child.project.getParentProjects().contains(it)
		);
		
		printInfo("Testing ID & versioning properties...");
		assertEquals(0, parent.project.getVersionIDs().size());
		assertNull(parent.project.getLatestVersion());
		assertEquals(0, parent.project.getAllInheritedVersionsList().size());
		
		
		printInfo("Checking SVG details...");
		assertStringContains("The SVG details should contain the number" +
				" of build steps", parent.project.getSVGDetail(), "0 build steps");
		assertStringContains("The SVG details should contain the number" +
				" of publishers", parent.project.getSVGDetail(), "0 publishers");
		try {
			@SuppressWarnings("unused")
			URL newUrl = parent.project.getSVGLabelLink();
			TestCase.fail("The SVG label link should give " +
					" an IllegalStateException");
		} catch (IllegalStateException ex) {
		}
		printInfo("Checking miscellaneous properties");
		assertTrue(
				"The list of compatible projects should be empty",
				parent.project.getCompatibleProjects().isEmpty());
		assertEquals(
				"The list of parameters should have one parameter",
				1, parent.project.getParameterDerivationList().size());
		assertNull(
				"Labels have been assigned, but shouldn't have been",
				parent.project.getAssignedLabelString()
		);
		
		assertEquals(
				"The quiet period should be 5",
				5, parent.project.getQuietPeriod()
		);
		assertEquals(
				"The overrides collection should be empty",
				0, parent.project.getOverrides().size());
		
		assertEquals(
				"There should not be reference issues",
				0, parent.project.getProjectReferenceIssues().size()
		);
	}
	
	/**
	 * Creates two projects, changes one, verifies the change, updates the other
	 * with the XML of the former and verifies whether the change was loaded
	 * correctly.
	 * 
	 * @throws IOException
	 * @throws ServletException
	 */
	public void testXmlLoad() throws IOException, ServletException {
		// Testing all the general xml options available here.
		printInfo("testXmlLoad()");
		
		if (!canRunTests()) {
			printInfo("Test is skipped, due to incompatibility with OS/Jenkins");
			return;
		}
		
		//Creating two projects; one with and one without a param
		XmlProject A = new XmlProject("A");
		XmlProject B = new XmlProject("B");
				
		//Attach one parameter to the first project
		A.setParameter("param", "val");
		
		//Save the settings of project A to disk
		printInfo("Saving the configuration of project A...");
		A.project.save();
		B.project.save();
		
		//Check if a config file is present
		XmlFile xml = A.project.getConfigFile();
		assertNotNull("Config file is null for project A", xml);
		assertTrue("No config file present for project A", xml.getFile().exists());
		
		//Check if both have no versioning set
		assertEquals("A has != 0 versions", 0, A.project.getVersions().size());
		assertEquals("B has != 0 versions", 0, B.project.getVersions().size());
		
		//Verify that A has a parameter, and B has not
		assertEquals("A has != 1 parameter", 1, A.project.getParameters().size());
		assertEquals("B has != 0 parameters", 0, B.project.getParameters().size());
		
		//Load the XML into project B
		printInfo("Loading XML from A into B...");
		B.project.updateByXml((Source) new StreamSource(A.project.getConfigFile().getFile()));
		
		//Now, A should still have 0 versions, but B should have one
		assertEquals("A has != 0 versions", 0, A.project.getVersions().size());
		assertEquals("B has != 1 versions", 1, B.project.getVersions().size());
		
		//Verify that A has a parameter, and B has one, too
		assertEquals("Project A has != 1 parameter", 1, A.project.getParameters().size());
		
		List<ParameterDefinition> defs = B.project.getParameters();
		assertEquals("Project B has != 1 parameters", 1, defs.size());
		for (ParameterDefinition def : defs) {
			assertEquals(
					"Project B has a parameter with an invalid name",
					"param", def.getName()
			);
		}
	}
	
	
	/**
	 * Tests if adding duplicated, circular or diamon parent references is
	 * properly detected.
	 * 
	 * @throws IOException
	 */
	public void testParentDuplication() throws IOException {
		printInfo("testParentDuplication()");
		
		if (!canRunTests()) {
			printInfo("Test is skipped, due to incompatibility with OS/Jenkins");
			return;
		}
		
		XmlProject A = new XmlProject("A");
		XmlProject B = new XmlProject("B");
		XmlProject C = new XmlProject("C");
		XmlProject D = new XmlProject("D");
		
		assertEquals("B should inherit anything yet", 0, B.project.getRelationships().size());
		
		//Add one reference to A and verify
		B.addParent("A", null);
		assertEquals("A should have 0 parents", 0, A.project.getParentReferences().size());
		assertEquals("A should have 1 children", 1, A.project.getChildrenProjects().size());
		assertEquals("A should have 1 connections", 1, A.project.getRelationships().size());
		assertEquals("B should have 1 parents", 1, B.project.getParentReferences().size());
		assertEquals("B should have 0 children", 0, B.project.getChildrenProjects().size());
		assertEquals("B should have 1 connections", 1, B.project.getRelationships().size());
		//Cycle test
		assertFalse("A should not have a cyclic dependency", A.project.hasCyclicDependency());
		assertFalse("B should not have a cyclic dependency", B.project.hasCyclicDependency());
		
		//Add another, duplicate reference (which is not counted for child conns)
		B.addParent("A", null);
		assertEquals("A should have 0 parents", 0, A.project.getParentReferences().size());
		assertEquals("A should have 2 children", 2, A.project.getChildrenProjects().size());
		assertEquals("A should have 1 connections", 1, A.project.getRelationships().size());
		assertEquals("B should have 2 parents", 2, B.project.getParentReferences().size());
		assertEquals("B should have 0 children", 0, B.project.getChildrenProjects().size());
		assertEquals("B should have 1 connections", 1, B.project.getRelationships().size());
		//Cycle test
		assertFalse("A should not have a cyclic dependency", A.project.hasCyclicDependency());
		assertTrue("B should have a cyclic dependency", B.project.hasCyclicDependency());
		
		//Clean up the references and verify the ground-state
		while (B.dropParent("A")) {}
		assertEquals("B should have 0 connections", 0, B.project.getRelationships().size());
		assertFalse("A should not have a cyclic dependency", A.project.hasCyclicDependency());
		assertFalse("B should not have a cyclic dependency", B.project.hasCyclicDependency());
		
		
		// Create a cycle between A,B,C
		A.addParent("B", null);
		B.addParent("C", null);
		C.addParent("A", null);
		assertEquals("A should have 1 parents", 1, A.project.getParentReferences().size());
		assertEquals("A should have 1 children", 1, A.project.getChildrenProjects().size());
		assertEquals("A should have 2 connections", 2, A.project.getRelationships().size());
		assertEquals("B should have 1 parents", 1, B.project.getParentReferences().size());
		assertEquals("B should have 1 children", 1, B.project.getChildrenProjects().size());
		assertEquals("B should have 2 connections", 2, B.project.getRelationships().size());
		assertEquals("C should have 1 parents", 1, C.project.getParentReferences().size());
		assertEquals("C should have 1 children", 1, C.project.getChildrenProjects().size());
		assertEquals("C should have 2 connections", 2, C.project.getRelationships().size());
		//Cycle test
		assertTrue("A should have a cyclic dependency", A.project.hasCyclicDependency());
		assertTrue("B should have a cyclic dependency", B.project.hasCyclicDependency());
		assertTrue("C should have a cyclic dependency", C.project.hasCyclicDependency());
		
		//Clean up
		A.dropParent("B"); B.dropParent("C"); C.dropParent("A");
		
		
		//Create diamond dependency: A->B,C ; B->D; C->D
		A.addParent("B", null);
		A.addParent("C", null);
		B.addParent("D", null);
		C.addParent("D", null);
		
		//Cycle test
		assertTrue("A should have a cyclic dependency", A.project.hasCyclicDependency());
		assertFalse("B should not have a cyclic dependency", B.project.hasCyclicDependency());
		assertFalse("C should not have a cyclic dependency", C.project.hasCyclicDependency());
		assertFalse("D should not have a cyclic dependency", D.project.hasCyclicDependency());
	}

	
	public void testParameterInheritance() throws IOException {
		printInfo("testParameterInheritance()");
		if (!canRunTests()) {
			printInfo("Test is skipped, due to incompatibility with OS/Jenkins");
			return;
		}
		
		XmlProject A = new XmlProject("A");
		XmlProject B = new XmlProject("B");
		
		B.addParent("A", null);
		
		assertEquals("A has != 0 parameters", 0, A.project.getParameters().size());
		assertEquals("B has != 0 parameters", 0, B.project.getParameters().size());
		
		A.setParameter("P", "A");
		assertEquals("A has != 1 parameters", 1, A.project.getParameters().size());
		assertEquals("B has != 0 parameters", 0, B.project.getParameters().size());
		assertEquals("B has != 1 inherited parameters", 1, B.project.getParameters(IMode.INHERIT_FORCED).size());
		
		//Build one instance of B and check if it got parameter "P" = "A"
		buildAndAssertValue(B, "P", "A");
		
		//Now, add an override of "P" into "B" and build again
		B.setParameter("P", "B");
		buildAndAssertValue(B, "P", "B");
		
		
		// Test parameter mode correctness
		this.subTestParameterMode(A, B);
		
		// Test parameter assign-check
		this.subTestParameterFlagAssignCheck(A, B);
		
		// Test parameter default-check
		this.subTestParameterFlagDefaultCheck(A, B);
	}
	
	public void subTestParameterMode(XmlProject A, XmlProject B) throws IOException {
		//Alter the parameter in A to be "extend" instead of "overwrite"
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.EXTENSIBLE, false, false, false, false
		));
		B.setParameter(new InheritableStringParameterDefinition(
				"P", "B", IModes.OVERWRITABLE, false, false, false, false
		));
		//Build and check value
		buildAndAssertValue(B, "P", "AB");
		
		//Test the reverse; overwrite followed by extend
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, false, false, false, false
		));
		B.setParameter(new InheritableStringParameterDefinition(
				"P", "B", IModes.EXTENSIBLE, false, false, false, false
		));
		buildAndAssertValue(B, "P", "B");
		
		//Test fixed mode
		B.setParameter(new InheritableStringParameterDefinition(
				"P", "B", IModes.FIXED, false, false, false, false
		));
		buildAndAssertValue(B, "P", "B");
		
		//Also fix 'A', this should lead to a failed build
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.FIXED, false, false, false, false
		));
		//The build must even fail to queue in this case
		QueueTaskFuture<InheritanceBuild> qtf = B.project.scheduleBuild2(0);
		assertNull("Building 'B' with 2 fixed parameters should have failed to schedule!", qtf);
	}
	
	public void subTestParameterFlagAssignCheck(XmlProject A, XmlProject B) throws IOException {
		//Check for positive case, with to real parameters
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, false, true, false, false
		));
		B.setParameter(new InheritableStringParameterDefinition(
				"P", "B", IModes.OVERWRITABLE, false, true, false, false
		));
		Entry<Boolean, String> sanity = B.project.getParameterSanity();
		assertTrue("Project B should have passed the parameter sanity check", sanity.getKey());
		
		InheritanceBuild build = buildAndAssertValue(B, "P", "B", false);
		assertTrue(
				"Build for 'B' should have succeeded",
				build.getResult().isBetterOrEqualTo(Result.SUCCESS)
		);
		
		//Check for negative case, with to real parameters
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, false, true, false, false
		));
		B.setParameter(new InheritableStringParameterDefinition(
				"P", "", IModes.OVERWRITABLE, false, true, false, false
		));
		sanity = B.project.getParameterSanity();
		assertTrue("Project B should have passed the parameter sanity check", sanity.getKey());
		
		build = buildAndAssertValue(B, "P", "", false);
		assertTrue(
				"Build for 'B' should have failed due to an empty value for 'P'",
				build.getResult().isWorseOrEqualTo(Result.FAILURE)
		);
		
		
		//Check for positive case, with one reference
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, false, true, false, false
		));
		B.setParameter(new InheritableStringParameterReferenceDefinition(
				"P", "B"
		));
		sanity = B.project.getParameterSanity();
		assertTrue("Project B should have passed the parameter sanity check", sanity.getKey());
		
		build = buildAndAssertValue(B, "P", "B", false);
		assertTrue(
				"Build for 'B' should have succeeded",
				build.getResult().isBetterOrEqualTo(Result.SUCCESS)
		);
		
		//Check for negative case, with to real parameters
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, false, true, false, false
		));
		B.setParameter(new InheritableStringParameterReferenceDefinition(
				"P", ""
		));
		sanity = B.project.getParameterSanity();
		assertTrue("Project B should have passed the parameter sanity check", sanity.getKey());
		
		build = buildAndAssertValue(B, "P", "", false);
		assertTrue(
				"Build for 'B' should have failed due to an empty value for 'P'",
				build.getResult().isWorseOrEqualTo(Result.FAILURE)
		);
	}
	
	public void subTestParameterFlagDefaultCheck(XmlProject A, XmlProject B) throws IOException {
		//Check for positive case, with two real parameters
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, true, false, false, false
		));
		B.setParameter(new InheritableStringParameterDefinition(
				"P", "B", IModes.OVERWRITABLE, true, false, false, false
		));
		Entry<Boolean, String> sanity = B.project.getParameterSanity();
		assertTrue("Project B should have passed the parameter sanity check", sanity.getKey());
		
		InheritanceBuild build = buildAndAssertValue(B, "P", "B", false);
		assertTrue(
				"Build for 'B' should have succeeded",
				build.getResult().isBetterOrEqualTo(Result.SUCCESS)
		);
		
		//Check for negative case, with two real parameters
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, true, false, false, false
		));
		B.setParameter(new InheritableStringParameterDefinition(
				"P", "", IModes.OVERWRITABLE, true, false, false, false
		));
		sanity = B.project.getParameterSanity();
		assertFalse("Project B should have failed the parameter sanity check", sanity.getKey());
		
		//The build must fail to queue in this case
		QueueTaskFuture<InheritanceBuild> qtf = B.project.scheduleBuild2(0);
		assertNull("Building 'B' (with a missing default value) should have failed to schedule!", qtf);
		
		
		//Check for positive case, with one reference
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, true, false, false, false
		));
		B.setParameter(new InheritableStringParameterReferenceDefinition(
				"P", "B"
		));
		sanity = B.project.getParameterSanity();
		assertTrue("Project B should have passed the parameter sanity check", sanity.getKey());
		
		build = buildAndAssertValue(B, "P", "B", false);
		assertTrue(
				"Build for 'B' should have succeeded",
				build.getResult().isBetterOrEqualTo(Result.SUCCESS)
		);
		
		//Check for negative case, with to real parameters
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, true, false, false, false
		));
		B.setParameter(new InheritableStringParameterReferenceDefinition(
				"P", ""
		));
		sanity = B.project.getParameterSanity();
		assertFalse("Project B should have failed the parameter sanity check", sanity.getKey());
		
		//The build must fail to queue in this case
		qtf = B.project.scheduleBuild2(0);
		assertNull("Building 'B' (with a missing default value) should have failed to schedule!", qtf);
	}
	
	
	public void testCompoundCreation() throws IOException {
		printInfo("testCompoundCreation()");
		if (!canRunTests()) {
			printInfo("Test is skipped, due to incompatibility with OS/Jenkins");
			return;
		}
		
		XmlProject left = new XmlProject("LeftJob");
		XmlProject right = new XmlProject("RightJob");
		XmlProject util = new XmlProject("UtilityJob");
		
		//Create three classes; two for mating, one as an "outsider"
		ProjectCreationEngine pce = ProjectCreationEngine.instance;
		List<CreationClass> classes = pce.getCreationClasses();
		classes.add(new CreationClass("Left", ""));
		classes.add(new CreationClass("Right", ""));
		classes.add(new CreationClass("Utility", ""));
		
		//Add a suitable mating
		List<CreationMating> mates = pce.getMatings();
		mates.add(new CreationMating("Left", "Right", ""));
		
		//Add the 3 classes to the 3 projects
		left.project.setCreationClass("Left");
		right.project.setCreationClass("Right");
		util.project.setCreationClass("Utility");
		
		//Add a mating between left & right
		List<AbstractProjectReference> lMates = left.project.getRawCompatibleProjects();
		AbstractProjectReference rightRef = new SimpleProjectReference("RightJob");
		lMates.add(rightRef);
		
		/* Check if the project reference's descriptor would evaluate the
		 * options in the select-box correctly. It should list 2 entries:
		 *   - FOOBARDEFAULT (the 'old' value selected in the box)
		 *   - RightJob (the only really permissible value
		 */
		ProjectReferenceDescriptor desc = rightRef.getDescriptor();
		ListBoxModel m = desc.internalFillNameItems(
				"FOOBARDEFAULT", new MatingReferenceFilter(left.project)
		);
		assertEquals(
				"APR.doFillNameItems() should've only returned 2 values",
				2, m.size()
		);
		for (Option o : m) {
			assertTrue(
					"APR.doFillNameItems() contains invalid value: " + o.name,
					o.name.equals("FOOBARDEFAULT") || o.name.equals("RightJob")
			);
		}
		
		//Add an invalid mating between left & utility
		lMates = right.project.getRawCompatibleProjects();
		lMates.add(new SimpleProjectReference("UtilityJob"));
		
		//Add an invalid mating between utility & left
		lMates = util.project.getRawCompatibleProjects();
		lMates.add(new SimpleProjectReference("LeftJob"));
		
		//Add an invalid mating between left & left
		lMates = left.project.getRawCompatibleProjects();
		lMates.add(new SimpleProjectReference("LeftJob"));
		
		//Add an invalid mating between right & left
		lMates = right.project.getRawCompatibleProjects();
		lMates.add(new SimpleProjectReference("LeftJob"));
		
		//Run the project creation engine and decode the map
		pce.setEnableCreation(true);
		Map<String, String> report = pce.triggerCreateProjects();
		assertNotNull("Automatic generation of jobs did not return a valid report-map", report);
		assertEquals("PCE report did not contain enough creation attempts",  5, report.size());
		
		assertTrue("PCE report did not contain the 'LeftJob_RightJob' job", report.containsKey("LeftJob_RightJob"));
		
		TopLevelItem it = Jenkins.getInstance().getItem("LeftJob_RightJob");
		assertTrue("PCE should have created the 'LeftJob_RightJob' job", it != null);
		
		//Loop over the result, check if such a job was created, and if it's valied
		for (String pName : report.keySet()) {
			it = Jenkins.getInstance().getItem(pName);
			if (it == null) { continue; }
			assertEquals("PCE created invalid job", "LeftJob_RightJob", it.getFullName());
		}
		
		
	}
	
	
	@SuppressWarnings("deprecation")
	public void testWorkspacePathAllocation() throws IOException, InterruptedException {
		printInfo("testWorkspacePathAllocation()");
		if (!canRunTests()) {
			printInfo("Test is skipped, due to incompatibility with OS/Jenkins");
			return;
		}
		
		//Fetch the jenkins instance; which is a valid build host
		Jenkins j = Jenkins.getInstance();
		
		//Create a dummy project with a strange name
		XmlProject job = new XmlProject("foobar");
		
		//Get a default environment map for this job and jenkins node
		DummyListener listen = new DummyListener();
		EnvVars vars = job.project.getEnvironment(j, listen);
		
		//Checking the "raw" workspace allocation
		this.subTestWorkspacePathAllocation(j, job, vars, "foobar");
		
		//Adding a parameterized workspace variable, creating "foobar-1" as the basename
		job.project.setRawParameterizedWorkspace("${JOB_NAME}-1");
		this.subTestWorkspacePathAllocation(j, job, vars, "foobar-1");
		
		//Adding a customized workspace, set to "zort"
		job.project.setCustomWorkspace("zort");
		this.subTestWorkspacePathAllocation(j, job, vars, "zort");
	}
	
	public void subTestWorkspacePathAllocation(Jenkins j, XmlProject job, EnvVars vars, String expect) {
		//Checking the "raw" workspace allocation
		FilePath ws = InheritanceBuild.getWorkspacePathFor(j, job.project, vars);
		assertNotNull("Could not fetch a valid workspace", ws);
		
		String remote = ws.getRemote();
		assertNotNull("Could not fetch a valid workspace", remote);
		assertFalse("Could not fetch a valid workspace", remote.isEmpty());
		assertTrue("Workspace did not end with 'foobar'", remote.endsWith(expect));
	}
	
	
	public void testLabelCaching() throws IOException, InterruptedException {
		printInfo("testLabelCaching()");
		if (!canRunTests()) {
			printInfo("Test is skipped, due to incompatibility with OS/Jenkins");
			return;
		}
		
		//Fetch the jenkins instance; which is a valid build host
		Jenkins j = Jenkins.getInstance();
		//Set its label to a test value
		j.setLabelString("test:foo");
		
		//Create a simple job
		XmlProject A = new XmlProject("A");
		//Assign the test:foo label
		A.project.setAssignedLabel(new LabelAtom("test:foo"));
		
		//Build the job; should work
		try {
			QueueTaskFuture<InheritanceBuild> future = A.project.scheduleBuild2(0);
			InheritanceBuild b = future.get(5, TimeUnit.SECONDS);
			this.assertBuildStatusSuccess(b);
			
			//Then, change the label on the Jenkins instance
			j.setLabelString("test:bar");
			//Refresh the node information, to force label re-association
			j.setNodes(j.getNodes());
			//Nuke the buffers, to force label regeneration
			InheritanceProject.clearBuffers(null);
			
			//Build the job again. It must not be able to start, as the labels are wrong
			future = A.project.scheduleBuild2(0);
			Thread.sleep(5*1000);
			assertFalse(
					"Job A should not have run, as no host with a correct label is online",
					future.isDone()
			);
			
			//Now, change the label and wait for at most 15 seconds for the
			//label cache to clear and the job to finish
			j.setLabelString("test:foo");
			//Refresh the node information, to force label re-association
			j.setNodes(j.getNodes());
			
			b = future.get(15, TimeUnit.SECONDS);
			this.assertBuildStatusSuccess(b);
		} catch (Exception ex) {
			fail(String.format(
					"Got exception during execution of project 'A':\n%s",
					ex.getMessage()
			));
		}
		
	}
	
	
	// === HELPER METHODS ===
	
	public InheritanceBuild buildAndAssertValue(XmlProject p, String param, String value) throws IOException {
		return this.buildAndAssertValue(p, param, value, true);
	}
	
	public InheritanceBuild buildAndAssertValue(XmlProject p, String param, String value, boolean assertSuccess) throws IOException {
		try {
			InheritanceBuild build = (assertSuccess)
					? this.buildAndAssertSuccess(p.project)
					: p.project.scheduleBuild2(0).get();
			assertNotNull(build);
			ParametersAction pa = build.getAction(ParametersAction.class);
			assertNotNull("Build has no parameters", pa);
			
			ParameterValue pv = pa.getParameter(param);
			assertNotNull("Build has no parameter P", pv);
			assertTrue("Parameter is not a StringParameterValue", pv instanceof StringParameterValue);
			assertEquals("Parameter does not have correct value", value, ((StringParameterValue)pv).value);
			return build;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
}