package edu.boun.edgecloudsim.applications.pdmjournal;

import java.util.List;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.network.CustomNetworkModel;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.EdgeTask;
import edu.boun.edgecloudsim.utils.SimLogger;
import java.util.Map;
import java.util.HashMap;

/**
 * Orquestrador Customizado para Fluxo Sequencial:
 * 1. Se o módulo for EDGE_PRE_PROCESSOR -> Envia para o Intel i7 (Edge).
 * 2. Se o módulo for CLOUD_FINAL_PROCESSOR -> Envia para a Nuvem (Cloud).
 * O fluxo Mobile -> Edge -> Cloud é garantido pelo mapeamento de módulos.
 */
public class SampleEdgeOrchestrator extends EdgeOrchestrator {
	private int numberOfHost;
	private int dcCounter = 0;
    //private int rrCounter = 0;
    private Map<Integer,Integer> rrMap =
    	    new HashMap<Integer,Integer>();
    
	public SampleEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost = SimSettings.getInstance().getNumOfEdgeHosts();
	}

	
	
	
	
	@Override
	public int getDeviceToOffload(Task task) {

		int result = 0;

		/*
		 * =========================================================
		 * Custom network model
		 * =========================================================
		 */

		CustomNetworkModel networkModel =
			(CustomNetworkModel)
			SimManager.getInstance().getNetworkModel();

		/*
		 * =========================================================
		 * HES
		 * =========================================================
		 *
		 * Hierarchical Edge-Cloud Scheduling
		 *
		 * Stage 0:
		 *     MOBILE -> EDGE
		 *
		 * Stage 1:
		 *     EDGE -> CLOUD
		 *
		 */

		if(policy.equals("HES")){

			EdgeTask edgeTask = (EdgeTask) task;

			/*
			 * -----------------------------------------------------
			 * Connectivity verification
			 * -----------------------------------------------------
			 */

			if(!networkModel.isConnected(
					task.getMobileDeviceId()))
			{
				/*
				 * Device offline
				 * task remains locally stored
				 */

				return -1;
			}

			/*
			 * -----------------------------------------------------
			 * Stage 0 -> EDGE
			 * -----------------------------------------------------
			 */

			if(edgeTask.getStage() == 0){

			    edgeTask.setStage(1);

			    int numDC =
			        SimManager.getInstance()
			            .getEdgeServerManager()
			            .getDatacenterList()
			            .size();

			    result = dcCounter % numDC;

			    dcCounter++;
			}

			/*
			 * -----------------------------------------------------
			 * Stage 1 -> CLOUD
			 * -----------------------------------------------------
			 */

			else if(edgeTask.getStage() == 1){

				edgeTask.setStage(2);

				result =
					SimSettings.CLOUD_DATACENTER_ID;
			}

			/*
			 * -----------------------------------------------------
			 * Finished
			 * -----------------------------------------------------
			 */

			else{

				result =
					SimSettings.CLOUD_DATACENTER_ID;
			}
		}

		else if(policy.equals("ONLY_MOBILE")){

			result =
				SimSettings.MOBILE_DATACENTER_ID;
		}

		/*
		 * =========================================================
		 * CLOUD_FIRST
		 * =========================================================
		 */

		else if(policy.equals("CLOUD_FIRST")){

			if(!networkModel.isConnected(
					task.getMobileDeviceId()))
			{
				SimLogger.printLine(
					"Task failed due connectivity loss");

				return -1;
			}

			result =
				SimSettings.CLOUD_DATACENTER_ID;
		}

		/*
		 * =========================================================
		 * OFFLINE_DIRECT
		 * =========================================================
		 *
		 * Store locally while offline
		 * Synchronize directly to cloud
		 *
		 */

		else if(policy.equals("OFFLINE_DIRECT")){

			if(!networkModel.isConnected(
					task.getMobileDeviceId()))
			{
				/*
				 * Offline:
				 * keep task local
				 */

				return -1;
			}

			result =
				SimSettings.CLOUD_DATACENTER_ID;
		}

		/*
		 * =========================================================
		 * EDGE_ONLY
		 * =========================================================
		 */

		else if(policy.equals("EDGE_ONLY")){

		    if(!networkModel.isConnected(
		            task.getMobileDeviceId()))
		    {
		        return -1;
		    }

		    result =
		        task.getMobileDeviceId()
		        %
		        SimManager.getInstance()
		            .getEdgeServerManager()
		            .getDatacenterList()
		            .size();
		}

		/*
		 * =========================================================
		 * UNKNOWN POLICY
		 * =========================================================
		 */

		else{

			SimLogger.printLine(
				"Unknown edge orchestrator policy!");

			System.exit(0);
		}
		int stage = -1;

		if(task instanceof EdgeTask)
		{
		    stage = ((EdgeTask)task).getStage();
		}

		return result;
	}
	
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
	    Vm selectedVM = null;

	    
	    try {
	        if(deviceId == SimSettings.CLOUD_DATACENTER_ID){
	            // Cloud possui apenas um host (host 0)
	            List<? extends Vm> vms =
	                SimManager.getInstance()
	                          .getCloudServerManager()
	                          .getVmList(0);

	            if(vms == null || vms.isEmpty())
	                return null;

	            selectedVM = vms.get(0);
	            // Na Nuvem, o retorno geralmente já é List<Vm>
	            //selectedVM = SimManager.getInstance().getCloudServerManager().getVmList(deviceId).get(0);
	        }
	        else {
	            // PROTEÇÃO: Garante que o ID do servidor de borda esteja dentro dos limites
	            int safeDeviceId = deviceId % SimManager.getInstance().getEdgeServerManager().getDatacenterList().size();
	            
	            // AJUSTE AQUI: Usando o curinga "? extends Vm" para aceitar List<EdgeVM>
	            List<? extends Vm> vms = SimManager.getInstance().getEdgeServerManager().getVmList(safeDeviceId);
	            
	            if(vms != null && !vms.isEmpty()){

	            	int counter =
	            		    rrMap.getOrDefault(
	            		        safeDeviceId,
	            		        0);

	            		selectedVM =
	            		    vms.get(
	            		        counter % vms.size());

	            		rrMap.put(
	            		    safeDeviceId,
	            		    counter + 1);

	            		int stage = -1;

	            		if(task instanceof EdgeTask)
	            		{
	            		    stage = ((EdgeTask)task).getStage();
	            		}

	            		/*ystem.out.println(
	            		    "[VM_SELECT] Task=" +
	            		    task.getCloudletId() +
	            		    " Stage=" +
	            		    stage +
	            		    " DC=" +
	            		    safeDeviceId +
	            		    " VM=" +
	            		    selectedVM.getId());*/
	            }
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	        return null;
	    }
	    
	    return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {}

	@Override
	public void shutdownEntity() {}

	@Override
	public void startEntity() {
		SimLogger.printLine(
			    "[POLICY] " + policy);
	}
}