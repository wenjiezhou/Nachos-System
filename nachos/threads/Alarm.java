package nachos.threads;

import nachos.machine.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
    	if (waitQueue == null) { return; }
    	for(int i = 0; i<waitQueue.size(); i++){  
    	      waitThread current_th = waitQueue.get(i);
    	      if (current_th.getWakeTime() <= Machine.timer().getTime()) {
	  	    	  boolean intStatus = Machine.interrupt().disable();
	    	      (current_th.getThread()).ready();
	    	      waitQueue.remove(current_th);
	    	      Machine.interrupt().restore(intStatus);
    	      }
    	}
        KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
		//busy waiting
		long wakeTime = Machine.timer().getTime() + x;
	    boolean status = Machine.interrupt().disable();      
	    waitThread wait_th = new waitThread(KThread.currentThread(), wakeTime);
	    waitQueue.add(wait_th);
	    KThread.currentThread().sleep();
	    Machine.interrupt().restore(status);
    }
    /** to store the waitQueue. **/
    private static LinkedList<waitThread> waitQueue = new LinkedList<waitThread>();
    
    /** inner class to store the wait queue. */
    private class waitThread {
    	private long waketime = 0;
    	private KThread thread = null;
    	
    	waitThread(KThread th, long t) {
    		thread = th;
    		waketime = t;
    	}
    	
    	KThread getThread() {
    		return thread;
    	}
    	
    	long getWakeTime() {
    		return waketime;
    	}
    	
    }
    
    /*public static void selfTest() {
        AlarmTest.runTest();
   }*/

    
    
}