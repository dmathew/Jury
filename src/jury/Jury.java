package jury;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.ParsingException;
import nu.xom.xinclude.XIncludeException;
import nu.xom.xinclude.XIncluder;


/**
 *
 * @author Daniel J Mathew
 */
public class Jury {

    public static Protocol p;
    public static Emr emr;
    public static Profile profile;
    public static int visitid;
    private static Connection conn;
    private static final int RUNSIZE = 100; // Number of EMRs processed before
                                            // invoking the GC
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        String startDate = args[0];
        ArrayList<Integer> ids = new ArrayList();
        Document doc = null;
        String fname, pname = "protocols\\ht1";
        Element root = null;
        
        try {
            String currentDir = new java.io.File( "." ).getCanonicalPath();
            fname = currentDir + "\\" + pname + ".xml";
            doc = (new Builder()).build(new File(fname));
            doc = XIncluder.resolve(doc);
            root = doc.getRootElement();
        }
        catch (IOException | ParsingException | XIncludeException e) {
            System.out.println("Protocol: " + e.toString());
        }
        String protocolId = root.getAttributeValue("id");
        switch(protocolId) {
            case "hypertension":
                p = new Hypertension(doc);
                break;
            default:
                System.out.println("Unrecognized protocol. Exiting.");
                System.exit(-1);
        }
        
        // Make a db connection
        conn = Database.ConnectToDatabase(Database.Db.ICTPH_CLEAN);
        //query db to get visit ids
        //ids = GetVisitsFromDate(startDate);
        //ids = GetVisitsFromDate("2013-01-01");
        ids = GetVisitsBetweenDates("2011-10-01", "2013-03-29");
        //ids = GetSingleVisit("3338605");
        //ids = GetVisitsFromVisitID("3333340");
        //ids = GetVisitsForPerson("3051000");
        
        if(Debug.state && Debug.dbgJury) {
            System.out.println(ids.size() + " visit IDs found.");
            System.out.println("");
        }
        int i = 0;
        Audit.isCSV = true;
        System.out.println("sep=" + Audit.delim);   // custom CSV delimiter
        for(Integer vid : ids) {
            Debug.visitId = vid;
            emr = new Emr(vid);
            profile = new Profile(emr.personid, vid);
            if(IsInfoMissing(profile))
                continue;
            p.Eval(emr, profile);
            p.LogStateInfo();
            
            // hints to the GC
//            emr.diagnosis.clear();
//            emr.followup.clear();
//            emr.labtest.clear();
//            emr.medicine.clear();
//            emr = null;
//            p.emr = null;
//            profile = null;
//            
//            // invoke the GC after processing RUNSIZE records
//            i = (i+1)%RUNSIZE;
//            if(i == RUNSIZE-1)
//                System.gc();
        }
        
    }
    
    private static ArrayList<Integer> GetVisitsFromDate(String startDate)
    {
        return GetVisitsForQuery("select id from visits where date >= '" 
                + startDate +"' and valid_state like 'valid'");
    }
    
    private static ArrayList<Integer> GetVisitsBetweenDates(String startDate, String endDate)
    {
        return GetVisitsForQuery("select id from visits where date >= '" + startDate
                + "' and date<='" + endDate + "' and valid_state like 'valid'");
    }
    
    private static ArrayList<Integer> GetVisitsForPerson(String personId)
    {
        return GetVisitsForQuery("select id from visits where person_id=" 
                + personId + " and valid_state like 'valid'");
    }
    
    private static ArrayList<Integer> GetVisitsFromVisitID(String visitId)
    {
        return GetVisitsForQuery("select id from visits where id>=" + visitId 
                + " and valid_state like 'valid'");
    }
    
    private static ArrayList<Integer> GetSingleVisit(String visitId)
    {
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(Integer.parseInt(visitId));
        return ids;
    }
    private static ArrayList<Integer> GetVisitsForQuery(String query)
    {
        ArrayList<Integer> ids = new ArrayList<>();
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                ids.add(rs.getInt("id"));
            }
            stmt.close();
        }
        catch(Exception e) {
            System.out.println(query + ": " + e.toString());
        }
        return ids;
    }
    
    /**
     * Checks whether important pieces of information are missing from patient
     * profile.
     * @return true if something is missing, false otherwise.
     */
    private static boolean IsInfoMissing(Profile p)
    {
        if(!p.personExists) {
            Audit.Print(206, "No record found for person ID " + p.personid, emr, profile);
            return true;
        }
        String msg1 = "Important info missing (";
        String msg2 = "). Skipped visit record.";
        if((p.age == 0) && (p.ageDays == 0)) {
            Audit.Print(205, msg1 + "age" + msg2, emr, profile);
            return true;
        }
        // Other checks
        return false;
    }
}
