package reinforcement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

import reinforcement.StateAction.Action;
import util.Pair;

/**
 * This is a singleton class
 * @author Seong-Hwan Jun (s2jun.uw@gmail.com)
 *
 */
public class LUT implements LUTInterface 
{
	private int argNumInputs;
	private int [] argVariableFloor, argVariableCeiling; 		// assume that the number of dimension for each variable i is argVariableCeiling[i] - argVariableFloor[i] + 1		
	
	private Hashtable<Integer, Double> lut = new Hashtable<Integer, Double>();
	private static LUT instance = null;

	private LUT(int argNumInputs, int [] argVariableFloor, int [] argVariableCeiling)
	{
		this.argNumInputs = argNumInputs;
		this.argVariableCeiling = argVariableCeiling;
		this.argVariableFloor = argVariableFloor;
		initialiseLUT(); // gets called only once
	}
	
	public static LUT getInstace()
	{
		if (instance == null)
		{
			instance = new LUT(StateAction.numStates + 1, StateAction.argVariableFloor, StateAction.argVariableCeiling);
		}
		
		return instance;
	}
	
	@Override
	public double outputFor(double[] X) 
	{
		return 0;
	}

	@Override
	public double train(double[] X, double argValue) 
	{
		return 0;
	}

	@Override
	public void save(File argFile) 
	{
	}

	@Override
	public void load(String argFileName) throws IOException 
	{
	}

	@Override
	public void initialiseLUT() 
	{
		// do not initialize anything, just insert as new states are encountered
	}
	
	/**
	 * X: representation of the state before dimension reduction 
	 */
	@Override
	public int indexFor(double[] X) 
	{
		// reduce dimension
		double [] reducedX = StateAction.reduceDim(X);

		// use Java string's hashCode()
		String str = "";
		for (int i = 0; i < reducedX.length; i++)
		{
			str += reducedX[i];
		}
		return str.hashCode();
	}

	public double getQValue(double [] sa)
	{
		if (sa.length != (StateAction.numStates + 1))
			throw new RuntimeException();

		int key = indexFor(sa);
		if (!lut.contains(key))
		{
			// enter into the hash table
			lut.put(key, 0.0);
		}
		return lut.get(key);
	}
	
	public void updateQValue(double [] sa, double newValue)
	{
		int key = indexFor(sa);
		if (!lut.contains(key))
			throw new RuntimeException(); // this key should have been entered, if not, there must have been a bug

		lut.put(key, newValue);
	}
	
	// return the action id and the chosen Q(s, a)
	public Pair<Action, Double> chooseAction(double [] s)
	{
		double [] stateAction = new double[s.length + 1];
		System.arraycopy(s, 0, stateAction, 0, s.length);
		double [] values = new double[StateAction.getNumAction()];
		for (int i = 0; i < StateAction.getNumAction(); i++)
		{
			stateAction[s.length] = i;
			values[i] = getQValue(stateAction);
		}

		// choose the action based on your policy
		// 1. argmax_a Q(s, a)
		Action maxAction = argmax(values);
		return new Pair<Action, Double>(maxAction, values[maxAction.ordinal()]);
	}

	private Action argmax(double [] values)
	{
		int bestAction = 0;
		List<Integer> ties = new ArrayList<Integer>();
		for (int i = 1; i < StateAction.getNumAction(); i++)
		{
			if (values[i] == values[bestAction])
			{
				ties.add(bestAction);
				bestAction = i;
			}
			else if (values[i] > values[bestAction]) 
			{
				bestAction = i;
			}
		}
		ties.add(bestAction);
		
		// break any ties
		if (ties.size() > 1)
		{
			Random rand = new Random();
			int index = rand.nextInt(ties.size());
			bestAction = ties.get(index);
		}
		return Action.values()[bestAction]; 
	}
	
	private boolean checkDimension(double [] s)
	{
		for (int i = 0; i < StateAction.numStates; i++)
		{
			if ((s[i] < argVariableFloor[i]) || (s[i] > argVariableCeiling[i]))
				return false;
		}
		
		return true;
	}

}
