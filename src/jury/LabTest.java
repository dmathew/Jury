package jury;

/**
 * Each LabTest object represents a lab test conducted.
 */
public class LabTest extends ClinicalEntity {
    int id;
    String result;
    
    @Override
    public String toString()
    {
        return "Name: " + this.name + " [test id: " + id + "]\n"
                + "Result: " + result + "\n";
    }
}
