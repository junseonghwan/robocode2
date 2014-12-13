package reinforcement;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;

public class DeepState 
{
	public enum DeepStates
	{
		MY_X,
		MY_Y,
		MY_ENERGY,
		ENEMY_X,
		ENEMY_Y,
		ENEMY_ENERGY
//		GUN_COOLED
	}

	public static int [] stateSpace = new int[]{9, 7, 2, 9, 7, 2};
	public static int totalStates;

	static {
		totalStates = 1;
		for (int i = 0; i < stateSpace.length; i++)
		{
			totalStates *= stateSpace[i];
		}
	}

	// internal representation of the state
	private double [] state;
	
	public DeepState()
	{
		state = new double[DeepStates.values().length];
	}
	
	public void update(AdvancedRobot robot)
	{
		// energy
		state[DeepStates.MY_ENERGY.ordinal()] = energyLevel(robot.getEnergy());

		int x = (int)Math.round(robot.getX()/100);
		int y = (int)Math.round(robot.getY()/100);
		state[DeepStates.MY_X.ordinal()] = x;
		state[DeepStates.MY_Y.ordinal()] = y;
		
//		state[DeepStates.GUN_COOLED.ordinal()] = (robot.getGunHeat() == 0 ? 1 : 0);
	}

	public void update(ScannedRobotEvent status, AdvancedRobot robot)
	{
		update(robot);
		// enemy energy
		state[DeepStates.ENEMY_ENERGY.ordinal()] = energyLevel(status.getEnergy());

		// enemy near wall?
		double enemyBearingRad = status.getBearingRadians();
		double enemyAngleRad = robot.getHeadingRadians() + enemyBearingRad;
		double enemyDist = status.getDistance();
		int enemyX = (int)Math.round((robot.getX() + Math.sin(enemyAngleRad)*enemyDist)/100);
		int enemyY = (int)Math.round((robot.getY() + Math.cos(enemyAngleRad)*enemyDist)/100);

		state[DeepStates.ENEMY_X.ordinal()] = enemyX;
		state[DeepStates.ENEMY_Y.ordinal()] = enemyY;
	}
	
	public double getStateAt(DeepStates index)
	{
		return state[index.ordinal()];
	}
	
	private int energyLevel(double energy)
	{
		return (energy < 30 ? 0 : 1);
	}

	public double [] getState()
	{
		return state;
	}
	
	@Override
	public String toString()
	{
		String str = "";
		//StateIndex [] stateNames = StateIndex.values();
		for (int i = 0; i < state.length; i++)
		{
			str += Math.floor(state[i]) + " ";
		}
		return str;
	}
	
	public void copy(DeepState dest)
	{
		if (dest == null)
			throw new RuntimeException("Bug");
		
		for (int i = 0; i < state.length; i++)
		{
			dest.state[i] = this.state[i];
		}
	}
}
