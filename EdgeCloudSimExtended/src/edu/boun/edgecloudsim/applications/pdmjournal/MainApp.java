/*
 * Title:        EdgeCloudSim - Main Application
 *
 * Description:  Main application for HES experiments
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University
 */

package edu.boun.edgecloudsim.applications.pdmjournal;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.HESLogger;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class MainApp {

	public static final int EXPECTED_NUM_OF_ARGS = 5;

	public static final String APPLICATION_FOLDER =
		"pdmjournal";

	public static void main(String[] args) {

		Log.disable();

		SimLogger.enablePrintLog();

		int iterationStart;

		int iterationEnd;

		String configFile = null;

		String outputFolder = null;

		String edgeDevicesFile = null;

		String applicationsFile = null;

		/*
		 * =====================================================
		 * Arguments
		 * =====================================================
		 */

		if(args.length == EXPECTED_NUM_OF_ARGS){

			configFile =
				args[0];

			edgeDevicesFile =
				args[1];

			applicationsFile =
				args[2];

			outputFolder =
				args[3];

			iterationStart =
				Integer.parseInt(args[4]);

			iterationEnd =
				iterationStart;
		}
		else{

			SimLogger.printLine(
				"Simulation setting file, "
				+ "output folder and iteration "
				+ "number are not provided! "
				+ "Using default ones...");

			configFile =
				"scripts/"
				+ APPLICATION_FOLDER
				+ "/config/default_config.properties";

			applicationsFile =
				"scripts/"
				+ APPLICATION_FOLDER
				+ "/config/applications.xml";

			edgeDevicesFile =
				"scripts/"
				+ APPLICATION_FOLDER
				+ "/config/edge_devices.xml";

			iterationStart = 1;

			iterationEnd = 6;
		}

		/*
		 * =====================================================
		 * Initialize Simulation Settings
		 * =====================================================
		 */

		SimSettings SS =
			SimSettings.getInstance();

		if(SS.initialize(
				configFile,
				edgeDevicesFile,
				applicationsFile) == false)
		{
			SimLogger.printLine(
				"Cannot initialize simulation settings!");

			System.exit(0);
		}

		/*
		 * =====================================================
		 * Dates
		 * =====================================================
		 */

		DateFormat df =
			new SimpleDateFormat(
				"dd/MM/yyyy HH:mm:ss");

		Date simulationStartDate =
			Calendar.getInstance().getTime();

		SimLogger.printLine(
			"Application folder "
			+ APPLICATION_FOLDER);

		SimLogger.printLine(
			"directory at "
			+ df.format(simulationStartDate));

		SimLogger.printLine(
			"----------------------------------------------------------------------");

		/*
		 * =====================================================
		 * Main Iterations
		 * =====================================================
		 */

		for(int iterationNumber =
				iterationStart;
				iterationNumber <= iterationEnd;
				iterationNumber++)
		{

			SimLogger.printLine(
				"Interaction number: "
				+ iterationNumber);

			if(args.length != EXPECTED_NUM_OF_ARGS){

				outputFolder =
					"sim_results/"
					+ APPLICATION_FOLDER
					+ "/ite"
					+ iterationNumber;
			}

			/*
			 * =================================================
			 * File Logging
			 * =================================================
			 */

			if(SS.getFileLoggingEnabled()){

				SimLogger.enableFileLog();

				File dir =
					new File(outputFolder);

				if(dir.exists()
						&& dir.isDirectory())
				{
					for(File f : dir.listFiles()){

						if(f.exists()
								&& f.isFile())
						{
							f.delete();
						}
					}
				}
				else{

					dir.mkdirs();
				}
			}

			/*
			 * =================================================
			 * Device Scale Loop
			 * =================================================
			 */

			for(int j =
					SS.getMinNumOfMobileDev();
					j <= SS.getMaxNumOfMobileDev();
					j += SS.getMobileDevCounterSize())
			{
				/*
				 * =============================================
				 * Scenario Loop
				 * =============================================
				 */

				for(int k = 0;
						k < SS.getSimulationScenarios().length;
						k++)
				{
					/*
					 * =========================================
					 * Policy Loop
					 * =========================================
					 */

					for(int i = 0;
							i < SS.getOrchestratorPolicies().length;
							i++)
					{
						String simScenario =
							SS.getSimulationScenarios()[k];

						String orchestratorPolicy =
							SS.getOrchestratorPolicies()[i];

						Date scenarioStartDate =
							Calendar.getInstance().getTime();

						SimLogger.printLine(
							"Scenario started at "
							+ df.format(scenarioStartDate));

						SimLogger.printLine(
							"Scenario: "
							+ simScenario
							+ " - Policy: "
							+ orchestratorPolicy
							+ " - #devices: "
							+ j);

						/*
						 * =====================================
						 * Initialize SimLogger
						 * =====================================
						 */

						SimLogger.getInstance()
							.simStarted(
								outputFolder,
								"SIMRESULT_"
								+ simScenario
								+ "_"
								+ orchestratorPolicy
								+ "_"
								+ j
								+ "DEVICES");

						/*
						 * =====================================
						 * Initialize HESLogger
						 * =====================================
						 */

						String hesFilePrefix =
							    "SIMRESULT_"
							    + simScenario
							    + "_"
							    + orchestratorPolicy
							    + "_"
							    + j
							    + "DEVICES";

							HESLogger.getInstance().initialize(
							    outputFolder,
							    hesFilePrefix
							);

						try {

							/*
							 * =================================
							 * CloudSim Init
							 * =================================
							 */

							CloudSim.init(
								2,
								Calendar.getInstance(),
								false,
								0.01);

							/*
							 * =================================
							 * Create Scenario Factory
							 * =================================
							 */

							ScenarioFactory sampleFactory =
								new SampleScenarioFactory(
									j,
									SS.getSimulationTime(),
									orchestratorPolicy,
									simScenario);

							/*
							 * =================================
							 * Create Simulation Manager
							 * =================================
							 */

							SimManager manager =
								new SimManager(
									sampleFactory,
									j,
									simScenario,
									orchestratorPolicy);

							/*
							 * =================================
							 * Start Simulation
							 * =================================
							 */
							long startMs = System.currentTimeMillis();
							
                           
							System.out.println(
							    "[START] "
							    + orchestratorPolicy
							    + " devices="
							    + j);
							 System.out.println("Iteraction: "+iterationNumber);
							manager.startSimulation();
							long endMs = System.currentTimeMillis();

							System.out.println(
							    "[END] "
							    + orchestratorPolicy
							    + " devices="
							    + j
							    + " elapsed="
							    + ((endMs - startMs)/1000.0)
							    + " sec");

							/*
							 * =================================
							 * HES Summary
							 * =================================
							 */

							HESLogger.getInstance()
								.printSummary();

							HESLogger.getInstance()
								.close();
						}
						catch(Exception e){

							SimLogger.printLine(
								"The simulation has been "
								+ "terminated due to an "
								+ "unexpected error");

							e.printStackTrace();

							System.exit(0);
						}

						Date scenarioEndDate =
							Calendar.getInstance().getTime();

						SimLogger.printLine(
							"Scenario finished at "
							+ df.format(scenarioEndDate)
							+ ". It took "
							+ SimUtils.getTimeDifference(
									scenarioStartDate,
									scenarioEndDate));

						SimLogger.printLine(
							"----------------------------------------------------------------------");
					}
				}
			}
		}

		/*
		 * =====================================================
		 * Simulation End
		 * =====================================================
		 */

		Date simulationEndDate =
			Calendar.getInstance().getTime();

		SimLogger.printLine(
			"Simulation finished at "
			+ df.format(simulationEndDate));
	}
}