package edu.boun.edgecloudsim.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.Location;

/*
 * =========================================================
 * CustomNetworkModel
 * =========================================================
 *
 * Connectivity model based on ON/OFF states
 * for intermittent connectivity simulation.
 *
 * Supports:
 *
 * - intermittent connectivity
 * - reconnect behavior
 * - offline-first execution
 * - HES synchronization
 *
 * Scenarios:
 *
 * - Urban
 * - Semi-urban
 * - Rural
 *
 */

public class CustomNetworkModel extends NetworkModel {

	/*
	 * =========================================================
	 * Connection State
	 * =========================================================
	 */

	public enum ConnectionState {
		ON,
		OFF
	}

	/*
	 * =========================================================
	 * Connectivity parameters
	 * =========================================================
	 */

	private double onMeanTime;
	private double offMeanTime;

	/*
	 * =========================================================
	 * Device maps
	 * =========================================================
	 */

	private Map<Integer, ConnectionState> deviceStates;

	private Map<Integer, Double> nextTransitionTimes;

	private Random random;

	/*
	 * =========================================================
	 * Constructor
	 * =========================================================
	 */

	public CustomNetworkModel(
			int numberOfMobileDevices,
			String simScenario)
	{
		super(numberOfMobileDevices, simScenario);

		deviceStates =
			new HashMap<Integer, ConnectionState>();

		nextTransitionTimes =
			new HashMap<Integer, Double>();

		long seed = Long.getLong("edgecloudsim.seed", 12345L);
		random = new Random(seed);

		/*
		 * =====================================================
		 * Scenario configuration
		 * =====================================================
		 */

		configureScenario(simScenario);

		/*
		 * =====================================================
		 * Initialize devices
		 * =====================================================
		 */

		initializeDevices(numberOfMobileDevices);
	}

	/*
	 * =========================================================
	 * Scenario configuration
	 * =========================================================
	 */

	private void configureScenario(
			String simScenario)
	{

	    onMeanTime =
	            SimSettings.getInstance()
	                .getConnectivityOnMeanTime();

	        offMeanTime =
	            SimSettings.getInstance()
	                .getConnectivityOffMeanTime();
	}

	/*
	 * =========================================================
	 * Initialize devices
	 * =========================================================
	 */

	private void initializeDevices(
			int numberOfDevices)
	{
		if (onMeanTime <= 0.0 || offMeanTime <= 0.0) {
			throw new IllegalArgumentException(
				"Connectivity ON/OFF mean durations must be positive.");
		}

		/*
		 * Initialize the alternating exponential process in its
		 * stationary state. For an ON/OFF process, P(ON) is
		 * E[T_ON] / (E[T_ON] + E[T_OFF]). Exponential durations are
		 * memoryless, so sampling the state from this distribution
		 * and then sampling its residual duration avoids an all-ON
		 * startup transient.
		 */
		double pOn = onMeanTime / (onMeanTime + offMeanTime);

		for (int i = 0; i < numberOfDevices; i++) {
			ConnectionState initialState =
				(random.nextDouble() < pOn)
					? ConnectionState.ON
					: ConnectionState.OFF;

			deviceStates.put(i, initialState);

			double meanDuration =
				(initialState == ConnectionState.ON)
					? onMeanTime
					: offMeanTime;

			nextTransitionTimes.put(
				i, CloudSim.clock() + expRandom(meanDuration));
		}
	}

	/*
	 * =========================================================
	 * Connectivity verification
	 * =========================================================
	 */

	public boolean isConnected(
			int mobileDeviceId)
	{
		/*
		 * Non-mobile entities are always connected
		 */

		if(!deviceStates.containsKey(
				mobileDeviceId))
		{
			return true;
		}

		updateDeviceState(mobileDeviceId);

		ConnectionState state =
			deviceStates.get(mobileDeviceId);

		return state == ConnectionState.ON;
	}

	/*
	 * =========================================================
	 * Update device ON/OFF state
	 * =========================================================
	 */

