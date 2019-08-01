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
package hudson.plugins.project_inheritance.util.exceptions;

public class HttpStatusException extends Exception {
	private static final long serialVersionUID = -7244196611069661093L;
	
	public final int status;
	
	public HttpStatusException(int status, String msg) {
		super(msg);
		this.status = status;
	}

	public HttpStatusException(int status, Throwable cause) {
		super(cause);
		this.status = status;
	}

	public HttpStatusException(int status, String msg, Throwable cause) {
		super(msg, cause);
		this.status = status;
	}
}
