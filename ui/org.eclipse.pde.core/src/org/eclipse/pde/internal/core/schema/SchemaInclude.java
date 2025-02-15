/*******************************************************************************
 *  Copyright (c) 2000, 2013 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.core.schema;

import java.io.PrintWriter;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.ischema.ISchema;
import org.eclipse.pde.internal.core.ischema.ISchemaDescriptor;
import org.eclipse.pde.internal.core.ischema.ISchemaInclude;
import org.eclipse.pde.internal.core.ischema.ISchemaObject;

public class SchemaInclude extends SchemaObject implements ISchemaInclude {

	private static final long serialVersionUID = 1L;

	private String fLocation;

	private ISchema fIncludedSchema;

	private final boolean fAbbreviated;

	private final SchemaProvider schemaProvider;

	public SchemaInclude(ISchemaObject parent, String location, boolean abbreviated) {
		this(parent, location, abbreviated, null);
	}

	/**
	 * Creates a new schema include describing an included schema at the given
	 * location. An optional search path may be provided to assist in finding
	 * the included schema.
	 *
	 * @param parent
	 *            parent object, should be the schema containing this include
	 * @param location
	 *            the string location from the schema xml
	 * @param abbreviated
	 *            whether the schema is following the abbreviated syntax
	 * @param schemaProvider
	 *            Provider to look for the included schema, may be
	 *            <code>null</code>
	 */
	public SchemaInclude(ISchemaObject parent, String location, boolean abbreviated, SchemaProvider schemaProvider) {
		super(parent, location);
		fLocation = location;
		fAbbreviated = abbreviated;
		this.schemaProvider = schemaProvider;
	}

	/**
	 * @see org.eclipse.pde.internal.core.ischema.ISchemaInclude#getLocation()
	 */
	@Override
	public String getLocation() {
		return fLocation;
	}

	/**
	 * @see org.eclipse.pde.internal.core.ischema.ISchemaInclude#setLocation(java.lang.String)
	 */
	@Override
	public void setLocation(String location) throws CoreException {
		String oldValue = this.fLocation;
		this.fLocation = location;
		fIncludedSchema = null;
		getSchema().fireModelObjectChanged(this, P_LOCATION, oldValue, location);
	}

	/**
	 * @see org.eclipse.pde.core.IWritable#write(java.lang.String,
	 *      java.io.PrintWriter)
	 */
	@Override
	public void write(String indent, PrintWriter writer) {
		writer.print(indent);
		writer.println("<include schemaLocation=\"" + fLocation + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public ISchema getIncludedSchema() {
		if (fIncludedSchema != null) {
			return fIncludedSchema;
		}
		ISchemaDescriptor descriptor = getSchema().getSchemaDescriptor();
		if (fAbbreviated) {
			SchemaRegistry registry = PDECore.getDefault().getSchemaRegistry();
			fIncludedSchema = registry.getIncludedSchema(descriptor, fLocation);
		} else if (fIncludedSchema == null && schemaProvider != null) {
			fIncludedSchema = schemaProvider.createSchema(descriptor, fLocation);
		}
		return fIncludedSchema;
	}

	@Override
	public void dispose() {
		if (fIncludedSchema != null && !fIncludedSchema.isDisposed()) {
			fIncludedSchema.dispose();
			fIncludedSchema = null;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ISchemaInclude other) {
			if (fLocation != null) {
				return fLocation.equals(other.getLocation());
			}
			return other.getLocation() == null;
		}
		return false;
	}
}
