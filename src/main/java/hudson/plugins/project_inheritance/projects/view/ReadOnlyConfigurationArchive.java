/**
 * Copyright (c) 2015-2017, Intel Deutschland GmbH
 * Copyright (c) 2011-2015, Intel Mobile Communications GmbH
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
package hudson.plugins.project_inheritance.projects.view;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.servlet.ServletException;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;



public class ReadOnlyConfigurationArchive implements HttpResponse {
	private final File file;

	public ReadOnlyConfigurationArchive(File file) {
		this.file = file;
	}
	
	private void close() {
		if (file != null && file.exists()) {
			file.delete();
		}
	}
	
	public void doIndex( StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
		URLConnection con = connect();
		//rsp.setHeader("Content-Disposition", "attachment; filename=" + file.getAbsolutePath());
		rsp.setHeader("Content-Disposition", "attachment; filename=" + file.getName());
		InputStream in = con.getInputStream();
		rsp.serveFile(
				req,
				in,
				con.getLastModified(),
				con.getContentLengthLong(),
				file.getName()
		);
		in.close();
		//The file has been read; the request will not be repeated
		this.close();
	}

	public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
		doIndex(req,rsp);
	}

	private URLConnection connect() throws IOException {
		URL res = getURL();
		return res.openConnection();
	}

	public URL getURL() throws MalformedURLException {
		URL res = file.toURI().toURL();
		return res;
	}
}