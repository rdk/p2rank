package cz.siret.prank.features.chemproperties

import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.FeatureCalculator
import cz.siret.prank.features.generic.GenericHeader
import cz.siret.prank.features.generic.GenericVector
import cz.siret.prank.features.tables.PropertyTable
import cz.siret.prank.features.volsite.VolSitePharmacophore
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.PDBUtils
import cz.siret.prank.utils.futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Element

/**
 * Feature vector of Physico-Chemical Properties (and everything else contained in GenericVector additionalVector)
 */
@Slf4j
@CompileStatic
public class ChemVector extends FeatureVector implements Cloneable {

    static final PropertyTable aa5FactorsTable   = PropertyTable.parse(futils.readResource("/tables/aa-5factors.csv"))
    static final PropertyTable aa5PropensitiesTable   = PropertyTable.parse(futils.readResource("/tables/aa-propensities.csv"))

    static final PropertyTable aaPropertyTable   = aa5FactorsTable.join(aa5PropensitiesTable)
    static final PropertyTable atomPropertyTable = PropertyTable.parse(futils.readResource("/tables/atomic-properties.csv"))

    boolean includeVsProps() {
        return Params.INSTANCE.@use_volsite_features
    }

    public double  hydrophobic; // 1=hydrophobic (A/Ala, C/Cys, I/Ile, L/Leu, M/Met, F/Phe, W/Trp, V/Val),   +Pro +Gly
    public double  hydrophilic; // 1=hyrophilic (R/Arg, N/Asn, D/Asp, Q/Gln, E/Glu, K/Lys)
    public double  hydrophatyIndex;

    public double  aliphatic; //1 (A/Ala,  I/Ile, L/Leu,, V/Val)    G/Gly,    P/Pro
    public double  aromatic; //1 (F/Phe, W/Trp, Y/Tyr)
    public double  sulfur; //1 (C/Cys, M/Met)
    public double  hydroxyl; //1 (S/Ser, T/Thr)
    public double  basic; //1 (R/Arg, H/His, K/Lys)
    public double  acidic; //1 (D/Asp, E/Glu)
    public double  amide; //1 (N/Asn, Q/Gln)
    public double  posCharge; // -1=negative charged (D/Asp, E/Glu), 0=uncharged,  1=positive charged (R/Arg, H/Hys, K/Lys)
    public double  negCharge;
    public double  hBondDonor; //1 (R/Arg, K/Lys, V/Val)
    public double  hBondAcceptor; //1 (D/Asp, E/Glu)
    public double  hBondDonorAcceptor; // 1 (N/Asn, Q/Gln, H/His, S/Ser, T/Thr, Y/Tyr)
    public double  polar; //1=polar (R/Arg, N/Asn, D/Asp, Q/Gln, E/Glu, H/His, K/Lys, S/Ser, T/Thr, Y/Tyr)   +Cys
    public double  ionizable
    public double  atoms
    public double  atomDensity
    public double  atomC
    public double  atomO
    public double  atomN
    public double  hDonorAtoms
    public double  hAcceptorAtoms

    public double vsAromatic
    public double vsCation
    public double vsAnion
    public double vsHydrophobic
    public double vsAcceptor
    public double vsDonor

    public GenericVector additionalVector

    ChemVector() {
        this(GenericHeader.EMPTY)
    }

    ChemVector(GenericHeader additionalHeader) {
        this.additionalVector = new GenericVector(additionalHeader)
    }

    @Override
    final List<Double> getVector() {       // TODO: this is horrible

        List<Double> res = new ArrayList<Double>(30+additionalVector.size)

        res.add(hydrophobic)
        res.add(hydrophilic)
        res.add(hydrophatyIndex)
        res.add(aliphatic)
        res.add(aromatic)
        res.add(sulfur)
        res.add(hydroxyl)
        res.add(basic)
        res.add(acidic)
        res.add(amide)
        res.add(posCharge)
        res.add(negCharge)
        res.add(hBondDonor)
        res.add(hBondAcceptor)
        res.add(hBondDonorAcceptor)
        res.add(polar)
        res.add(ionizable)

        res.add(atoms)
        res.add(atomDensity)
        res.add(atomC)
        res.add(atomO)
        res.add(atomN)
        res.add(hDonorAtoms)
        res.add(hAcceptorAtoms)

        if (includeVsProps()) {
            res.add(vsAromatic)
            res.add(vsCation)
            res.add(vsAnion)
            res.add(vsHydrophobic)
            res.add(vsAcceptor)
            res.add(vsDonor)
        }

        if (additionalVector!=null) {
            additionalVector.addTo(res)
        }

        return res
    }

