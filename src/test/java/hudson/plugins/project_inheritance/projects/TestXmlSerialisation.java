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
package hudson.plugins.project_inheritance.projects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.SleepBuilder;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.DomText;
import com.gargoylesoftware.htmlunit.xml.XmlPage;

import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;

/**
 * This class tests the sanity of the XML serialisation of InheritanceProjects.
 * <p>
 * Mostly, this covers the rewrite of
 * {@link AbstractProject#doConfigDotXml(org.kohsuke.stapler.StaplerRequest, org.kohsuke.stapler.StaplerResponse)}.
 * <p>
 * The main difference here is that the rewrite will show the configuration of
 * the selected version and not (necessarily) what is on disk. 
 * 
 * @author Martin Schroeder
 */
public class TestXmlSerialisation {
	@Rule public final JenkinsRule jRule = new JenkinsRule();
	
	/**
	 * A small dummy publisher, used only for test purposes.
	 */
	public static class DummyPublisher extends Recorder {
		@Override
		public BuildStepMonitor getRequiredMonitorService() {
			return null;
		}
	}
	
	
	public TestXmlSerialisation() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * This test creates an inheritance project with multiple versions and
	 * then checks whether the config.xml is generated properly.
	 * 
	 * @throws IOException in case the jobs can't be created or the XML be generated. 
	 * @throws SAXException in case the config.xml is broken.
	 */
	@Test
	public void testReadXml() throws IOException, SAXException {
		//Create the test project
		InheritanceProject p = jRule.jenkins.createProject(
				InheritanceProject.class, "VersioningTestProject"
		);
		
		// ==== V1 Config ====
		
		//Assign a builder
		p.getRawBuildersList().add(new SleepBuilder(5));
		//Assign a set of initial parameters
		ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
				Arrays.<ParameterDefinition>asList(
						new InheritableStringParameterDefinition("Param1", "V1")
				)
		);
		p.addProperty(pdp);
		
		//Freeze this as the first version
		p.save();
		p.dumpConfigToNewVersion("V1");
		
		// ==== V2 Config ==== 
		
		//Add another builder
		p.getRawBuildersList().add(new SleepBuilder(10));
		//And add a dummy publisher
		p.getRawPublishersList().add(new DummyPublisher());
		
		//Freeze this as the second version
		p.save();
		p.dumpConfigToNewVersion("V2");
		
		// ==== V3 Config ====
		
		//Extend the parameters
		p.removeProperty(ParametersDefinitionProperty.class);
		pdp = new ParametersDefinitionProperty(
				Arrays.<ParameterDefinition>asList(
						new InheritableStringParameterDefinition("Param1", "V3"),
						new InheritableStringParameterDefinition("Param2", "V3")
				)
		);
		p.addProperty(pdp);
		
		//Freeze this as the third version
		p.save();
		p.dumpConfigToNewVersion("V3");
		
		//Set V2 as stable
		p.setVersionStability(2, true);
		
		
		// === BASIC ASSERTIONS ===
		
		//Now, assert that the versions were created
		assertNotNull(p.getVersions());
		assertEquals("Invalid number of versions", 3, p.getVersions().size());
		//That "2" is the latest stable version
		assertEquals("Invalid version marked as stable", (Long) 2L, p.getStableVersion());
		//That "3" is the latest version
		assertEquals("Invalid version marked as stable", (Long) 3L, p.getLatestVersion());
		
		
		// === XML RETRIEVAL SETUP === 
		
		//Get the config.xml via a web query
		WebClient wc = jRule.createWebClient();
		DomText txt;
		
		
		// === FETCH XML WITHOUT VERSION SPEC ===
		XmlPage xml = getConfig(wc, p, null);
		
		//Check that the value of Param1 is "V1" (as in V1, V2)
		txt = getParameter(xml, "Param1");
		assertNotNull("Parameter Param1 missing", txt);
		assertEquals("V1", txt.asText());
		
		//Check that Param2 is not defined (comes in V3)
		txt = getParameter(xml, "Param2");
		assertNull("Parameter Param2 is present, but should not be", txt);
		
		//Check that 2 builders are defined, one with 5 and one with 10 secs time
		List<?> lst = xml.getByXPath("//org.jvnet.hudson.test.SleepBuilder/time/text()");
		assertNotNull(lst);
		assertEquals(2, lst.size());
		assertEquals("5", ((DomText)lst.get(0)).asText());
		assertEquals("10", ((DomText)lst.get(1)).asText());
		
		
		// === FETCH XML WITHOUT VERSION 3 ===
		xml = getConfig(wc, p, 3L);
		
		txt = getParameter(xml, "Param1");
		assertNotNull("Parameter Param1 missing", txt);
		assertEquals("V3", txt.asText());
		
		txt = getParameter(xml, "Param2");
		assertNotNull("Parameter Param2 missing", txt);
		assertEquals("V3", txt.asText());
		
		//Check that 2 builders are defined, one with 5 and one with 10 secs time
		lst = xml.getByXPath("//org.jvnet.hudson.test.SleepBuilder/time/text()");
		assertNotNull(lst);
		assertEquals(2, lst.size());
		assertEquals("5", ((DomText)lst.get(0)).asText());
		assertEquals("10", ((DomText)lst.get(1)).asText());
		
		
		// === FETCH XML WITHOUT VERSION 1 ===
		xml = getConfig(wc, p, 1L);
		
		txt = getParameter(xml, "Param1");
		assertNotNull("Parameter Param1 missing", txt);
		assertEquals("V1", txt.asText());
		
		//Check that Param2 is not defined (comes in V3)
		txt = getParameter(xml, "Param2");
		assertNull("Parameter Param2 is present, but should not be", txt);
		
		//Check that 1 builder is defined, one with 5 seconds
		lst = xml.getByXPath("//org.jvnet.hudson.test.SleepBuilder/time/text()");
		assertNotNull(lst);
		assertEquals(1, lst.size());
		assertEquals("5", ((DomText)lst.get(0)).asText());
		
		
		//Now, at the end, we ensure that all this config XML stuff has not
		//confused Jenkins
		assertTrue(
				"Jenkins has forgotten the project instance. Bad Jenkins!",
				p == jRule.jenkins.getItem("VersioningTestProject")
		);
	}
	
	
	private XmlPage getConfig(
			WebClient wc, InheritanceProject p, Long version
	) throws IOException, SAXException {
		// Generate the URL
		String url = p.getUrl() + "config.xml";
		if (version != null) {
			url += "?version=" + version.toString();
		}
		//Fetch the page
		Page page = wc.goTo(url, "application/xml");
		assertNotNull(page);
		assertEquals(
				"Expected HTTP code 200 when querying config",
				200, page.getWebResponse().getStatusCode()
		);
		if (!(page instanceof XmlPage)) {
			fail(String.format(
					"Expected an XML page, but got: %s",
					page
			));
		}
		return (XmlPage) page;
	}
	
	private DomText getParameter(XmlPage page, String var) {
		String ispdTag = "hudson.plugins.project__inheritance.projects.parameters.InheritableStringParameterDefinition";
		
		DomText txt = (DomText) page.getFirstByXPath(
				"//properties//" + ispdTag + "[name=\"" + var + "\"]/defaultValue/text()"
		);
		
		return txt;
	}
}
