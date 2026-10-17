/*
 * Title:        EdgeCloudSim - Idle/Active Load Generator implementation
 *
 * Description:
 * IdleActiveLoadGenerator implements a basic load generator model where
 * mobile devices generate tasks during active periods and remain idle
 * during inactive periods.
 *
 * Task interarrival time, active period, and idle period are defined
 * in the configuration file.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.task_generator;

import java.util.ArrayList;

import org.apache.commons.math3.distribution.ExponentialDistribution;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

/**
 * Idle/Active load generator implementation.
 *
 * <p>
 * Each mobile device is assigned one application/task type according to
 * the usage percentages defined in the simulation configuration.
 * The device then generates tasks according to a Poisson process during
 * active periods and remains inactive during idle periods.
 * </p>
 *
 * <p>
 * The following parameters are obtained from the task lookup table:
 * </p>
 *
 * <ul>
 *   <li>usage percentage</li>
 *   <li>mean task inter-arrival time</li>
 *   <li>active period</li>
 *   <li>idle period</li>
 *   <li>input data size</li>
 *   <li>output data size</li>
 *   <li>task computational length</li>
 * </ul>
 */
public class IdleActiveLoadGenerator extends LoadGeneratorModel {

	/**
	 * Application/task type assigned to each mobile device.
	 *
	 * The assignment is performed once during initializeModel().
	 */
	private int[] taskTypeOfDevices;

	/**
	 * Constructs the load generator.
	 *
	 * @param _numberOfMobileDevices number of mobile devices
	 * @param _simulationTime total simulation time in seconds
	 * @param _simScenario simulation scenario name
	 */
	public IdleActiveLoadGenerator(
			int _numberOfMobileDevices,
			double _simulationTime,
			String _simScenario) {

		super(_numberOfMobileDevices, _simulationTime, _simScenario);
	}

