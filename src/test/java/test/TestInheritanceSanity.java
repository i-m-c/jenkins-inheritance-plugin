package test;

import hudson.model.Result;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.Cause.UserIdCause;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.util.VersionNumber;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.commons.lang.SystemUtils;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SleepBuilder;

import test.TestInheritanceMain.XmlProject;

public class TestInheritanceSanity extends HudsonTestCase {
	private static final Logger log = Logger.getLogger(
			TestInheritanceSanity.class.toString()
	);
	
	private void printInfo(String info) {
		log.info("[TestInheritanceSanity] " + info);
	}
	
	@Override
	protected void tearDown() throws Exception {
		printInfo("tearDown()");
		super.tearDown();
	}

	@Override
	protected void setUp() throws Exception {
		printInfo("setUp()");
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
	
	
	public void testConcurrency() throws IOException, InterruptedException {
		printInfo("testConcurrency()");
		if (!canRunTests()) {
			printInfo("Test is skipped, due to incompatibility with OS/Jenkins");
			return;
		}
		
		//Fetch the jenkins instance; which is a valid build host
		Jenkins j = Jenkins.getInstance();
		//Set its executor count to 4, just to be sure
		j.setNumExecutors(4);
		try {
			j.reload();
		} catch (ReactorException ex) {
			fail(ex.getMessage());
		}
		
		//Create a sequence of jobs: Grandparent, Parent and Child
		XmlProject gpp = new XmlProject("GrandParent");
		XmlProject pp = new XmlProject("Parent");
		XmlProject cp = new XmlProject("Child");
		
		//Set the relationships
		cp.addParent(pp.project.getFullName(), "");
		pp.addParent(gpp.project.getFullName(), "");
		
		//Set the parallel execution bit on pp
		pp.project.setConcurrentBuild(true);
		
		final int sleep = 3*1000;
		
		//Now, add a sleeper build ster to the GP
		gpp.project.getBuildersList().add(new SleepBuilder(sleep));
		
		//Build 2 grandparents; wait until they're done and check that they're sequential
		buildTwoandCheckOrder(sleep, 1.0, false, gpp.project);
		
		//Build 2 parents; wait until they're done and check that they're parallel
		buildTwoandCheckOrder(sleep, 1.0, true, pp.project);
		
		//Build 2 children; wait until they're done and check that they're parallel
		buildTwoandCheckOrder(sleep, 1.0, true, cp.project);
	}
	
	// === HELPER METHODS ===
	
	/**
	 * @param sleep
	 * @param eps
	 * @param parallel
	 * @param p
	 */
	private void buildTwoandCheckOrder(long sleep, double eps, boolean parallel, InheritanceProject p) {
		try {
			/* Scheduling the builds.
			 * We need to give the gpp a random parameter, to avoid Jenkins
			 * from mercilessly killing our duplicated jobs, if two of them
			 * happen to be simultaneously in the queue.
			 */
			QueueTaskFuture<InheritanceBuild> f1 = p.scheduleBuild2(
					0, new UserIdCause(),
					new ParametersAction(
							new StringParameterValue("RNG", UUID.randomUUID().toString())
					)
			);
			QueueTaskFuture<InheritanceBuild> f2 = p.scheduleBuild2(
					0, new UserIdCause(),
					new ParametersAction(
							new StringParameterValue("RNG", UUID.randomUUID().toString())
					)
			);
			assertNotNull("First build failed to start, miserably", f1);
			assertNotNull("Second build failed to start, miserably", f2);
			
			InheritanceBuild[] builds = { f1.get(), f2.get() };
			assertNotNull("First build failed to evaluate", builds[0]);
			assertNotNull("Second build failed to evaluate", builds[1]);
			
			checkBuildOrder((long)eps*sleep, parallel, builds);
		} catch (Exception ex) {
			fail(ex.getMessage());
		}
	}
	
	private void checkBuildOrder(long minDuration, boolean parallel, InheritanceBuild... builds) {
		try {
			this.assertBuildStatus(Result.SUCCESS, builds[0]);
			this.assertBuildStatus(Result.SUCCESS, builds[1]);
			
			assertTrue("Build duration too short", builds[0].getDuration() > minDuration);
			assertTrue("Build duration too short", builds[1].getDuration() > minDuration);
			
			if (parallel) {
				//Check if timings overlap
				long minStart = -1;
				long maxEnd = -1;
				for (InheritanceBuild ib : builds) {
					long start = ib.getStartTimeInMillis();
					long end = start + ib.getDuration();
					
					if (minStart >= 0 && maxEnd >= 0) {
						assertTrue(
								"Builds did not run parallel",
								start <= maxEnd && end >= minStart
						);
					}
					minStart = Math.min(minStart, start);
					maxEnd = Math.max(maxEnd, end);
				}
			} else {
				//Check if timings are sequential
				long lastStart = -1;
				long lastEnd = -1;
				for (InheritanceBuild ib : builds) {
					long start = ib.getStartTimeInMillis();
					long end = start + ib.getDuration();
					if (lastStart >= 0 && lastEnd >= 0) {
						assertTrue("Invalid build order", lastEnd <= start);
					}
					lastStart = start;
					lastEnd = end;
				}
			}
			
		} catch (Exception ex) {
			fail(ex.getMessage());
		}
	}
	
}
