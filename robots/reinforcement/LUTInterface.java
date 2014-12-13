package reinforcement;

import neuralnet.CommonInterface;

public interface LUTInterface extends CommonInterface
{

	public void initialiseLUT();
	
	public int indexFor(double [] X);
	
}
