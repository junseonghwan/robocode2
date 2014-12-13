package reinforcement;

public class StateAction 
{
	public static int numStates = 7; // states are: (my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, scanned)
	//actions are: (turn_gun_left_15, turn_gun_right_15, turn_left_move_ahead, turn_right_move_ahead, move_ahead, move_back, fire1, fire2, fire3)
	public enum Action {
		TURN_GUN_LEFT, 
		TURN_GUN_RIGHT, 
		TURN_LEFT_MOVE_AHEAD, 
		TURN_RIGHT_MOVE_AHEAD, 
		MOVE_AHEAD, 
		MOVE_BACK, 
		FIRE1, 
		FIRE2, 
		FIRE3,
		FULL_SCAN;
		
	}

	public static int [] argVariableCeiling, argVariableFloor;

	static {
		argVariableCeiling = new int[numStates + 1];
		argVariableFloor = new int[numStates + 1];

		// total number of states is, 9*7*4*5*2*4*2*3 = 60480!!!
		
		// my_x
		argVariableFloor[0] = 0;
		argVariableCeiling[0] = 8;

		// my_y
		argVariableFloor[1] = 0;
		argVariableCeiling[1] = 6;

		// my_energy, group into {0, 1, 2, 3} = {< 25, < 50, < 75, <= 100)  
		argVariableFloor[2] = 0;
		argVariableCeiling[2] = 3;

		// dist_to_enemy, group into {0, 1, 2, 3} = {< 25, < 50, < 100, >= 100}
		argVariableFloor[3] = 0;
		argVariableCeiling[3] = 3;

		// enemy_bearing: divide into 2 (> 0, < 0) in other words, (0~180, 0~-180), on left or on right
		argVariableFloor[4] = 0;
		argVariableCeiling[4] = 1;

		// enemy_energy
		argVariableFloor[5] = 0;
		argVariableCeiling[5] = 3;

		// enemy scanned ahead (1 if yes, 0 if no)
		argVariableFloor[6] = 0;
		argVariableCeiling[6] = 1;

		argVariableFloor[numStates] = 1;
		argVariableFloor[numStates] = Action.values().length;
	}

	public static int getNumAction()
	{
		return Action.values().length;
	}

	public static double [] reduceDim(double [] currState)
	{
		double [] state = new double[currState.length];
		System.arraycopy(currState, 0, state, 0, currState.length);
		state[0] = (int)Math.round(state[0]/100);
		state[1] = (int)Math.round(state[1]/100);
		state[2] = state[2] < 25 ? 0 : (state[2] < 50 ? 1 : (state[2] < 75 ? 2 : 3));
		state[3] = state[3] < 25 ? 0 : (state[3] < 50 ? 1 : (state[3] < 100 ? 2 : 3));
		state[4] = state[4] > 0 ? 0 : 1;
		state[5] = state[5] < 25 ? 0 : (state[5] < 50 ? 1 : (state[5] < 75 ? 2 : 3));

		return state;
	}

	//states are: (my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, scanned)
	public static double [] getState(double my_x, double my_y, double my_energy, double dist_to_enemy, double enemy_bearing, double enemy_energy, double scanned)
	{
		double [] s = new double[]{my_x, my_y, my_energy, dist_to_enemy, enemy_bearing, enemy_energy, scanned};
		return s;
	}

}
