package test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import jenkins.model.Jenkins;
import junit.framework.TestCase;
import hudson.XmlFile;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Computer;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ProjectReference;
import hudson.remoting.VirtualChannel;
import hudson.slaves.SlaveComputer;

import org.jvnet.hudson.test.HudsonTestCase;

public class TestInheritanceMain extends HudsonTestCase {

	/* Sample projects needed for testing */
	String parentJob;
	String childJob1;
	String childJob2;
	String childJob3;
	InheritanceProject parentProject;
	InheritanceProject childProject1;
	InheritanceProject childProject2;
	InheritanceProject childProject3;
	String parentName;
	String child1Name;
	String child2Name;
	String child3Name;

	private void printInfo(String info) {
		System.out.println("[testInheritanceMain] " + info);
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

	@Override
	protected void tearDown() throws Exception {
		printInfo("tearDown()");
		purgeSlaves();
		super.tearDown();
	}

	@Override
	protected void setUp() throws Exception {
		printInfo("setUp()");
		setPluginManager(null);
		super.setUp();
		// Depending on the os, the jobs have different build steps
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			parentJob = "src\\test\\xmljobs\\win\\job1.xml";
			childJob1 = "src\\test\\xmljobs\\win\\job2.xml";
			childJob2 = "src\\test\\xmljobs\\win\\job3.xml";
			childJob3 = "src\\test\\xmljobs\\win\\job4.xml";
		} else {
			parentJob = "src/test/xmljobs/linux/job1.xml";
			childJob1 = "src/test/xmljobs/linux/job2.xml";
			childJob2 = "src/test/xmljobs/linux/job3.xml";
			childJob3 = "src/test/xmljobs/linux/job4.xml";
		}
		parentName = "test_tcloud1";
		child1Name = "test_tcloud2";
		child2Name = "test_tcloud3";
		child3Name = "test_tcloud4";
		/* Setting two initial projects */
		Jenkins j = Jenkins.getInstance();
		parentProject = (InheritanceProject) j.createProject(
				InheritanceProject.DESCRIPTOR, parentName);
		childProject1 = (InheritanceProject) j.createProject(
				InheritanceProject.DESCRIPTOR, child1Name);
		childProject2 = (InheritanceProject) Jenkins.getInstance()
				.createProject(InheritanceProject.DESCRIPTOR, child2Name);
		childProject3 = (InheritanceProject) Jenkins.getInstance()
				.createProject(InheritanceProject.DESCRIPTOR, child3Name);
	}

	public void testGeneralProperties() throws IOException, ServletException {
		printInfo("testGeneralProperties()");
		InputStream is1 = new FileInputStream(new File(parentJob));
		parentProject.updateByXml((Source) new StreamSource(is1));
		is1.close();
		InputStream is2 = new FileInputStream(new File(childJob1));
		childProject1.updateByXml((Source) new StreamSource(is2));
		is2.close();
		printInfo("Testing project comparison...");
		assertNotEquals("Project comparison failed", childProject1.compareTo(parentProject), 0);
		printInfo("Testing attribute changing (e.g. abstract)...");
		assertTrue("Abstract property is not changed correctly", parentProject.isAbstract);
		parentProject.isAbstract = false;
		assertFalse("Abstract property is not changed correctly", parentProject.isAbstract);
		parentProject.isAbstract = true;
		printInfo("Testing renaming of projects...");
		parentProject.renameTo("First project beta");
		for (InheritanceProject p : InheritanceProject.getProjectsMap().values()) {
			for (AbstractProjectReference ref : p.getParentReferences()) {
				assertNotEquals("References not updated properly when renaming",ref.getName(), parentName);
			}
			for (AbstractProjectReference ref : p.getCompatibleProjects()) {
				assertNotEquals("References not updated properly when renaming", ref.getName(), parentName);
			}
		}
		assertEquals(childProject1.getParentProjects().get(0), parentProject);
		parentProject.renameTo(parentName);
		assertEquals(parentProject.getName(), parentProject.getSVGLabel());
		printInfo("Testing ID properties...");
		assertEquals(parentProject.getVersionIDs().size(), 0);
		assertNull(parentProject.getLatestVersion());
		assertEquals(parentProject.getAllInheritedVersionsList().size(), 0);
		printInfo("Checking SVG details...");
		assertStringContains("The SVG details should contain the number" +
				" of build steps", parentProject.getSVGDetail(), "0 build steps");
		assertStringContains("The SVG details should contain the number" +
				" of publishers", parentProject.getSVGDetail(), "0 publishers");
		try {
			@SuppressWarnings("unused")
			URL newUrl = parentProject.getSVGLabelLink();
			TestCase.fail("The SVG label link should give " +
					" an IllegalStateException");
		} catch (IllegalStateException ex) {
		}
		printInfo("Checking related and compatible projects...");
		/* Should this be this way? 
		 * assertEquals(parentProject.getRelatedProjects().size(), 1);
		 */
		assertTrue("The list of compatible projects should be empty",
				parentProject.getCompatibleProjects().isEmpty());
		printInfo("Checking the parameter derivation list...");
		assertEquals("The list of parameters should have one parameter"
				,parentProject.getParameterDerivationList().size(), 1);
		assertNull(parentProject.getAssignedLabelString());
		printInfo("Checking default quiet period...");
		assertEquals("The quiet period should be 5"
				,parentProject.getQuietPeriod(), 5);
		assertEquals("The overrides collection should be empty"
				, parentProject.getOverrides().size(), 0);
		printInfo("Checking that there are no reference issues...");
		assertEquals("There should not be reference issues",
				parentProject.getProjectReferenceIssues().size(), 0);
	}

