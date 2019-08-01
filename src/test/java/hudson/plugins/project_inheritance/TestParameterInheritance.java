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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.cli.BuildCommand.CLICause;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition.IModes;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition.WhitespaceMode;
import hudson.plugins.project_inheritance.projects.references.SimpleProjectReference;

public class TestParameterInheritance {
	private static final Logger log = Logger.getLogger(
			TestParameterInheritance.class.toString()
	);
	
	@Rule public final JenkinsRule jRule = new JenkinsRule();
	
	/**
	 * This provides the same value as the ParametersAction
	 * "KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME" static field.
	 * The reason it needs to be done that way is because it is marked as
	 * restricted to "noExternalUse", which is plainly idiotic.
	 */
	private final static String paramSysProp = ParametersAction.class.getName() + ".keepUndefinedParameters";
	private String oldParamSysPropVal = "";
	
	@Before
	public void setUp() {
		//These tests only work right if arbitrary parameters are permitted
		oldParamSysPropVal = System.getProperty(paramSysProp);
		System.setProperty(paramSysProp, "true");
	}
	
	@After
	public void tearDown() {
		if (oldParamSysPropVal == null) {
			System.getProperties().remove(paramSysProp);
		} else {
			System.setProperty(paramSysProp, oldParamSysPropVal);
		}
	}
	
	
	@Test
	public void testParameters()
			throws IOException, InterruptedException, ExecutionException {
		// Check all permutations of parameter assignments
		List<List<StringParameterValue>> candidateLists = Arrays.asList(
				Collections.<StringParameterValue>emptyList(),
				Arrays.asList(
						new StringParameterValue("Foo", "Bar")
				),
				Arrays.asList(
						new StringParameterValue("Foo", "Spam")
				),
				Arrays.asList(
						new StringParameterValue("Ni", "Narf"),
						new StringParameterValue("Foo", "Poit")
				)
		);
		
		//Loop 3x over project, parent and extra variables
		//That makes 4^3 = 64 tests
		int cnt = 1;
		for (List<StringParameterValue> proV : candidateLists) {
			for (List<StringParameterValue> parV : candidateLists) {
				for (List<StringParameterValue> extV : candidateLists) {
					log.info(String.format(
							"Running parameter permutation test #%d:\n\tproV=%s\n\tparV=%s\n\textV=%s",
							cnt, proV, parV, extV
					));
					this.checkBuildWithParameters(
							String.format("Test-%d", cnt++),
							proV, parV, extV
					);
				}
			}
		}
		
	}
	
