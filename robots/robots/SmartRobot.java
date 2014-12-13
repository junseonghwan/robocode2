package robots;

import java.util.Random;

import reinforcement.LUT;
import reinforcement.StateAction;
import reinforcement.StateAction.Action;
import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;
import util.Pair;

public class SmartRobot extends AdvancedRobot 
{
	// RL parameters
	public static final double alpha = 0.1;
	public static final double gamma = 0.9;
	
	public static final double scanReward = 0.5;	
	public static final double winReward = 10;
	public static final double losePenalty = -10;
	
	public static LUT lut = LUT.getInstace();

	private boolean roundEnded  = false;
	private double [] s = null;
	private Action action = null;

	private void initState()
	{
		double my_x = this.getX();
		double my_y = this.getY();
		double my_energy = this.getEnergy();
		double enemy_bearing = 180; // assume that the enemy is behind me
		double dist_to_enemy = 1000; // make it large (incorrect)
		double enemy_energy = 100;
		double scanned = 0;
		s = StateAction.getState(my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, scanned);
	}
	
	public void run()
	{
		// initialize state
		initState();

		while(!roundEnded)
		{
			// 1. a_opt <- argmax_a Q(s, a)
			Pair<Action, Double> pair = lut.chooseAction(s);
			action = pair.getFirst();

			// 2. execute the action
			executeAction(action);

			// executing an action will trigger one of the handled events:
			// this is where new state as well as the reward will be observed
			// i. onBulletHit
			// ii. onBulletMissed
			// iii. onScannedRobot
			// iv. onHitByBullet
			// v. onHitRobot
			// vi. onHitWall

			// 3. update Q(s, a) <- Q(s, a) + alpha (r + gamma * max_a' Q(s', a') - Q(s, a))
			// this will take place in the callback functions
		}

		// output something here if needed 
	}

	/**
	 * update Q(s, a) <- Q(s, a) + alpha (reward + gamma * max_a' Q(s', a') - Q(s, a))
	 * @param reward
	 */
	private void updateQ(double reward, double [] nextState)
	{
		double [] stateAction = new double[StateAction.numStates + 1];
		System.arraycopy(s, 0, stateAction, 0, s.length);
		stateAction[StateAction.numStates] = action.ordinal();
		double prevValue = lut.getQValue(stateAction);
		double futureValue = lut.chooseAction(nextState).getSecond();
		double newValue = prevValue + alpha*(reward + gamma*futureValue - prevValue);
		lut.updateQValue(stateAction, newValue);
	}

	@Override
	public void onBulletHit(BulletHitEvent event)
	{
		// observe the new state 
		double my_x = this.getX();
		double my_y = this.getY();
		double my_energy = this.getEnergy();

		// compute the distance to the enemy
		double enemy_x = event.getBullet().getX();
		double enemy_y = event.getBullet().getY();
		double dist_to_enemy = Math.sqrt(Math.pow(enemy_x - my_x, 2.0) + Math.pow(enemy_y - my_y, 2.0)); 

		// compute the enemy_bearing 
		double my_heading = this.getHeading();
		double bullet_heading = event.getBullet().getHeading();
		double enemy_bearing = bullet_heading - my_heading;

		double enemy_energy = event.getEnergy();

		// compute the reward as follows: difference between my current energy and previous energy plus
		// the difference between the enemy's previous energy and the enemy's current energy
		double reward = (my_energy - s[2]) + (s[5] - enemy_energy);

		// observe the new state
		double [] nextState = StateAction.getState(my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, 0);

		// update Q(s, a)
		updateQ(reward, nextState);

		// update the current state
		s = nextState;
	}
	
	@Override
	public void onBulletMissed(BulletMissedEvent event)
	{
		// observe the new state 
		double my_x = this.getX();
		double my_y = this.getY();
		double my_energy = this.getEnergy();

		// enemy information is no longer known, keep the old values
		double dist_to_enemy = s[3]; 
		double enemy_bearing = s[4];
		double enemy_energy = s[5];

		// observe the reward
		double reward = -event.getBullet().getPower();

		// observe the new state
		double [] nextState = StateAction.getState(my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, 0);

		// update Q(s, a)
		updateQ(reward, nextState);

		// update the current state
		s = nextState;
	}
	
	@Override
	public void onScannedRobot(ScannedRobotEvent event)
	{
		stop();
		double my_x = this.getX();
		double my_y = this.getY();
		double my_energy = this.getEnergy();
		double dist_to_enemy = event.getDistance();
		double enemy_bearing = event.getBearingRadians();
		double enemy_energy = event.getEnergy();

		// observe the new state
		double [] nextState = StateAction.getState(my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, 1);

		// update Q(s, a)
		updateQ(scanReward, nextState);

		// observe the new state
		s = nextState;
	}
	
