package neuralnet;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class NeuralNet implements NeuralNetInterface
{
	private Random rand;
	
	public double [][] weightMatHidden;
	public double [][] weightMatHiddenChange;
	public double [][] weightMatInputs;
	public double [][] weightMatInputsChange;
	public double [][] gradMatHidden;
	public double [][] gradMatInputs;
	public double deltaHidden; // in general, deltaHidden should be a vector of output length
	public double [] deltaInputs;
	public double [] hiddenPreActivation;
	public double [] hiddenPostActivation;
	public double outputPreActivation;
	public double outputPostActivation;
	private double learningRate, momentum, a, b;
	private int numInputs, numHidden;

	public NeuralNet(Random rand, int numInputs, int numHidden, double learningRate, double momentum, double a, double b)
	{
		this.rand = rand;
		this.numHidden = numHidden;
		this.numInputs = numInputs;

		weightMatHidden = new double[numHidden + 1][1];
		gradMatHidden = new double[numHidden + 1][1];
		weightMatHiddenChange = new double[numHidden + 1][1];
		weightMatInputs = new double[numInputs + 1][numHidden];
		gradMatInputs = new double[numInputs + 1][numHidden];
		weightMatInputsChange = new double[numInputs + 1][numHidden];
		deltaHidden = 0;
		deltaInputs = new double[numHidden];
		hiddenPreActivation = new double[numHidden];
		hiddenPostActivation = new double[numHidden];
		outputPreActivation = 0.5;
		outputPostActivation = 0.5;
		this.a = a;
		this.b = b;
		this.learningRate = learningRate;
		this.momentum = momentum;
		
		initializeWeights();
	}

	@Override
	public double sigmoid(double x) 
	{
		double sigm = 2.0 / (1.0 + Math.exp(-x)) + 1.0;
		return sigm;
	}

	@Override
	public double customSigmoid(double x)	
	{
		double sigm = (b - a) / (1.0 + Math.exp(-x)) + a;
		return sigm;
	}
	

	@Override
	public void initializeWeights() 
	{
		for (int i = 0; i < numHidden + 1; i++)
		{
			for(int j = 0; j < 1; j++)
			{
				weightMatHidden[i][j] = rand.nextDouble() - 0.5;
			}
		}
		for (int i = 0; i < numInputs + 1; i++)
		{
			for(int j = 0; j < numHidden; j++)
			{
				weightMatInputs[i][j] = rand.nextDouble() - 0.5;
			}
		}
	}

	@Override
	public void zeroWeights() 
	{
		weightMatHidden = new double[numHidden + 1][1]; // what's the point in declaring it as a 2D array? It is clearly a vector
		weightMatInputs = new double[numInputs + 1][numHidden];
	}
	
	public void forwardprop(double [] x)
	{
		if(x.length != numInputs)
		{
			throw new RuntimeException("Number of inputs is wrong!");
		}

		for(int i = 0; i < numHidden; i++)
		{
			hiddenPreActivation[i] = weightMatInputs[0][i]; // intialize by intercept
			for(int j = 1; j < numInputs + 1; j++)
			{
				hiddenPreActivation[i] += weightMatInputs[j][i] * x[j - 1];
			}
			hiddenPostActivation[i] = customSigmoid(hiddenPreActivation[i]);
		}

		for(int i = 0; i < 1; i++) // what is the point of this? to make it more general for the case where there are more than one output?
		{
			outputPreActivation = weightMatHidden[0][i]; // intercept
			for(int j = 1; j < numHidden + 1; j++)
			{
				outputPreActivation += weightMatHidden[j][i] * hiddenPostActivation[j - 1];
			}
			outputPostActivation = outputPreActivation; // TODO customSigmoid(outputPreActivation);
		}
	}

	public double derivCustomSigmoid(double x)
	{
		double derivSigm = (b - a) * Math.exp(-x) / Math.pow(1.0 + Math.exp(-x), 2);
		return derivSigm;
	}

	public double l2Loss(double y, double yhat)
	{
		double loss = 0.5 * Math.pow(y - yhat, 2.0);
		return loss;
	}
	
	public double derivL2Loss(double y, double yhat)
	{
		double derivLoss = yhat - y;
		return derivLoss;
	}
	
	/*
	public double entropyLoss(double y, double yhat)
	{
		double loss = y * Math.log(yhat) + (1 - y) * Math.log(1 - yhat);
		return loss;
	}

	public double derivEntropyLoss(double y, double yhat)
	{
		double derivLoss = y / yhat - (1 - y)/(1 - yhat);
		return derivLoss;
	}
	*/
	
	public void backprop(double [] x, double y)
	{
		if(x.length != numInputs)
		{
			throw new RuntimeException("Number of inputs is wrong!");
		}
		
		forwardprop(x);

		// backprop from output to hidden layer
		double derivLoss = derivL2Loss(y, outputPostActivation);
		// update delta
		deltaHidden = derivLoss; // TODO derivLoss * derivCustomSigmoid(outputPreActivation);
		// update gradient
		gradMatHidden[0][0] = deltaHidden;
		// update weightChange
		weightMatHiddenChange[0][0] = learningRate * gradMatHidden[0][0] + momentum * weightMatHiddenChange[0][0];
		// udpate weight
		weightMatHidden[0][0] -= weightMatHiddenChange[0][0];
		
		for(int i = 0; i < numHidden; i++)
		{
			// update gradient
			gradMatHidden[i + 1][0] = deltaHidden * hiddenPostActivation[i];
			// update weightChange
			weightMatHiddenChange[i + 1][0] = learningRate * gradMatHidden[i + 1][0] + momentum * weightMatHiddenChange[i + 1][0];
			// update weight
			weightMatHidden[i + 1][0] -= weightMatHiddenChange[i + 1][0];
		}
		
		// backprop from hidden layer to inputs
		for(int i = 0; i < numHidden; i++)
		{
			// update delta
			deltaInputs[i] = deltaHidden * weightMatHidden[i + 1][0] * derivCustomSigmoid(hiddenPreActivation[i]);
			// update gradient
			gradMatInputs[0][i] = deltaInputs[i];
			// update weightChange
			weightMatInputsChange[0][i] = learningRate * gradMatInputs[0][i] + momentum * weightMatInputsChange[0][i];
			// update weight
			weightMatInputs[0][i] -= weightMatInputsChange[0][i]; 
			for(int j = 0; j < numInputs; j++)
			{
				// update gradient
				gradMatInputs[j + 1][i] = deltaInputs[i] * x[j];
				// update weightChange
				weightMatInputsChange[j + 1][i] = learningRate * gradMatInputs[j + 1][i] + momentum * weightMatInputsChange[j + 1][i];
				// update weight
				weightMatInputs[j + 1][i] -= weightMatInputsChange[j + 1][i]; 
			}
		}
	}
	
	

	@Override
	public double outputFor(double[] X) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double train(double[] X, double y) 
	{
		// perform, forward prop, backward prop, and updating of weights for one training instance, label pair (X, y)
		forwardprop(X);
		backprop(X, y);
		return Math.pow(outputPostActivation - y, 2.0);
	}

	@Override
	public void save(File argFile) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void load(String argFileName) throws IOException {
		// TODO Auto-generated method stub
		
	}
}
