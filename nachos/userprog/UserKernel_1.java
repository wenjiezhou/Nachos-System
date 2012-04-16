package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel_1 extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel_1() {
	super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
	super.initialize(args);

	console = new SynchConsole(Machine.console());
	
	Machine.processor().setExceptionHandler(new Runnable() {
		public void run() { exceptionHandler(); }
	    });
	int numPhysPages = Machine.processor().getNumPhysPages();
	for(int i = 0; i < numPhysPages; i++){
		TranslationEntry eachPage= new TranslationEntry(0,i,false,false,false,false);
		freePhysicalPages.add(eachPage);
	}
	pagesLock = new Lock();
    }
    

    /**
     * Test the console device.
     */	
    public void selfTest() {
	super.selfTest();

	System.out.println("Testing the console device. Typed characters");
	System.out.println("will be echoed until q is typed.");

	char c;

	do {
	    c = (char) console.readByte(true);
	    console.writeByte(c);
	}
	while (c != 'q');

	System.out.println("");
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
	if (!(KThread.currentThread() instanceof UThread))
	    return null;
	
	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
	Lib.assertTrue(KThread.currentThread() instanceof UThread);

	UserProcess process = ((UThread) KThread.currentThread()).process;
	int cause = Machine.processor().readRegister(Processor.regCause);
	process.handleException(cause);
    }
    
    /**
     * get the requested number of page from the free physical pages
     * @param numPages
     * @return a PageTable that the process need
     */
    public TranslationEntry[] getPages(int numPages){
    	//TranslationEntry[] abtainedPages = null;
    	pagesLock.acquire();
    	TranslationEntry[] abtainedPages = null;
    	if(freePhysicalPages.size() > numPages - 1){
    		abtainedPages = new TranslationEntry[numPages];
    		for(int i = 0; i < numPages; i++){
    			abtainedPages[i] = freePhysicalPages.remove();
    			abtainedPages[i].valid = true;
    		}
    	}
    	pagesLock.release();
    	return abtainedPages;
    }
    
    /**
     * free the memory, and append all this free pages back to freePhysicalPages list
     * @param exitPages
     */
    public void pagesFree(TranslationEntry[] exitPages){
    	pagesLock.acquire();
    	
    	for(TranslationEntry page : exitPages){
    		page.valid = false;
    		freePhysicalPages.add(page);	
    	}
    	pagesLock.release();
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
	super.run();

	UserProcess process = UserProcess.newUserProcess();
	
	String shellProgram = Machine.getShellProgramName();	
	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

	KThread.currentThread().finish();
    }
    

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
    
    // a lock to protect access to the  linked list of free pages
    private Lock pagesLock;
    // global linked list of free physical pages
    private LinkedList <TranslationEntry> freePhysicalPages = new LinkedList<TranslationEntry>();
}