	/**
	 * This tests the whitespace trimming modes of inheritance parameters
	 * 
	 * @throws IOException 
	 * @throws TimeoutException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	@Test
	public void testWhitespaces() throws IOException, InterruptedException, ExecutionException, TimeoutException {
		InheritanceProject ip = jRule.jenkins.createProject(
				InheritanceProject.class,
				"WhitespaceProject"
		);
		String pName = "VAR";
		String pVal = "   A  B   ";
		
		WhitespaceMode[] wsModes = {
				WhitespaceMode.KEEP,
				WhitespaceMode.TRIM
		};
		
		for (WhitespaceMode wsMode : wsModes) {
			//Creating a parameter with that flag
			InheritableStringParameterDefinition ispd = new InheritableStringParameterDefinition(
					"VAR", pVal, "",
					IModes.OVERWRITABLE,
					false, false,
					wsMode,
					false
			);
			ip.addProperty(new ParametersDefinitionProperty(ispd));
			
			//Build once
			QueueTaskFuture<InheritanceBuild> future =
					ip.scheduleBuild2(0, new CLICause());
			InheritanceBuild ib = future.get(30, TimeUnit.SECONDS);
			
			//Check if parameter kept spaces
			ParametersAction pa = ib.getAction(ParametersAction.class);
			Assert.assertNotNull(pa);
			ParameterValue pv = pa.getParameter(pName);
			Assert.assertNotNull(pv);
			
			//Now, assert the value
			switch(wsMode) {
				case KEEP:
					Assert.assertEquals(
							"Whitespaces were not kept",
							pVal,
							pv.getValue()
					);
					break;
				case TRIM: 
					Assert.assertEquals(
							"Whitespaces were not trimmed",
							StringUtils.trim(pVal),
							pv.getValue()
					);
					break;
				default:
					break;
			}
			
		}
		
	}
	
	
	
	// === HELPER METHODS ===
	
	private void checkBuildWithParameters(
			String projectName,
			List<StringParameterValue> projectValues,
			List<StringParameterValue> parentValues,
			List<StringParameterValue> extraValues
	) throws IOException, InterruptedException, ExecutionException {
		//Create the base-job
		InheritanceProject ip = jRule.jenkins.createProject(
				InheritanceProject.class,
				projectName
		);
		//Add parameters to it -- if any
		if (projectValues != null && !projectValues.isEmpty()) {
			this.addParameters(ip, projectValues);
		}
		
		//Check if a parent needs to be created
		if (parentValues != null && !parentValues.isEmpty()) {
			String parName = "Parent_" + projectName;
			InheritanceProject parent = jRule.jenkins.createProject(
					InheritanceProject.class,
					parName
			);
			//Add parameter values to parent
			this.addParameters(parent, parentValues);
			
			//Add reference to child job
			ip.addParentReference(new SimpleProjectReference(parName));
		}
		
		//Check whether additional parameters need to be passed in
		ParametersAction pa = null;
		if (extraValues != null && !extraValues.isEmpty()) {
			pa = new ParametersAction(
					new LinkedList<ParameterValue>(extraValues)
			);
		}
		
		//Build the build
		InheritanceBuild ib = (pa != null)
				? ip.scheduleBuild2(0, new CLICause(), pa).get()
				: ip.scheduleBuild2(0, new CLICause()).get();
		Assert.assertNotNull("Build should've run", ib);
		Assert.assertEquals("Build should have succeeded", Result.SUCCESS, ib.getResult());
		
		//Compute the values that are to be expected from such a build
		HashMap<String, String> expected = new HashMap<>();
		for (List<StringParameterValue> lst : Arrays.asList(parentValues, projectValues, extraValues)) {
			for (StringParameterValue spv : lst) {
				expected.put(spv.getName(), spv.value);
			}
		}
		
		//Compute the values that are actually in that build
		HashMap<String, String> actual = new HashMap<>();
		for (ParametersAction ba : ib.getActions(ParametersAction.class)) {
			for (ParameterValue pv : ba.getParameters()) {
				actual.put(
						pv.getName(),
						(String) pv.getValue()
				);
			}
		}
		
		//Instead of just asserting the equality (which is sufficient), we actually
		//want to know WHICH parameter was wrong
		for (String eKey : expected.keySet()) {
			Assert.assertTrue(
					String.format("Expected parameter '%s' not present", eKey),
					actual.containsKey(eKey)
			);
			Assert.assertEquals(
					String.format("Invalid value of '%s'", eKey),
					expected.get(eKey),
					actual.get(eKey)
			);
		}
		//Checking whether there are unexpected keys
		for (String aKey : actual.keySet()) {
			Assert.assertTrue(
					String.format("Actual parameter '%s' was not expected", aKey),
					expected.containsKey(aKey)
			);
			//No need to check values, was done above
		}
	}
	
	private void addParameters(
			InheritanceProject ip, List<StringParameterValue> values) throws IOException {
		List<ParameterDefinition> pDefs = new LinkedList<>();
		for (StringParameterValue pv : values) {
			pDefs.add(new StringParameterDefinition(pv.getName(), pv.value));
		}
		ParametersDefinitionProperty pdp =
				new ParametersDefinitionProperty(pDefs);
		ip.addProperty(pdp);
	}
}
