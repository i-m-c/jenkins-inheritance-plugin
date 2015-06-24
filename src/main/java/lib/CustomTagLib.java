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
    
    void blankEntry(Map<?,?> args, Closure<?> body);
    void blankEntry(Closure<?> body);
    void blankEntry(Map<?,?> args);
    void blankEntry();
}
