package robots;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

import reinforcement.LUTInterface;
import robocode.AdvancedRobot;
import robocode.BattleEndedEvent;
import robocode.BattleResults;
import robocode.Bullet;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobocodeFileWriter;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;
import robocode.control.snapshot.RobotState;
import util.Pair;

public class Fred1 extends AdvancedRobot implements LUTInterface
{
	public static Hashtable<Integer, Double> LUT = new Hashtable<Integer, Double>();
	public static ArrayList<Integer> outcomes = new ArrayList<Integer>();
	public static ArrayList<Double> trackQvalues = new ArrayList<Double>();
	public static Integer trackStateKey = null;
	public static Integer counter = 0;
	public static Integer numRounds = 0;
	
	private double alpha = 0.1;
	private double gamma = 0.9;
	// TODO: try different epsilon
	private double epsilon = 0.05;
	private double initQ = 1.0;
	private double robotDistance;
	private double absBearing = 0;
	
	private boolean bulletHit = false;
	private double bulletHitPower;
	private boolean bulletMiss = false;
	private double bulletMissPower;
	private boolean hitByBullet = false;
	private double hitByBulletPower;
	private boolean win = false;
	private double winReward;
	private boolean dead = false;
	private double deadPenalty;
	
	public static int numInputs = 5;
	public static int numActions = 7;
	
	private double [] s = null;
	private double [] state = null, prevState = null;

	public Fred1()
	{
	}

	public void smartFire(double robotDistance) {
		setFire(Math.min(400 / robotDistance, 3));
	}
	
	private void executeAction(int action)
	{
		
		switch(action)
		{
			case 0: 
			{
				this.setAhead(100);
				break;
			}
			case 1:
			{
				this.setBack(100);
				break;
			}
			case 2:
			{
				this.setTurnRight(90);
				this.setAhead(100);
				break;
			}
			case 3:
			{
				this.setTurnLeft(90);
				this.setAhead(100);
				break;
			}
			case 4:
			{
				setTurnGunRightRadians( robocode.util.Utils.normalRelativeAngle(absBearing - getGunHeadingRadians()) );
				smartFire(robotDistance);
				break;
			}
			case 5:
			{
				smartFire(robotDistance);
				break;
			}
			case 6:
			{
				setTurnGunRightRadians( 2.1 * robocode.util.Utils.normalRelativeAngle(absBearing - getGunHeadingRadians()) );
				smartFire(robotDistance);
				break;
			}
		}
	}

	/*
	 * (x, y, energy, enemy_x, enemy_y, action)
	 * The first x[0] to x[X.length-2], are the state s
	 * The last element, x[X.length-1], contains the action ID
	 */
	@Override
	public int indexFor(double[] X) {
		String s = ""; 
		for (int i = 0; i < X.length; i++)
		{
			s += X[i];
		}
		return s.hashCode();
	}

	private double [] reduceDimension(double [] currState)
	{
		double [] state = new double[currState.length];
		System.arraycopy(currState, 0, state, 0, currState.length);
		state[0] = (int)Math.round(state[0])/100;
		state[1] = (int)Math.round(state[1])/100;
		state[2] = state[2] > 70 ? 3 : (state [2] > 30 ? 2 : 1);
		state[3] = (int)Math.round(state[3])/100;
		state[4] = (int)Math.round(state[4])/100;

		return state;
	}
	
	private void enterState(double [] newState)
	{
		int key = indexFor(reduceDimension(newState));
		if (!LUT.containsKey(key))
		{
			// this state does not exist in the LUT, enter it with an initial value
			LUT.put(key, initQ);
		}
	}
	
	private void enterStates(double [][] newStates)
	{
		for (int i = 0; i < newStates.length; i++)
			enterState(newStates[i]);
	}

	// snapshot of the state in X and my heading in radius
	public Pair<Double, double []> getNextState(double[] X, double myHeading)
	{
		double [][] newStates = new double[numActions][numInputs + 1]; // +1 for action

		for (int i = 0; i < numActions; i++)
		{
			System.arraycopy(X, 0, newStates[i], 0, X.length);
			newStates[i][numInputs] = i;
		}

		enterStates(newStates);

		// choose the action that is the best, and return that action
		Pair<Double, double []> optimalState = optimalAction(newStates, epsilon);
		return optimalState;
	}
	