	/**
	 * Initializes the workload model and generates the complete task list.
	 *
	 * <p>
	 * Each mobile device is assigned one application type according to
	 * the usage_percentage configured for each application.
	 * </p>
	 *
	 * <p>
	 * After assignment, tasks are generated only during the corresponding
	 * application's active periods. Task arrivals within an active period
	 * follow an exponential distribution, representing a Poisson arrival
	 * process.
	 * </p>
	 */
	@Override
	public void initializeModel() {

		/*
		 * ------------------------------------------------------------
		 * 1. Initialize task list
		 * ------------------------------------------------------------
		 */
		taskList = new ArrayList<TaskProperty>();

		SimSettings settings = SimSettings.getInstance();

		double[][] taskLookupTable = settings.getTaskLookUpTable();

		/*
		 * ------------------------------------------------------------
		 * 2. Create exponential distributions for task characteristics
		 *
		 * Index:
		 *
		 * [taskType][0] = input data size
		 * [taskType][1] = output data size
		 * [taskType][2] = computational length
		 * ------------------------------------------------------------
		 */
		ExponentialDistribution[][] expRngList =
				new ExponentialDistribution[taskLookupTable.length][3];

		for (int taskType = 0;
				taskType < taskLookupTable.length;
				taskType++) {

			/*
			 * Ignore application types whose usage percentage is zero.
			 */
			if (taskLookupTable[taskType][0] <= 0) {
				continue;
			}

			/*
			 * Input data size distribution.
			 */
			expRngList[taskType][0] =
					new ExponentialDistribution(
							taskLookupTable[taskType][5]);

			/*
			 * Output data size distribution.
			 */
			expRngList[taskType][1] =
					new ExponentialDistribution(
							taskLookupTable[taskType][6]);

			/*
			 * Computational workload distribution.
			 */
			expRngList[taskType][2] =
					new ExponentialDistribution(
							taskLookupTable[taskType][7]);
		}

		/*
		 * ------------------------------------------------------------
		 * 3. Validate usage percentages
		 * ------------------------------------------------------------
		 */

		double totalUsagePercentage = 0;

		for (int taskType = 0;
				taskType < taskLookupTable.length;
				taskType++) {

			totalUsagePercentage += taskLookupTable[taskType][0];
		}

		/*
		 * The workload configuration should normally sum to 100%.
		 */
		if (Math.abs(totalUsagePercentage - 100.0) > 0.0001) {

			SimLogger.printLine(
					"Error: Application usage percentages must sum to 100%. "
					+ "Current total = "
					+ totalUsagePercentage);

			throw new IllegalArgumentException(
					"Application usage percentages must sum to 100%.");
		}

		/*
		 * ------------------------------------------------------------
		 * 4. Assign one application type to each mobile device
		 * ------------------------------------------------------------
		 */

		taskTypeOfDevices = new int[numberOfMobileDevices];

		for (int deviceId = 0;
				deviceId < numberOfMobileDevices;
				deviceId++) {

			int selectedTaskType = selectTaskType(taskLookupTable);

			if (selectedTaskType < 0) {

				SimLogger.printLine(
						"Critical Error: Could not assign a task type "
						+ "to mobile device " + deviceId);

				throw new IllegalStateException(
						"Invalid task type assignment.");
			}

			taskTypeOfDevices[deviceId] = selectedTaskType;

			/*
			 * --------------------------------------------------------
			 * 5. Read parameters for the selected application
			 * --------------------------------------------------------
			 */

			double poissonMean =
					taskLookupTable[selectedTaskType][2];

			double activePeriod =
					taskLookupTable[selectedTaskType][3];

			double idlePeriod =
					taskLookupTable[selectedTaskType][4];

			/*
			 * Validate the parameters that control the workload cycle.
			 */
			if (poissonMean <= 0) {

				SimLogger.printLine(
						"Error: Invalid poisson_interarrival for task type "
						+ selectedTaskType);

				throw new IllegalArgumentException(
						"poisson_interarrival must be greater than zero.");
			}

			if (activePeriod <= 0) {

				SimLogger.printLine(
						"Error: Invalid active_period for task type "
						+ selectedTaskType);

				throw new IllegalArgumentException(
						"active_period must be greater than zero.");
			}

			if (idlePeriod < 0) {

				SimLogger.printLine(
						"Error: Invalid idle_period for task type "
						+ selectedTaskType);

				throw new IllegalArgumentException(
						"idle_period cannot be negative.");
			}

			/*
			 * --------------------------------------------------------
			 * 6. Randomize initial active period
			 * --------------------------------------------------------
			 *
			 * Randomization prevents all devices from generating
			 * their first task at exactly the same simulation time.
			 */
			double activePeriodStartTime =
					SimUtils.getRandomDoubleNumber(
							SimSettings.CLIENT_ACTIVITY_START_TIME,
							SimSettings.CLIENT_ACTIVITY_START_TIME
									+ activePeriod);

			/*
			 * Current active period start.
			 */
			double currentActiveStart = activePeriodStartTime;

			/*
			 * Current active period end.
			 */
			double currentActiveEnd =
					currentActiveStart + activePeriod;

			/*
			 * --------------------------------------------------------
			 * 7. Create exponential distribution for task arrivals
			 * --------------------------------------------------------
			 */
			ExponentialDistribution arrivalRng =
					new ExponentialDistribution(poissonMean);

			/*
			 * Start generating tasks from the randomized active time.
			 */
			double virtualTime = currentActiveStart;

			/*
			 * --------------------------------------------------------
			 * 8. Generate tasks until simulation ends
			 * --------------------------------------------------------
			 */
			while (virtualTime < simulationTime) {

				/*
				 * Generate the next inter-arrival interval.
				 */
				double interval = arrivalRng.sample();

				/*
				 * Apache Commons Math exponential distribution should
				 * return a positive value. This check protects against
				 * invalid values and avoids an infinite loop.
				 */
				if (interval <= 0) {

					SimLogger.printLine(
							"Warning: Invalid inter-arrival interval "
							+ interval
							+ " for device "
							+ deviceId);

					continue;
				}

				/*
				 * Calculate the next task arrival time.
				 */
				double nextTaskTime = virtualTime + interval;

				/*
				 * ----------------------------------------------------
				 * 9. Check whether the task falls outside the
				 *    current active period.
				 * ----------------------------------------------------
				 */
				if (nextTaskTime >= currentActiveEnd) {

					/*
					 * Move to the next active period.
					 *
					 * Current active period:
					 *
					 * [currentActiveStart, currentActiveEnd]
					 *
					 * Idle period:
					 *
					 * [currentActiveEnd,
					 *  currentActiveEnd + idlePeriod]
					 *
					 * Next active period starts after the idle period.
					 */
					currentActiveStart =
							currentActiveEnd + idlePeriod;

					currentActiveEnd =
							currentActiveStart + activePeriod;

					/*
					 * Start the next cycle at the beginning of
					 * the new active period.
					 */
					virtualTime = currentActiveStart;

					continue;
				}

				/*
				 * ----------------------------------------------------
				 * 10. Task is inside the active period
				 * ----------------------------------------------------
				 */
				virtualTime = nextTaskTime;

				/*
				 * Do not generate tasks beyond the simulation time.
				 */
				if (virtualTime >= simulationTime) {
					break;
				}

				/*
				 * Create the task description.
				 */
				taskList.add(
						new TaskProperty(
								deviceId,
								selectedTaskType,
								virtualTime,
								expRngList));
			}
		}
	}

