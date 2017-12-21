package jury;

import java.sql.Date;

/**
 * 
 */
public class FollowUp extends ClinicalEntity
{
    String protocol;
    String nextAction;
    Date dueDate;
    
    @Override
    public String toString()
    {
        return("Protocol: " + protocol + "\nNext action: " + nextAction +
                "\nDue date: " + dueDate);
    }
}
