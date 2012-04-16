package nachos.threads;

import nachos.machine.*;

//import java.util.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fashion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority
	 * <tt>true</tt> if this queue should transfer priority from
	 * waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

		ThreadState ts = getThreadState(thread);

		//To make sure we don't do unnecessary calculation
		//may change
		if (priority != ts.getPriority())
			ts.setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean returnBool = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			returnBool = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return returnBool;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean returnBool = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			returnBool = false;
		else
			setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return returnBool;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread
	 * the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		  // take advantage of java build in Priority Queue		 
		private java.util.PriorityQueue<ThreadState> waitQueue = new java.util.PriorityQueue<ThreadState>(8,new ThreadStateComparator<ThreadState>(this));		
		 //The KThread that locks this PriorityQueue. null initially.		 
		private KThread inLockedThread = null;
		protected class ThreadStateComparator<T extends ThreadState> implements Comparator<T> {
			private nachos.threads.PriorityScheduler.PriorityQueue pQueue;
			protected ThreadStateComparator(nachos.threads.PriorityScheduler.PriorityQueue priorityQ) {
				pQueue = priorityQ;
			}

			
			public int compare(T t1, T t2) {
				//first compare by effective priority
				int o1EP = t1.getEffectivePriority();
				int o2EP = t2.getEffectivePriority();
				if (o1EP < o2EP) {
					return 1;
				} else if (o1EP > o2EP) {
					return -1;
				} else {
					//compare by the times these threads have stay in this queue
					long waitTimeforo1 = t1.waitingMap.get(pQueue);
					long waitTimeforo2 = t2.waitingMap.get(pQueue);
					
					// compare the waiting time between two thread with same priority
					if (waitTimeforo1 > waitTimeforo2) {
						return 1;
					} else if(waitTimeforo1 < waitTimeforo2) {
						return -1;
					} else {
						//The threads are equal, should be the same thread
						return 0;
					}
				}
			}
			
		}
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			if (waitQueue.isEmpty()) {
				return null;				
			} else {
				acquire(waitQueue.poll().threadObjedt);
				return inLockedThread;
			}
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			//implement me
			return waitQueue.peek();
		}

		public void print() {
			// implement me (if you want)
			//answer: i do not want to implement this
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		boolean transferPriority;	

	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 *
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		
        // initialize the variables that using inside TreadState class
		protected KThread threadObjedt;
		protected int valuePriority;
		protected int valueEffectivePriority;

		// A hash set of all the PriorityQueues,this ThreadState has acquired 
		private HashSet<PriorityScheduler.PriorityQueue> threadStateHasacquired = new HashSet<PriorityScheduler.PriorityQueue>();

		// A hash map of all the PriorityQueues, this ThreadState is waiting on mapped to the time they were waiting on them
		private HashMap<PriorityScheduler.PriorityQueue,Long> waitingMap = new HashMap<PriorityScheduler.PriorityQueue,Long>();
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread
		 * the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.threadObjedt = thread;
			// set effectivePriority equal to priorityDfault value, which is 1
			valueEffectivePriority = priorityDefault;
			setPriority(priorityDefault);
		}

		/**
		 * Release this priority queue from the resources this ThreadState has locked.
		 * <p>
		 * This is the only time the effective priority of a thread can go down and needs a full recalculation.
		 * <p>
		 * We can detect if this exists if the top effective priority of the queue we are release is equal to this current effective priority.
		 * If it is less than (it cannot be greater by definition), then we know that something else is contributing to the effective priority of <tt>this</tt>.
		 * @param priorityQueue
		 */
		private void release(PriorityQueue priorityQueue) {
			// remove priorityQueue from my acquired set
			if (threadStateHasacquired.remove(priorityQueue)) {
				priorityQueue.inLockedThread = null;
				updateEffectivePriority();
			}
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		int getPriority() {
			return valuePriority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return the effective priority of the associated thread.
		 */
		int getEffectivePriority() {
			// implement me
			return valueEffectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value. <p>
		 * This method assumes the priority has changed. Protection is from PriorityScheduler class calling this.
		 * @param priority   the new priority.
		 */
		public void setPriority(int priority) {
			this.valuePriority = priority;
			updateEffectivePriority();
		}

		protected void updateEffectivePriority() {
			
			// update effective Priority 
			for (PriorityQueue innerPQ : waitingMap.keySet()) //hashmap.keySet() return a set view of the keys contained in this map
				innerPQ.waitQueue.remove(this);

			int tPriority = valuePriority;

			for (PriorityQueue innerPQ : threadStateHasacquired) {
				if (innerPQ.transferPriority) {
					ThreadState topTS = innerPQ.waitQueue.peek();
					if (topTS != null) {
						int highestPQ = topTS.getEffectivePriority();

						if (highestPQ > tPriority)
							tPriority = highestPQ;
					}
				}
			}

			boolean isTransfer = (tPriority != valueEffectivePriority);

			valueEffectivePriority = tPriority;

			
			 //Add this thread back into waitQueue and update all the results
			 
			for (PriorityQueue pq : waitingMap.keySet())
				pq.waitQueue.add(this);

			if (isTransfer)
				for (PriorityQueue innerPQ : waitingMap.keySet()) {
					if (innerPQ.transferPriority && innerPQ.inLockedThread != null)
						getThreadState(innerPQ.inLockedThread).updateEffectivePriority();
				}
			// done with update
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 *
		 * @param priorityQ
		 * the queue that the associated thread is now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		void waitForAccess(PriorityQueue priorityQ) {
			if (!waitingMap.containsKey(priorityQ)) {
				//Unlock this wait queue, if this thread holds it
				release(priorityQ);
				//Put it on the queue
				waitingMap.put(priorityQ, Machine.timer().getTime());

				//The effective priority of this shouldn't change, so just shove it onto the waitQueue's members
				priorityQ.waitQueue.add(this);

				if (priorityQ.inLockedThread != null) {
					getThreadState(priorityQ.inLockedThread).updateEffectivePriority();
				}
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		void acquire(PriorityQueue priorityQ) {
			//unlock the current locking thread
			if (priorityQ.inLockedThread != null) {
				getThreadState(priorityQ.inLockedThread).release(priorityQ);
			}

			
			// Remove the passed thread state from the queues, if it exists on them
			 
			priorityQ.waitQueue.remove(this);

			//the hash set acquire the thread
			priorityQ.inLockedThread = this.threadObjedt;
			threadStateHasacquired.add(priorityQ);
			waitingMap.remove(priorityQ);

			updateEffectivePriority();
		}

	}


}