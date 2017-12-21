/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jury;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/**
 * 
 */
public class Database {
    public static enum Db {ICTPH_CLEAN, PROTOVERIFIER};
    
    private static Connection icConn, pvConn;
    
    /**
     * Initializes a connection to @db if its connection variable is null.
     * Otherwise, returns the respective connection variable.
     * @param db
     * @return 
     */
    public static Connection ConnectToDatabase(Db db)
    {
        String dbString;
        Connection conn = null;
        Properties connectionProps = new Properties();
        // TODO: put these properties in an XML (config) file and read them in
        connectionProps.put("user", "root");
        connectionProps.put("password", "passw0rd");
        
        if(db.equals(Db.ICTPH_CLEAN)) {
            dbString = "ictph_clean";
            if(icConn != null)
                return icConn;
        }
        else if(db.equals(Db.PROTOVERIFIER)) {
            dbString = "protoverifier";
            if(pvConn != null)
                return pvConn;
        }
        else
            return null;

        try {
            conn = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/" + dbString, 
                    connectionProps);
        }
        catch(Exception e) {
            System.out.println("ConnectToDatabase: " + e.toString());
        }
        
        switch(db) {
            case ICTPH_CLEAN:
                icConn = conn;
                break;
            case PROTOVERIFIER:
                pvConn = conn;
                break;
        }
        return conn;
    }
}
