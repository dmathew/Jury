package jury;

import java.util.ArrayList;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Node;
import nu.xom.Nodes;

/**
 * @author Daniel J Mathew
 */
public class State {
    public static enum StateType {startState, tempState, dState, tState, tgState, otherState};

    public String id;
    ArrayList<Transition> transitions;
    ArrayList<Medication> meds;
    StateType type;
    boolean checkDD;    //check for protocol id in differential diagnosis (DD)?
    String name;
    
    /**
     * Initializes a State object that represents a medical state.
     * @param snode The XML node representing this state.
     * @param doc The protocol XML document object.
     */
    public State(Node snode, Document doc)
    {
        Element sElt = (Element)snode;
        Element tElt;
        Nodes trefs, tnodes, mrefs;
        String tname, strType;
        Transition t;
        id = sElt.getAttributeValue("id");
        strType = sElt.getAttributeValue("type");
        switch (strType) {
            case "start":   // usually the Unknown state
                type = StateType.startState;
                break;
            case "temp":    // intermediate states (e.g.: ht_interm1)
                type = StateType.tempState;
                break;
            case "d":       // diagnosis state
                type = StateType.dState;
                break;
            case "t":       // treatment state
                type = StateType.tState;
                break;
            case "tg":      // treatment goal state
                type = StateType.tgState;
                break;
            case "refer":      // referral state
                type = StateType.otherState;
                break;
            default:
                System.out.println("Unknown node type " + strType);
                System.exit(-1);
        }
        trefs = snode.query("child::transition"); // references to transition nodes
                                                  // from within a state node
        transitions = new ArrayList<>();
        
        for(int i=0; i<trefs.size(); i++) {
            tElt = (Element)trefs.get(i);
            tname = tElt.getAttributeValue("id");
            tnodes = doc.query("/protocol/transition[@id='" + tname + "']");
            //System.out.println(tname);
            t = new Transition(tnodes.get(0));
            t.srcState = this;
            transitions.add(t);
        }
        
        // Read info about medication(s) if this is a t-state
        if(this.type == StateType.tState) {
            meds = new ArrayList<>();
            mrefs = snode.query("child::med");
            for(int i=0; i<mrefs.size(); i++) {
                meds.add(new Medication().ReadFromXML(mrefs.get(i)));
            }
        }
        
        if(snode.query("child::checkDD").size() > 0)    //i.e., if a <checkDD /> 
                                                        // child node exists
            checkDD = true;
        
        if(snode.query("child::name").size() > 0)
            name = snode.query("child::name").get(0).getValue();
        else 
            name = null;
    }
    
    @Override
    public String toString()
    {
        return this.id;
    }
}