    List<String> getHeader() {
        List<String> res = [
           "hydrophobic",
           "hydrophilic",
           "hydrophatyIndex",
           "aliphatic",
           "aromatic",
           "sulfur",
           "hydroxyl",
           "basic",
           "acidic",
           "amide",
           "posCharge",
           "negCharge",
           "hBondDonor",
           "hBondAcceptor",
           "hBondDonorAcceptor",
           "polar",
           "ionizable",

           "atoms",
           "atomDensity",
           "atomC",
           "atomO",
           "atomN",
           "hDonorAtoms",
           "hAcceptorAtoms"
        ]

        if (includeVsProps()) {
            res.addAll( [
                "vsAromatic",
                "vsCation",
                "vsAnion",
                "vsHydrophobic",
                "vsAcceptor",
                "vsDonor"
            ] )
        }

        if (additionalVector!=null) {
            res.addAll(additionalVector.header.colNames)
        }

        return res
    }

    public static ChemVector forAtom(Atom atom, ChemFeatureExtractor extractor) {
        String residueCode = PDBUtils.normAAcode(PDBUtils.getAtomResidueCode(atom))

        ChemVector p = new ChemVector()

        ChemVector proto = ChemDefaults.AA_DEFAULTS.get(residueCode)
        if (proto!=null) {
            p.copyFrom(proto)
        }

        p.additionalVector = new GenericVector(extractor.headerAdditionalFeatures)

        p.setFromResidueAtom(atom, residueCode, extractor)

        if (atom.element == Element.C) { p.atomC=1 }
        else if (atom.element == Element.O) { p.atomO=1 }
        else if (atom.element == Element.N) { p.atomN=1 }
        else {
            if (atom.name.startsWith("C")) { p.atomC=1 }
            else if (atom.name.startsWith("O")) { p.atomO=1 }
            else if (atom.name.startsWith("N")) { p.atomN=1 }
            else {
                //log.debug "ignore ATOM: "+atom.element  + " | " +atom.getPDBline()
            }
        }

        return p
    }

    /**
     *
     * @param residueCode 3 letter residue code (e.g. "Ala")
     */
    private void setFromResidueAtom(Atom atom, String residueCode, ChemFeatureExtractor extractor) {

        atomDensity = 1

        String an = atom.name // atom name

        if (residueCode == "Ala") {
        } else if (residueCode == "Arg") {
            if(["NE","NH1","NH2"].contains(an))
                hDonorAtoms++
        } else if (residueCode == "Asn") {
            if(an=="ND2") hDonorAtoms++
            if(an=="OD1") hAcceptorAtoms++
        } else if (residueCode == "Asp") {
            if(["OD1","OD2"].contains(an)) hAcceptorAtoms++
        } else if (residueCode == "Cys") {
        } else if (residueCode == "Gln") {
            if(an=="NE2") hDonorAtoms++
            if(an=="OE1") hAcceptorAtoms++
        } else if (residueCode == "Glu") {
            if(["OE1","OE2"].contains(an)) hAcceptorAtoms++
        } else if (residueCode == "Gly") {
        } else if (residueCode == "His") {
            if(["ND1","NE2"].contains(an)) { hDonorAtoms++; hAcceptorAtoms++ }
        } else if (residueCode == "Ile") {
        } else if (residueCode == "Leu") {
        } else if (residueCode == "Lys") {
            if(an=="NZ") hDonorAtoms++
        } else if (residueCode == "Met") {
        } else if (residueCode == "Phe") {
        } else if (residueCode == "Pro") {
        } else if (residueCode == "Ser") {
            if(["OG"].contains(an)) { hDonorAtoms++; hAcceptorAtoms++ }
        } else if (residueCode == "Thr") {
            if(["OG1"].contains(an)) { hDonorAtoms++; hAcceptorAtoms++ }
        } else if (residueCode == "Trp") {
            if(an=="NE1") hDonorAtoms++
        } else if (residueCode == "Tyr") {
            if(["OH"].contains(an)) { hDonorAtoms++; hAcceptorAtoms++ }
        } else if (residueCode == "Val") {
        } else {
            if (!ChemDefaults.AACODES.contains(residueCode) ) {
                log.debug "!! masking unknown residue code:  $residueCode"
                //if (params.mask_unknown_residues) {
                //    log.debug "!!! masking unknown residue code:  $residueCode"
                //} else {
                //    throw new PrankException("unknown residue code: $residueCode")
                //}
            }
        }

        if (includeVsProps()) {
            VolSitePharmacophore.AtomProps va = VolSitePharmacophore.getAtomProperties(atom.name, residueCode)

            vsAromatic = va.aromatic ? 1 : 0
            vsCation = va.cation ? 1 : 0
            vsAnion = va.anion ? 1 : 0
            vsHydrophobic = va.hydrophobic ? 1 : 0
            vsAcceptor = va.acceptor ? 1 : 0
            vsDonor = va.donor ? 1 : 0
        }

        double ATOM_POW = Params.INSTANCE.@atom_table_feat_pow
        boolean KEEP_SGN = Params.INSTANCE.@atom_table_feat_keep_sgn

        for (String property : extractor.atomTableFeatures) {
            double val = getAtomTableValue(atom, property)

            double pval = val
            if (ATOM_POW != 1d) {
                if (KEEP_SGN) {
                    pval = Math.signum(val) * Math.abs( Math.pow(val, ATOM_POW) )
                } else {
                    pval = Math.pow(val, ATOM_POW)
                }
            }

            //log.info "$val^$ATOM_POW = $pval"

            additionalVector.set(property, pval)
        }

        for (String property : extractor.residueTableFeatures) {
            double val = getResidueTableValue(atom, property)

            additionalVector.set(property, val)
        }

        // Calculate extra atom features

        AtomFeatureCalculationContext context = new AtomFeatureCalculationContext(extractor.protein)
        for (FeatureCalculator feature : extractor.extraFeatureSetup.enabledAtomFeatures) {
            double[] values = feature.calculateForAtom(atom, context)
            additionalVector.setValues(feature.header, values)
        }

    }

