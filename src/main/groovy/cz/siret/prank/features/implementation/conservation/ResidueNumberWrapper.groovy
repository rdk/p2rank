package cz.siret.prank.features.implementation.conservation

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.ResidueNumber;

/**
 * TODO remove the wrapper
 * Note: originally wrapper was here to ignore chainId in equals() but that seemed to be a legacy bug. Now it serves no purpose.
 */
@CompileStatic
public class ResidueNumberWrapper {
    private ResidueNumber resNum;

    public ResidueNumberWrapper(ResidueNumber resNum) {
        this.resNum = resNum;
    }

    public ResidueNumber getResNum() {
        return resNum;
    }
    public void setResNum(ResidueNumber resNum) {
        this.resNum = resNum;
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        ResidueNumberWrapper wrapper = (ResidueNumberWrapper) o

        if (resNum != wrapper.resNum) return false

        return true
    }

    int hashCode() {
        return (resNum != null ? resNum.hashCode() : 0)
    }

    @Override
    public String toString() {
        return resNum != null ? resNum.toString() : "null";
    }
    
}