package jury;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import jury.Database.Db;
import jury.State.StateType;
import nu.xom.*;

/**
 * @author Daniel J Mathew
 */
public class Protocol {
    
    public State startState, currentState;
    private ArrayList<State> states;
    private Document doc = null;
    public Emr emr;
    public Profile profile;
    int protocolId;
    String name;
    private Connection pvConn;
    
    // The details of the previous visit that resulted in a state transition for
    // this patient.
    private int prevProtocolVisitId;
    public Emr prevProtocolEmr;
    private State prevProtocolState;
    
    // used as a bookmark to store the current state if doing some look-ahead
    public State lastUnloggedState;
    
    public State lastDState;    // last d-state the machine went through
    public State lastTState;
    
    // Override in subclasses
    public Boolean GetImprovement(){return false;}
    
    public Protocol(Document d, int id, String pname)
    {
        Element root = null;
        String strStartState;
        Nodes stateNodes;
        State s;
        Transition t;
        
        this.protocolId = id;
        this.name = pname;
        this.doc = d;
        root = doc.getRootElement();
        XMLUtil.CleanUp(root);
        strStartState = root.getAttributeValue("startstate");
        stateNodes = doc.query("/protocol/state");
        states = new ArrayList<>();
        lastUnloggedState = null;
        
        // Initialize list of states. The destination states of the transitions
        // are not populated in this (first) pass. This is because the deststate 
        // might be a state not yet processed.
        for(int i=0; i<stateNodes.size(); i++) {
            s = new State(stateNodes.get(i), doc);
            states.add(s);
            if(s.id.equals(strStartState))
                startState = s;
        }
        
        // A second pass over all the states to fix the destination states of 
        // each state's transitions.
        for(State st:states)
            FixDestStates(st);
        
        pvConn = Database.ConnectToDatabase(Db.PROTOVERIFIER);
    }
    
    /**
     * The engine of the state machine.
     */
    public void Eval(Emr emr, Profile profile)
    {
        boolean stop = false;
        Boolean isReady;
        lastDState = lastUnloggedState = null;
        this.emr = emr;
        this.profile = profile;
        prevProtocolState = currentState = LoadStateLog();
        if(currentState == null)
            currentState = startState;
        
        while(true) {
            if(currentState.transitions.isEmpty()) {
                if(Debug.state && Debug.dbgProtocol)
                    Debug.Println("No more transitions from state " + currentState);
                break;
            }
            for(Transition t:currentState.transitions) {
                isReady = t.IsReady(this);
                if(isReady != null && isReady) {
                    lastUnloggedState = currentState;
                    currentState = t.destState;
                    if(currentState.type.equals(StateType.dState))
                        lastDState = currentState;
                    break;
                }
                else if(currentState.transitions.indexOf(t) == currentState.transitions.size()-1) {
                    if(Debug.state && Debug.dbgProtocol)
                        Debug.Println("No ready transitions from state " + currentState);
                    stop = true;
                    break;
                }
            }
            if(stop)  //break out to prevent infinite loop
                break;
            else
                if(Debug.state && Debug.dbgProtocol)
                    Debug.Println("Moved to state " + currentState);
        }
        if(currentState.type == StateType.tState || currentState.type == StateType.tgState)
            CheckMedPrescription();
        if(Debug.state && Debug.dbgProtocol)
            Debug.Println("Final state is " + currentState);
        
        // check for mention in DD if requested in currentState or if this is a t-state
        if(currentState.checkDD || currentState.type.equals(StateType.tState)) {
            CheckDD();
        }
        
        // check if chief complaint has been mentioned
        if(emr.chiefComplaint.equals("Not Mentioned") || emr.chiefComplaint.equals(""))
            Audit.Print(101, "Chief complaint not mentioned.", emr, profile);
    }
    
