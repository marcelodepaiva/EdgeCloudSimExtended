package edu.boun.edgecloudsim.edge_client;

/*
 * =========================================================
 * DefaultMobileDeviceManager
 * =========================================================
 *
 * HES-enabled Mobile Device Manager
 *
 * Supports:
 *
 * - HES orchestration
 * - intermittent connectivity
 * - offline-first execution
 * - EDGE -> CLOUD continuation
 * - delayed synchronization
 * - reconnect handling
 * - custom HES logging
 *
 */

import java.util.LinkedList;
import java.util.Queue;

import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.network.CustomNetworkModel;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.EdgeTask;
import edu.boun.edgecloudsim.utils.HESLogger;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.TaskProperty;

public class DefaultMobileDeviceManager
	extends MobileDeviceManager {

	private static long completedCounter = 0;
	
	/*
	 * =========================================================
	 * Custom Events
	 * =========================================================
	 */

	private static final int BASE = 100000;

	private static final int REQUEST_RECEIVED_BY_CLOUD =
		BASE + 1;

	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE =
		BASE + 2;

	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE =
		BASE + 3;
	private static final int OFFLINE_SYNC_EVENT =
		    BASE + 4;
	/*
	 * =========================================================
	 * Variables
	 * =========================================================
	 */

	private int taskIdCounter = 0;

	private Queue<EdgeTask> offlineTaskQueue;

	/*
	 * Return the SimLogger ID for the logical task. HES creates a new
	 * Cloudlet for the cloud continuation, but that continuation must
	 * not create a second logical-task record in SimLogger.
	 */
	private boolean isTerminalTask(EdgeTask task) {
		return task.getStage() == 0
			|| task.getStage() == 2
			|| (task.getStage() == 1
				&& task.getAssociatedDatacenterId() != SimSettings.CLOUD_DATACENTER_ID
				&& !SimManager.getInstance().getOrchestratorPolicy().equals("HES"));
	}

	private int getLoggerTaskId(EdgeTask task) {
		if (task.getStage() == 2 && task.getLogicalTaskId() >= 0) {
			return task.getLogicalTaskId();
		}
		return task.getCloudletId();
	}

	/*
	 * =========================================================
	 * Constructor
	 * =========================================================
	 */

	public DefaultMobileDeviceManager()
			throws Exception {

		super();
	}

	/*
	 * =========================================================
	 * Initialization
	 * =========================================================
	 */

	@Override
	public void initialize() {

		offlineTaskQueue =
			new LinkedList<EdgeTask>();
	}

	
	@Override
	public void startEntity()
	{
	    super.startEntity();

	    schedule(
	    	    getId(),
	    	    SimSettings.getInstance()
	    	        .getOfflineQueueCheckInterval(),
	    	    OFFLINE_SYNC_EVENT);
	    /*SimLogger.printLine(
	    	    "[SYNC] Scheduler started");*/
	}
	/*
	 * =========================================================
	 * Event Processing
	 * =========================================================
	 */

	@Override
	public void processEvent(SimEvent ev)
	{
		switch(ev.getTag()) {

		/*
		 * =====================================================
		 * Custom HES events
		 * =====================================================
		 */

		case REQUEST_RECEIVED_BY_CLOUD:

		case REQUEST_RECEIVED_BY_EDGE_DEVICE:

		    processOtherEvent(ev);

		    break;

		case OFFLINE_SYNC_EVENT:

		    synchronizeOfflineTasks();

		    if(CloudSim.clock() <
		        SimSettings.getInstance()
		            .getSimulationTime())
		    {
		    	schedule(
		    		    getId(),
		    		    SimSettings.getInstance()
		    		        .getOfflineQueueCheckInterval(),
		    		    OFFLINE_SYNC_EVENT);
		    }

		    break;
		    
		case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:

			processOtherEvent(ev);

			break;

		/*
		 * =====================================================
		 * Default CloudSim events
		 * =====================================================
		 */

		default:

			super.processEvent(ev);

			break;
		}
	}

	/*
	 * =========================================================
	 * CPU Utilization
	 * =========================================================
	 */

	@Override
	public UtilizationModel
		getCpuUtilizationModel() {

		return new CpuUtilizationModel_Custom();
	}

	/*
	 * =========================================================
	 * Not Used
	 * =========================================================
	 */

	protected void submitCloudlets() {

	}

	/*
	 * =========================================================
	 * Cloudlet Return
	 * =========================================================
	 */

	@Override
	protected void processCloudletReturn(
			SimEvent ev)
	{
		completedCounter++;

		NetworkModel networkModel =
			SimManager.getInstance()
			.getNetworkModel();

		EdgeTask task =
			(EdgeTask) ev.getData();

		SimLogger.getInstance().taskExecuted(
			getLoggerTaskId(task));

		/*
		 * Direct CLOUD execution:
		 * stage 0 is used by CLOUD_FIRST/OFFLINE_DIRECT.
		 */
		if(task.getStage() == 0)
		{
			task.setCloudStartTime(
				task.getExecStartTime());

			task.setCloudCompletionTime(
				CloudSim.clock());

			HESLogger.getInstance()
				.taskCompletedAtCloud(task);

		}

		/*
		 * EDGE completion.
		 */
		if(task.getStage() == 1 &&
			task.getAssociatedDatacenterId()
				!= SimSettings.CLOUD_DATACENTER_ID)
		{
			task.setEdgeStartTime(
				task.getExecStartTime());

			task.setEdgeCompletionTime(
				CloudSim.clock());

			HESLogger.getInstance()
				.taskCompletedAtEdge(task);

			/*
			 * Only HES continues from EDGE to CLOUD.
			 */
			if(SimManager.getInstance()

			        .getOrchestratorPolicy()

			        .equals("HES"))

			{

			    /*

			     * The Cloudlet that completed at EDGE cannot be submitted

			     * again to CloudSim. Create a fresh Cloudlet execution

			     * object for the CLOUD continuation while preserving the

			     * logical task identity and lifecycle timestamps.

			     */

			    EdgeTask cloudTask =

			        createCloudContinuationTask(task);


			    submitTaskToCloud(cloudTask);


			    return;

			}

			/*
			 * EDGE_ONLY terminates at EDGE.
			 */
		}

		/*
		 * CLOUD completion after EDGE -> CLOUD continuation.
		 */
		if(task.getStage() == 2)
		{
			task.setCloudStartTime(
				task.getExecStartTime());

			task.setCloudCompletionTime(
				CloudSim.clock());

			HESLogger.getInstance()
				.taskCompletedAtCloud(task);

		}

		/*
		 * Download result.
		 */
		double delay;
		boolean responseScheduled = false;

		if(task.getAssociatedDatacenterId()
				== SimSettings.CLOUD_DATACENTER_ID)
		{
			delay =
				networkModel.getDownloadDelay(
					SimSettings.CLOUD_DATACENTER_ID,
					task.getMobileDeviceId(),
					task);

			if(delay > 0){
				SimLogger.getInstance().setDownloadDelay(
					getLoggerTaskId(task), delay,
					NETWORK_DELAY_TYPES.WAN_DELAY);

				networkModel.downloadStarted(
					task.getSubmittedLocation(),
					SimSettings.CLOUD_DATACENTER_ID);

				schedule(
					getId(),
					delay,
					RESPONSE_RECEIVED_BY_MOBILE_DEVICE,
					task);
				responseScheduled = true;
			}
		}
		else
		{
			delay =
				networkModel.getDownloadDelay(
					task.getAssociatedHostId(),
					task.getMobileDeviceId(),
					task);

			if(delay > 0){
				SimLogger.getInstance().setDownloadDelay(
					getLoggerTaskId(task), delay,
					NETWORK_DELAY_TYPES.WLAN_DELAY);

				networkModel.downloadStarted(
					task.getSubmittedLocation(),
					SimSettings.GENERIC_EDGE_DEVICE_ID);

				schedule(
					getId(),
					delay,
					RESPONSE_RECEIVED_BY_MOBILE_DEVICE,
					task);
				responseScheduled = true;
			}
		}

		/*
		 * Aggregate the task only after upload, processing and download
		 * delays have been registered. HES stage 1 returns above because
		 * its logical task continues in a separate Cloudlet at the cloud.
		 */
		if (!responseScheduled && isTerminalTask(task)) {
			SimLogger.getInstance().taskEnded(
				getLoggerTaskId(task), CloudSim.clock());
		}
	}

	/*
	 * =========================================================
	 * Other Events
	 * =========================================================
	 */

	protected void processOtherEvent(
			SimEvent ev)
	{
		if(ev == null){

			System.exit(1);

			return;
		}

		NetworkModel networkModel =
			SimManager.getInstance()
			.getNetworkModel();

		switch(ev.getTag()) {

		/*
		 * =====================================================
		 * REQUEST -> CLOUD
		 * =====================================================
		 */

		case REQUEST_RECEIVED_BY_CLOUD:
		{
			EdgeTask task =
				(EdgeTask) ev.getData();

			networkModel.uploadFinished(
				task.getSubmittedLocation(),
				SimSettings.CLOUD_DATACENTER_ID);

			/*
			 * Cloud arrival is distinct from actual VM execution start.
			 * The actual Cloud processing start is obtained from the
			 * Cloudlet execution timestamp when the task returns.
			 */
			task.setCloudArrivalTime(
				CloudSim.clock());

			submitTaskToVm(
				task,
				0,
				SimSettings.CLOUD_DATACENTER_ID);

			break;
		}

		/*
		 * =====================================================
		 * REQUEST -> EDGE
		 * =====================================================
		 */

		case REQUEST_RECEIVED_BY_EDGE_DEVICE:
		{
		    EdgeTask task =
		        (EdgeTask) ev.getData();

		    task.setStage(1);

		    networkModel.uploadFinished(
		        task.getSubmittedLocation(),
		        task.getAssociatedDatacenterId());

		    /*
		     * The actual EDGE execution start is recorded when
		     * the Cloudlet returns, using getExecStartTime().
		     * Do not set edgeStartTime at datacenter arrival,
		     * because that would mix arrival/queueing time with
		     * actual VM execution time.
		     */

		    submitTaskToVm(
		        task,
		        0,
		        task.getAssociatedDatacenterId());

		    break;
		}

		/*
		 * =====================================================
		 * RESPONSE -> MOBILE
		 * =====================================================
		 */

		case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
		{
			EdgeTask task = (EdgeTask) ev.getData();

			// End-to-end service time includes the result download delay.
			if (isTerminalTask(task)) {
				SimLogger.getInstance().taskEnded(
					getLoggerTaskId(task), CloudSim.clock());
			}
			break;
		}

		default:
			break;
		}
	}

	/*
	 * =========================================================
	 * Initial Task Submission
	 * =========================================================
	 */

	@Override
	public void submitTask(
	        TaskProperty edgeTask)
	{
	    EdgeTask task =
	        createTask(edgeTask);

	    HESLogger.getInstance()
	        .taskSubmitted(task);

	    resubmitTask(task);
	}

	/*
	 * =========================================================
	 * Resubmit Existing Task
	 * =========================================================
	 */

	private void resubmitTask(
			EdgeTask task)
	{
		CustomNetworkModel networkModel =
			(CustomNetworkModel)
			SimManager.getInstance()
			.getNetworkModel();

		Location currentLocation =
			SimManager.getInstance()
			.getMobilityModel()
			.getLocation(
				task.getMobileDeviceId(),
				CloudSim.clock());

		task.setSubmittedLocation(
			currentLocation);

		/*
		 * =====================================================
		 * Connectivity verification
		 * =====================================================
		 */

		if(!networkModel.isConnected(
				task.getMobileDeviceId()))
		{
			/*
			 * Cloud-first does not buffer tasks that cannot be
			 * submitted during a mobile-device outage. The other
			 * policies preserve the task for their offline workflow.
			 */
			String policy = SimManager.getInstance()
				.getOrchestratorPolicy();

			if ("CLOUD_FIRST".equals(policy)) {
				HESLogger.getInstance().taskDiscarded(task);
				SimLogger.getInstance().rejectedDueToWlanCoverage(
					getLoggerTaskId(task),
					CloudSim.clock(),
					SimSettings.VM_TYPES.CLOUD_VM.ordinal());
			} else {
				storeLocally(task);
			}
			return;
		}

		/****SimLogger.printLine(
			"[SUBMIT] Task "
			+ task.getCloudletId()
			+ " Stage "
			+ task.getStage());
			*/

		int nextHopId =
			SimManager.getInstance()
			.getEdgeOrchestrator()
			.getDeviceToOffload(task);

		double delay;

		/*
		 * =====================================================
		 * CLOUD
		 * =====================================================
		 */

		if(nextHopId ==
				SimSettings.CLOUD_DATACENTER_ID)
		{
			delay =
				networkModel.getUploadDelay(
					task.getMobileDeviceId(),
					nextHopId,
					task);
			if(delay > 0){
				SimLogger.getInstance().setUploadDelay(
					getLoggerTaskId(task), delay,
					NETWORK_DELAY_TYPES.WAN_DELAY);

				networkModel.uploadStarted(
					currentLocation,
					nextHopId);

				schedule(
					getId(),
					delay,
					REQUEST_RECEIVED_BY_CLOUD,
					task);
			}
		}

		/*
		 * =====================================================
		 * EDGE
		 * =====================================================
		 */

		else if(nextHopId >= 0 &&
		        nextHopId <
		        SimManager.getInstance()
		            .getEdgeServerManager()
		            .getDatacenterList()
		            .size())
		{
		    delay =
		        networkModel.getUploadDelay(
		            task.getMobileDeviceId(),
		            nextHopId,
		            task);
		    /*ystem.out.println(
		            "[UPLOAD_DELAY] Task="
		            + task.getCloudletId()
		            + " Delay="
		            + delay
		            + " NextHop="
		            + nextHopId);
*/
		    if(delay > 0){
		        SimLogger.getInstance().setUploadDelay(
		            getLoggerTaskId(task), delay,
		            NETWORK_DELAY_TYPES.WLAN_DELAY);

		        networkModel.uploadStarted(
		            currentLocation,
		            nextHopId);

		        /*
		         * Guarda o Edge Datacenter escolhido
		         */
		        task.setAssociatedDatacenterId(
		            nextHopId);

		        schedule(
		            getId(),
		            delay,
		            REQUEST_RECEIVED_BY_EDGE_DEVICE,
		            task);
		    }
		}
	}

	/*
	 * =========================================================
	 * EDGE -> CLOUD continuation
	 * =========================================================
	 */

	@Override
	public void submitTaskToCloud(
	        EdgeTask task)
	{
	    NetworkModel networkModel =
	        SimManager.getInstance()
	            .getNetworkModel();

	    double delay =
	        networkModel.getUploadDelay(
	            task.getAssociatedDatacenterId(),
	            SimSettings.CLOUD_DATACENTER_ID,
	            task);

	    if(delay <= 0)
	    {
	        delay = 0.001;
	    }

	    task.setStage(2);

	    SimLogger.getInstance().setUploadDelay(
	        getLoggerTaskId(task), delay,
	        NETWORK_DELAY_TYPES.WAN_DELAY);

	    schedule(
	        getId(),
	        delay,
	        REQUEST_RECEIVED_BY_CLOUD,
	        task);
	}

	/*
	 * =========================================================
	 * Offline Queue
	 * =========================================================
	 */

	@Override
	public void storeLocally(
			EdgeTask task)
	{
		offlineTaskQueue.add(task);

		HESLogger.getInstance()
			.taskStoredOffline(task);

		/*SimLogger.printLine(
			"[OFFLINE] Task "
			+ task.getCloudletId()
			+ " stored locally");*/
	}

	/*
	 * =========================================================
	 * Synchronization
	 * =========================================================
	 */

	@Override
	public void synchronizeOfflineTasks() {
		
		if(offlineTaskQueue.isEmpty())
	    {
	        return;
	    }

		CustomNetworkModel networkModel =
			(CustomNetworkModel)
			SimManager.getInstance()
			.getNetworkModel();

		/*
		 * Process a bounded snapshot of the queue. Tasks that remain
		 * offline are appended after tasks that have not yet been
		 * examined, preventing the same disconnected tasks at the
		 * head of the queue from being retried before newer entries.
		 */
		final int MAX_SYNC_PER_CYCLE = 1000;
		int batchSize = Math.min(
			MAX_SYNC_PER_CYCLE, offlineTaskQueue.size());

		Queue<EdgeTask> stillOffline = new LinkedList<EdgeTask>();

		for (int processed = 0; processed < batchSize; processed++) {
			EdgeTask task = offlineTaskQueue.poll();
			if (task == null) {
				break;
			}

			if (networkModel.isConnected(task.getMobileDeviceId())) {
				task.setSyncStartTime(CloudSim.clock());
				HESLogger.getInstance().taskSynchronized(task);
				resubmitTask(task);
			} else {
				stillOffline.add(task);
			}
		}

		/*
		 * Keep unexamined tasks ahead of the just-tested offline tasks.
		 * This makes the bounded scan fair across queue entries.
		 */
		while (!stillOffline.isEmpty()) {
			offlineTaskQueue.add(stillOffline.poll());
		}
	}

	/*
	 * =========================================================
	 * Metrics
	 * =========================================================
	 */

	@Override
	public void updateTaskMetrics(
			EdgeTask task)
	{
		/*
		 * HES metrics are recorded from lifecycle timestamps.
		 * This method intentionally does not redefine the
		 * synchronization interval.
		 */
		SimLogger.printLine(
			"[METRICS] Task "
			+ task.getCloudletId()
			+ " E2E="
			+ HESLogger.getInstance()
				.getEndToEndLatency(task));
	}

	/*
	 * =========================================================
	 * VM Submission
	 * =========================================================
	 */

	private void submitTaskToVm(
			EdgeTask task,
			double delay,
			int datacenterId)
	{
		Vm selectedVM =
			SimManager.getInstance()
			.getEdgeOrchestrator()
			.getVmToOffload(
				task,
				datacenterId);

		if(selectedVM != null){

			task.setAssociatedVmId(
				selectedVM.getId());
			
			task.setVmId(selectedVM.getId());
			
			task.setAssociatedHostId(
				selectedVM.getHost().getId());

			task.setAssociatedDatacenterId(
				datacenterId);
			/*System.out.println();

			System.out.println(
			    "========== VM ASSIGNMENT ==========");

			System.out.println(
			    "Task = " + task.getCloudletId());

			System.out.println(
			    "Datacenter = " + datacenterId);

			System.out.println(
			    "Host = "
			    + selectedVM.getHost().getId());

			System.out.println(
			    "VM = "
			    + selectedVM.getId());
			*/
			int vmType;

			if(datacenterId ==
			        SimSettings.CLOUD_DATACENTER_ID)
			{
			    vmType =
			        SimSettings.VM_TYPES.CLOUD_VM.ordinal();
			}
			else
			{
			    vmType =
			        SimSettings.VM_TYPES.EDGE_VM.ordinal();
			}

			SimLogger.getInstance().taskAssigned(
			    getLoggerTaskId(task),
			    task.getAssociatedDatacenterId(),
			    task.getAssociatedHostId(),
			    task.getAssociatedVmId(),
			    vmType);
			
			getCloudletList().add(task);

			bindCloudletToVm(
				task.getCloudletId(),
				selectedVM.getId());
			Integer dc =
				    getVmsToDatacentersMap().get(selectedVM.getId());

			/*schedule(
				getVmsToDatacentersMap()
					.get(task.getVmId()),
				delay,
				CloudSimTags.CLOUDLET_SUBMIT,
				task);*/
				schedule(
					    dc,
					    delay,
					    CloudSimTags.CLOUDLET_SUBMIT,
					    task);
		}
		else{

			SimLogger.printLine(
				"[ERROR] No VM available for task "
				+ task.getCloudletId());
		}
	}

	/*
	 * =========================================================
	 * Task Creation
	 * =========================================================
	 */

	private EdgeTask createTask(
			TaskProperty edgeTask)
	{
		UtilizationModel utilizationModel =
			new UtilizationModelFull();

		UtilizationModel utilizationModelCPU =
			getCpuUtilizationModel();

		EdgeTask task =
			new EdgeTask(
				edgeTask.getMobileDeviceId(),
				++taskIdCounter,
				edgeTask.getLength(),
				edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(),
				edgeTask.getOutputFileSize(),
				utilizationModelCPU,
				utilizationModel,
				utilizationModel);

		task.setUserId(this.getId());

		task.setTaskType(
			edgeTask.getTaskType());

		task.setStage(0);
		task.setLogicalTaskId(task.getCloudletId());

		task.setCreationTime(
			CloudSim.clock());
		SimLogger.getInstance().addLog(
			    task.getMobileDeviceId(),
			    task.getCloudletId(),
			    task.getTaskType(),
			    (int)task.getCloudletLength(),
			    (int)task.getCloudletFileSize(),
			    (int)task.getCloudletOutputSize());

			SimLogger.getInstance().taskStarted(
			    task.getCloudletId(),
			    CloudSim.clock());

		task.setOfflineStartTime(-1.0);
		task.setSyncStartTime(-1.0);

		task.setEdgeStartTime(-1.0);
		task.setEdgeCompletionTime(-1.0);

		task.setCloudArrivalTime(-1.0);
		task.setCloudStartTime(-1.0);
		task.setCloudCompletionTime(-1.0);

		if(utilizationModelCPU
				instanceof CpuUtilizationModel_Custom)
		{
			((CpuUtilizationModel_Custom)
				utilizationModelCPU)
				.setTask(task);
		}

		return task;
	}
    /**
     * Creates a fresh CloudSim Cloudlet for the CLOUD continuation.
     */
    private EdgeTask createCloudContinuationTask(
            EdgeTask edgeTask)
    {
        UtilizationModel utilizationModel =
            new UtilizationModelFull();

        UtilizationModel utilizationModelCPU =
            getCpuUtilizationModel();

        EdgeTask cloudTask =
            new EdgeTask(
                edgeTask.getMobileDeviceId(),
                ++taskIdCounter,
                edgeTask.getCloudletLength(),
                edgeTask.getNumberOfPes(),
                edgeTask.getCloudletFileSize(),
                edgeTask.getCloudletOutputSize(),
                utilizationModelCPU,
                utilizationModel,
                utilizationModel);

        cloudTask.setUserId(this.getId());
        cloudTask.setTaskType(edgeTask.getTaskType());
        cloudTask.setStage(2);

        cloudTask.setLogicalTaskId(
            edgeTask.getLogicalTaskId());

        cloudTask.setCreationTime(
            edgeTask.getCreationTime());

        cloudTask.setOfflineStartTime(
            edgeTask.getOfflineStartTime());

        cloudTask.setSyncStartTime(
            edgeTask.getSyncStartTime());

        cloudTask.setEdgeStartTime(
            edgeTask.getEdgeStartTime());

        cloudTask.setEdgeCompletionTime(
            edgeTask.getEdgeCompletionTime());

        cloudTask.setCloudArrivalTime(-1.0);
        cloudTask.setCloudStartTime(-1.0);
        cloudTask.setCloudCompletionTime(-1.0);

        cloudTask.setSubmittedLocation(
            edgeTask.getSubmittedLocation());

        cloudTask.setAssociatedDatacenterId(
            edgeTask.getAssociatedDatacenterId());

        if(utilizationModelCPU
                instanceof CpuUtilizationModel_Custom)
        {
            ((CpuUtilizationModel_Custom)
                utilizationModelCPU)
                .setTask(cloudTask);
        }

        return cloudTask;
    }

}