package robots;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Random;

import reinforcement.LUTInterface;
import robocode.AdvancedRobot;
import robocode.DeathEvent;
import robocode.RobocodeFileOutputStream;
import robocode.RobocodeFileWriter;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;
import util.Pair;

public class RobotRL extends AdvancedRobot implements LUTInterface
{
	public static final double winReward = 10;
	public static final double deadPenalty = 100;

	public static HashMap<Integer, Double> LUT = new HashMap<Integer, Double>();
	public static int ID = 0;

	private double alpha = 1;
	private double gamma = 0.5;
	private double epsilon = 0.1;
	private double initQ = 0;
	private double robotDistance;

	private boolean win = false;
	private boolean dead = false;
	
	public static int numInputs = 5;
	public static int numActions = 5;

	private double [] state = null, prevState = null;
	
	// wipeout RobotRL.data folder manually (there must be a better way to do this)

	public RobotRL()
	{
		ID++;
	}

	private void executeAction(int action)
	{
		
		switch(action)
		{
			case 0: 
			{
				this.ahead(100);
				break;
			}
			case 1:
			{
				this.back(100);
				break;
			}
			case 2:
			{
				this.turnRight(90);
				this.ahead(100);
				break;
			}
			case 3:
			{
				this.turnLeft(90);
				this.ahead(100);
				break;
			}
			case 4:
			{
				smartFire(robotDistance);
				break;
			}
		}
	}

	public void smartFire(double robotDistance) {
		if (robotDistance > 200 || getEnergy() < 15) {
			fire(1);
		} else if (robotDistance > 100) {
			fire(2);
		} else {
			fire(3);
		}
	}
	
	public void run()
	{
		while (true) 
		{
			if (!scanned)
			{
				quickScan(15); // intialize s
				//randomMove();
			}
			
			// at this point, s != null
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
				scanned = false;
			}
			
		}
	}

	private void outputQvalues() throws Exception
	{
		RobocodeFileWriter fileWriter = new RobocodeFileWriter(getDataFile("qvalues.txt"));
		String line = "";
	    for (Integer key : LUT.keySet())
	    {
	    	double value = LUT.get(key);
	    	line += value + " ";
	    }

        fileWriter.write(line);
        fileWriter.close();
	}

	private void quickScan(int gunIncrement)
	{
		int numIter = (int)Math.ceil(360.0/gunIncrement);
		for (int i = 0; i < numIter; i++) {
			if (scanned)
				break;
			
			turnGunLeft(gunIncrement);
		}

	}
	
	private boolean scanned = false;
	private double [] s = null;

	public void onScannedRobot(ScannedRobotEvent event)
	{
		scanned = true;
		//stop();
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

		//resume();
	}
	
	public void onWin(WinEvent event) 
	{
		win = true;
		outputResult(1);
	}
	
	public void outputResult(int outcome)
	{
		PrintWriter writer = null;
		try {
			File outcomeFile = getDataFile("outcomes" + ID + ".txt");
			RobocodeFileOutputStream outputStream = new RobocodeFileOutputStream(outcomeFile);
			writer = new PrintWriter(outputStream);
	        writer.println(outcome);
		} catch (IOException ex) {
			
		} finally {
			if (writer != null)
				writer.close();
		}
		
		
	}
	
//	@Override
	public void onDeath(DeathEvent event) {
		dead = true;
		outputResult(0);
	}
	
	@Override
	public void initialiseLUT() 
	{
		
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
		state[2] = state[2] > 70 ? 3 : (state [2] > 40 ? 2 : 1);
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
	
	public void computeReward(double [] prevState, double [] newState, double bestValue)
	{
		double reward = 0;
		if (prevState == null)
			reward = newState[2] - 100;
		else
			reward = newState[2] - prevState[2];
		
//		if (bulletHit)
//		{
//			reward = reward + bulletHitPower;
//			bulletHit = false;
//		}
//		
//		if (bulletMiss)
//		{
//			reward = reward - bulletMissPower;
//			bulletMiss = false;
//		}
//		
//		if (hitByBullet)
//		{
//			reward = reward - hitByBulletPower;
//			hitByBullet = false;
//		}
		
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

	// snapshot of the state in X and my heading in radians
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
		int bestAction = 0;
		double bestValue = 0.0;
		for (int i = 0; i < newStates.length; i++)
		{
			double val = LUT.get(indexFor(reduceDimension(newStates[i])));
			// TODO: implement how to break ties
			if (val > bestValue)
			{
				bestValue = val;
				bestAction = i;
			}
			else if (val == bestValue)
			{
				bestAction = (random.nextInt(2) == 1) ? i :bestAction;
			}
		}
		
		int exploreAction = random.nextInt(numActions);
		
		int finalAction = (random.nextDouble() <= epsilon) ? exploreAction : bestAction;
		
		Pair<Double, double[]> best = new Pair<Double, double[]>(bestValue, newStates[finalAction]);
		return best;
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
