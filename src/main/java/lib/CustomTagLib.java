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
    
    void range(Map<?,?> args, Closure<?> body);
    void range(Closure<?> body);
    void range(Map<?,?> args);
    void range();
}
