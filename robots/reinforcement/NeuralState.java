package reinforcement;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;

public class NeuralState 
{
	public enum NeuralStates
	{
		MY_X,
		MY_Y,
		MY_ENERGY,
		ENEMY_X,
		ENEMY_Y,
		ENEMY_ENERGY
	}

	// internal representation of the state
	private double [] state;
	
	public NeuralState()
	{
		state = new double[NeuralStates.values().length];
	}
	
	public NeuralState(double my_x, double my_y, double my_energy, double enemy_x, double enemy_y, double enemy_energy)
	{
		this();
		this.state[0] = my_x;
		this.state[1] = my_y;
		this.state[2] = my_energy;
		this.state[3] = enemy_x;
		this.state[4] = enemy_y;
		this.state[5] = enemy_energy;
	}
	
	public void update(AdvancedRobot robot)
	{
		// energy
		state[NeuralStates.MY_ENERGY.ordinal()] = energyLevel(robot.getEnergy());
		state[NeuralStates.MY_X.ordinal()] = robot.getX()/100.0;
		state[NeuralStates.MY_Y.ordinal()] = robot.getY()/100.0;
	}

	public void update(ScannedRobotEvent status, AdvancedRobot robot)
	{
		update(robot);
		// enemy energy
		state[NeuralStates.ENEMY_ENERGY.ordinal()] = energyLevel(status.getEnergy());
		// enemy near wall?
		double enemyBearingRad = status.getBearingRadians();
		double enemyAngleRad = robot.getHeadingRadians() + enemyBearingRad;
		double enemyDist = status.getDistance();
		double enemyX = (robot.getX() + Math.sin(enemyAngleRad)*enemyDist)/100.0;
		double enemyY = (robot.getY() + Math.cos(enemyAngleRad)*enemyDist)/100.0;

		state[NeuralStates.ENEMY_X.ordinal()] = enemyX;
		state[NeuralStates.ENEMY_Y.ordinal()] = enemyY;
	}
	
	public double getStateAt(NeuralStates index)
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
	
	public void copy(NeuralState dest)
	{
		if (dest == null)
			throw new RuntimeException("Bug");
		
		for (int i = 0; i < state.length; i++)
		{
			dest.state[i] = this.state[i];
		}
	}
}
