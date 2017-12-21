
package jury;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;
//import org.hibernate.Session;
//import org.hibernate.SessionFactory;
//import org.hibernate.cfg.Configuration;

/**
 *
 * @author dany
 */
public class Emr {
    
    public int visitid;
    public int personid;
    Date date;
    Connection conn = null;
    
    // Vitals
    float temperature_f;
    int pulse;
    int respiratory_rate;
    int sbp, dbp;
    float height_cm, weight_kg, waist_cm, hip_cm;
    float head_circumference_cm, arm_circumference_cm;
    boolean is_pregnant;
    
    ArrayList<LabTest> labtest = new ArrayList<>();
    ArrayList<Diagnosis> diagnosis = new ArrayList<>();
    ArrayList<FollowUp> followup = new ArrayList<>();
    ArrayList<Medication> medicine = new ArrayList<>();
    
    // Misc
    String chiefComplaint;
    boolean isHtTreatmentOn;
    int clinicianId, locationId;
    
    public Emr(int visitid)
    {
        this.visitid = visitid;
        if(Debug.state && Debug.dbgEmr)
            System.out.println("Visit ID: " + visitid);
        
//        URL url = Thread.currentThread().getContextClassLoader().getResource("protoverifier/ictph_clean.cfg.xml");
//        SessionFactory sessionFactory = new Configuration().configure(url).buildSessionFactory();
//        Session session = sessionFactory.openSession();
//        Transaction tx = null;
//        int va_id = -1;
//        try {
//            tx = session.beginTransaction();
//            hmis.visit_addendums va = new hmis.visit_addendums(99999999, "protoverifier", 
//                    Date.valueOf("2012-9-9"), "no errors");
//            va_id = (int) session.save(va);
//            tx.commit();
//        }
//        catch (Exception e) {
//            if(tx != null) tx.rollback();
//            e.printStackTrace();
//        }
//        finally {
//            session.close();
//        }
//        System.out.println(va_id);
        
        conn = Database.ConnectToDatabase(Database.Db.ICTPH_CLEAN);
        ReadPersonidAndDate();
        ReadVitals();
        ReadLabtests();
        ReadMedications();
        ReadFollowups();
        ReadDiagnoses();
    }
    
    
    