	// return the action ID (0, ..., 6) and the new state in double []
	private Pair<Double, double []> optimalAction(double [][] newStates, double epsilon)
	{
		Random random = new Random();
		ArrayList<Integer> bestCands = new ArrayList<Integer>();
		int bestAction = 0;
		double bestValue = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < newStates.length; i++)
		{
			double val = LUT.get(indexFor(reduceDimension(newStates[i])));
			// implement how to break ties
			if (val > bestValue)
			{
				bestValue = val;
				bestCands = new ArrayList<Integer>();
				bestCands.add(i);
			}
			else if (val == bestValue)
			{
				bestCands.add(i);
			}
		}
		
		
		int idx = random.nextInt(bestCands.size());
		bestAction = bestCands.get(idx);
		
		int exploreAction = random.nextInt(numActions);
		
		int finalAction = (random.nextDouble() <= epsilon) ? exploreAction : bestAction;
		
		// TODO: switch on and off policy
		// off-policy
//		double finalValue = bestValue;
		// on-policy
		double finalValue = LUT.get(indexFor(reduceDimension(newStates[finalAction])));
		
		Pair<Double, double[]> best = new Pair<Double, double[]>(finalValue, newStates[finalAction]);
		return best;
	}
	
	
	public void computeReward(double [] prevState, double [] newState, double bestValue)
	{
		double reward = 0;
		// TODO: delete intermediate reward
		if (bulletHit)
		{
			reward = reward + bulletHitPower * 2;
			bulletHit = false;
		}
		
		if (bulletMiss)
		{
			reward = reward - bulletMissPower;
			bulletMiss = false;
		}
		
		if (hitByBullet)
		{
			reward = reward - hitByBulletPower;
			hitByBullet = false;
		}
		
		if (win)
		{
			reward = reward + winReward;
			win = false;
		}
		
		if (dead)
		{
			reward = reward - deadPenalty;
			dead = false;
		}
		
		// update Q(s, a) <- Q(s, a) + alpha*(reward + gamma * Q(s', a') - Q(s, a)) 
		int key = indexFor(reduceDimension(prevState));
		double prevValue = LUT.get(key);
		double newValue = prevValue + alpha * (reward + gamma*bestValue - prevValue);
		LUT.put(key, newValue);
	}
	
	
	public void run()
	{
		while (true) 
		{
			if (getRadarTurnRemaining() == 0)
				setTurnRadarRight(360);
			
			if (trackStateKey == null)
			{
				for (Integer key : LUT.keySet())
				{
					trackStateKey = key;
					break;
				}
			}
			
			counter += 1;
			
			if (counter % 100 == 0)
			{
				trackQvalues.add(LUT.get(trackStateKey));
			}
			
			setAdjustGunForRobotTurn(true);
			execute();
			
			if (numRounds % 1000 == 0)
			{
				try {
					outputOutcomes();
				} catch (Exception ex) {
					out.println(ex.getMessage());
				}
			}
			
			if (numRounds % 1000 == 0)
			{
				try {
					outputTrackQvalues();
				} catch (Exception ex) {
					out.println(ex.getMessage());
				}
			}
			
		}
		
	}
	
	 
	public void onScannedRobot(ScannedRobotEvent event)
	{
		double myX = this.getX();
		double myY = this.getY();
		double myEnergy = this.getEnergy();
		double myHeading = this.getHeadingRadians();
		double enemyBearing = event.getBearingRadians();
		double enemyAngle = myHeading + enemyBearing;
		double enemyDist = event.getDistance();
		double enemyX = myX + Math.sin(enemyAngle)*enemyDist;
		double enemyY = myY + Math.cos(enemyAngle)*enemyDist;
		robotDistance = enemyDist;
		
		s = new double[]{myX, myY, myEnergy, enemyX, enemyY};
		
		absBearing = event.getBearingRadians() + getHeadingRadians();
		
	     if (s != null)
			{
				// choose action based on Q(s, a)
				Pair<Double, double[]> ret = this.getNextState(s, getHeadingRadians());
				
				if (state != null)
				{
					prevState = state;
				}

				state = ret.getSecond();

				if (prevState != null)
				{
					this.computeReward(prevState, state, ret.getFirst());
				}
				
				executeAction((int)state[numInputs]);
			}

	}
	
	
