package test;

import static org.junit.Assert.assertNotEquals;
import hudson.XmlFile;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.model.Computer;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition.IModes;
import hudson.plugins.project_inheritance.projects.references.SimpleProjectReference;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ParameterizedProjectReference;
import hudson.remoting.VirtualChannel;
import hudson.slaves.SlaveComputer;
import hudson.util.VersionNumber;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import jenkins.model.Jenkins;
import junit.framework.TestCase;

import org.apache.commons.lang.SystemUtils;
import org.jvnet.hudson.test.HudsonTestCase;

public class TestInheritanceMain extends HudsonTestCase {
	private static final Logger log = Logger.getLogger(
			TestInheritanceMain.class.toString()
	);
	
	
	private static class XmlProject {
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
		setPluginManager(null);
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
						ref.getName(), "parent-renamed"
				);
			}
			for (AbstractProjectReference ref : p.getCompatibleProjects()) {
				assertEquals(
						"References not updated properly when renaming",
						ref.getName(), "parent-renamed"
				);
			}
		}
		//Checking identity of the renamed parent
		assertTrue(
				"Renaming caused reference inconsistency",
				child.project.getParentProjects().contains(it)
		);
		
		printInfo("Testing ID & versioning properties...");
		assertEquals(parent.project.getVersionIDs().size(), 0);
		assertNull(parent.project.getLatestVersion());
		assertEquals(parent.project.getAllInheritedVersionsList().size(), 0);
		
		
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
				parent.project.getParameterDerivationList().size(), 1);
		assertNull(
				"Labels have been assigned, but shouldn't have been",
				parent.project.getAssignedLabelString()
		);
		
		assertEquals(
				"The quiet period should be 5",
				parent.project.getQuietPeriod(), 5
		);
		assertEquals(
				"The overrides collection should be empty",
				parent.project.getOverrides().size(), 0);
		
		assertEquals(
				"There should not be reference issues",
				parent.project.getProjectReferenceIssues().size(), 0
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
		assertEquals("A has != 0 versions", A.project.getVersions().size(), 0);
		assertEquals("B has != 0 versions", B.project.getVersions().size(), 0);
		
		//Verify that A has a parameter, and B has not
		assertEquals("A has != 1 parameter", A.project.getParameters().size(), 1);
		assertEquals("B has != 0 parameters", B.project.getParameters().size(), 0);
		
		//Load the XML into project B
		printInfo("Loading XML from A into B...");
		B.project.updateByXml((Source) new StreamSource(A.project.getConfigFile().getFile()));
		
		//Now, A should still have 0 versions, but B should have one
		assertEquals("A has != 0 versions", A.project.getVersions().size(), 0);
		assertEquals("B has != 1 versions", B.project.getVersions().size(), 1);
		
		//Verify that A has a parameter, and B has one, too
		assertEquals("Project A has != 1 parameter", A.project.getParameters().size(), 1);
		
		List<ParameterDefinition> defs = B.project.getParameters();
		assertEquals("Project B has != 1 parameters", defs.size(), 1);
		for (ParameterDefinition def : defs) {
			assertEquals(
					"Project B has a parameter whose name is not name 'param'",
					def.getName(), "param"
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
		
		assertEquals("B should inherit anything yet", B.project.getRelationships().size(), 0);
		
		//Add one reference to A and verify
		B.addParent("A", null);
		assertEquals("A should have 0 parents", A.project.getParentReferences().size(), 0);
		assertEquals("A should have 1 children", A.project.getChildrenProjects().size(), 1);
		assertEquals("A should have 1 connections", A.project.getRelationships().size(), 1);
		assertEquals("B should have 1 parents", B.project.getParentReferences().size(), 1);
		assertEquals("B should have 0 children", B.project.getChildrenProjects().size(), 0);
		assertEquals("B should have 1 connections", B.project.getRelationships().size(), 1);
		//Cycle test
		assertFalse("A should not have a cyclic dependency", A.project.hasCyclicDependency());
		assertFalse("B should not have a cyclic dependency", B.project.hasCyclicDependency());
		
		//Add another, duplicate reference (which is not counted for child conns)
		B.addParent("A", null);
		assertEquals("A should have 0 parents", A.project.getParentReferences().size(), 0);
		assertEquals("A should have 2 children", A.project.getChildrenProjects().size(), 2);
		assertEquals("A should have 1 connections", A.project.getRelationships().size(), 1);
		assertEquals("B should have 2 parents", B.project.getParentReferences().size(), 2);
		assertEquals("B should have 0 children", B.project.getChildrenProjects().size(), 0);
		assertEquals("B should have 1 connections", B.project.getRelationships().size(), 1);
		//Cycle test
		assertFalse("A should not have a cyclic dependency", A.project.hasCyclicDependency());
		assertTrue("B should have a cyclic dependency", B.project.hasCyclicDependency());
		
		//Clean up the references and verify the ground-state
		while (B.dropParent("A")) {}
		assertEquals("B should have 0 connections", B.project.getRelationships().size(), 0);
		assertFalse("A should not have a cyclic dependency", A.project.hasCyclicDependency());
		assertFalse("B should not have a cyclic dependency", B.project.hasCyclicDependency());
		
		
		// Create a cycle between A,B,C
		A.addParent("B", null);
		B.addParent("C", null);
		C.addParent("A", null);
		assertEquals("A should have 1 parents", A.project.getParentReferences().size(), 1);
		assertEquals("A should have 1 children", A.project.getChildrenProjects().size(), 1);
		assertEquals("A should have 2 connections", A.project.getRelationships().size(), 2);
		assertEquals("B should have 1 parents", B.project.getParentReferences().size(), 1);
		assertEquals("B should have 1 children", B.project.getChildrenProjects().size(), 1);
		assertEquals("B should have 2 connections", B.project.getRelationships().size(), 2);
		assertEquals("C should have 1 parents", C.project.getParentReferences().size(), 1);
		assertEquals("C should have 1 children", C.project.getChildrenProjects().size(), 1);
		assertEquals("C should have 2 connections", C.project.getRelationships().size(), 2);
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
		
		assertEquals("A has != 0 parameters", A.project.getParameters().size(), 0);
		assertEquals("B has != 0 parameters", B.project.getParameters().size(), 0);
		
		A.setParameter("P", "A");
		assertEquals("A has != 1 parameters", A.project.getParameters().size(), 1);
		assertEquals("B has != 0 parameters", B.project.getParameters().size(), 0);
		assertEquals("B has != 1 inherited parameters", B.project.getParameters(IMode.INHERIT_FORCED).size(), 1);
		
		//Build one instance of B and check if it got parameter "P" = "A"
		buildAndAssertValue(B, "P", "A");
		
		//Now, add an override of "P" into "B" and build again
		B.setParameter("P", "B");
		buildAndAssertValue(B, "P", "B");
		
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
		
		
		//Test if value assignment causes an actual build failure
		A.setParameter(new InheritableStringParameterDefinition(
				"P", "A", IModes.OVERWRITABLE, false, true, false, false
		));
		B.setParameter(new InheritableStringParameterDefinition(
				"P", "", IModes.OVERWRITABLE, false, true, false, false
		));
		InheritanceBuild build = buildAndAssertValue(B, "P", "", false);
		assertTrue(
				"Build for 'B' should have failed due to an empty value for 'P'",
				build.getResult().isWorseOrEqualTo(Result.FAILURE)
		);
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
			assertEquals("Parameter does not have correct value", ((StringParameterValue)pv).value, value);
			return build;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
}