    /**
     * Check that the patient was prescribed all the medications mentioned in
     * the protocol document for this state.
     */
    void CheckMedPrescription()
    {
        //System.out.println("Checking prescriptions for state " + this.currentState);
        ArrayList<Integer> unprescribed = new ArrayList<>();
        ArrayList<Integer> wrongDosage = new ArrayList<>();
        ArrayList<Integer> wrongDuration = new ArrayList<>();
        ArrayList<Integer> wrongFrequency = new ArrayList<>();
        boolean medFlag;
        float pStrength=(float) 0.0, rStrength=(float) 0.0;
        int i = 0;
        
        ArrayList<Medication> meds;
        if(currentState.type == StateType.tgState) {
            if(lastTState != null)
                meds = lastTState.meds;
            else 
                meds = prevProtocolState.meds;
        }
        else 
            meds = currentState.meds;
            
        
        for(Medication r : meds) { //r : recommended med
            medFlag = false;
            for(Medication p : emr.medicine) {  //p : prescribed med
                if(p.genericName.toLowerCase().contains(r.genericName.toLowerCase())) {
                    // Med prescribed is correct; now check for dosage, duration etc.
                    if(profile.firstVisit) {    //ht-specific
                        String out = "Medicine prescribed in first visit: " + r.genericName;
                        Audit.Print(207, out, emr, profile);
                        break;
                    }
                    medFlag = true;
                    try {
                        pStrength = Float.parseFloat(p.strength);
                        rStrength = Float.parseFloat(r.strength);
                    } catch(NumberFormatException e) {
                        System.out.println("Dosage format is not specified correctly: " 
                                + r.genericName);
                    }
                    if(pStrength != rStrength) {
                        wrongDosage.add(i);
                    }
                    if(p.duration != r.duration) {
                        wrongDuration.add(i);
                    }
                    if(!p.frequency.equals(r.frequency)) {
                        wrongFrequency.add(i);
                    }
                    break;  //matched med r, look for next r
                }
            }
            if(!medFlag) {
                unprescribed.add(i);
            }
            i++;
        }
        if(unprescribed.size() > 0) {
            String out = "Medicine(s) not prescribed [state " + currentState + "]: ";
            //Debug.Print("Error (201): Medicine(s) not prescribed [state " + currentState 
            //        + "]: ");
            for(int m : unprescribed)
                out += meds.get(m).genericName + ", ";
                //System.out.print(currentState.meds.get(m).genericName + ", ");
            //System.out.println("");
            Audit.Print(201, out, emr, profile);
        }
        if(wrongDosage.size() > 0) {
            String out = "Medicine(s) with wrong dosage [state " + currentState + "]: ";
            for(int m : wrongDosage)
                out += meds.get(m).genericName + ", ";
            Audit.Print(202, out, emr, profile);
        }
        if(wrongDuration.size() > 0) {
            String out = "Medicine(s) prescribed for wrong duration [state " + currentState + "]: ";
            for(int m : wrongDuration)
                out += meds.get(m).genericName + ", ";
            Audit.Print(203, out, emr, profile);
        }
        if(wrongFrequency.size() > 0) {
            String out = "Medicine(s) prescribed with wrong frequency [state " + currentState + "]: ";
            for(int m : wrongFrequency)
                out += meds.get(m).genericName + ", ";
            Audit.Print(204, out, emr, profile);
        }
    }
    
    
    void CheckDD()
    {
        for(Diagnosis d : emr.diagnosis)
            if(d.name.equals(this.name))
                return;
        
        Audit.Print(103, "Diagnosis *" + name + "* not present in DD", emr, profile);
    }
    
    /**
     * A second pass over the transitions emanating from state @s.
     */
    private void FixDestStates(State s)
    {
        for(Transition t:s.transitions) {
            for(State d:states) {
                if(t.strDestState.equals(d.id)) {
                    t.destState = d;
                    break;
                }
            }
        }
    }
    