	public void testCreationParentManual() throws IOException {
		printInfo("testCreationParentManual()");
		printInfo("Adding manually a parent...");
		childProject1.addParentReference(new ProjectReference(parentName, 0));
		printInfo("Testing whether relationship is ok...");
		assertEquals("The relationship is not well established with addParentReference"
				, childProject1.getParentProjects().get(0), parentProject);
		/* Duplicate references should not be allowed */
		printInfo("Adding same parent...");
		childProject1.addParentReference(new ProjectReference(parentName, 0));
		printInfo("Checking that duplicated relationships do not exist...");
		assertEquals("There should be only one relationship"
				, childProject1.getRelationships().size(), 1);
		assertEquals("There is not only one parent reference"
				,childProject1.getParentReferences().size(), 1);
		assertEquals("There is not only one child of the parent job"
				, parentProject.getChildrenProjects().get(0), childProject1);
	}

	public void testCreationParent() throws IOException {
		printInfo("testCreationParent()");
		printInfo("Loading two projects from file...");
		InputStream is1 = new FileInputStream(new File(parentJob));
		parentProject.updateByXml((Source) new StreamSource(is1));
		is1.close();
		InputStream is2 = new FileInputStream(new File(childJob1));
		childProject1.updateByXml((Source) new StreamSource(is2));
		is2.close();
		printInfo("Testing their relation...");
		assertEquals("The relation was not loaded correctly from the xml",
				childProject1.getParentProjects().get(0), parentProject);
	}

	public void testParameterInheritanceManual() throws IOException {
		printInfo("testParameterInheritanceManual()");
		printInfo("Creating a job with parameter1 = 0...");
		InputStream is1 = new FileInputStream(new File(parentJob));
		parentProject.updateByXml((Source) new StreamSource(is1));
		is1.close();
		printInfo("Creating a child job with parameter1 = 5...");
		InputStream is2 = new FileInputStream(new File(childJob2));
		childProject1.updateByXml((Source) new StreamSource(is2));
		is2.close();
		printInfo("Checking that the parent has only parameter1 = 0...");
		Map<JobPropertyDescriptor, JobProperty<? super InheritanceProject>> properties = parentProject
				.getProperties();
		boolean found = false;
		for (JobProperty<? super InheritanceProject> prop : properties.values()) {
			if (prop == null) {
				continue;
			}
			if (prop instanceof InheritanceParametersDefinitionProperty) {
				InheritanceParametersDefinitionProperty ipdp = (InheritanceParametersDefinitionProperty) prop;
				InheritableStringParameterDefinition pd = (InheritableStringParameterDefinition) ipdp
						.getParameterDefinition("parameter1");
				assertEquals(pd.getDefaultParameterValue().value, "0");
				found = true;
			}
		}
		assertTrue("The expected parameter was not found",found);
		printInfo("Checking that the parent has only parameter1 = 5...");
		Map<JobPropertyDescriptor, JobProperty<? super InheritanceProject>> propertiesChild = childProject1
				.getProperties();
		found = false;
		for (JobProperty<? super InheritanceProject> prop : propertiesChild
				.values()) {
			if (prop == null) {
				continue;
			}
			if (prop instanceof InheritanceParametersDefinitionProperty) {
				InheritanceParametersDefinitionProperty ipdp = (InheritanceParametersDefinitionProperty) prop;
				InheritableStringParameterDefinition pd = (InheritableStringParameterDefinition) ipdp
						.getParameterDefinition("parameter1");
				assertEquals(pd.getDefaultParameterValue().value, "5");
				found = true;
			}
		}
		assertTrue("The expected parameter was not found",found);
	}