//	@Override
	public void onBulletHit(BulletHitEvent event) {
		bulletHit = true;
		Bullet bul = event.getBullet();
		bulletHitPower = bul.getPower();
	}
	
//	@Override
	public void onBulletMissed(BulletMissedEvent event) {
		bulletMiss = true;
		Bullet bul = event.getBullet();
		bulletMissPower = bul.getPower();
	}
	
//	@Override
	public void onHitByBullet(HitByBulletEvent event) {
		hitByBullet = true;
		Bullet bul = event.getBullet();
		hitByBulletPower = bul.getPower();
	}
	
//	@Override
	public void onWin(WinEvent event) {
		win = true;
		winReward = 15;
		outcomes.add(1);
		numRounds += 1;
	}
	
//	@Override
	public void onDeath(DeathEvent event) {
		dead = true;
		deadPenalty = 15;
		outcomes.add(0);
		numRounds += 1;
	}
	
	
	public void onBattleEnded(BattleEndedEvent e)
	{
		try {
			outputOutcomes();
		} catch (Exception ex) {
			out.println(ex.getMessage());
		}
		
		try {
			outputTrackQvalues();
		} catch (Exception ex) {
			out.println(ex.getMessage());
		}
	}

	private void outputOutcomes() throws Exception
	{
		RobocodeFileWriter fileWriter = new RobocodeFileWriter(getDataFile("outcomes.txt"));
		String line = "";
		if(outcomes.size() <= 1000)
		{
			for (int i = 0; i < outcomes.size(); i++)
		    {
		    	line += outcomes.get(i) + " ";
		    }
	        fileWriter.write(line + '\n');
	        fileWriter.close();
		}
		else{
			ArrayList<Double> meanOutcomes = new ArrayList<Double>();
			int bucket = (int)Math.floor(outcomes.size()/1000);
			for(int i = 0; i < 1000; i++)
			{
				Double meanOut = 0.0; 
				for(int j = 0; j < bucket; j++)
				{
					meanOut += outcomes.get(i * bucket + j);
				}
				meanOutcomes.add(meanOut/bucket);
			}
			
			for (int i = 0; i < meanOutcomes.size(); i++)
		    {
		    	line += meanOutcomes.get(i) + " ";
		    }
	        fileWriter.write(line + '\n');
	        fileWriter.close();
		}
	}
	
	private void outputTrackQvalues() throws Exception
	{
		RobocodeFileWriter fileWriter = new RobocodeFileWriter(getDataFile("trackQvalues.txt"));
		String line = "";
		if(trackQvalues.size() <= 1000)
		{
			for (int i = 0; i < trackQvalues.size(); i++)
		    {
		    	line += trackQvalues.get(i) + " ";
		    }
	        fileWriter.write(line + '\n');
	        fileWriter.close();
		}
		else{
			ArrayList<Double> windowedTrackQvalues = new ArrayList<Double>();
			int bucket = (int)Math.floor(trackQvalues.size()/1000);
			for(int i = 0; i < 1000; i++)
			{
				windowedTrackQvalues.add(trackQvalues.get(i*bucket));
			}
			
			for (int i = 0; i < windowedTrackQvalues.size(); i++)
		    {
		    	line += windowedTrackQvalues.get(i) + " ";
		    }
	        fileWriter.write(line + '\n');
	        fileWriter.close();
		}
	}
	

	@Override
	public void initialiseLUT() 
	{
		
	}

	
	@Override
	public double outputFor(double[] X) {
		return 0;
	}

	@Override
	public double train(double[] X, double argValue) {
		return 0;
	}

	@Override
	public void save(File argFile) {
	}

	@Override
	public void load(String argFileName) throws IOException {
	}
}