    /**
     * Returns true if at least @numDays has elapsed since the last visit 
     * recorded for this protocol, false if lesser, and NULL if no such visit
     * prior to this one.
     */
    public Integer DateDiff()
    {
        // if there is a state transition that has not been logged, then don't allow
        // state machine to progress. [State machine progresses when there is a considerable
        // date difference.]
        if(lastUnloggedState != null) return 0;
        if(prevProtocolEmr == null)
            return null;
        return (int)getDateDiff(prevProtocolEmr.date, emr.date, TimeUnit.DAYS);
    }
    
    /**
     * Get a diff between two dates
     * @param date1 the oldest date
     * @param date2 the newest date
     * @param timeUnit the unit in which you want the diff
     * @return the diff value, in the provided unit
     */
    public static long getDateDiff(Date date1, Date date2, TimeUnit timeUnit) {
        long diffInMillies = date2.getTime() - date1.getTime();
        return timeUnit.convert(diffInMillies,TimeUnit.MILLISECONDS);
    }

    /**
     * Insert a new record corresponding to this patient+protocol+visitId combo, and 
     * delete any such existing record.
     */
    public void LogStateInfo()
    {
        if(currentState == null) {
            // This happens, for instance, when the SBP and/or DBP are zero, and
            // this is the first visit for this patient. This happens because 
            // execution of Eval() is skipped.
            return;
        }
        if(Debug.dbgDbNoWrite)
            return;
        String delquery = "delete from protoverifier.statelog where personId=" +
                emr.personid + " and protocolid=" + this.protocolId + " and " +
                "visitid=" + emr.visitid;
        
        String insquery;
        if(currentState.type == StateType.tgState) {
            // if currentState is a tg-state, log the last t-state encountered
            if(lastTState == null &&   //if lastTState is null, then the most recent transition was from t->tg
                    prevProtocolState.type != StateType.tState) {
                if(Debug.state && Debug.dbgProtocol) 
                    System.out.println("Error: don't know previous t-state");
            }
            String prevTState;
            if(lastTState != null)
                prevTState = lastTState.toString();
            else
                prevTState = prevProtocolState.toString();
            insquery = "insert into protoverifier.statelog (personId, "
                    + "protocolId, state, prevTState, visitId) values (" + emr.personid + "," +
                    this.protocolId + ",'" + this.currentState + "', '" + prevTState + "', " + emr.visitid + 
                    ")";
        }
        else {
            insquery = "insert into protoverifier.statelog (personId, "
                    + "protocolId, state, visitId) values (" + emr.personid + "," +
                    this.protocolId + ",'" + this.currentState + "'," + emr.visitid + 
                    ")";
        }
        try {
            Statement stmt = pvConn.createStatement();
            stmt.executeUpdate(delquery);
            stmt.executeUpdate(insquery);
            stmt.close();
        }
        catch(Exception e) {
            System.out.println("LogStateInfo: " + e.toString());
        }
        lastUnloggedState = null;
    }
    
    /**
     * Load the last record for this patient+protocol combination from stateLog table.
     * @return The latest state in which the patient was, for this protocol; null
     * if no record found.
     */
    public State LoadStateLog()
    {
        Statement stmt;
        String query = "select * from protoverifier.statelog where personId=" +
                emr.personid + " and protocolid=" + this.protocolId + " and " +
                "visitid<" + emr.visitid + " order by visitId desc limit 1";
        String laststate = null;
        String prevTState = null;
        try {
            stmt = pvConn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                laststate = rs.getString("state");
                this.prevProtocolVisitId = rs.getInt("visitId");
                // System.out.println("personid = " + personid);
                this.prevProtocolEmr = new Emr(prevProtocolVisitId);
                prevTState = rs.getString("prevTState");
                stmt.close();
            }
            else {
                this.prevProtocolVisitId = -1;
                this.prevProtocolEmr = null;
                stmt.close();
                return null;
            }
        }
        catch(Exception e) {
            System.out.println("LoadStateLog: " + e.toString());
        }
        if(prevTState != null) {
            for(State s : this.states)
                if(s.id.equals(prevTState)) {
                    lastTState = s;
                }
        }
        for(State s : this.states)
            if(s.id.equals(laststate))
                return s;
        
        return null;
    }
}
