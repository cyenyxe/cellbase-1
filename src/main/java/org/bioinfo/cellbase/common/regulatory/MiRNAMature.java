package org.bioinfo.cellbase.common.regulatory;

/**
 *
 * @author Cristina Yenyxe Gonzalez Garcia <cgonzalez@cipf.es>
 */
public class MiRNAMature {

    public String miRBaseAccession;
    public String miRBaseID;
    public String sequence;

    public MiRNAMature() {
    }

    public MiRNAMature(String miRBaseAccession, String miRBaseID, String sequence) {
        this.miRBaseAccession = miRBaseAccession;
        this.miRBaseID = miRBaseID;
        this.sequence = sequence;
    }

    public String getMiRBaseAccession() {
        return miRBaseAccession;
    }

    public void setMiRBaseAccession(String miRBaseAccession) {
        this.miRBaseAccession = miRBaseAccession;
    }

    public String getMiRBaseID() {
        return miRBaseID;
    }

    public void setMiRBaseID(String miRBaseID) {
        this.miRBaseID = miRBaseID;
    }

    public String getSequence() {
        return sequence;
    }

    public void setSequence(String sequence) {
        this.sequence = sequence;
    }
}