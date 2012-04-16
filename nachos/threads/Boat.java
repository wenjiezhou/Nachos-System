package nachos.threads;

import nachos.machine.Lib;
import nachos.ag.BoatGrader;
import java.util.ArrayList;

public class Boat {
	enum PersonType { Adult, Child }
	enum Location { Oahu, Molokai }
	static BoatGrader bg;
	static PersonType Adult = PersonType.Adult, Child = PersonType.Child;
	static Location Oahu = Location.Oahu,Molokai = Location.Molokai;
	static Location boatLocation;
	static ArrayList<Person> boat = new ArrayList<Person>();
	static int adultsOnOahu,childrenOnOahu,adultsOnMolokai,childrenOnMolokai;// tracking the numbers of individuls on each isLand
	static Lock boatLock; // make sure only one boat in use each time
	static Lock gameLock; // 
	static Condition moreThanOneChildAndEmptyBoatOnOahu,oneChildAndEmptyBoatOnOahu,childRiderInBoat, finishRowing, emptyBoatOnMolokai,gameOverOrNot; 
	static boolean gameOver,isFirstChildAppearedOnOahu; 

	/*public void selfTest() {
		BoatGrader b = new BoatGrader();

		System.out.println("\n ******************Testing Boats with only 2 children******************");
		begin(0, 2, b);

		System.out.println("\n ******************Testing Boats with 2 children, 1 adult******************");
		begin(1, 2, b);

		System.out.println("\n ******************Testing Boats with 3 children, 5 adults******************");
		begin(5, 3, b);
	}*/

	public static void begin(int adults, int children, BoatGrader b) {
		Lib.assertTrue(adults >= 0 && children >= 2);

		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		adultsOnOahu = childrenOnOahu = adultsOnMolokai = childrenOnMolokai = 0;
		isFirstChildAppearedOnOahu = gameOver = false;
		boatLocation = Oahu; // boat initially located on Oahu
		boatLock = new Lock();
		moreThanOneChildAndEmptyBoatOnOahu = new Condition(boatLock);
		oneChildAndEmptyBoatOnOahu = new Condition(boatLock);
		emptyBoatOnMolokai = new Condition(boatLock);
		childRiderInBoat = new Condition(boatLock);
		finishRowing = new Condition(boatLock);
		gameLock = new Lock();
		gameOverOrNot = new Condition(gameLock);
		gameLock.acquire();


		for (int i = 0; i < adults; i++) {
			KThread adultThread = new KThread(new Person(Adult));
			adultThread.setName("Adult");
			adultThread.fork();
		}
		for (int i = 0; i < children; i++) {
			KThread childThread = new KThread(new Person(Child));
			childThread.setName("Child");
			childThread.fork();
		}
		while (!gameOver) {
			gameOverOrNot.sleep();
			if (childrenOnMolokai + adultsOnMolokai != adults + children)
				emptyBoatOnMolokai.wake();				
			else {
				gameOver = true;
			}
		}
		gameLock.release();
	}



	static void rowToMolokaiByAdult(Person adult) {
		// adult row to Molokai
		adult.numberOnPreviousIsland = childrenOnOahu + adultsOnOahu - 1;
		adultsOnOahu--;
		adultsOnMolokai++;
		boatLocation = Molokai;
		GetoffFromBoat(adult);
		adult.location = Molokai; // done
	}
	static void rowToMolokaiByChild(Person child) {
		// child tow to Molokai
		child.numberOnPreviousIsland = childrenOnOahu + adultsOnOahu - 2;
		childrenOnOahu--;
		childrenOnMolokai++;
		boatLocation = Molokai;
		GetoffFromBoat(child);
		child.location = Molokai;
		boatLock.acquire();
		finishRowing.wake();
		boatLock.release();//done
	}
	static void rideToMolokaibyChild(Person child) {
		// child ride to molokai
		child.numberOnPreviousIsland = childrenOnOahu + adultsOnOahu - 1;
		childrenOnOahu--;
		childrenOnMolokai++;
		boatLocation = Molokai;
		GetoffFromBoat(child);
		child.location = Molokai;//done
	}
	static void rowToOahuByChild(Person child) {
		// child row to oahu
		child.numberOnPreviousIsland = childrenOnMolokai + adultsOnMolokai - 1;
		childrenOnMolokai--;
		childrenOnOahu++;
		boatLocation = Oahu;
		GetoffFromBoat(child);
		child.location = Oahu;// done
	}