	/**
	 * Selects an application/task type according to the configured
	 * usage percentages.
	 *
	 * Example:
	 *
	 * <pre>
	 * AR_CONTENT    = 35%
	 * TRANSPORT     = 15%
	 * VISITOR_COUNT = 15%
	 * TICKET        = 10%
	 * INTERACTION   = 25%
	 * </pre>
	 *
	 * The cumulative probability distribution is therefore:
	 *
	 * <pre>
	 * 0 - 35   -> AR_CONTENT
	 * 35 - 50  -> TRANSPORT
	 * 50 - 65  -> VISITOR_COUNT
	 * 65 - 75  -> TICKET
	 * 75 - 100 -> INTERACTION
	 * </pre>
	 *
	 * @param taskLookupTable EdgeCloudSim task lookup table
	 * @return selected task type
	 */
	private int selectTaskType(double[][] taskLookupTable) {

		double selector =
				SimUtils.getRandomDoubleNumber(0, 100);

		double cumulativePercentage = 0;

		for (int taskType = 0;
				taskType < taskLookupTable.length;
				taskType++) {

			double usagePercentage =
					taskLookupTable[taskType][0];

			/*
			 * Ignore inactive task types.
			 */
			if (usagePercentage <= 0) {
				continue;
			}

			cumulativePercentage += usagePercentage;

			if (selector <= cumulativePercentage) {
				return taskType;
			}
		}

		/*
		 * Floating-point protection.
		 *
		 * If the selector is extremely close to 100, return
		 * the last active task type.
		 */
		for (int taskType = taskLookupTable.length - 1;
				taskType >= 0;
				taskType--) {

			if (taskLookupTable[taskType][0] > 0) {
				return taskType;
			}
		}

		return -1;
	}

	/**
	 * Returns the application/task type assigned to a mobile device.
	 *
	 * @param deviceId mobile device identifier
	 * @return task type assigned to the device
	 */
	@Override
	public int getTaskTypeOfDevice(int deviceId) {

		if (taskTypeOfDevices == null) {
			throw new IllegalStateException(
					"Load generator has not been initialized.");
		}

		if (deviceId < 0 ||
				deviceId >= taskTypeOfDevices.length) {

			throw new IllegalArgumentException(
					"Invalid mobile device ID: " + deviceId);
		}

		return taskTypeOfDevices[deviceId];
	}
}