    private void ReadPersonidAndDate()
    {
        Statement stmt = null;
        String query = null;
        query = "select person_id, date, chief_complaint, provider_id, "
                + "provider_location_id from visits where id = " + visitid;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            if (rs.next()) {
                this.personid = rs.getInt("person_id");
                this.date = rs.getDate("date");
                this.chiefComplaint = rs.getString("chief_complaint");
                this.clinicianId = rs.getInt("provider_id");
                this.locationId = rs.getInt("provider_location_id");
                // System.out.println("personid = " + personid);
            }
            else {
                this.personid = -1;
                System.out.println("No visit record corresponding to the given "
                        + "visit id (" + visitid + "). Terminating.");
                System.exit(-1);
            }
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("ReadPersonid: " + e.toString());
        }
        if(Debug.state && Debug.dbgEmr) {
            System.out.println("Person ID: " + personid);
            System.out.println("");
        }
    }
    
    private void ReadVitals()
    {
        Statement stmt = null;
        String query = null;
        query = "select temperature_f, pulse, respiratory_rate, "
                + "bp_systolic, bp_diastolic, height_cm, weight_kg, waist_cm, "
                + "hip_cm, head_circumference_cm, arm_circumference_cm, "
                + "is_pregnant from visit_vitals where visit_id = " + visitid;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) { 
                this.temperature_f = rs.getFloat("temperature_f");
                this.pulse = rs.getInt("pulse");
                this.respiratory_rate = rs.getInt("respiratory_rate");
                this.sbp = rs.getInt("bp_systolic");
                this.dbp = rs.getInt("bp_diastolic");
                this.height_cm = rs.getFloat("height_cm");
                this.weight_kg = rs.getFloat("weight_kg");
                this.waist_cm = rs.getFloat("waist_cm");
                this.hip_cm = rs.getFloat("hip_cm");
                this.head_circumference_cm = rs.getFloat("head_circumference_cm");
                this.arm_circumference_cm = rs.getFloat("arm_circumference_cm");
                this.is_pregnant = rs.getBoolean("is_pregnant");
            }
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("ReadVitals: " + e.toString());
        }
        if(Debug.state && Debug.dbgEmr) {
            System.out.println("VITALS");
            System.out.println("temperature_f: " + temperature_f);
            System.out.println("pulse: " + pulse);
            System.out.println("respiratory_rate: " + respiratory_rate);
            System.out.println("bp_systolic: " + sbp);
            System.out.println("bp_diastolic: " + dbp);
            System.out.println("height_cm: " + height_cm);
            System.out.println("weight_kg: " + weight_kg);
            System.out.println("waist_cm: " + waist_cm);
            System.out.println("hip_cm: " + hip_cm);
            System.out.println("head_cirumference_cm: " + head_circumference_cm);
            System.out.println("arm_circumference_cm: " + arm_circumference_cm);
            System.out.println("is_pregnant: " + is_pregnant);
            System.out.println("");
        }
    }
    
    private void ReadLabtests()
    {
        Statement stmt = null;
        String query = null;
        int i = -1;
        LabTest ltest;
        query = "select tt.id, tt.name, vte.result "
                + "from visit_test_entries vte join test_types tt "
                + "on vte.test_type_id = tt.id where visit_id = " + visitid;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            for (i=0; rs.next(); i++) { 
                ltest  = new LabTest();
                ltest.id = rs.getInt("id");
                ltest.name = rs.getString("name").trim();
                ltest.result = rs.getString("result");
                labtest.add(ltest);
            }
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("ReadLabtests: " + e.toString() + " (" + visitid + ")");
            e.printStackTrace();
        }
        if(Debug.state && Debug.dbgEmr && i>0) {
            System.out.println("LAB TESTS");
            for(int j=0; j<i; j++) {
                System.out.println(labtest.get(j).toString());
            }
        }
    }
    
    private void ReadMedications()
    {
        Statement stmt = null;
        String query = null;
        int i = -1;
        Medication m;
        
        query = "select p.name, p.generic_name, p.strength, p.strength_unit, "
                + "vme.duration, vme.duration_type, vme.frequency "
                + "from visit_medication_entries vme join products p "
                + "on vme.product_id = p.id "
                + "where vme.visit_id = " + visitid;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            
            for (i=0; rs.next(); i++) { 
                m = new Medication();
                m.name = rs.getString("name");
                m.genericName = rs.getString("generic_name");
                m.strength = rs.getString("strength");
                m.strengthUnit = rs.getString("strength_unit");
                m.duration = rs.getInt("duration");
                m.durationType = rs.getString("duration_type");
                m.frequency = rs.getString("frequency");
                medicine.add(m);
            }
            stmt.close();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        if(Debug.state && Debug.dbgEmr && i>0) {
            System.out.println("MEDICATIONS");
            for(int j=0; j<i; j++) {
                System.out.println(medicine.get(j).toString());
            }
        }
        
        //Find whether pharmacological treatment for ht is going on
        int vid = visitid;
        
        String medQuery = "select v.id, v.date, vme.product_id from visit_medication_entries vme "
            + "join visits v on vme.visit_id = v.id join products p on vme.product_id = p.id "
            + "where v.id in (select followup_to_visit_id from visits where id=" + visitid + ") "
            + "and (p.generic_name like '%Amlodipine%' or p.generic_name like '%Losartan%');";
        
        String infoQuery = "select v.followup_to_visit_id as old_id, v.date as date "
                + "from visits v where v.id = " + visitid;
        
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(medQuery);
            if(rs.next())
                isHtTreatmentOn = true;
            stmt.close();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    private void ReadDiagnoses()
    {
        Statement stmt = null;
        String query = null;
        int i = -1;
        Diagnosis d;
        
        query = "select diagnosis from visit_diagnosis_entries "
                + "where visit_id = " + visitid;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            for (i=0; rs.next(); i++) { 
                d = new Diagnosis();
                d.name = rs.getString("diagnosis");
                diagnosis.add(d);
            }
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("ReadDiagnoses:" + e.toString());
        }
        if(Debug.state && Debug.dbgEmr && i>0) {
            System.out.println("DIAGNOSES");
            for(int j=0; j<i; j++) 
                System.out.println(diagnosis.get(j));
        }
    }
    
    private void ReadFollowups()
    {
        Statement stmt = null;
        String query = null;
        int i = -1;
        FollowUp f;
        
        query = "select protocol, next_action, due_date from followup_informations "
                + "where visit_id = " + visitid;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            for (i=0; rs.next(); i++) { 
                f = new FollowUp();
                f.name = f.protocol = rs.getString("protocol");
                f.nextAction = rs.getString("next_action");
                f.dueDate = rs.getDate("due_date");
                followup.add(f);
            }
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("ReadFollowups: " + e.toString());
        }
        if(Debug.state && Debug.dbgEmr && i>0) {
            System.out.println("FOLLOW-UP(S)");
            for(int j=0; j<i; j++) {
                System.out.println(followup.get(j));
            }
        }
    }
    
    public Object GetEmrProperty(String propertyName)
    {
        Object ret = null;
        try {
            //ret = Emr.class.getField(propertyName).get(this); //works only for public fields
            Field f = Emr.class.getDeclaredField(propertyName);
            f.setAccessible(true);
            ret = f.get(this);
        }
        catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            System.out.println("GetEmrProperty: " + e.toString());
        }
        return ret;
    }
    
    /*public Object GetEmrProperty(String propertyName, String param, String value)
    {
        Object ret = null;
        switch(propertyName) {
            case "due_date":
                // assert param.equals("protocol");
                for(int i=0; i<due_date.length; i++)
                    if(followupProtocol[i]!=null && followupProtocol[i].equals(value))
                        return due_date[i];
                break;
        }
        return ret;
    } */
    
    /**
     * Adds a note to the visit_addendums table in ictph_clean db
     * @param note The note to be added
     */
    public void AddNote(String note)
    {
        if(Debug.dbgDbNoWrite)
            return;
        java.util.Date d = new java.util.Date();
        Date sqlDate = new Date(d.getTime());
        String query = "insert into visit_addendums (visit_id, username, date, "
                + "addendum) values (" + Jury.visitid + ", 'protoverifier', '"
                + sqlDate.toString() + "', '" + note + "')";
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(query);
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("AddNote: " + e.toString());
        }
    }
}
