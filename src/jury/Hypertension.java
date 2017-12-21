package jury;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import jury.State.StateType;
import nu.xom.Document;

/**
 * 
 */
public class Hypertension extends Protocol {

    String name;
    
    public Hypertension(Document d)
    {
        super(d, 1, "hypertension");
        name = "hypertension";
    }
    
    /**
     * Find whether the patient has made any improvement with respect to this 
     * protocol between the last visit and the present one.
     * @return null if last visit information is not available
     */
    @Override
    public Boolean GetImprovement()
    {
        // if there is a state transition that has not been logged, then don't allow
        // state machine to progress. [State machine progresses when there is no improvement.]
        // 
        if(super.lastUnloggedState != null) 
            return true;
        
        if(super.prevProtocolEmr == null)
            return false;    //no previous record => no improvement
        if((super.emr.sbp >= super.prevProtocolEmr.sbp) && (super.emr.dbp >= super.prevProtocolEmr.dbp))
            return false;
        return true;
    }
    
    /**
     * Calls Protocol class's Eval function. Hypertension-specific pre- and post-
     * processing actions are also executed.
     * Inspired by the behavioural Template design pattern.
     */
    public void Eval(Emr emr, Profile profile)
    {
        if(emr.sbp == 0 || emr.dbp == 0) {
            if(profile.age >= 18)   //BP measurement is done only for adults
                Audit.Print(301, "SBP and/or DBP is zero", emr, profile);
            super.emr = emr;
            super.profile = profile;
            super.currentState = super.LoadStateLog();
            if(super.currentState != null) {
                super.LogStateInfo();
            }
            return;
        }
        super.Eval(emr, profile);
        
        StateType stype = super.currentState.type;
        if(stype.equals(StateType.dState) || stype.equals(StateType.tState)) {
            CheckCVDStates();
        }
    }
    
    /**
     * Checks whether protocol information has been entered correctly in the 
     * 'Assessment' tab on HMIS. The state mentioned there should tally with the
     * state of the state machine after processing this visit. Since the current
     * state might be a t-state, a record is kept of the last d-state seen - and
     * this state is what is expected in the HMIS db.
     */
    private void CheckCVDStates()
    {
        if(lastDState==null || lastDState.name == null)
            return;
        
        int count = 0;
        Statement stmt;
        Connection conn = Database.ConnectToDatabase(Database.Db.ICTPH_CLEAN);
        String query = "SELECT count(1) as numRecords FROM visit_protocol_information_entries "
                + "WHERE details LIKE '%" + lastDState.name + "%' and visit_id = " 
                + emr.visitid;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                count = rs.getInt("numRecords");
            }
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("CheckCVDStates: " + e.toString());
        }
        if(count == 0) {
            Audit.Print(102, "CVD state not mentioned (" + lastDState.name + ")", emr, profile);
        }
    }
}
