package reinforcement;

import robots.NeuralRobot;
import robots.NeuralRobot.Action;

public class Test 
{
	public static double [] reverseIndex(int i)
	{
		double [] s = new double[DeepState.stateSpace.length + 1];
		
		s[s.length - 1] = i % NeuralRobot.numActions;
		String str = s[s.length - 1] + ", ";
		int val = i / NeuralRobot.numActions;
		for (int j = DeepState.stateSpace.length - 1; j >= 0; j--)
		{
			s[j] = val % DeepState.stateSpace[j];
			val /= DeepState.stateSpace[j];
			str += s[j] + ", ";
		}
		
		System.out.println(str);
		return s;
	}
	
	private static int getIndex(double [] s, Action action) {
		int index = 0;
		for (int i = 0; i < s.length; i++) {
			index = (int) (index * DeepState.stateSpace[i] + s[i]);
		}

		index = index * NeuralRobot.numActions + action.ordinal();

		return index;
	}

	public static void main(String [] args)
	{
		//double [] s = new double[]{0, 0, 0, 8, 5, 1, 0};
		//System.out.println(getIndex(s, NeuralRobot.Action.values()[4]));
		reverseIndex(60316);
	}

}
