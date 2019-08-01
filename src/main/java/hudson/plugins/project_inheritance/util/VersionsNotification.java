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
package hudson.plugins.project_inheritance.util;

import hudson.plugins.project_inheritance.util.VersionedObjectStore.Version;

/**
 * A simple class that matches the current state of versioning to a set of
 * messages that might be useful to the user.
 */
public class VersionsNotification {
	private final boolean isNewest;
	private final boolean isStable;
	private final boolean stablesBefore;
	private final boolean stablesAfter;
	private final boolean isWarning;
	private final String notificationMessage;
	private final Version latestStable;
	
	private boolean highlightWarning = false;

	public VersionsNotification(boolean isNewest,
			boolean isStable,
			boolean stablesBefore,
			boolean stablesAfter,
			Version latestStableVersion) {
		this.isNewest = isNewest;
		this.isStable = isStable;
		this.stablesBefore = stablesBefore;
		this.stablesAfter = stablesAfter;
		this.latestStable = latestStableVersion;
		
		StringBuffer msg = new StringBuffer();
		
		//Select the combinations that should render as a warning
		//Basically: Whenever it's non-stable, and something else overrides it
		this.isWarning = (
				(!isStable && stablesAfter) ||
				(!isNewest && !isStable && !stablesBefore && !stablesAfter)
		);
		if (isWarning) {
			msg.append(Messages.VersionsNotification_WARNING());
			msg.append(' ');
		}
		
		if (isNewest) {
			msg.append(Messages.VersionsNotification_VERSION_NEWEST());
		} else {
			msg.append(Messages.VersionsNotification_VERSION_OLDER());
		}
		
		if (isStable) {
			msg.append(Messages.VersionsNotification_VERSION_SELF_STABLE());
		} else {
			msg.append(Messages.VersionsNotification_VERSION_SELF_UNSTABLE());
		}
		msg.append(' ');
		
		if (stablesAfter) {
			msg.append(Messages.VersionsNotification_VERSION_STABLE_FUTURE());
		} else if (stablesBefore) {
			msg.append(Messages.VersionsNotification_VERSION_STABLE_PAST());
		} else {
			msg.append(Messages.VersionsNotification_VERSION_STABLE_NONE());
			msg.append(' ');
			msg.append(Messages.VersionsNotification_VERSION_IMPLICIT());
		}
		msg.append(' ');
		
		msg.append(Messages.VersionsNotification_LATEST());
		msg.append(' ');
		if (latestStableVersion != null) {
			msg.append(latestStableVersion.id);
		} else {
			msg.append("N/A");
		}
		
		this.notificationMessage = msg.toString();
	}

	public boolean isNewest() {
		return isNewest;
	}

	public boolean isStable() {
		return isStable;
	}

	public boolean hasStablesBefore() {
		return stablesBefore;
	}
	
	public boolean hasStablesAfter() {
		return stablesAfter;
	}
	
	public boolean isWarning() {
		return isWarning;
	}
	
	/**
	 * Returns the message associated with this notification
	 * @return a message that can be empty, but is never null.
	 */
	public String getNotificationMessage() {
		return notificationMessage;
	}

	public boolean isHighlightWarning() {
		return highlightWarning;
	}
	
	/**
	 * @return True or False, depending if all versions of the project are unstable,
	 * meaning no version has been marked as stable
	 */
	public boolean areAllVersionsUnstable() {
		return !(isStable || stablesAfter || stablesBefore);
	}
}
