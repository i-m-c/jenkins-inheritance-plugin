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
package hudson.plugins.project_inheritance.projects.parameters;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * The {@link InheritableStringParameterReferenceDefinition} class extends
 * {@link InheritableStringParameterDefinition}, but does not actually need
 * any of the fields it defines, except for the parameter name and value.
 * <p>
 * As such, this converter will make ensure that all
 * {@link InheritableStringParameterReferenceDefinition} instances only
 * serialise the actually needed data to and from XML.
 * <p>
 * Do note that this converter is registered by the Reference class itself.
 * This will make the connection between these classes better visible.
 * 
 * @author Martin Schroeder
 */
/*package*/ class InheritableParameterReferenceConverter implements Converter {

	@SuppressWarnings("rawtypes")
	@Override
	public boolean canConvert(Class type) {
        if (type == null) { return false; }
		//Only convert the parent class
		return type.equals(InheritableStringParameterReferenceDefinition.class);
	}

	@Override
	public void marshal(
			Object source,
			HierarchicalStreamWriter writer,
			MarshallingContext context) {
		if (!(source instanceof InheritableStringParameterReferenceDefinition)) {
			return;
		}
		InheritableStringParameterReferenceDefinition param =
				(InheritableStringParameterReferenceDefinition) source;
		//The ONLY relevant fields are name and defaultValue
		writer.startNode("name");
		writer.setValue(param.getName());
		writer.endNode();
		
		writer.startNode("defaultValue");
		writer.setValue(param.getDefaultValue());
		writer.endNode();
	}

	@Override
	public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
		String pName = null;
		String pValue = null;
		while (reader.hasMoreChildren()) {
			reader.moveDown();
			String node = reader.getNodeName();
			String value = reader.getValue();
			
			if ("name".equals(node)) {
				pName = value;
			} else if ("defaultValue".equals(node)) {
				pValue = value;
			} else {
				//Additional fields are simply ignored
			}
			reader.moveUp();
		}
		
		if (pName == null) {
			throw new ConversionException("Missing parameter 'name' field");
		}
		if (pValue == null) {
			pValue = "";
		}
		return new InheritableStringParameterReferenceDefinition(
				pName, pValue
		);
	}
}
