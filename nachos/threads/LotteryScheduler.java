package nachos.threads;

import nachos.machine.*;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends Scheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
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
		Lib.assertTrue(priority > 0);
		getThreadState(thread).setPriority(priority);
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		KThread thread = KThread.currentThread();
		int priority = getPriority(thread);
		if(priority == 1) {
			return false;
		}
		setPriority(thread, priority - 1);
		Machine.interrupt().restore(intStatus);
		return true;
	}
	
	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		KThread thread = KThread.currentThread();
		int priority = getPriority(thread);
		setPriority(thread, priority + 1);
		Machine.interrupt().restore(intStatus);
		return true;
	}
	
	private ThreadState getThreadState(KThread thread) {
		if(thread.schedulingState == null) {
			thread.schedulingState = new ThreadState(thread);
		}
		return (ThreadState) thread.schedulingState;
	}
	
	protected class LotteryQueue extends ThreadQueue{
		
		public LotteryQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			this.dirty = true;
			this.totalTickets = 0;
			this.owner = null;
			this.threadQueue = new TreeSet<KThread>(new ThreadComparator());
		}
		
		class ThreadComparator implements Comparator<KThread>{
			public int compare(KThread thread1, KThread thread2){
				ThreadState threadState1 = getThreadState(thread1);
				ThreadState threadState2 = getThreadState(thread2);
				int thread1EffectivePriority = threadState1.getEffectivePriority();
				int thread2EffectivePriority = threadState2.getEffectivePriority();
				if(thread1EffectivePriority > thread2EffectivePriority) {
					return -1;
				} else if(thread1EffectivePriority < thread2EffectivePriority) {
					return 1;
				} else {
					if(threadState1.id < threadState2.id) {
						return -1;
					} else if(threadState1.id > threadState2.id) {
						return 1;
					} else {
						return 0;
					}
				}
			}
		}
		
		public void acquire(KThread thread) {
			this.owner = thread;
			if(this.transferPriority) {
				getThreadState(thread).update(this.totalTickets);
			}
		}
		
		public void waitForAccess(KThread thread) {
			this.threadQueue.add(thread);
			ThreadState threadState = getThreadState(thread);
			threadState.waiting = this;
			this.update(threadState.getEffectivePriority());
		}
		
		public KThread nextThread() {
			this.release();
			KThread thread = this.pickNextThread();
			if(thread != null) {
				threadQueue.remove(thread);
				ThreadState threadState = getThreadState(thread);
				threadState.waiting = null;
				this.update(-threadState.getEffectivePriority());
				this.acquire(thread);
			}
			return thread;
		}
		
		public KThread pickNextThread() {
			KThread thread;
			if(this.dirty) {
				if(this.totalTickets > 0) {
					int depth = Lib.random(this.totalTickets);
					Iterator<KThread> iterator = this.threadQueue.iterator();
					do {
						thread = iterator.next();
						depth -= getThreadState(thread).getEffectivePriority();
					} while(depth >= 0);
					this.nextThread = thread;
				} else {
					this.nextThread = null;	
				}
				this.dirty = false;
			}
			return this.nextThread;
		}
		
		public void print() {
		}
		
		public void update(int changeInPriority) {
			this.dirty = true;
			this.totalTickets += changeInPriority;
			if(this.transferPriority && (this.owner != null)) {
				getThreadState(this.owner).update(changeInPriority);
			}
		}
		
		void release() {
			if(this.transferPriority && (this.owner != null)) {
				getThreadState(this.owner).update(-this.totalTickets);
			}
			this.owner = null;
		}
		
		boolean transferPriority;
		boolean dirty;
		int totalTickets;
		KThread nextThread;
		KThread owner;
		TreeSet<KThread> threadQueue;
	}

	protected class ThreadState {
		
		public ThreadState(KThread thread) {
			this.thread = thread;
			this.priority = 1;
			this.effectivePriority = 1;
			this.waiting = null;
			this.id = LotteryScheduler.id++;
		}

		public int getPriority() {
			return this.priority;
		}
		
		public int getEffectivePriority() {
			return this.effectivePriority;
		}
		
		public void setPriority(int priority) {
			int changeInPriority = priority - this.priority;
			this.priority = priority;
			this.update(changeInPriority);
		}
		
		public void update(int changeInPriority) {
			if(this.waiting != null) {
				this.waiting.threadQueue.remove(this.thread);
			}
			this.effectivePriority += changeInPriority;
			if(this.waiting != null) {
				this.waiting.threadQueue.add(this.thread);
				this.waiting.update(changeInPriority);
			}
		}
		
		KThread thread;
		LotteryQueue waiting;
		public int priority;
		public int effectivePriority;
		public long id;
	}
	
	static long id = 0;
}