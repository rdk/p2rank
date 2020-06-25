package cz.siret.prank.features.implementation.conservation

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.ResidueNumber;

/**
 * TODO remove the wrapper
 * Note: originally wrapper was here to ignore chainId in equals() but that seemed to be a legacy bug. Now it server no purpose.
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

//    /**
//     * Check if the seqNum and insertion code are equivalent,
//     * ignoring the chain. Copied from new BioJava5.
//     */
//    public static boolean equalsPositional(ResidueNumber r1, ResidueNumber r2) {
//        if (r1 == r2)
//            return true;
//        if (r2 == null)
//            return false;
//        if (r1.getInsCode() == null) {
//            if (r2.getInsCode() != null)
//                return false;
//        } else if (!r1.getInsCode().equals(r2.getInsCode()))
//            return false;
//        if (r1.getSeqNum() == null) {
//            if (r2.getSeqNum() != null)
//                return false;
//        } else if (!r1.getSeqNum().equals(r2.getSeqNum()))
//            return false;
//
//        return true;
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this.is(o)) return true;
//        if (o.is(null) || getClass() != o.getClass()) return false;
//
//        ResidueNumberWrapper that = (ResidueNumberWrapper) o;
//
//        return resNum != null ? equalsPositional(resNum,that.resNum) : that.resNum == null;
//    }
//
//    @Override
//    public int hashCode() {
//        if (resNum == null) return 0;
//        final int prime = 31;
//        int result = 1;
//        result = prime * result + ((resNum.getInsCode() == null) ? 0 : resNum.getInsCode().hashCode());
//        result = prime * result + ((resNum.getSeqNum() == null) ? 0 : resNum.getSeqNum().hashCode());
//        return result;
//    }

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