package neuralnet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;

public class NeuralNetMain 
{
	
	// prints the erros to the specified file, returns the number of epochs
	public static int trainNN(Random random, double learningRate, double momentum, double a, double b, 
			double [][] X, double [] y, double tol, boolean write, String fileName)  throws IOException
	{
		NeuralNet nn = new NeuralNet(random, 2, 4, learningRate, momentum, a, b);

		nn.initializeWeights();

		ArrayList<Double> errors = new ArrayList<Double>();

		int numEpochs = 0;
		while (true)
		{
			double errss = 0.0;
			for (int j = 0; j < 4; j++)
			{
				double err = nn.train(X[j], y[j]);
				errss += err;
			}
			
			numEpochs += 1;
			errss /= 2;
			errors.add(errss);
			//System.out.println("nEpochs=" + numEpochs + ", err=" + errss);
			if (Math.abs(errss) < tol)
				break;
		}

		System.out.println("nEpochs = " + numEpochs);

		if (write)
		{
			// print the errors
			PrintWriter writer = new PrintWriter(new FileWriter(new File("../output/" + (fileName + "_rho=" + momentum) + ".csv")));
	
			for (int i = 0; i < errors.size(); i++)
			{
				writer.println(errors.get(i).doubleValue());
			}
			writer.close();
		}
		
		return numEpochs;
	}

	public static void main(String[] args) throws IOException
	{
		double tol = 0.05;
		Random random = new Random(new String("ZhangHongyang").hashCode());

		// training data
		double [][] binaryX = new double[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}};		
		double [] binaryY = new double[]{0, 1, 1, 0};

		double [][] bipolarX = new double[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
		double [] bipolarY = new double[]{-1, 1, 1, -1}; // bipolar representation

		int numSimulations = 10;
		System.out.println("binary");
		for (int n = 0; n < numSimulations; n++)
		{
			trainNN(random, 0.2, 0.0, 0.0, 1.0, binaryX, binaryY, tol, n == 0, "a1-binary");
		}

		System.out.println("bipolar");
		for (int n = 0; n < numSimulations; n++)
		{
			trainNN(random, 0.2, 0.0, -1.0, 1.0, bipolarX, bipolarY, tol, n == 0, "a1-bipolar");
		}

	}

}
