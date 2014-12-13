package robots;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import neuralnet.NeuralNet;
//import reinforcement.DeepState;
//import reinforcement.DeepState.DeepStates;
import reinforcement.NeuralState;
import reinforcement.NeuralState.NeuralStates;
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
import robocode.WinEvent;
import robocode.util.Utils;
import util.Pair;

public class NeuralRobot extends AdvancedRobot
{
	public static double alpha = 0.05;
	public static double gamma = 0.99;
	public static double epsilon = 0.3;
	public static double learningRate = 0.0005;
	public static double momentum = 0.4;

	private Random random = new Random(1);

	private static NeuralState state, prevState;
	private static Action action;
	private static NeuralNet nn;
	private static double reward = 0.0;
	private double enemyAngleDegrees;
	private static List<Integer> outcomes = new ArrayList<Integer>();

	public static int numActions = Action.values().length;
	public static int numInputs = NeuralStates.values().length + numActions;
	public static int numHidden = (int)Math.ceil(numInputs)*2;
	public static int numStates = NeuralStates.values().length;

	public enum Action {
		MOVE_FORWARD, DYNAMIC_FIRE, MOVE_BACKWARD, MOVE_LEFT, MOVE_RIGHT
	}

	public static int NUM_ROUNDS = 0;

	public NeuralRobot()
	{
		NUM_ROUNDS += 1;
		if (NUM_ROUNDS == 1)
		{
			nn = new NeuralNet(random, numInputs, numHidden, learningRate, momentum, -1, 1);
			prevState = new NeuralState();
			state = new NeuralState();
		}
	}

	public void run() {
		setBodyColor(Color.GRAY);
		setGunColor(Color.GRAY);
		setRadarColor(Color.GRAY);

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
				trainNN(prevState, action, state, reward);

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
		//out.print("enemyX=" + state.getStateAt(NeuralStates.ENEMY_X));
	}

	private void setTurn(double enemyBearingDegrees) {
		double bodyTurn =
		// Absolute bearing to target
		getHeading() + enemyBearingDegrees;

		setTurnRight(Utils.normalRelativeAngleDegrees(bodyTurn));
	}

	private Action getNextAction(NeuralState currState) 
	{
		Action action = optimalAction(currState).getFirst();
		if (random.nextDouble() < epsilon)
		{
			int exploration = random.nextInt(Action.values().length);
			action = Action.values()[exploration];
		}
		return action;
	}

	// given the state, give the next action to choose, needs to return value as well
	private Pair<Action, Double> optimalAction(NeuralState currState) {
		Action[] actions = Action.values();
		double max = Double.NEGATIVE_INFINITY;
		int bestAction = 0;
		List<Integer> ties = new ArrayList<Integer>();
		for (int i = 0; i < actions.length; i++) {
			double val = forward(state, actions[i]);
			if (val > max) {
				max = val;
				bestAction = i;
				ties = new ArrayList<Integer>();
			}
			else if (val == max)
			{
				ties.add(i);
			}
		}

		if (ties.size() > 1)
		{
			bestAction = ties.get(random.nextInt(ties.size()));
		}

//		out.println("forward_prop=" + max);
		return new Pair<Action, Double>(actions[bestAction], max);
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
	
	private double [] formInput(NeuralState state, Action action)
	{
		double [] input = new double[numInputs];
		System.arraycopy(state.getState(), 0, input, 0, numStates);
		
		input[0] = input[0] / 8.0;
		input[1] = input[1] / 6.0;
		if(input[2] == 0)
			input[2] = -1.0;
		input[3] = input[3] / 8.0;
		input[4] = input[4] / 6.0;
		if(input[5] == 0)
			input[5] = -1.0;
			
		Action [] actions = Action.values();
		for (int i = 0; i < actions.length; i++)
		{
			if (actions[i].ordinal() == action.ordinal())
				input[numStates + i] = 1.0;
			else
				input[numStates + i] = -1.0;
		}
		return input;
	}
	
	private double forward(NeuralState state, Action action) 
	{
		nn.forwardprop(formInput(state, action));
		return nn.outputPostActivation;
	}

	private void trainNN(NeuralState prevState, Action prevAction,
			NeuralState currState, double reward) {
		// update LUT:
		// 1. forward(prevState, prevAction) -> prev q-value
		// 2. forward(currState, a*) -> optimal q
		// 3. newQvalue = prevValue + alpha * (reward + gamma * maxFutureValue - prevValue);
		// 4. backward prop
		double prevValue = forward(prevState, prevAction);
		Pair<Action, Double> optActionValuePair = optimalAction(currState); // forward props
		double maxFutureValue = optActionValuePair.getSecond().doubleValue();
		double newValue = prevValue + alpha * (reward + gamma * maxFutureValue - prevValue);

		// do back propagation
		nn.backprop(formInput(prevState, prevAction), newValue); // TODO nn.backprop(formInput(prevState, prevAction), nn.customSigmoid(newValue));

		// reset the reward
		NeuralRobot.reward = 0;
	}

	@Override
	public void onHitWall(HitWallEvent e) {
		reward += -1;
	}

	@Override
	public void onBulletHit(BulletHitEvent e) {
		reward += 3;
	}

	@Override
	public void onBulletMissed(BulletMissedEvent e) {
		if (state.getStateAt(NeuralStates.MY_ENERGY) == 0)
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
		
		double est = forward(new NeuralState(6.0, 5.0, 1.0, 6.0, 4.0, 1.0), Action.DYNAMIC_FIRE);
		estimates.add(est);
	}

	private static List<Double> estimates = new ArrayList<Double>();
	private static List<Double> errors = new ArrayList<Double>();
	@Override
	public void onWin(WinEvent e) {
		outcomes.add(1);
		reward += 2;
		
		if (NUM_ROUNDS % 100 == 0)
		{
  		double est = forward(new NeuralState(6.0, 5.0, 1.0, 6.0, 4.0, 1.0), Action.DYNAMIC_FIRE);
  		estimates.add(est);
		}
	}
	
	@Override
	public void onBattleEnded(BattleEndedEvent e)
	{
		//String timeStamp = System.currentTimeMillis() + "";
		writeOutcomes("", 10);
		//writeEstimates();
	}
	
	private void writeEstimates()
	{
		PrintWriter writer = null;
		try {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < estimates.size(); i++)
			{
				double est = estimates.get(i);
				sb.append(est + "\n");
			}
			
			File outcomeFile = getDataFile("_estimate" + alpha + "_" + gamma + "_" + epsilon 
					+ "_" + learningRate + "_" + momentum + "_" + numHidden + ".txt");
			RobocodeFileOutputStream outputStream = new RobocodeFileOutputStream(outcomeFile);
			writer = new PrintWriter(outputStream);
			writer.print(sb.toString());

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
					+ "_" + learningRate + "_" + momentum + "_" + numHidden + ".txt");
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
	
	private void fire(double rotation, double enemyBearingRad) {
		// compute the distance
		double myX = state.getStateAt(NeuralStates.MY_X);
		double myY = state.getStateAt(NeuralStates.MY_Y);
		double enemyX = state.getStateAt(NeuralStates.ENEMY_X);
		double enemyY = state.getStateAt(NeuralStates.ENEMY_Y);

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

}
