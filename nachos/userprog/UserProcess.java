package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.UserKernel.NotEnoughPagesException;
//import nachos.userprog.UserProcess.MemoryChunk;

import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;


/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		pidLock.acquire();
		pid = nextPid++;
		runningProcesses++;
		pidLock.release();

		// stdin/stdout
		fileTable[0] = UserKernel.console.openForReading(); //added on 314
	    fileTable[1] = UserKernel.console.openForWriting(); // added on 314

		// Exit/Join syncronization
		joinWaiting = new Condition(joinLock);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param name
	 * the name of the file containing the executable.
	 * @param args
	 * the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
		new UThread(this).setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}


	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param vaddr
	 * the starting virtual address of the null-terminated string.
	 * @param maxLength
	 * the maximum number of characters in the string, not including
	 * the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr
	 * the first byte of virtual memory to read.
	 * @param data
	 * the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr
	 * the first byte of virtual memory to read.
	 * @param data
	 * the array where the data will be stored.
	 * @param offset
	 * the first byte to write in the array.
	 * @param length
	 * the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length && memoryAccessLock != null);
		if (validAddress(vaddr)){
			LinkedList<MemoryChunk> memoryAccesses = newMemoryAccesses(vaddr, data, offset, length);

			int rBytes = 0;
			int temp = 0;

			memoryAccessLock.acquire();
			for (MemoryChunk ma : memoryAccesses) {
				temp = ma.readAccess();

				if (temp == 0)
					break;
				else
					rBytes += temp;
			}
			memoryAccessLock.release();
			return rBytes;
		}
		else{
			return 0;
		}
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr
	 * the first byte of virtual memory to write.
	 * @param data
	 * the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr
	 * the first byte of virtual memory to write.
	 * @param data
	 * the array containing the data to transfer.
	 * @param offset
	 * the first byte to transfer from the array.
	 * @param length
	 * the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length && memoryAccessLock != null);
		if (validAddress(vaddr)){
			LinkedList<MemoryChunk> memoryAccesses = newMemoryAccesses(vaddr, data, offset, length);

			int rBytes = 0;
			int temp = 0;
			memoryAccessLock.acquire();
			for (MemoryChunk ma : memoryAccesses) {
				temp = ma.writeAccess();
				if (temp == 0)
					break;
				else
					rBytes += temp;
			}
			memoryAccessLock.release();

			return rBytes;
		}
		else{
			return 0;
		}
	}
	 //Check to see if the virtual address is valid.

	protected boolean validAddress(int vaddr) {
		int vpn = Processor.pageFromAddress(vaddr);
		return vpn < numPages && vpn >= 0;
	}
	
	//Generates a set of <tt>MemoryChunk</tt> instances corresponding to the desired action.
	
	private LinkedList<MemoryChunk> newMemoryAccesses(int vaddr, byte[] data, int offset, int length) {
		LinkedList<MemoryChunk> returnList = new LinkedList<MemoryChunk>();
		MemoryChunk memoryChunk;
		int vpn, pageMayAccess,sizeGetAccess;
		while (length > 0) {
			vpn = Processor.pageFromAddress(vaddr);

			pageMayAccess = Processor.pageSize - Processor.offsetFromAddress(vaddr);
			if(length < pageMayAccess)
				sizeGetAccess = length;
			else
				sizeGetAccess = pageMayAccess;
			memoryChunk = new MemoryChunk(data, vpn, offset, Processor.offsetFromAddress(vaddr), sizeGetAccess);
			returnList.add(memoryChunk);
			length -= sizeGetAccess;
			vaddr += sizeGetAccess;
			offset += sizeGetAccess;
		}

		return returnList;
	}

	/**
	 * An inner class to represent a memory access.
	 */
	protected class MemoryChunk {
		//A reference to the data array we are supposed to fill or write from
		private byte[] data;

		//The translation entry corresponding to the appropriate page to be accessed.
		private TranslationEntry translationEntry;

		private int dataStart;//Bounds for accessing the data array.		 
		private int pageStart;//Bounds for accessing the page.
		private int length;//Length of the access (the same for the array and the page).
		private int vpn;//The VPN of the page needed.
		
		protected MemoryChunk(byte[] d, int Vpn, int dStart, int pStart, int len) {
			data = d;
			vpn = Vpn;
			dataStart = dStart;
			pageStart = pStart;
			length = len;
		}

		/**
		 * Execute the requested memory access.
		 * @return The number of bytes successfully written (or 0 if it fails).
		 */
		public int readAccess() {
			if (translationEntry == null)
				translationEntry = pageTable[vpn];
			if (translationEntry.valid) {
				System.arraycopy(Machine.processor().getMemory(), pageStart + (Processor.pageSize * translationEntry.ppn), data, dataStart, length);
				translationEntry.used = true;
				return length;
			}

			return 0;
		}
		
		/**
		 * Execute the requested memory access.
		 * @return The number of bytes successfully read (or 0 if it fails).
		 */
		public int writeAccess() {
			if (translationEntry == null)
				translationEntry = pageTable[vpn];
			if (translationEntry.valid) {
				if(!translationEntry.readOnly){
					System.arraycopy(data, dataStart, Machine.processor().getMemory(), pageStart + (Processor.pageSize * translationEntry.ppn), length);
					translationEntry.used = true;
					translationEntry.dirty = true;
					return length;
				}
			}
			return 0;
		}		
		
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param name
	 * the name of the file containing the executable.
	 * @param args
	 * the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i=0; i<args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();	

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i=0; i<argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
				argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be
	 * run (this is the last step in process initialization that can fail).
	 *
	 * @return	<tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		try {
			pageTable = ((UserKernel) Kernel.kernel).getPages(numPages);

			for (int i = 0; i < pageTable.length; i++)
				pageTable[i].vpn = i;

			for (int j = 0; j < coff.getNumSections(); j++) {
				CoffSection section = coff.getSection(j);

				Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

				int firstVPN = section.getFirstVPN();
				for (int k = 0; k < section.getLength(); k++)
					section.loadPage(k, pageTable[k+firstVPN].ppn);
			}
		} catch (NotEnoughPagesException a) {
			coff.close();
			Lib.debug(dbgProcess, "physical memory out of bound");
			return false;
		} catch (ClassCastException c) {
			Lib.assertNotReached("Error : Class Cast Exception");
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < Processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if (pid != 0)
			return 0;

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}


	

	/** Handle the create() system call. */
    private int handleCreate(int stringAddress) {
        if (readVirtualMemoryString(stringAddress, maxLen)==null) {
        	return -1;
        }
      
        int fd = firstOpen();
        if (fd!=-1) {
        	fileTable[fd]=ThreadedKernel.fileSystem.open
        		(readVirtualMemoryString(stringAddress, maxLen),true);
        	if (fileTable[fd]==null) {
        		return -1;
        	} else {
        		return fd;
            }
        } else {
        	return -1;
        }
    }

    /** Handle the open() sytem call. */
    private int handleOpen(int stringAddress) {
    	if (readVirtualMemoryString(stringAddress, maxLen)==null) {
        	return -1;
        }
      
        int fd = firstOpen();
        if (fd!=-1) {
        	fileTable[fd]=ThreadedKernel.fileSystem.open
        		(readVirtualMemoryString(stringAddress, maxLen),false);
        	if (fileTable[fd]==null) {
        		return -1;
        	} else {
        		return fd;
            }
        } else {
        	return -1;
        }
    }

	/** Handle the read() sytem call. */
    private int handleRead(int fd,int bufferAddress,int length) {
        // check if fd is validate
    	if (!checkFd(fd)) return -1;  //changed on 314
    	//if (!checkFd(fd)) return 0;
        byte[] tmp = new byte[length];
        int readAmount = fileTable[fd].read(tmp, 0, length);
        if (readAmount < 0) {   // whole if statement changed on 314
        	return -1;
        } else {
        	if (readAmount == 0) return 0;
        }
        byte[] data = new byte[readAmount];
        System.arraycopy(tmp, 0, data, 0, readAmount);
        int written = this.writeVirtualMemory(bufferAddress, tmp, 0, readAmount);
        
        if (written < 0) { return -1;
        } else {
        	return readAmount;
        }

    }

	
    /** Handle the write() system call.*/
    private int handleWrite(int fd,int bufferAddress,int length) {
    	// check if fd is validate
    	if (!checkFd(fd)) return -1;  //changed on 314
    	//if (!checkFd(fd)) return 0;
        byte[] tmp = new byte[length];
        int readAmount = readVirtualMemory(bufferAddress, tmp, 0, length);
        if (readAmount != length) {
        	return -1;
        } else {
        	return fileTable[fd].write(tmp, 0, length);
        }
    }
    
    /** Handle the close() system call. */
    private int handleClose(int fd) {
    	// check if fd is validate
        if (!checkFd(fd)) return -1;
        OpenFile tmp = fileTable[fd];
        tmp.close();
        fileTable[fd] = null;
        return 0;
    }
    
    /** Handle the unlink() system call. */
    private int handleUnlink(int address) {
    	if (ThreadedKernel.fileSystem.remove
    			(readVirtualMemoryString(address, maxLen))) {
    		return 0;
    	}
    	return -1;    	
    }
    
    private int firstOpen() {
    	for (int i = 0; i < fileTable.length; i++) {
    		if (fileTable[i] == null) return i;
    	}
    	return -1;
    }

    private boolean checkFd(int fd) {
    	if (fd>15 || fd < 0 || fileTable[fd]==null) {
        	return false; 
        }
    	return true;
    }

    // part III Start:
    /**
	 * return -1 on attempt to join non child process
	 * 1 if child exited due to unhandled exception
	 * 0 if child exited cleanly
	 */
	private int join(int pid, int statusAddr) {
		if (!validAddress(statusAddr))
			return terminate();

		ExtendedProcess child = childrenProc.get(pid);

		// Can't join on non-child!
		if (child == null)
			return -1;

		// Child still running, try to join
		if (child.c_process != null)
			child.c_process.joinProcess();
		// We can safely forget about this child after join
		childrenProc.remove(pid);

		// Child will have transfered return value to us

		// Child exited due to unhandled exception
		if (child.rValue == null)
			return 0;

		// Transfer return value into status ptr
		writeVirtualMemory(statusAddr, Lib.bytesFromInt(child.rValue));

		// Child exited cleanly
		return 1;
	}

	/**
	 * Cause caller to sleep until this process has exited
	 */
	private void joinProcess() {
		joinLock.acquire();
		while (!isExit)
			joinWaiting.sleep();
		joinLock.release();
	}

	/**
	 *return pid of child process, and return -1 if failure
	 */
	private int exec(int fileNameAddr, int argc, int argvAddr) {
		// Read filename from virtual memory
		String fileName = readVirtualMemoryString(fileNameAddr, maxLen);
		// Gather arguments for the new process
		String arguments[] = new String[argc];
		UserProcess newChild;
		
		// Verify that passed pointers are valid
		if (!validAddress(fileNameAddr) || !validAddress(argv))
			return terminate();

		if (fileName == null || !fileName.endsWith(".coff"))
			return -1;

		byte argcByteArray[] = new byte[argc * 4];
		
		if (readVirtualMemory(argvAddr, argcByteArray) != (argc * 4)) {
			// Failed to read the whole array
			return -1;
		}
		// Read each argument string from argcByteArray
		for (int i = 0; i < argc; i++) {
			// Get char* pointer for next position in array
			int tempAddr = Lib.bytesToInt(argcByteArray, i*4);

			if (!validAddress(tempAddr))
				return -1;

			// Read in the argument string
			arguments[i] = readVirtualMemoryString(tempAddr, maxLen);
		}

		// New process
		newChild = newUserProcess();
		newChild.parentProc = this;

		// Remember our children
		childrenProc.put(newChild.pid, new ExtendedProcess(newChild));

		// Run and be free!
		newChild.execute(fileName, arguments);

		return newChild.pid;
	}

	/**
	 * Handle exiting and cleanup of a process
	 * @param status
	 * Integer exit status, or null if exiting due to unhandled exception
	 * @return
	 * Irrelevant - user process never sees this syscall return
	 */
	private int exit(Integer status) {
		joinLock.acquire();

		// tell parent that we're exiting
		if (parentProc != null)	
			parentProc.childStatus(pid, status);

		// Disown all of our running children
		for (ExtendedProcess child : childrenProc.values())
			if (child.c_process != null)
				child.c_process.notOwn();
		childrenProc = null;

		// Loop through all open files and close them, releasing references
		for (int fileDesc = 0; fileDesc < fileTable.length; fileDesc++){
			if(fileDesc >= 0 && fileDesc < fileTable.length){
				if(fileTable[fileDesc] != null){
					handleClose(fileDesc);
				}
			}
		}
	
		// Free virtual memory
		((UserKernel)Kernel.kernel).pagesFree(pageTable);

		// Wakeup anyone who is waiting for this to exit
		isExit = true;
		joinWaiting.wakeAll();
		joinLock.release();

		// Halt the machine if we were the last process
		pidLock.acquire();
		if (--runningProcesses == 0)
			Kernel.kernel.terminate();
		pidLock.release();

		// Terminate current thread
		KThread.finish();

		return 0;
	}

	
	// Called on a parent process by an exiting child to inform them that the child has terminated.
	protected void childStatus(int childPid, Integer childStatus) {
		ExtendedProcess cProcess = childrenProc.get(childPid);
		if (cProcess == null)
			return;

		// Remove reference to actual child so it can be garbage collected
		cProcess.c_process = null;
		// Record child's exit status for posterity
		cProcess.rValue = childStatus;
	}

	 // Called on a child by an exiting parent to inform them that they are now an orphan. 
	protected void notOwn() {
		parentProc = null;
	}

	
	// Terminate this process due to unhandled exception
	private int terminate() {
		exit(null);
		return -1;
	}

	

	private static final int
    syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int exec(char *name, int argc, char **argv);
	 * </tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int read(int fd, char *buffer, int size);
	 * </tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int write(int fd, char *buffer, int size);
	 * </tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 *
	 * @param syscall
	 * the syscall number.
	 * @param a0
	 * the first syscall argument.
	 * @param a1
	 * the second syscall argument.
	 * @param a2
	 * the third syscall argument.
	 * @param a3
	 * the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();

		case syscallExit:
			return exit(a0);
		case syscallExec:
			return exec(a0, a1, a2);
		case syscallJoin:
			return join(a0, a1);

		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exception</tt> constants.
	 *
	 * @param cause
	 * the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);

			terminate();
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/**
	 * Internal class to keep track of children processes and their exit value
	 */
	private static class ExtendedProcess {
		public Integer rValue;
		public UserProcess c_process;

		ExtendedProcess(UserProcess child) {
			c_process = child;
			rValue = null;
		}
	}
	/** Lock to protect static variables */
	private static Lock pidLock = new Lock();

	/** Process ID */
	private static int nextPid = 0;
	protected int pid;

	/** Parent/Child process tree */
	protected UserProcess parentProc;
	private HashMap<Integer, ExtendedProcess> childrenProc = new HashMap<Integer, ExtendedProcess> ();

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final int maxLen = 256;

	/**
	 * A lock to protect memory accesses.
	 */
	private Lock memoryAccessLock = new Lock();

	/** File table to deal with file system calls. */
	protected OpenFile[] fileTable = new OpenFile[16];

	/** Join condition */
	private boolean isExit = false;
	private Lock joinLock = new Lock();
	private Condition joinWaiting;

	/** Number of processes */
	private static int runningProcesses = 0;

	
}