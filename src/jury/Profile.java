
package jury;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Date;
import java.text.DateFormat;
import java.util.GregorianCalendar;
import java.util.Properties;

/**
 *
 * @author dany
 */
public class Profile {

    public int personid;
    public String policyId;
    public Date dob;
    private int currentVisitid;
    private Date currentVisitDate;
    int age, ageDays;
    String gender; //"M" or "F"
    
    boolean isHypertensive, isDiabetic, isHyperlipidemic, isStrokePatient;
    boolean personExists;
    boolean firstVisit;
    Date lastAuditedVisitDate;
    
    Connection protoverifierConn = null;
    Connection ictph_cleanConn = null;
    
    public Profile(int personid, int currentVisitid)
    {
        this.personid = personid;
        this.currentVisitid = currentVisitid;
        personExists = true;
        
        ictph_cleanConn = Database.ConnectToDatabase(Database.Db.ICTPH_CLEAN);
        protoverifierConn = Database.ConnectToDatabase(Database.Db.PROTOVERIFIER);
        currentVisitDate = GetCurrentVisitDate();
        
        Statement stmt = null;
        String query = null;
        query = "select isHypertensive, isDiabetic, isHyperlipidemic, "
                + "isStrokePatient, lastAuditedVisitDate, dob, gender "
                + "from profile where personid = " + personid;
        try {
            stmt = protoverifierConn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                this.isHypertensive = rs.getBoolean("isHypertensive");
                this.isDiabetic = rs.getBoolean("isDiabetic");
                this.isHyperlipidemic = rs.getBoolean("isHyperlipidemic");
                this.isStrokePatient = rs.getBoolean("isStrokePatient");
                this.lastAuditedVisitDate = rs.getDate("lastAuditedVisitDate");
                this.dob = rs.getDate("dob");
                this.gender = rs.getString("gender");
                stmt.close();
            }
            else {
                stmt.close();
                firstVisit = true;
                personExists = BuildProfile();
                if(!personExists)
                    return;
            }
        }
        catch(Exception e) {
            System.out.println("Profile: " + e.toString());
            e.printStackTrace();
        }
        if(dob!=null && dob.equals(""))
            dob = null;
        long ageMillis;
        if(dob != null) {
            ageMillis = currentVisitDate.getTime() - dob.getTime();
        } 
        else 
            ageMillis = 0;
        int days = (int) (ageMillis / (1000 * 60 * 60 * 24));
        this.age = days / 365;
        this.ageDays = days % 365;  // especially for those of age < 1 year
        if(Debug.state && Debug.dbgProfile)
            System.out.println("Age: " + age + "years " + ageDays + "days");
        
        if(lastAuditedVisitDate.before(currentVisitDate))
            UpdateProfile();
        
