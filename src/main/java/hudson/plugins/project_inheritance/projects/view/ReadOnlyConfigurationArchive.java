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
		rsp.serveFile(req, in, con.getLastModified(), con.getContentLength(), file.getName());
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