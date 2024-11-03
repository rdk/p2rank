package cz.siret.prank.domain.labeling;

import org.biojava.nbio.structure.Atom;
import org.biojava.nbio.structure.Bond;
import org.biojava.nbio.structure.Element;
import org.biojava.nbio.structure.Group;

import javax.vecmath.Point3d;
import java.util.List;

/**
 * Annotated point used for generating visualizations and pocket predictions.
 */
public class LabeledPoint implements Atom {

    private Atom point;

    private double score = Double.NaN;
    private double transformedScore = 0d;

    private boolean predicted;
    private boolean observed;

    /**
     * pocket number <br>
     * 0 = no pocket
     */
    private int pocket = 0;

    public LabeledPoint(Atom point, boolean observed, boolean predicted) {
        this.point = point;
        this.predicted = predicted;
        this.observed = observed;
    }

    public LabeledPoint(Atom point, boolean observed, boolean predicted, double score) {
        this.point = point;
        this.predicted = predicted;
        this.observed = observed;
        this.score = score;
    }

    public LabeledPoint(Atom point) {
        this.point = point;
        this.predicted = false;
        this.observed = false;
    }

    public LabeledPoint(Atom point, boolean observed) {
        this.point = point;
        this.predicted = false;
        this.observed = observed;
    }

//===============================================================================================//

    public Atom getPoint() {
        return point;
    }

    public void setPoint(Atom point) {
        this.point = point;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getTransformedScore() {
        return transformedScore;
    }

    public void setTransformedScore(double transformedScore) {
        this.transformedScore = transformedScore;
    }

    public boolean getPredicted() {
        return predicted;
    }

    public boolean isPredicted() {
        return predicted;
    }

    public void setPredicted(boolean predicted) {
        this.predicted = predicted;
    }

    public boolean getObserved() {
        return observed;
    }

    public boolean isObserved() {
        return observed;
    }

    public void setObserved(boolean observed) {
        this.observed = observed;
    }

    public int getPocket() {
        return pocket;
    }

    public void setPocket(int pocket) {
        this.pocket = pocket;
    }

//===============================================================================================//

    @Override
    public Point3d getCoordsAsPoint3d() {
        return point.getCoordsAsPoint3d();
    }

    @Override
    public void setName(String s) {
        point.setName(s);
    }

    @Override
    public String getName() {
        return point.getName();
    }

    @Override
    public void setElement(Element element) {
        point.setElement(element);
    }

    @Override
    public Element getElement() {
        return point.getElement();
    }

    @Override
    public void setPDBserial(int i) {
        point.setPDBserial(i);
    }

    @Override
    public int getPDBserial() {
        return point.getPDBserial();
    }

    @Override
    public void setCoords(double[] doubles) {
        point.setCoords(doubles);
    }

    @Override
    public double[] getCoords() {
        return point.getCoords();
    }

    @Override
    public void setX(double v) {
        point.setX(v);
    }

    @Override
    public void setY(double v) {
        point.setY(v);
    }

    @Override
    public void setZ(double v) {
        point.setZ(v);
    }

    @Override
    public double getX() {
        return point.getX();
    }

    @Override
    public double getY() {
        return point.getY();
    }

    @Override
    public double getZ() {
        return point.getZ();
    }

    @Override
    public void setAltLoc(Character character) {
        point.setAltLoc(character);
    }

    @Override
    public Character getAltLoc() {
        return point.getAltLoc();
    }

    @Override
    public void setOccupancy(float v) {
        point.setOccupancy(v);
    }

    @Override
    public float getOccupancy() {
        return point.getOccupancy();
    }

    @Override
    public void setTempFactor(float v) {
        point.setTempFactor(v);
    }

    @Override
    public float getTempFactor() {
        return point.getTempFactor();
    }

    @Override
    public Object clone() {
        return point.clone();
    }

    @Override
    public void setGroup(Group group) {
        point.setGroup(group);
    }

    @Override
    public Group getGroup() {
        return point.getGroup();
    }

    @Override
    public void addBond(Bond bond) {
        point.addBond(bond);
    }

    @Override
    public List<Bond> getBonds() {
        return point.getBonds();
    }

    @Override
    public void setBonds(List<Bond> list) {
        point.setBonds(list);
    }

    @Override
    public boolean hasBond(Atom atom) {
        return point.hasBond(atom);
    }

    @Override
    public short getCharge() {
        return point.getCharge();
    }

    @Override
    public void setCharge(short i) {
        point.setCharge(i);
    }

    @Override
    public String toPDB() {
        return point.toPDB();
    }

    @Override
    public void toPDB(StringBuffer stringBuffer) {
        point.toPDB(stringBuffer);
    }

}
