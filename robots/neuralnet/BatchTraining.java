package neuralnet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

import reinforcement.DeepState;
import reinforcement.NeuralState;
import reinforcement.DeepState.DeepStates;
import robots.DeepRobot;
import robots.NeuralRobot;
import robots.NeuralRobot.Action;
import util.Pair;

public class BatchTraining 
{
	public static double tol = 0.05;
	public static double stopCriterion = 0.000001;
	
	private static void formInput(double [] input, int action)
	{
		input[DeepStates.MY_X.ordinal()] = input[DeepStates.MY_X.ordinal()] / 8.0;
		input[DeepStates.MY_Y.ordinal()] = input[DeepStates.MY_Y.ordinal()] / 6.0;
		if(input[DeepStates.MY_ENERGY.ordinal()] == 0)
			input[DeepStates.MY_ENERGY.ordinal()] = -1.0;
		input[DeepStates.ENEMY_X.ordinal()] = input[DeepStates.ENEMY_X.ordinal()] / 8.0;
		input[DeepStates.ENEMY_Y.ordinal()] = input[DeepStates.ENEMY_Y.ordinal()] / 6.0;
		if(input[DeepStates.ENEMY_ENERGY.ordinal()] == 0)
			input[DeepStates.ENEMY_ENERGY.ordinal()] = -1.0;

		Action [] actions = Action.values();
		for (int i = 0; i < actions.length; i++)
		{
			if (actions[i].ordinal() == action)
				input[DeepState.stateSpace.length + i] = 1.0;
			else
				input[DeepState.stateSpace.length + i] = -1.0;
		}
	}
	
	public static void makeData(ArrayList<Pair<Integer, Double>> dat, double [][] X, double [] y)
	{
		int N = dat.size();
		int p = X[0].length;
		int index = 0;
		for (int i = 0; i < dat.size(); i++)
		{
			Pair<Integer, Double> row = dat.get(i);
			index = row.getFirst().intValue();
			y[i] = row.getSecond().doubleValue();

  		int action = index % DeepRobot.numActions;
  		int val = index / DeepRobot.numActions;
  		for (int j = DeepState.stateSpace.length - 1; j >= 0; j--)
  		{
  			X[i][j] = val % DeepState.stateSpace[j];
  			val /= DeepState.stateSpace[j];
  		}
  		formInput(X[i], action);
		}
	}
	
	public static ArrayList<Double> errors = new ArrayList<Double>();
	
	public static double batchTrain(Random random, double [][] X, double [] y, int numHidden, double learningRate, double momentum, double a, double b, int maxIter)
	{
		NeuralNet nn = new NeuralNet(random, X[0].length, numHidden, learningRate, momentum, a, b);
		nn.initializeWeights();

		int numEpochs = 0;
		double prevRMSE = 0.0, rmse = 0.0;
		while (true)
		{
			double errss = 0.0;
			for (int j = 0; j < X.length; j++)
			{
				double err = nn.train(X[j], y[j]);
				errss += err;
			}
			
			numEpochs += 1;
			//errss /= 2;

			rmse = Math.sqrt(errss/X.length);
			errors.add(rmse);
			
			double diff = Math.abs(prevRMSE - rmse);
			
			if (Math.abs(rmse) < tol)
				break;
			if (diff < stopCriterion)
				break;
			prevRMSE = rmse;

			//if (numEpochs % 50 == 0)
			//System.out.println("nEpochs=" + numEpochs + ", rmse=" + rmse + ", diff=" + diff);

			if (numEpochs == maxIter)
				break;
			if (new Double(rmse).isNaN())
				return Double.NaN;
		}

		return rmse;
	}
	
	public static void main(String[] args) throws IOException
	{
		// 1. load the q values
		ArrayList<Pair<Integer, Double>> dat = new ArrayList<Pair<Integer, Double>>(); 
		Scanner scanner = new Scanner(new File("_qvalues_2.txt"));
		String str = ""; 
		while (scanner.hasNextLine())
		{
			str = scanner.nextLine();
			String [] row = str.split(" ");
			//System.out.println(row[0] + ", " + row[1]);
			int x= Integer.valueOf(row[0]).intValue();
			double y = Double.valueOf(row[1]).doubleValue();
			if (y == 0.0)
				continue;
			else
			{
				dat.add(new Pair<Integer, Double>(x, y));
			}
		}
		scanner.close();

		// 2. translate the index to inputs
		double [][] X = new double[dat.size()][DeepRobot.numActions + DeepState.stateSpace.length];
		double [] y = new double[dat.size()];
		makeData(dat, X, y);
		str = dat.get(0).getFirst().intValue() + " ";
		for (int i = 0; i < X[0].length; i++)
		{
			str += X[0][i] + " ";
		}
		//System.out.println(str);

		// 3. batch training
		Random random = new Random(1231);

		// momentume from 0.0001 to 1.0
		double [][] momentum = new double[10][10];
		for (int i = 0; i < momentum.length; i++)
		{
			for (int j = 0; j < momentum[0].length; j++)
			{
				momentum[i][j] = 0.1*j;
			}
		}

		// TODO: uncomment below line to do a grid search over the learning rate. Note: momentum search is fixed over {0, 0.1, ..., 0.9}
		// the second and third arguments are the begin and the ending values of the learning rate
		// the last argument is the number of iterations (maxiter)
		//gridSearch1(random, 0, 1.0, momentum, X, y, "grid_search1.txt", 50);
		//gridSearch1(random, 0, 0.1, momentum, X, y, "grid_search2.txt", 50);
		gridSearch1(random, 0, 0.01, momentum, X, y, "grid_search3.txt", 50);
		//gridSearch1(random, 0, 0.001, momentum, X, y, "grid_search4.txt", 50); // (0.0005, 0.2) is the best so far
		//gridSearch1(random, 0.001, 0.002, momentum, X, y, "grid_search5.txt", 50); // (0.0018, 0.1) would work well too

		// output
		/*
		batchTrain(random, X, y, X[0].length*2, 0.0005, 0.2, -1, 1, 2000);
		
		PrintWriter writer = new PrintWriter(new File("rmse.txt"));
		for (int i = 0; i < errors.size(); i++)
		{
			writer.println(errors.get(i));
		}
		
		writer.close();
		*/

	}
	
	private static void gridSearch1(Random random, double begin, double end, double [][] momentum, double [][] X, double [] y, String filename, int maxit) throws IOException
	{
		// do a grid search
		double [][] rmse = new double[momentum.length][momentum[0].length];
		for (int i = 0; i < momentum.length; i++)
		{
			double learningRate = begin + (double)(end-begin)*(i+1)/rmse[0].length;
			for (int j = 0; j < momentum[i].length; j++)
			{
  			double temp = batchTrain(random, X, y, X[0].length*2, learningRate, momentum[i][j], -1, 1, maxit);
  			System.out.println(learningRate + ", " + momentum[i][j] + ", " + temp);
  			rmse[i][j] = temp;
			}
		}

		// output the rmse for different learning rates
		PrintWriter writer = new PrintWriter(new File(filename));
		for (int i = 0; i < rmse.length; i++)
		{
			for (int j = 0; j < rmse[i].length; j++)
			{
				writer.print(rmse[i][j] + " ");
			}
			
			writer.print("\n");
		}
		
		writer.close();
	}

}