        if(Debug.state && Debug.dbgProfile) {
            System.out.println("isHypertensive: " + isHypertensive);
            System.out.println("isDiabetic: " + isDiabetic);
            System.out.println("isHyperlipidemic: " + isHyperlipidemic);
        }
    }
    
    private Date GetCurrentVisitDate()
    {
        Date d = null;
        String query;
        Statement stmt;
        
        query = "select date as visitDate from visits where id = " + this.currentVisitid;
        try {
            stmt = ictph_cleanConn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                d = rs.getDate("visitDate");
            }
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("GetCurrentVisitDate: " + e.toString());
        }
        return d;
    }
    
    public Object GetProfileProperty(String propertyName)
    {
        Object ret = null;
        try {
            //ret = Emr.class.getField(propertyName).get(this); //works only for public fields
            Field f = Profile.class.getDeclaredField(propertyName);
            f.setAccessible(true);
            ret = f.get(this);
        }
        catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            System.out.println("GetProfileProperty: " + e.toString());
        }
        return ret;
    }
    
    /** 
     * Creates a new row in the profile table.
     * Returns false if no record exists with the given person id.
     */
    private boolean BuildProfile()
    {
        Statement stmt = null;
        String query;
        
        if(!GetMiscDetails())
            return false;   // person does not exist in the system
        
        // check for history of ht, dm, hl
        this.isHypertensive = (GetNumVisitsWithDiagnosisAs("hypertension", null) > 0);
        this.isDiabetic = (GetNumVisitsWithDiagnosisAs("diabetes mellitus", null) > 0);
        this.isHyperlipidemic = (GetNumVisitsWithDiagnosisAs("hyperlipidemia", null) > 0);
        this.lastAuditedVisitDate = this.currentVisitDate;
        
        if(Debug.dbgDbNoWrite)
            return true;
        // write profile record to protoverifier.profile
        query = "insert into profile (personid, isHypertensive, isDiabetic, "
                + "isHyperlipidemic, policyId, lastAuditedVisitDate,";
        if(dob != null)
            query += "dob, ";
        query += "gender) "
                + "values (" + personid + "," + isHypertensive + "," + isDiabetic + "," 
                + isHyperlipidemic + ", '" + policyId + "', '" 
                + lastAuditedVisitDate.toString() + "', ";
        if(dob != null)
            query += "'" + dob.toString() + "', ";
        query += "'" + gender + "')";
        try {
            stmt = protoverifierConn.createStatement();
            stmt.executeUpdate(query);
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("BuildProfile: " + e.toString() + "[" + currentVisitid + "]");
            e.printStackTrace();
        }
        if(Debug.state && Debug.dbgProfile)
            System.out.println("[Wrote into protoverifier.profile table.]");
        
        return true;
    }
    
    private void UpdateProfile()
    {
        // update only if any one of {isHyper*, isDiabetic} is false
        if(!isHyperlipidemic)
            this.isHyperlipidemic = 
                    (GetNumVisitsWithDiagnosisAs("hyperlipidemia", lastAuditedVisitDate) > 0);
        if(!isHypertensive)
            this.isHypertensive =
                    (GetNumVisitsWithDiagnosisAs("hypertension", lastAuditedVisitDate) > 0);
        if(!isDiabetic)
            this.isDiabetic = 
                    (GetNumVisitsWithDiagnosisAs("diabetes mellitus", lastAuditedVisitDate) > 0);
        
        if(Debug.dbgDbNoWrite)
            return;
        //update query
        if(isHyperlipidemic || isHypertensive || isDiabetic) {
            String query = "update profile set ";
            if(isHyperlipidemic)
                query += "isHyperlipidemic=true";
            if(isHypertensive) {
                if(isHyperlipidemic)
                    query += ", ";
                query += "isHypertensive=true";
            }
            if(isDiabetic) {
                if(isHyperlipidemic || isHypertensive)
                    query += ", ";
                query += "isDiabetic=true";
            }
            
            query += " where personid = " + this.personid;
            try {
                Statement stmt = protoverifierConn.createStatement();
                stmt.executeUpdate(query);
                stmt.close();
            }
            catch(Exception e) {
                System.out.println("UpdateProfile: " + e.toString());
            }
        }
    }
    
    /**
     * Number of visits with a particular diagnosis.
     * @param diagnosis
     * @return 
     */
    private int GetNumVisitsWithDiagnosisAs(String diagnosis, Date sinceDate)
    {
        Statement stmt;
        int num_diagnoses = -1;
        
        String query = "select count(1) as num_diagnoses from visits v "
                + "join visit_diagnosis_entries vde on vde.visit_id = v.id and "
                + "vde.diagnosis like '" + diagnosis
                + "' where v.person_id = " + this.personid + " and v.date < '" 
                + this.currentVisitDate.toString() + "'";
        if(sinceDate != null)
            query += " and v.date > '" + sinceDate.toString() + "'";
        try {
            stmt = ictph_cleanConn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            
            if (rs.next()) {
                num_diagnoses = rs.getInt("num_diagnoses");
            }    
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("GetNumVisitsWithDiagnosisAs: " + e.toString());
        }
        return num_diagnoses;
    }
    
    /**
     * Get policy id, date of birth, gender from the person id.
     * Returns false if no record exists for the given person id.
     */
    private boolean GetMiscDetails()
    {
        Statement stmt;
        String dobString = null;
        
        String queryPolicyId = "select hh.policy_id, p.date_of_birth, p.gender from "
                + "persons p join households hh "
                + "on p.household_id = hh.id where p.id = " + this.personid + ";";
        
        try {
            stmt = ictph_cleanConn.createStatement();
            ResultSet rs = stmt.executeQuery(queryPolicyId);
            
            if (rs.next()) {
                policyId = rs.getString("policy_id");
                dobString = rs.getString("date_of_birth");
                gender = rs.getString("gender");
                stmt.close();
            }    
            else {
                stmt.close();
                return false;
            }
            
        }
        catch(Exception e) {
            System.out.println("GetPolicyId: " + e.toString());
        }
        //TODO: replace with proper parsing using DateFormat.parse
        if(dobString==null || dobString.equals(""))
            dob = null;
        else {
            dob = Date.valueOf(dobString);
        }
        if(Debug.state && Debug.dbgProfile) {
            System.out.println("Policy ID: " + policyId);
            System.out.println("DOB: " + dobString);
            System.out.println("Gender: " + gender);
        }
        return true;
    }
}
