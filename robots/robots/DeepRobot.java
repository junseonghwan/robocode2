package robots;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import reinforcement.DeepState;
import reinforcement.LUTInterface;
import reinforcement.DeepState.DeepStates;
import robocode.AdvancedRobot;
import robocode.BattleEndedEvent;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobocodeFileOutputStream;
import robocode.ScannedRobotEvent;
import robocode.StatusEvent;
import robocode.WinEvent;
import robocode.util.Utils;

public class DeepRobot extends AdvancedRobot implements LUTInterface
{
	public static double alpha = 0.05; // decrease the learning rate, but not too small
	public static double gamma = 0.99; // increase gamma is really important
	public static double epsilon = 0.3;
	
	private Random random = new Random(1);

	private static DeepState state, prevState;
	private static Action action;
	private static double[] LUT;
	private static double reward = 0.0;
	private double enemyAngleDegrees;
	private static List<Integer> outcomes = new ArrayList<Integer>();
	
	public static int numActions = Action.values().length;

	public enum Action {
		MOVE_FORWARD, DYNAMIC_FIRE, MOVE_BACKWARD, MOVE_LEFT, MOVE_RIGHT // don't need dumb moves
	}

	public static int NUM_ROUNDS = 0;
	
	public DeepRobot() {
		NUM_ROUNDS += 1;
		if (NUM_ROUNDS == 1)
		{
			LUT = new double[DeepState.totalStates * numActions];
			prevState = new DeepState();
			state = new DeepState();
//			initialiseLUT(); // important for faster learning
		}
	}

	public void run() {
		
		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);
		setAdjustRadarForGunTurn(true);
		setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
		
