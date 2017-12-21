/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jury;

import nu.xom.Element;
import nu.xom.Node;

/**
 *
 * @author Danny
 */
public class Medication extends ClinicalEntity {
    String genericName;
    String strength;
    String strengthUnit;
    int duration;
    String durationType;
    String frequency;
    
    /**
     * Reads details of a medication from a medication reference (medref) in a state node.
     * A medref looks like this:
     * <med>
     *   <name>Enalapril</name>
     *   <dosage unit="mg">5</dosage>
     *   <frequency>OD</frequency>
     *   <duration type="days">15</duration>
     * </med>
     * @return A medication object populated with the values picked up from the XML.
     */
    public Medication ReadFromXML(Node medref)
    {
        Element dosage = (Element)medref.query("child::dosage").get(0);
        Element duration = (Element)medref.query("child::duration").get(0);
        
        this.genericName = medref.query("child::name").get(0).getValue();
        this.strength = dosage.getValue();
        this.strengthUnit = dosage.getAttributeValue("unit");
        this.frequency = medref.query("child::frequency").get(0).getValue();
        this.duration = Integer.parseInt(duration.getValue());
        this.durationType = duration.getAttributeValue("type");
        
        if(Debug.state && Debug.dbgMedication) {
            System.out.println(this);
        }
        return this;
    }
    
    @Override
    public String toString()
    {
        String ret;
        ret = "Name: " + name + " (" + genericName + ")\n";
        ret += "Strength: " + strength + " (" + strengthUnit + ")\n";
        ret += "Duration: " + duration + " (" + durationType + ")\n";
        ret += "Frequency: " + frequency + "\n";
        return ret;
    }
}
