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

    public final double[] coords;

    public Point() {
        coords = new double[3];
    }

    public Point(double x, double y, double z) {
        coords = new double[3];
        coords[0] = x;
        coords[1] = y;
        coords[2] = z;
    }

    public Point(double[] coords) {
        this.coords = coords;
    }

    public Point copy() {
        return new Point(Arrays.copyOf(coords, 3));
    }

    @Override
    public Element getElement() {
        return Element.C; // return something so it can be used in center-of-mass calculation
    }

    public double dist(Atom a) {
        return PerfUtils.dist(coords, a.getCoords());
    }

    public static Point of(double x, double y, double z) {
        return new Point(x, y, z);
    }

    public static Point copyOf(Atom a) {
        return new Point(Arrays.copyOf(a.getCoords(), 3));
    }

//===============================================================================================//

    @Override
    public Point3d getCoordsAsPoint3d() {
        return new Point3d(coords[0],coords[1],coords[2]);
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
    public void setCoords(double[] c) {

    }

    @Override
    public double[] getCoords() {
        return coords;
    }

    public void setXYZ(double x, double y, double z) {
        coords[0] = x;
        coords[1] = y;
        coords[2] = z;
    }

    public double getX() {
        return coords[0];
    }

    public void setX(double x) {
        coords[0] = x;
    }

    public double getY() {
        return coords[1];
    }

    public void setY(double y) {
        coords[1] = y;
    }

    public double getZ() {
        return coords[2];
    }

    @Override
    public void setAltLoc(Character character) {

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

    public void setZ(double z) {
        coords[2] = z;
    }


    @Override
    public String toPDB() {
        return null;
    }

    @Override
    public void toPDB(StringBuffer stringBuffer) {

    }

}