	public void testParameterInheritanceEffect() throws IOException,
			InterruptedException, ExecutionException {
		printInfo("testParameterInheritanceEffect()");
		/* Parent job with parameter = 0 */

		InputStream is1 = new FileInputStream(new File(parentJob));
		parentProject.updateByXml((Source) new StreamSource(is1));
		is1.close();
		/* Child job with no overwriting, thus parameter = 0 */
		printInfo("Creating a child job that inherits parameter1 = 0...");
		InputStream is2 = new FileInputStream(new File(childJob1));
		childProject1.updateByXml((Source) new StreamSource(is2));
		is2.close();
		/* Child job overwriting parameter = 5 */
		printInfo("Creating a child job that overwrites parameter1 = 5...");
		InputStream is3 = new FileInputStream(new File(childJob2));
		childProject2.updateByXml((Source) new StreamSource(is3));
		is3.close();
		printInfo("Checking that we cannot schedule a build for the parent (abstract job)...");
		/*
		 * Testing that we cannot schedule a build for the parent (abstract
		 * project)
		 */
		QueueTaskFuture<InheritanceBuild> queue = parentProject
				.scheduleBuild2(0);
		assertNull("It is possible to build an abstract job, which " +
				"should not be possible", queue);
		printInfo("Scheduling a build for the first child job and checking that the results"
				+ " show that the value of parameter1 is 0...");
		/* Testing that the parameter used in the build is the parent parameter */
		buildAndLookForString(childProject1, "The value is 0");
		printInfo("Scheduling a build for the second child job and checking that the results"
				+ " show that the value of parameter1 is 5...");
		/* Testing that the parameter used in the build is overwritten */
		buildAndLookForString(childProject2, "The value is 5");
	}

	public void testShutdown() throws IOException, InterruptedException,
			ExecutionException, RestartNotSupportedException {
		printInfo("testShutdown()");
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			printInfo("Exiting because windows does not support jenkins restart call...");
			return;
		}
		InputStream is1 = new FileInputStream(new File(parentJob));
		parentProject.updateByXml((Source) new StreamSource(is1));
		is1.close();
		/* Child job with no overwriting, thus parameter = 0 */
		printInfo("Creating a child job...");
		InputStream is2 = new FileInputStream(new File(childJob3));
		childProject3.updateByXml((Source) new StreamSource(is2));
		is2.close();
		printInfo("Scheduling a first build for the child job...");
		/* Testing that the parameter used in the build is the parent parameter */
		InheritanceBuild ib1 = childProject3.scheduleBuild2(0).get();
		printInfo("Scheduling another build and restarting jenkins in while doing it...");
		childProject3.scheduleBuild2(0);
		Jenkins j = Jenkins.getInstance();
		printInfo("Checking that the last build is the first one (the second one was lost)...");
		assertEquals("The build was completed and saved, which should not happen", 
				childProject3.getBuilds().getLastBuild(), ib1);
		/*
		 * TODO: check safe restart without entering a loop printInfo(
		 * "Scheduling a build and safe-restarting jenkins in while doing it..."
		 * ); QueueTaskFuture<InheritanceBuild> ib2 =
		 * childProject3.scheduleBuild2(0); j.safeRestart(); printInfo(
		 * "Checking that the last build is the one before the safe restart");
		 * InheritanceBuild finalIb = ib2.get();
		 * assertEquals(childProject3.getBuilds().getLastBuild(), finalIb);
		 */
	}

	public void testXmlLoad() throws IOException, ServletException {
		/* Testing all the general xml options available here. */
		printInfo("testXmlLoad()");
		assertFalse("The default abstract property for an abstract project " +
				"should be false, and it is not", parentProject.isAbstract);
		parentProject.isAbstract = true;
		assertTrue("The abstract property of a project was not correctly set"
				, parentProject.isAbstract);
		printInfo("Getting the configuration of a project...");
		XmlFile xml = parentProject.getConfigFile();
		printInfo("Saving the configuration...");
		parentProject.save();
		assertFalse("The default abstract property for an abstract project " +
				"should be false, and it is not", childProject1.isAbstract);
		printInfo("Loading the configuration...");
		InputStream is = new FileInputStream(xml.getFile());
		childProject1.updateByXml((Source) new StreamSource(is));
		assertTrue("The xml file from the parent project was not correctly loaded"
				, childProject1.isAbstract);
	}
}