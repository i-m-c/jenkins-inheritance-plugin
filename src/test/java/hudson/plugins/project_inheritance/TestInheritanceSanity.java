/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2015-2017 Intel Deutschland GmbH
 * Copyright (c) 2011-2015 Intel Mobile Communications GmbH
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
package hudson.plugins.project_inheritance;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.model.Cause.UserIdCause;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.Dependency;
import hudson.plugins.project_inheritance.utils.XmlProject;
import jenkins.model.Jenkins;

public class TestInheritanceSanity {
	private static final Logger log = Logger.getLogger(
			TestInheritanceSanity.class.toString()
	);
	
	@Rule
	public JenkinsRule jRule = new JenkinsRule();
	
	
	private void printInfo(String info) {
		log.info("[TestInheritanceSanity] " + info);
	}
	
	@Before
	public void setUp() throws Exception {
		printInfo("setUp()");
	}
	
	@After
	public void tearDown() throws Exception {
		printInfo("tearDown()");
	}
	
	
	@Test
	public void testMissingJobsDetection() throws IOException, InterruptedException {
		printInfo("testMissingJobsDetection()");
		
		//Create projects
		XmlProject leftPar = new XmlProject("left_parent");
		XmlProject rightPar = new XmlProject("right_parent");
		
		XmlProject left = new XmlProject("left");
		left.addParent("left_parent", null);
		
		XmlProject right = new XmlProject("right");
		right.addParent("right_parent", null);
		
		XmlProject child = new XmlProject("child");
		child.addParent("left", null);
		child.addParent("right", null);
		
		//Check, whether no job reports missing parents
		for (XmlProject p : Arrays.asList(child, left, right, leftPar, rightPar)) {
			checkMissingDep(p);
		}
		
		//Add a bad reference to the child
		child.addParent("noSuchJob", null);
		checkMissingDep(leftPar);
		checkMissingDep(rightPar);
		checkMissingDep(left);
		checkMissingDep(right);
		checkMissingDep(child, "noSuchJob");
		child.dropParent("noSuchJob");
		
		//Add a bad reference to "left"
		left.addParent("noSuchJob", null);
		checkMissingDep(leftPar);
		checkMissingDep(rightPar);
		checkMissingDep(left, "noSuchJob");
		checkMissingDep(right);
		checkMissingDep(child, "noSuchJob");
		left.dropParent("noSuchJob");
		
		//Add a bad reference to "right"
		right.addParent("noSuchJob", null);
		checkMissingDep(leftPar);
		checkMissingDep(rightPar);
		checkMissingDep(left);
		checkMissingDep(right, "noSuchJob");
		checkMissingDep(child, "noSuchJob");
		right.dropParent("noSuchJob");
		
		//Add a bad reference to "left_parent"
		leftPar.addParent("noSuchJob", null);
		checkMissingDep(leftPar, "noSuchJob");
		checkMissingDep(rightPar);
		checkMissingDep(left, "noSuchJob");
		checkMissingDep(right);
		checkMissingDep(child, "noSuchJob");
		leftPar.dropParent("noSuchJob");
		
		//Add a bad reference to "right_parent"
		rightPar.addParent("noSuchJob", null);
		checkMissingDep(leftPar);
		checkMissingDep(rightPar, "noSuchJob");
		checkMissingDep(left);
		checkMissingDep(right, "noSuchJob");
		checkMissingDep(child, "noSuchJob");
		rightPar.dropParent("noSuchJob");
	}
	
	private void checkMissingDep(XmlProject p, String... missing) {
		if (missing == null) { missing = new String[0]; }
		Collection<Dependency> deps = p.project.getMissingDependencies();
		List<String> misses = new LinkedList<>();
		for (Dependency dep : deps) {
			misses.add(dep.ref);
		}
		
		assertArrayEquals(
				String.format("Invalid dependencies for %s", p.project.getFullName()),
				missing,
				misses.toArray()
		);
	}
	
	
	@Test
	public void testConcurrency() throws IOException, InterruptedException {
		printInfo("testConcurrency()");
		
		//Fetch the jenkins instance; which is a valid build host
		Jenkins j = jRule.jenkins;
		
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
			jRule.assertBuildStatus(Result.SUCCESS, builds[0]);
			jRule.assertBuildStatus(Result.SUCCESS, builds[1]);
			
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
