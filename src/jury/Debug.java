package jury;

/**
 * Enable or disable debugging output.
 */
public class Debug {
    /* 2-level debugging switches */
    // app-level switch to turn on/off debugging output
    public static boolean state = false;
    
    // class specific switches
    public static boolean dbgJury = false;
    public static boolean dbgTransition = false;
    public static boolean dbgState = false;
    public static boolean dbgProtocol = true;
    public static boolean dbgEmr = false;
    public static boolean dbgMedication = false;
    public static boolean dbgProfile = false;
    
    // write to db?
    public static boolean dbgDbNoWrite = false;
    
    public static int visitId;
    
    public static void Println(String str)
    {
        System.out.println("[" + visitId + "] " + str);
    }
    
    public static void Print(String str)
    {
        System.out.print("[" + visitId + "] " + str);
    }
}
