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
package hudson.plugins.project_inheritance.util;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.UUID;

import org.acegisecurity.AccessDeniedException;
import org.apache.commons.io.FileUtils;

import com.google.common.io.Files;

import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.search.Search;
import hudson.search.SearchIndex;
import hudson.security.ACL;
import hudson.security.Permission;
import jenkins.model.Jenkins;
/**
 * This class is a pure mockery of an {@link ItemGroup}, used to handle
 * projects that are created only ephemerally and should not be visible
 * to the Jenkins core.
 * 
 * @author Martin Schroeder
 */
public class MockItemGroup<T extends Job<?,?>> implements ItemGroup<T>, TopLevelItem {
	private final File tmpDir = Files.createTempDir();
	private final String uuid = UUID.randomUUID().toString();
	
	LinkedList<T> items = new LinkedList<T>();
	
	
	public MockItemGroup() {
		//Nothing to do
	}
	
	@Override
	public void finalize() {
		this.clean();
	}
	
	public void clean() {
		if (tmpDir.exists()) {
			try {
				FileUtils.deleteDirectory(tmpDir);
			} catch (IOException e) {
				//Weird, but not too concerning
			}
		}
	}

	@Override
	public File getRootDir() {
		return tmpDir;
	}

	@Override
	public void save() throws IOException {
		//Nothing is ever saved
	}

	@Override
	public String getDisplayName() {
		return uuid;
	}

	@Override
	public String getFullName() {
		return uuid;
	}

	@Override
	public String getFullDisplayName() {
		return uuid;
	}

	@Override
	public Collection<T> getItems() {
		return this.items;
	}

	@Override
	public String getUrl() {
		return uuid;
	}

	@Override
	public String getUrlChildPrefix() {
		return uuid;
	}

	public void addItem(T item) {
		this.items.add(item);
	}
	
	@Override
	public T getItem(String name) throws AccessDeniedException {
		for (T item : this.items) {
			if (name.equals(item.getName())) {
				return item;
			}
		}
		
		return null;
	}

	@Override
	public File getRootDirFor(T child) {
		return new File(tmpDir, child.getFullName());
	}

	@Override
	public void onRenamed(T item, String oldName, String newName) throws IOException {
		//Nothing to do
	}

	@Override
	public void onDeleted(T item) throws IOException {
		// Nothing to do
	}

	// === MOCK-OVERRIDES FOR TopLeveItem COMPATIBILITY ===
	// It is normal that these methods return null/void

	@Override
	public void delete() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getAbsoluteUrl() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<? extends Job> getAllJobs() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		return uuid;
	}

	@Override
	public ItemGroup<? extends Item> getParent() {
		return Jenkins.getInstance().getItemGroup();
	}

	@Override
	public String getRelativeNameFrom(ItemGroup arg0) {
		return uuid;
	}

	@Override
	public String getRelativeNameFrom(Item arg0) {
		return uuid;
	}

	@Override
	public String getShortUrl() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCopiedFrom(Item arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCreatedFromScratch() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onLoad(ItemGroup<? extends Item> arg0, String arg1) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Search getSearch() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SearchIndex getSearchIndex() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSearchName() {
		return uuid;
	}

	@Override
	public String getSearchUrl() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void checkPermission(Permission arg0) throws AccessDeniedException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public ACL getACL() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasPermission(Permission arg0) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public TopLevelItemDescriptor getDescriptor() {
		// TODO Auto-generated method stub
		return null;
	}

}