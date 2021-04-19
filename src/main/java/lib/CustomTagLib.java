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
package lib;

import groovy.lang.Closure;

import java.util.Map;

import org.kohsuke.stapler.jelly.groovy.TagLibraryUri;
import org.kohsuke.stapler.jelly.groovy.TypedTagLibrary;

@TagLibraryUri("/form")
public interface CustomTagLib extends TypedTagLibrary {
    void colored_block(Map<?,?> args, Closure<?> body);
    void colored_block(Closure<?> body);
    void colored_block(Map<?,?> args);
    void colored_block();
    
    void id_block(Map<?,?> args, Closure<?> body);
    void id_block(Closure<?> body);
    void id_block(Map<?,?> args);
    void id_block();
    
    void range(Map<?,?> args, Closure<?> body);
    void range(Closure<?> body);
    void range(Map<?,?> args);
    void range();
    
    void hetero_list(Map<?,?> args, Closure<?> body);
    void hetero_list(Closure<?> body);
    void hetero_list(Map<?,?> args);
    void hetero_list();
    
    void leftExpandButton(Map<?,?> args, Closure<?> body);
    void leftExpandButton(Closure<?> body);
    void leftExpandButton(Map<?,?> args);
    void leftExpandButton();
    
    void expandHyperlink(Map<?,?> args, Closure<?> body);
    void expandHyperlink(Closure<?> body);
    void expandHyperlink(Map<?,?> args);
    void expandHyperlink();
    
    void redirectTask(Map<?,?> args, Closure<?> body);
    void redirectTask(Closure<?> body);
    void redirectTask(Map<?,?> args);
    void redirectTask();
}
