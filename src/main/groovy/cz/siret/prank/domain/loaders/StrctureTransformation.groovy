package cz.siret.prank.domain.loaders

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Structure

import java.util.function.Consumer

/**
 *
 */
@CompileStatic
class StructureTransformation {

    private String name
    private Consumer<Structure> inplaceStructureTransformation

    StructureTransformation(String name, Consumer<Structure> inplaceStructureTransformation) {
        this.name = name
        this.inplaceStructureTransformation = inplaceStructureTransformation
    }

    String getName() {
        return name
    }

    Consumer<Structure> getInplaceStructureTransformation() {
        return inplaceStructureTransformation
    }
    
}
