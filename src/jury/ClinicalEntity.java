
package jury;

/**
 * This is the base class for several clinical entities like Medication, LabTest
 * etc. Having these derive from the same base class enables polymorphism while 
 * parsing protocols that use these.
 */
public class ClinicalEntity {
    public String name;
}