	@Override
	public void onHitByBullet(HitByBulletEvent event)
	{
		double my_x = this.getX();
		double my_y = this.getY();
		double my_energy = this.getEnergy();
		double dist_to_enemy = s[3]; // carry over from the previous state
		double enemy_bearing = event.getBearingRadians();
		double enemy_energy = s[5]; // the enemy will have gained some energy but don't know by how much 

		// observe the new state
		double [] nextState = StateAction.getState(my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, 0);

		// update Q(s, a)
		double reward = my_energy - s[2]; // the difference between my current energy and previous energy
		updateQ(reward, nextState);

		// observe the new state
		s = nextState;
	}
	
	@Override
	public void onHitWall(HitWallEvent event)
	{
		double my_x = this.getX();
		double my_y = this.getY();
		double my_energy = this.getEnergy();
		double dist_to_enemy = s[3]; // carry over from the previous state
		double enemy_bearing = s[4];
		double enemy_energy = s[5]; // the enemy will have gained some energy but don't know by how much 

		// observe the new state
		double [] nextState = StateAction.getState(my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, 0);

		// update Q(s, a)
		double reward = my_energy - s[2]; // the difference between my current energy and previous energy
		updateQ(reward, nextState);

		// observe the new state
		s = nextState;
	}

	@Override
	public void onHitRobot(HitRobotEvent event)
	{
		double my_x = this.getX();
		double my_y = this.getY();
		double my_energy = this.getEnergy();
		double dist_to_enemy = 0;
		double enemy_bearing = event.getBearing();
		double enemy_energy = event.getEnergy();

		// observe the new state
		double [] nextState = StateAction.getState(my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, 0);

		// update Q(s, a)
		double reward = (my_energy - s[2])/s[2] - (enemy_energy - s[5])/s[5]; // the difference between my relative energy loss and enemy's relative loss in energy
		updateQ(reward, nextState);

		// observe the new state
		s = nextState;
	}

	public void onWin(WinEvent event)
	{
		roundEnded = true;
		
		double my_x = this.getX();
		double my_y = this.getY();
		double my_energy = this.getEnergy();
		double dist_to_enemy = s[3];
		double enemy_bearing = s[4];
		double enemy_energy = 0;

		// observe the new state
		double [] nextState = StateAction.getState(my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, 0);

		// update Q(s, a)
		updateQ(winReward, nextState);

		// observe the new state
		s = nextState;
	}

	public void onDeath(DeathEvent event)
	{
		roundEnded = true;
		
		double my_x = this.getX();
		double my_y = this.getY();
		double my_energy = 0;
		double dist_to_enemy = s[3];
		double enemy_bearing = s[4];
		double enemy_energy = s[5];

		// observe the new state
		double [] nextState = StateAction.getState(my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, 0);

		// update Q(s, a)
		updateQ(losePenalty, nextState);

		// observe the new state
		s = nextState;
	}

	private void executeAction(Action action)
	{
		switch(action)
		{
			case TURN_GUN_LEFT:
			{
				this.setTurnGunLeft(15);
				break;
			}
			case TURN_GUN_RIGHT:
			{
				this.setTurnGunRight(15);
				break;
			}
			case TURN_LEFT_MOVE_AHEAD:
			{
				this.setTurnLeft(90);
				this.setAhead(100);
				break;
			}
			case TURN_RIGHT_MOVE_AHEAD:
			{
				this.setTurnRight(90);
				this.setAhead(100);
				break;
			}
			case MOVE_AHEAD:
			{
				this.setAhead(100);
				break;
			}
			case MOVE_BACK:
			{
				Random rand = new Random();
				// choose which direction to turn
				int left = rand.nextInt(2);
				if (left == 0)
					this.setTurnLeft(180); 
				else
					this.setTurnRight(180);
				
				this.setAhead(100);
				break;
			}
			case FIRE1:
			{
				this.setFire(1);
				break;
			}
			case FIRE2:
			{
				this.setFire(2);
				break;
			}
			case FIRE3:
			{
				this.setFire(3);
				break;
			}
			case FULL_SCAN:
			{
				fullScan();
				return;
			}
		}
		
		this.execute();
	}

	private void fullScan()
	{
		for (int gunIncrement = 5; gunIncrement >= 1; gunIncrement -= 2)
		{
	  		int numIter = (int)Math.ceil(360.0/gunIncrement);
	  		for (int i = 0; i < numIter; i++) 
	  		{
	  			if (s != null)
	  				return;
	  			
	  			turnGunLeft(gunIncrement);
	  		}
		}
	}

}
