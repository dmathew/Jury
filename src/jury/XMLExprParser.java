/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jury;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import nu.xom.*;

/**
 *
 */
public class XMLExprParser 
{
    //Expression types
    private static final int TYPE_BOOLEAN = 0;
    private static final int TYPE_INTEGER = 1;
    private static final int TYPE_FLOAT = 2;
    private static Emr emr;
    private static Profile profile;
    private static Protocol protocol;
    
    /**
     * Evaluate a bvalue (boolean value) node.
     * Format: <bvalue>value</bvalue>
     * @param elem  XML Element for the bvalue node
     * @return      true, false or NULL (if text is malformed)
     */
    private static Boolean EvalBValue(Element elem)
    {
        Boolean ret = null;
        String value = elem.getValue();
        value = value.replace('\n', ' ').trim();
        if(value.equals("true"))
            ret = (Boolean)true;
        else if(value.equals("false"))
            ret = (Boolean)false;
        else
            System.out.println("Error in bvalue node.");
        return ret;
    }
    
    private static Integer EvalIValue(Element elem)
    {
        Integer ret = null;
        String value = elem.getValue();
        //TODO: catch NumberFormatException
        ret = Integer.parseInt(value);
        return ret;
    }
    
    private static Float EvalFValue(Element elem)
    {
        Float ret = null;
        String value = elem.getValue();
        //TODO: catch NumberFormatException
        ret = Float.parseFloat(value);
        return ret;
    }
    
    private static String EvalSValue(Element elem)
    {
        return elem.getValue();
    }
    
    /**
     * Evaluate an emr tag. This returns data from the medical record.
     * @param elem
     * @return An Object corresponding to the data requested. If the requested 
     *   data is part of a collection (like diagnoses, lab tests etc.) a ClinicalEntity
     *   is returned.
     */
    private static Object EvalEmr(Element elem)
    {
        Object ret = null;
        String attribute = elem.getAttributeValue("attribute");
        /*if(elem.getAttributeCount() > 1) { //TODO: Legacy; remove
            Attribute extraParam = elem.getAttribute(1);
            ret = emr.GetEmrProperty(attribute, extraParam.getLocalName(), extraParam.getValue());
        }
        else */
            ret = emr.GetEmrProperty(attribute);
        // System.out.println(ret.toString());
        return ret;
    }
    
    /**
     * Evaluates a profile tag. This returns data from the patient profile.
     * @param elem
     * @return 
     */
    private static Object EvalProfile(Element elem)
    {
        Object ret;
        String attribute = elem.getAttributeValue("attribute");
        ret = profile.GetProfileProperty(attribute);
        // System.out.println("profile attribute " + attribute + " = " + ret.toString());
        return ret;
    }
    
    /**
     * Evaluate an eq (equality) node - this can be an equality check between 
     * boolean or non-boolean types.
     * Format: <eq> expr1 expr2 </eq>
     * @param expr  XML Element for the eq node.
     * @return      true, false or NULL (if node is malformed)
     */
    private static Boolean EvalEq(Element expr)
    {
        Element lchild = (Element)expr.getChild(0);
        Element rchild = (Element)expr.getChild(1);
        Object lvalue, rvalue;
        
        lvalue = EvalExpr(lchild);
        rvalue = EvalExpr(rchild);
        //System.out.println("lvalue = " + lvalue.toString() + ", rvalue = " + rvalue.toString());
        //Handle float comparison separately
        if(lvalue instanceof Float) {
            int cmp = ((Float)lvalue).compareTo((Float)rvalue);
            if (cmp == 0)
                return true;
            else 
                return false;
        }
        else
            return (lvalue.equals(rvalue));
    }
    
    private static Boolean EvalGt(Element expr)
    {
        Element lchild = (Element)expr.getChild(0);
        Element rchild = (Element)expr.getChild(1);
        Object lvalue, rvalue;
        
        lvalue = EvalExpr(lchild);
        rvalue = EvalExpr(rchild);
        //System.out.println("lvalue = " + lvalue.toString() + ", rvalue = " + rvalue.toString());
        //Handle float comparison separately
        if(lvalue instanceof Float) {
            int cmp = ((Float)lvalue).compareTo((Float)rvalue);
            if (cmp > 0)
                return true;
            else 
                return false;
        }
        else
            return ((Integer)lvalue > (Integer)rvalue);
    }
    
    private static Boolean EvalLt(Element expr)
    {
        Element lchild = (Element)expr.getChild(0);
        Element rchild = (Element)expr.getChild(1);
        Object lvalue, rvalue;
        
        lvalue = EvalExpr(lchild);
        rvalue = EvalExpr(rchild);
        //Handle float comparison separately
        if(lvalue instanceof Float) {
            int cmp = ((Float)lvalue).compareTo((Float)rvalue);
            if (cmp < 0)
                return true;
            else 
                return false;
        }
        else
            return ((Integer)lvalue < (Integer)rvalue);
    }
    
    /**
     * Evaluate a logical or operation.
     * @param expr
     * @return True or False
     */
    private static Boolean EvalOr(Element expr)
    {
        Element lchild = (Element)expr.getChild(0);
        Element rchild = (Element)expr.getChild(1);
        Boolean lvalue, rvalue;
        
        lvalue = (Boolean)EvalExpr(lchild);
        rvalue = (Boolean)EvalExpr(rchild);
        return (lvalue || rvalue);
    }
    
