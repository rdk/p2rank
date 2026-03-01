package cz.siret.prank.geom;

import cz.siret.prank.utils.PerfUtils;
import org.biojava.nbio.structure.Atom;
import org.biojava.nbio.structure.Bond;
import org.biojava.nbio.structure.Element;
import org.biojava.nbio.structure.Group;

import javax.vecmath.Point3d;
import java.util.Arrays;
import java.util.List;

/**
 * lightweight implementation of Atom representing just 3D point with no properties.
 */
public final class Point implements Atom {

    private double x;
    private double y;
    private double z;


    public Point() {
        //
    }

    public Point(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point(double[] coords) {
        setCoords(coords);
    }

    public Point copy() {
        return new Point(x, y, z);
    }

    @Override
    public Element getElement() {
        return Element.C; // return something so it can be used in center-of-mass calculation
    }

    public double dist(Atom a) {
        return PerfUtils.dist(this, a);
    }

    public static Point of(double x, double y, double z) {
        return new Point(x, y, z);
    }

    public static Point copyOf(Atom a) {
        return new Point(a.getX(), a.getY(), a.getZ());
    }

//===============================================================================================//

    @Override
    public Point3d getCoordsAsPoint3d() {
        return new Point3d(x, y, z);
    }

    @Override
    public void setName(String s) {

    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setElement(Element element) {

    }

    @Override
    public void setPDBserial(int i) {

    }

    @Override
    public int getPDBserial() {
        return 0;
    }

    @Override
    public void setCoords(final double[] c) {
        x = c[0];
        y = c[1];
        z = c[2];
    }

    @Override
    public double[] getCoords() {
        double[] coords = new double[3];
        coords[0] = x;
        coords[1] = y;
        coords[2] = z;
        return coords;
    }

    public void setXYZ(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    @Override
    public void setAltLoc(Character character) {
        //
    }

    @Override
    public Character getAltLoc() {
        return null;
    }

    @Override
    public void setOccupancy(float v) {

    }

    @Override
    public float getOccupancy() {
        return 0;
    }

    @Override
    public void setTempFactor(float v) {

    }

    @Override
    public float getTempFactor() {
        return 0;
    }

    @Override
    public Object clone() {
        return copy();
    }

    @Override
    public void setGroup(Group group) {

    }

    @Override
    public Group getGroup() {
        return null;
    }

    @Override
    public void addBond(Bond bond) {

    }

    @Override
    public List<Bond> getBonds() {
        return null;
    }

    @Override
    public void setBonds(List<Bond> list) {

    }

    @Override
    public boolean hasBond(Atom atom) {
        return false;
    }

    @Override
    public short getCharge() {
        return 0;
    }

    @Override
    public void setCharge(short i) {

    }

    @Override
    public String toPDB() {
        return null;
    }

    @Override
    public void toPDB(StringBuffer stringBuffer) {

    }

}
