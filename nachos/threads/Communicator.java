package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
/*public class Communicator {
	private Lock lockMaker;
	private Condition speakCondition;
	private Condition listenCondition;
	private int getWord;
	private boolean isEmpty;
    /**
 * Allocate a new communicator.

    public Communicator() {
    	lockMaker = new Lock();
    	speakCondition = new Condition(lockMaker);
    	listenCondition = new Condition(lockMaker);
    	isEmpty = true;


    }

    /**
 * Wait for a thread to listen through this communicator, and then transfer
 * <i>word</i> to the listener.
 *
 * <p>
 * Does not return until this thread is paired up with a listening thread.
 * Exactly one listener should receive <i>word</i>.
 *
 * @param	word	the integer to transfer.

    public void speak(int word) {
    	lockMaker.acquire();
    	while(isEmpty == false){
    		speakCondition.sleep();
    	}
    	getWord = word;
    	listenCondition.wakeAll();
    	isEmpty = false;
    	lockMaker.release();
    }

    /**
 * Wait for a thread to speak through this communicator, and then return
 * the <i>word</i> that thread passed to <tt>speak()</tt>.
 *
 * @return	the integer transferred.

    public int listen() {
    	lockMaker.release();
    	while(isEmpty == true){
    		listenCondition.sleep();
    	}
    	speakCondition.wakeAll();
    	isEmpty = false;
    	lockMaker.release();

	return getWord;
    }
}*/

public class Communicator {

	private Lock lock;
	private Condition speaker;
	private Condition listener;

	private int sharedWord = 0;
	private Boolean isSharedWordInUse = false;
	private int waitListeners = 0;


	public Communicator() {
		lock = new Lock();
		speaker = new Condition(lock);
		listener = new Condition(lock);
	}


	public void speak(int word) {
		lock.acquire();
		// Wait until someone is listening, and the shared storage is free
		while (waitListeners == 0 || isSharedWordInUse)
			speaker.sleep();

		// Claim the shared storage
		isSharedWordInUse = true;
		sharedWord = word;

		// Tell a listener that a value is ready to be picked up
		listener.wake();
		lock.release();
	}


	public int listen() {
		lock.acquire();
		// Tell a speaker, if one exists, that a listener is available
		waitListeners++;
		speaker.wake();
		listener.sleep();

		// A speaker has notified us that there is data ready
		int word = sharedWord;
		isSharedWordInUse = false;
		waitListeners--;

		// Tell any waiting speakers that the shared storage is free
		speaker.wake();
		lock.release();
		return word;
	}
}