    private static Boolean EvalAnd(Element expr)
    {
        Element lchild = (Element)expr.getChild(0);
        Element rchild = (Element)expr.getChild(1);
        Boolean lvalue, rvalue;
        
        lvalue = (Boolean)EvalExpr(lchild);
        rvalue = (Boolean)EvalExpr(rchild);
        return (lvalue && rvalue);
    }
    
    private static Boolean EvalNot(Element expr)
    {
        Element child = (Element)expr.getChild(0);
        Boolean evalChild = (Boolean)EvalExpr(child);
        return !evalChild;
    }
    
    /**
     * Evaluate whether left child lies in the (Inclusive) range [lb, ub].
     */
    private static Boolean EvalIRange(Element expr)
    {
        Element lchild = (Element)expr.getChild(0);
        Element lb = (Element) expr.getChildElements("lb").get(0).getChild(0);
        Element ub = (Element) expr.getChildElements("ub").get(0).getChild(0);
        
        Object lvalue = EvalExpr(lchild);
        if(lvalue == null)
            return false;
        Object lb_value = EvalExpr(lb);
        Object ub_value = EvalExpr(ub);
        //TODO: add condition for Float
        return (((Integer)lvalue >= (Integer)lb_value) && ((Integer)lvalue <= (Integer)ub_value));
    }
    /**
     * Tests for membership. 
     * Format: <contains>expr1 expr2</contains>
     * expr1 should evaluate to a collection of ClinicalEntity objects. The 'name'
     * property of each object in the collection is taken as the representative
     * element, and is what expr2 is matched against.
     * @param expr
     * @return True or False.
     */
//    private static Boolean EvalContains(Element expr)
//    {
//        Element lchild = (Element)expr.getChild(0);
//        Element rchild = (Element)expr.getChild(1);
//        Object lvalue, rvalue;
//        Iterator<Medication> i;
//        ClinicalEntity item;
//        
//        lvalue = EvalExpr(lchild);  // A collection of ClinicalEntity objects.
//        rvalue = EvalExpr(rchild);  // This should match the 'name' property of
//                                    // one of the objects in lvalue for the 
//                                    // <contains> to evaluate to true.
//        for(i=((Collection)lvalue).iterator(); i.hasNext();) {
//            /* Polymorphism. 'item' can be a Diagnosis, LabTest, FollowUp or any
//             * other class that extends ClinicalEntity.
//             */
//            item = (ClinicalEntity)i.next();
////            System.out.println(item.name);
//            if((item).name.equals((String)rvalue))
//                return true;
//        }
//        return false;
//    }
    
    /**
     * Evaluate an expression.
     * @param expr  the root node of the expression
     * @return      Boolean: for relational, logical and set operations;
     *              Boolean, Integer, Float, String or NULL: for literals
     *              A value or ClinicalEntity: for external inputs
     */
    private static Object EvalExpr(Element expr)
    {
        Object evaluation = null;

        switch(expr.getLocalName()) {
            // Literals
            case "bvalue":
                evaluation = EvalBValue(expr);
                break;
            case "ivalue":
                evaluation = EvalIValue(expr);
                break;
            case "fvalue":
                evaluation = EvalFValue(expr);
                break;
            case "svalue":
                evaluation = EvalSValue(expr);
                break;
            case "null":
                evaluation = null;
                break;
                
            // External inputs
            case "emr":
                evaluation = EvalEmr(expr);
                break;
            case "profile":
                evaluation = EvalProfile(expr);
                break;
    
            // Relational operators
            case "eq":
                evaluation = EvalEq(expr);
                break;
            case "neq":
                evaluation = !(Boolean)EvalEq(expr);
                break;
            case "gt":
                evaluation = EvalGt(expr);
                break;
            case "gte":
                evaluation = EvalGt(expr) || EvalEq(expr);
                break;
            case "lt":
                evaluation = EvalLt(expr);
                break;
            case "lte":
                evaluation = EvalLt(expr) || EvalEq(expr);
                break;
            case "irange":
                evaluation = EvalIRange(expr);
                break;
            
            // Logical operators
            case "or":
                evaluation = EvalOr(expr);
                break;
            case "and":
                evaluation = EvalAnd(expr);
                break;
            case "not":
                evaluation = EvalNot(expr);
                break;
            
            // Set operation
//            case "contains":
//                evaluation = EvalContains(expr);
//                break;
            
            // Protocol-related operations
            case "noImprovement":
                evaluation = !protocol.GetImprovement();
                break;
                
            case "dateDiff":
                evaluation = protocol.DateDiff();
                break;
        }
        return evaluation;
    }
    
    /**
     * Evaluate the conditional that appears in an <if> tag.
     * @param cond      the conditional
     * @param emr       the medical record object
     * @param profile   the patient profile object
     * @return          True, False or NULL (if there is any error in the conditional)
     */
    public static Object EvalCondition(Node cond, Emr emr, Profile profile, Protocol p)
    {
        Object evaluation = null;
        XMLExprParser.emr = emr;
        XMLExprParser.profile = profile;
        protocol = p;
        evaluation = EvalExpr((Element)cond);
        //System.out.println(evaluation);
        return evaluation;
    }
}
