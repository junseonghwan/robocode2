package robots;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import reinforcement.LUTInterface;
import robocode.AdvancedRobot;
import robocode.BattleEndedEvent;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.DeathEvent;
import robocode.RobocodeFileOutputStream;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;
import util.Pair;

public class ImmobileRobot extends AdvancedRobot implements LUTInterface
{
	public static final double winReward = 10;
	public static final double deadPenalty = 10;

	public static Hashtable<Integer, Double> LUT = new Hashtable<Integer, Double>();
	private static boolean lutInit = false;

	private double alpha = 1;
	private double gamma = 0.5;
	private double epsilon = 0.1;

	public static int numInputs = 3;
	public static int numActions = 2;

	public static int ROUND_NO = 0;
	
	// just the states
	private double [] state = null;
		
	public ImmobileRobot()
	{
		if (ROUND_NO > 0 && (ROUND_NO % 100 == 0))
			storeQvalues();
		
		ROUND_NO++;
		
		if (!lutInit)
			initialiseLUT();
	}
	
	// build a Queue to store previous states
	private LinkedBlockingQueue<double []> bulletFiredQueue = new LinkedBlockingQueue<double []>();
	private LinkedBlockingQueue<double []> scannedQueue = new LinkedBlockingQueue<double []>();

	public void run()
	{
		// initialize s
		state = new double[]{0, 0, 0};

		while (true) 
		{
			// choose action based on Q(s, a)
			Pair<Double, double[]> ret = this.getBestStateAction(state);
			executeAction(ret.getSecond());
		}
	}

	private void executeAction(double [] stateAction)
	{
		int action = (int)stateAction[numInputs];
		
		switch(action)
		{
			case 0:
			{
				state[1] = 1;
				bulletFiredQueue.add(stateAction);
				fire(3);
				break;
			}
			case 1:
			{
				state[0] = 0;
				scannedQueue.add(stateAction);
				fullScan(5);
				break;
			}
		}
	}

	private void fullScan(int gunIncrement)
	{
		for (int j = gunIncrement; j >= 1; j--)
		{
			int numIter = (int)Math.ceil(360.0/j);
			for (int i = 0; i < numIter; i++) 
			{
				if (state[0] == 1) // scanned an enemy so stop scanning
					return;
				
				turnGunLeft(gunIncrement);
			}
		}
		
		state[0] = 0;
		updateLUT(1);
	}
	
	public void onBattleEnded(BattleEndedEvent event)
	{
		storeQvalues(); // store whatever is there
		outputQvalues();
	}
	
	@Override
	public void onScannedRobot(ScannedRobotEvent event)
	{
		state[0] = 1;
		updateLUT(1);
	}

	@Override
	public void onBulletHit(BulletHitEvent event)
	{
		state[2] = 1;
		updateLUT(0);
	}

	@Override
	public void onBulletMissed(BulletMissedEvent event)
	{
		state[2] = 0;
		updateLUT(0);
	}

	@Override
	public void onWin(WinEvent event) 
	{
	}
	
	@Override
	public void onDeath(DeathEvent event) 
	{
	}
	
	private double computeReward(double [] prevStateAction)
	{
		double reward = 0;
		
		// reward depends on the prevStateAction as well as the current state 
		if (prevStateAction[3] == 0)
		{
			// the prev action was to fire, then check if it was a hit or miss
			if (state[2] == 0)
			{
				reward = -1;
			}
			if (state[2] == 1)
			{
				reward = 5;
			}
		}
		else if (prevStateAction[3] == 1)
		{
			// the previous action was to scan for the enemy
			if (prevStateAction[0] == 0)
			{
				// if the enemy was not scanned before and the action was to scan, reward + 2
				reward = 2;
			}
			else
			{
				// if the enemy was scanned but redundantly made another scan, reward -2
				reward = -2;
			}
		}

		return reward;
	}

	public void updateLUT(int action)
	{
		LinkedBlockingQueue<double []> queue = null;
		if (action == 0)
		{
			queue = bulletFiredQueue;
		}
		else if (action == 1)
		{
			queue = scannedQueue;
		}

		if (queue.size() <= 0)
			throw new RuntimeException("BUG!");

		double [] prevStateAction = queue.remove();

		// compute the reward
		double reward = computeReward(prevStateAction);
		
		// compute the best future value given the current state
		double bestValue = this.getBestStateAction(state).getFirst();
		
		// update Q(s, a) <- Q(s, a) + alpha*(reward + gamma * Q(s', a') - Q(s, a)) 
		int key = indexFor(prevStateAction);
		double prevValue = LUT.get(key);
		double newValue = prevValue + alpha * (reward + gamma*bestValue - prevValue);
		LUT.put(key, newValue);
	}

	// snapshot of the state in X,
	// returns (value, stateAction)
	public Pair<Double, double []> getBestStateAction(double[] X)
	{
		double [][] newStates = new double[numActions][numInputs + 1]; // +1 for action

		for (int i = 0; i < numActions; i++)
		{
			System.arraycopy(X, 0, newStates[i], 0, X.length);
			newStates[i][numInputs] = i;
		}

		// choose the action that is the best, and return that action
		Pair<Double, double []> optimalState = optimalAction(newStates, epsilon);
		return optimalState;
	}

	// return the action ID and the new state in double []
	private Pair<Double, double []> optimalAction(double [][] newStates, double epsilon)
	{
		Random random = new Random();
		int bestAction = 0;
		double bestValue = 0.0;
		for (int i = 0; i < newStates.length; i++)
		{
			double val = LUT.get(indexFor(newStates[i]));
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
	public void initialiseLUT() 
	{
		// there are only 2^4 = 16 states, so enter them into the LUT
		// initialize stateAction as well
		for (int i = 0; i <= 15; i++)
		{
			LUT.put(i, 0.0);
		}

		lutInit = true;
	}

	/*
	 * (enemy_scanned, bullet_fired, bullet_hit, action)
	 */
	@Override
	public int indexFor(double[] X) 
	{
		int key = 0;
		for (int i = 0; i < X.length - 1; i++)
		{
			key += X[i]*Math.pow(2.0, i);
		}
		
		key *= 2;
		key += X[X.length-1];
		return key;
	}

	private static LinkedBlockingQueue<String> dataLines = new LinkedBlockingQueue<String>();
	private void storeQvalues()
	{
		String line = "";
		for (int i = 0; i < 16; i++)
		{
			line += LUT.get(i);
			if (i < 15)
			{
				line += ", ";
			}
		}
		
		dataLines.add(line);
	}
	
	public void outputQvalues()
	{
		PrintWriter writer = null;
		try {
			File outcomeFile = getDataFile(System.currentTimeMillis() + "_qvalues.txt");
			RobocodeFileOutputStream outputStream = new RobocodeFileOutputStream(outcomeFile);
			writer = new PrintWriter(outputStream);
			while (!dataLines.isEmpty())
			{
				String line = dataLines.remove();
		        writer.println(line);
			}
		} catch (IOException ex) {
			
		} finally {
			if (writer != null)
				writer.close();
		}
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