    private static Double getAtomTableValue(Atom atom, String property) {
        String atomName = PDBUtils.getAtomResidueCode(atom) + "." + atom.name
        Double val = atomPropertyTable.getValue(atomName, property)

        //log.info "atom table value ${atomName}.$property = $val"

        return val==null ? 0d : val
    }

    private static Double getResidueTableValue(Atom atom, String property) {
        Double val = aaPropertyTable.getValue(PDBUtils.getAtomResidueCode(atom), property)
        return val==null ? 0d : val
    }

    /**
     * @return new instance
     */
    public ChemVector copy() {
        return new ChemVector().copyFrom(this)
    }

    /**
     * modifies this instance
     */
    public ChemVector copyFrom(ChemVector p) {

        hydrophobic = p.hydrophobic
        hydrophilic = p.hydrophilic
        hydrophatyIndex = p.hydrophatyIndex
        aliphatic = p.aliphatic
        aromatic = p.aromatic
        sulfur = p.sulfur
        hydroxyl = p.hydroxyl
        basic = p.basic
        acidic = p.acidic
        amide = p.amide
        posCharge = p.posCharge
        negCharge = p.negCharge
        hBondDonor = p.hBondDonor
        hBondAcceptor = p.hBondAcceptor
        hBondDonorAcceptor = p.hBondDonorAcceptor
        polar = p.polar
        ionizable = p.ionizable
        atomDensity = p.atomDensity

        atomC = p.atomC
        atomO = p.atomO
        atomN = p.atomN

        hDonorAtoms = p.hDonorAtoms
        hAcceptorAtoms = p.hAcceptorAtoms

        vsAromatic = p.vsAromatic
        vsCation = p.vsCation
        vsAnion = p.vsAnion
        vsHydrophobic = p.vsHydrophobic
        vsAcceptor = p.vsAcceptor
        vsDonor = p.vsDonor

        additionalVector = p.additionalVector.copy()

        return this
    }

    /**
     * modifies this instance
     */
    public ChemVector multiply(double a) {

        hydrophobic *= a
        hydrophilic *= a
        hydrophatyIndex *= a
        aliphatic *= a
        aromatic *= a
        sulfur *= a
        hydroxyl *= a
        basic *= a
        acidic *= a
        amide *= a
        posCharge *= a
        negCharge *= a
        hBondDonor *= a
        hBondAcceptor *= a
        hBondDonorAcceptor *= a
        polar *= a
        ionizable *= a
        atomDensity *= a

        atomC *= a
        atomO *= a
        atomN *= a

        hDonorAtoms *= a
        hAcceptorAtoms *= a

        vsAromatic *= a
        vsCation *= a
        vsAnion *= a
        vsHydrophobic *= a
        vsAcceptor *= a
        vsDonor *= a

        additionalVector.multiply(a)

        return this
    }

    /**
     * modifies this instance
     */
    public ChemVector add(ChemVector p) {

        hydrophobic += p.hydrophobic
        hydrophilic += p.hydrophilic
        hydrophatyIndex += p.hydrophatyIndex
        aliphatic += p.aliphatic
        aromatic += p.aromatic
        sulfur += p.sulfur
        hydroxyl += p.hydroxyl
        basic += p.basic
        acidic += p.acidic
        amide += p.amide
        posCharge += p.posCharge
        negCharge += p.negCharge
        hBondDonor += p.hBondDonor
        hBondAcceptor += p.hBondAcceptor
        hBondDonorAcceptor += p.hBondDonorAcceptor
        polar += p.polar
        ionizable += p.ionizable
        atomDensity += p.atomDensity

        atomC += p.atomC
        atomO += p.atomO
        atomN += p.atomN

        hDonorAtoms += p.hDonorAtoms
        hAcceptorAtoms += p.hAcceptorAtoms

        vsAromatic += p.vsAromatic
        vsCation += p.vsCation
        vsAnion += p.vsAnion
        vsHydrophobic += p.vsHydrophobic
        vsAcceptor += p.vsAcceptor
        vsDonor += p.vsDonor

        additionalVector.add(p.additionalVector)

        return this
    }

    @Override
    public String toString() {
        return getVector().toListString()
    }

}