	static void adultAddToBoat(Person adult) {
		// add adult to boat
		boatLock.acquire();
		while (childrenOnOahu != 1 || boatLocation != Oahu || !boat.isEmpty() || !isFirstChildAppearedOnOahu) {
			System.out.println(KThread.currentThread() + " failed to get on boat");
			oneChildAndEmptyBoatOnOahu.sleep();
		}
		boat.add(adult);
		boatLock.release();//done
	}
	static void childAddToBoat(Person child) {
		//add child to boat
		boatLock.acquire();
		while (boatLocation != Molokai || !boat.isEmpty() || gameOver) {
			System.out.println(KThread.currentThread() + " failed to get on boat");
			emptyBoatOnMolokai.sleep();
		}
		boat.add(child);
		boatLock.release();// done
	}
	static boolean isThisChildRower(Person child) { // returns true if child is rower
		boatLock.acquire();
		if (!isFirstChildAppearedOnOahu)
			isFirstChildAppearedOnOahu = true;
		else
			while (childrenOnOahu == 1 || boatLocation != Oahu || boat.size() >= 2) {
				System.out.println(KThread.currentThread() + " failed to get on boat");
				moreThanOneChildAndEmptyBoatOnOahu.sleep();
			}
		boat.add(child);
		if (boat.size() == 2) { //child is rider
			childRiderInBoat.wake();
			while (boat.size() == 2)
				finishRowing.sleep();
			boatLock.release();
			return false;
		}
		else { //child is rower
			while (boat.size() != 2)
				childRiderInBoat.sleep();
			boatLock.release();
			return true;
		}
	}
	static void GetoffFromBoat(Person person) {
		// individul get off boat
		boatLock.acquire();
		boat.remove(person);
		if (boat.isEmpty()) {
			if (boatLocation == Molokai) {
				if (person.numberOnPreviousIsland == 0) {
					gameLock.acquire();
					gameOverOrNot.wake();
					gameLock.release();
				}
				else
					emptyBoatOnMolokai.wake();
			}
			else if (childrenOnOahu == 1)
				oneChildAndEmptyBoatOnOahu.wake();
			else
				moreThanOneChildAndEmptyBoatOnOahu.wake();
		}
		boatLock.release();// done
	}
	static void AdultItinerary(Person adult) {
		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */
		Lib.assertTrue(adult.location == Oahu); // adult here should always be on Oahu, no adult from M to Oahu

		adultsOnOahu++; // increase number of adults on oahu by 1
		adultAddToBoat(adult);
		bg.AdultRowToMolokai();
		rowToMolokaiByAdult(adult);
	}

	static void ChildItinerary(Person child) {
		childrenOnOahu++; // increase number of children on Oahu by 1
		while (!gameOver) {
			if (child.location == Oahu) {
				if (isThisChildRower(child)) {
					bg.ChildRowToMolokai();
					rowToMolokaiByChild(child);
				}
				else {
					bg.ChildRideToMolokai();
					rideToMolokaibyChild(child);
				}
			}
			else if (child.numberOnPreviousIsland != 0) { // child go back to Oahu from M
				childAddToBoat(child);
				bg.ChildRowToOahu();
				rowToOahuByChild(child);
			}
			KThread.yield();
		}
	}

	// person type implements runnable, which give peron the ability to run
	static class Person implements Runnable {
		PersonType type;
		Location location;
		int numberOnPreviousIsland;
		Person(PersonType PorC) {
			location = Oahu;
			type = PorC;
		}
		public void run() {
			if (type == Child){
				ChildItinerary(this);
				}
			else{
				AdultItinerary(this);
				}
		}
	}
}