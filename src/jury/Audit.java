package jury;

import java.sql.Connection;
import java.sql.Date;
import java.sql.Statement;
import jury.Database.Db;

/**
 * 
 */
public class Audit {
    public static boolean isCSV;
    public static char delim = '|';
    
    public static void Print(int msgid, String msg, Emr emr, Profile profile)
    {
        String msgtype;
        if(msgid < 200)
            msgtype = "Warning";
        else if(msgid < 300)
            msgtype = "Error";
        else
            msgtype = "Info";
        
        if(isCSV) {
            // visitId | msgid | msg | clinicianID | locationID
            String out = emr.visitid + "" + delim;  //"" to avoid integer addition
            out += msgid + "" + delim + msg + delim;
            out += emr.clinicianId + "" + delim + "" + emr.locationId;
            System.out.println(out);
        }
        else {
            String out = "[" + emr.visitid + "] " 
                    + msgtype + " (" + msgid + "): " 
                    + msg;
            System.out.println(out);
        }
        LogToDatabase(msgid, msg, emr, profile);
    }
    
    private static void LogToDatabase(int msgid, String msg, Emr emr, Profile profile)
    {
        if(Debug.dbgDbNoWrite)
            return;
        Connection conn = Database.ConnectToDatabase(Db.PROTOVERIFIER);
        java.util.Date d = new java.util.Date();
        Date sqlDate = new Date(d.getTime());
        String query = "insert into auditNotes (visitId, auditDate, msgId, msg) "
                + "values (" + emr.visitid + ", '" + sqlDate.toString() + "', "
                + msgid + ", '" + msg + "')";
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(query);
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("LogToDatabase: " + e.toString());
        }
    }
}