	private void updateDeviceState(
	        int mobileDeviceId)
	{
	    /*
	     * Safety check
	     */

	    if(!nextTransitionTimes.containsKey(
	            mobileDeviceId))
	    {
	        return;
	    }

	    double currentTime =
	        CloudSim.clock();

	    Double nextTransition =
	        nextTransitionTimes.get(
	            mobileDeviceId);

	    /*
	     * Safety check
	     */

	    if(nextTransition == null)
	    {
	        return;
	    }

	    /*
	     * Pode haver várias transições
	     * acumuladas desde a última consulta.
	     */

	    while(currentTime >= nextTransition)
	    {
	        ConnectionState currentState =
	            deviceStates.get(
	                mobileDeviceId);

	        /*
	         * =============================================
	         * ON -> OFF
	         * =============================================
	         */

	        if(currentState ==
	                ConnectionState.ON)
	        {
	            deviceStates.put(
	                mobileDeviceId,
	                ConnectionState.OFF);

	            nextTransition =
	                nextTransition
	                + expRandom(offMeanTime);

	            nextTransitionTimes.put(
	                mobileDeviceId,
	                nextTransition);
	        }

	        /*
	         * =============================================
	         * OFF -> ON
	         * =============================================
	         */

	        else
	        {
	            deviceStates.put(
	                mobileDeviceId,
	                ConnectionState.ON);

	            nextTransition =
	                nextTransition
	                + expRandom(onMeanTime);

	            nextTransitionTimes.put(
	                mobileDeviceId,
	                nextTransition);
	        }
	    }
	}

	/*
	 * =========================================================
	 * Exponential Random Variable
	 * =========================================================
	 */

	private double expRandom(double mean){

		return -mean *
			Math.log(
				1.0 - random.nextDouble());
	}

	/*
	 * =========================================================
	 * Upload Delay
	 * =========================================================
	 */

	@Override
	public double getUploadDelay(
			int sourceDeviceId,
			int destDeviceId,
			Task task)
	{
		/*
		 * Source device offline
		 */

		/*
		 * Apenas dispositivos móveis sofrem desconexão.
		 * Edge e Cloud são considerados sempre conectados.
		 */
		if(sourceDeviceId == task.getMobileDeviceId())
		{
		    if(!isConnected(sourceDeviceId))
		    {
		        return -1;
		    }
		}

		/*
		 * EDGE upload
		 */

		if(destDeviceId >= 0 &&
				   destDeviceId <
				   SimManager.getInstance()
				       .getEdgeServerManager()
				       .getDatacenterList()
				       .size())
				{
				    return 0.036;
				}

		/*
		 * CLOUD upload
		 */

		if(destDeviceId
				== SimSettings.CLOUD_DATACENTER_ID)
		{
			return 0.154;
		}

		return 0.050;
	}

	/*
	 * =========================================================
	 * Download Delay
	 * =========================================================
	 */

	@Override
	public double getDownloadDelay(
			int sourceDeviceId,
			int destDeviceId,
			Task task)
	{
		/*
		 * Destination mobile offline
		 */

		if(destDeviceId == task.getMobileDeviceId())
		{
		    if(!isConnected(destDeviceId))
		    {
		        return -1;
		    }
		}

		/*
		 * Download path is classified by its SOURCE (the serving
		 * datacenter). Do not call getUploadDelay() here: that method
		 * classifies by destination ID, which is a mobile-device ID
		 * for downloads and can accidentally overlap an edge ID.
		 */
		if(sourceDeviceId == SimSettings.CLOUD_DATACENTER_ID)
		{
			return 0.154;
		}

		if(sourceDeviceId >= 0 &&
		   sourceDeviceId < SimManager.getInstance()
		       .getEdgeServerManager().getDatacenterList().size())
		{
			return 0.036;
		}

		return 0.050;
	}

	/*
	 * =========================================================
	 * Initialize
	 * =========================================================
	 */

	@Override
	public void initialize() {

		/*
		 * Already initialized in constructor
		 */
	}

	/*
	 * =========================================================
	 * Upload Started
	 * =========================================================
	 */

	@Override
	public void uploadStarted(
			Location accessPointLocation,
			int destDeviceId)
	{
		/*
		 * Not used
		 */
	}

	/*
	 * =========================================================
	 * Upload Finished
	 * =========================================================
	 */

	@Override
	public void uploadFinished(
			Location accessPointLocation,
			int destDeviceId)
	{
		/*
		 * Not used
		 */
	}

	/*
	 * =========================================================
	 * Download Started
	 * =========================================================
	 */

	@Override
	public void downloadStarted(
			Location accessPointLocation,
			int sourceDeviceId)
	{
		/*
		 * Not used
		 */
	}

	/*
	 * =========================================================
	 * Download Finished
	 * =========================================================
	 */

	@Override
	public void downloadFinished(
			Location accessPointLocation,
			int sourceDeviceId)
	{
		/*
		 * Not used
		 */
	}
}