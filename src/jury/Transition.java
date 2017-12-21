package jury;

import nu.xom.*;

/**
 * @author Daniel J Mathew
 */
public class Transition {
    String name, strDestState;
    Node cond;
    State srcState, destState;
    
    public Transition(Node tnode)
    {
        Element eltTransition = (Element)tnode;
        Element eltDestState;
        name = eltTransition.getAttributeValue("id");
        eltDestState = (Element)tnode.query("child::deststate").get(0);
        strDestState = eltDestState.getAttributeValue("id");
        cond = tnode.query("child::cond").get(0).getChild(0);
    }
    
    /**
     * A transition is ready if its associated condition evaluates to true.
     */
    public Boolean IsReady(Protocol p)
    {
        if(Debug.state && Debug.dbgTransition)
            Debug.Println("Processing transition " + this);
        return (Boolean)XMLExprParser.EvalCondition(cond, p.emr, p.profile, p);
    }
    
    @Override
    public String toString()
    {
        return srcState + " -> " + destState + " (" + name + ")";
    }
}
