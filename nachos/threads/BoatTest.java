package nachos.threads;
import nachos.ag.BoatGrader;
import junit.framework.TestCase;


public class BoatTest extends TestCase {
	public void testBegin(){
		String s = "hello world";
		Boat b = new Boat();
		BoatGrader bg = new BoatGrader();
		System.out.print(s);
		// change static begin method to none static when run this 
		b.begin(0,2,bg);
		//b.selfTest();	
	}
}