		epsilon = Math.min(0.3, 50 / (NUM_ROUNDS + 0.0)); // TODO 
		out.println("epsilon=" + epsilon);
		do {
			// update the prevState, prevAction
			state.copy(prevState);
			scan();
			action = getNextAction(state);
			
			if (action != null && reward != 0)
				updateLUT(prevState, action, state, reward);

			//out.println("action=" + action.name());
			executeAction();
		} while (true);
	}

	@Override
	public void onScannedRobot(ScannedRobotEvent e) {
		double radarTurn =
		// Absolute bearing to target
		getHeadingRadians() + e.getBearingRadians()
		// Subtract current radar heading to get turn required
				- getRadarHeadingRadians();

		setTurnRadarRightRadians(Utils.normalRelativeAngle(radarTurn));
		
		enemyAngleDegrees = e.getBearing();
		
		state.update(e, this);
	}

	private void setTurn(double enemyBearingDegrees) {
		double bodyTurn =
		// Absolute bearing to target
		getHeading() + enemyBearingDegrees;

		setTurnRight(Utils.normalRelativeAngleDegrees(bodyTurn));
	}

	private Action getNextAction(DeepState currState) 
	{
		Action action = optimalAction(currState);
		if (random.nextDouble() < epsilon)
		{
			int exploration = random.nextInt(Action.values().length);
			action = Action.values()[exploration];
		}
		return action;
	}

	// given the state, give the next action to choose -- used for updating the
	// Q-value
	private Action optimalAction(DeepState currState) {
		Action[] actions = Action.values();
		double max = Double.NEGATIVE_INFINITY;
		int bestAction = 0;
		List<Integer> ties = new ArrayList<Integer>();
		for (int i = 0; i < actions.length; i++) {
			double qvalue = Q(state, actions[i]);
			if (qvalue > max) {
				max = qvalue;
				bestAction = i;
				ties = new ArrayList<Integer>();
			}
			else if (qvalue == max)
			{
				ties.add(i);
			}
		}

		if (ties.size() > 1)
		{
			bestAction = ties.get(random.nextInt(ties.size()));
		}
		
		return actions[bestAction];
	}
	
	private void executeAction()
	{
		switch (action) {
		case MOVE_FORWARD:
			setTurn(enemyAngleDegrees);
			setAhead(100);
			break;
		case MOVE_BACKWARD:
			setTurn(enemyAngleDegrees);
			setBack(100);
			break;
		case MOVE_LEFT:
			setTurn(enemyAngleDegrees + 90);
			setAhead(100);
			break;
		case MOVE_RIGHT:
			setTurn(enemyAngleDegrees - 90);
			setAhead(100);
			break;
		case DYNAMIC_FIRE:
			this.fire(1.0, enemyAngleDegrees / 180 * Math.PI);
			break;
		}
	}

	private double Q(DeepState state, Action action) {
		int index = getIndex(state, action);
		return LUT[index];
	}

	private int getIndex(DeepState state, Action action) {
		double[] s = state.getState();
		int index = 0;
		for (int i = 0; i < s.length; i++) {
			index = (int) (index * DeepState.stateSpace[i] + s[i]);
		}

		index = index * numActions + action.ordinal();

		return index;
	}

	@Override
	public void onBattleEnded(BattleEndedEvent e)
	{
		//String timeStamp = System.currentTimeMillis() + "";
		writeOutcomes("", 100);
		writeQvalues("");
	}
	
	private void writeQvalues(String timeStamp)
	{
		PrintWriter writer = null;
		try {
			File outcomeFile = getDataFile(timeStamp + "_qvalues" + alpha + "_" + gamma + "_" + epsilon 
					 						+ ".txt");
			RobocodeFileOutputStream outputStream = new RobocodeFileOutputStream(outcomeFile);
			writer = new PrintWriter(outputStream);
			StringBuilder sb = new StringBuilder();
			sb.append("total " + LUT.length + "\n");
			for (int i = 0; i < LUT.length; i++)
			{
				if (LUT[i] != 0)
					sb.append(i + " " + LUT[i] + "\n");
			}
			//out.println(sb.toString());
			writer.println(sb.toString());
			outputStream.close();
		} catch (IOException ex) {
			
		} finally {
			if (writer != null)
				writer.close();
		}
	}
	
	private void writeOutcomes(String timeStamp, int windowSize)
	{
		PrintWriter writer = null;
		try {
			File outcomeFile = getDataFile(timeStamp + "_outcomes" + alpha + "_" + gamma + "_" + epsilon 
						+ ".txt");
			RobocodeFileOutputStream outputStream = new RobocodeFileOutputStream(outcomeFile);
			writer = new PrintWriter(outputStream);
			int aggr = 0;
			
			for (int i = 0; i < outcomes.size(); i++)
			{
				aggr += outcomes.get(i);
				if ((i+1) % windowSize == 0)
				{
					writer.println(aggr);
					aggr = 0;
				}
			}
		} catch (IOException ex) {
		} finally {
			if (writer != null)
				writer.close();
		}

	}
	

	private void updateLUT(DeepState prevState, Action prevAction,
			DeepState currState, double reward) {
		int index = getIndex(prevState, prevAction);
		// update LUT:
		double prevValue = LUT[index];
		Action futureAction = optimalAction(currState);
		double maxFutureValue = LUT[getIndex(currState, futureAction)];
		double newValue = prevValue + alpha
				* (reward + gamma * maxFutureValue - prevValue);
		LUT[index] = newValue;

		// reset the reward
		DeepRobot.reward = 0;
	}
	
	@Override
	public void onHitWall(HitWallEvent e) {
		reward += -1;
	}

	@Override
	public void onBulletHit(BulletHitEvent e) {
		reward += 3; // important: increase the reward for onBulletHit for fast learning
	}

	@Override
	public void onBulletMissed(BulletMissedEvent e) {
		if (state.getStateAt(DeepStates.MY_ENERGY) == 0)
			reward += -1.5;
		else
			reward += -0.5;
	}

	@Override
	public void onHitByBullet(HitByBulletEvent e) {
		reward += -1;
	}

	@Override
	public void onHitRobot(HitRobotEvent e) {
		double enemyEnergy = e.getEnergy();
		double myEnergy = getEnergy();
		if (myEnergy >= enemyEnergy) {
			reward += 1;
		} else
			reward -= 1;
	}

	@Override
	public void onDeath(DeathEvent e) {
		outcomes.add(0);
		reward -= 2;
	}

	@Override
	public void onWin(WinEvent e) {
		outcomes.add(1);
		reward += 2;
	}

	private void fire(double rotation, double enemyBearingRad) {
		// compute the distance
		double myX = state.getStateAt(DeepStates.MY_X);
		double myY = state.getStateAt(DeepStates.MY_Y);
		double enemyX = state.getStateAt(DeepStates.ENEMY_X);
		double enemyY = state.getStateAt(DeepStates.ENEMY_Y);

		double angleRad = enemyBearingRad + getHeadingRadians()
				- getGunHeadingRadians();
		setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(angleRad
				* rotation));

		double distance = Math.sqrt(Math.pow(myX - enemyX, 2.0)
				+ Math.pow(myY - enemyY, 2.0));
		adaptiveFire(distance);
	}

	private void adaptiveFire(double dist) 
	{
		if (dist <= 2)
			setFire(3);
		else
			setFire(1);
	}

	@Override
	public double outputFor(double[] X) 
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double train(double[] X, double argValue) 
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void save(File argFile) 
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void load(String argFileName) throws IOException 
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void initialiseLUT() 
	{
		// initialize LUT with default value = 1.0
		for (int i = 0; i < LUT.length; i++)
		{
			LUT[i] = 1.0;
		}
	}

	@Override
	public int indexFor(double[] X) 
	{
		// TODO Auto-generated method stub
		return 0;
	}

}
