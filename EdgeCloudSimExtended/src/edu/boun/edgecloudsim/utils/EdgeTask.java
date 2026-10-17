package edu.boun.edgecloudsim.utils;

import org.cloudbus.cloudsim.UtilizationModel;

import edu.boun.edgecloudsim.edge_client.Task;

/*
 * =========================================================
 * EdgeTask
 * =========================================================
 *
 * Extended task model for HES experiments.
 *
 * Stores timestamps for the complete task lifecycle:
 * creation -> offline waiting -> EDGE -> synchronization ->
 * EDGE-to-CLOUD transfer -> CLOUD completion.
 *
 */
public class EdgeTask extends Task {

    /*
     * Execution stage:
     * 0 = initial/direct submission
     * 1 = EDGE execution
     * 2 = CLOUD continuation
     */
    private int stage;

    /*
     * Logical task identifier. A HES task can use separate CloudSim
     * Cloudlets for EDGE and CLOUD execution while retaining one
     * logical identifier.
     */
    private int logicalTaskId;

    /* Task lifecycle timestamps */
    private double creationTime;

    private double offlineStartTime;
    private double syncStartTime;

    private double edgeStartTime;
    private double edgeCompletionTime;

    /* Time at which the task reaches the Cloud datacenter. */
    private double cloudArrivalTime;

    private double cloudStartTime;
    private double cloudCompletionTime;

    /*
     * Constructor
     */
    public EdgeTask(
            int mobileDeviceId,
            int cloudletId,
            long cloudletLength,
            int pesNumber,
            long cloudletFileSize,
            long cloudletOutputSize,
            UtilizationModel utilizationModelCpu,
            UtilizationModel utilizationModelRam,
            UtilizationModel utilizationModelBw) {

        super(
            mobileDeviceId,
            cloudletId,
            cloudletLength,
            pesNumber,
            cloudletFileSize,
            cloudletOutputSize,
            utilizationModelCpu,
            utilizationModelRam,
            utilizationModelBw
        );

        this.stage = 0;
        this.logicalTaskId = cloudletId;

        this.creationTime = -1.0;
        this.offlineStartTime = -1.0;
        this.syncStartTime = -1.0;

        this.edgeStartTime = -1.0;
        this.edgeCompletionTime = -1.0;

        this.cloudArrivalTime = -1.0;

        this.cloudStartTime = -1.0;
        this.cloudCompletionTime = -1.0;
    }

    /* Stage */
    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    /* Logical task identifier */
    public int getLogicalTaskId() {
        return logicalTaskId;
    }

    public void setLogicalTaskId(int logicalTaskId) {
        this.logicalTaskId = logicalTaskId;
    }

    /* Creation time */
    public double getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(double creationTime) {
        this.creationTime = creationTime;
    }

    /* Offline waiting */
    public double getOfflineStartTime() {
        return offlineStartTime;
    }

    public void setOfflineStartTime(double offlineStartTime) {
        this.offlineStartTime = offlineStartTime;
    }

    public double getSyncStartTime() {
        return syncStartTime;
    }

    public void setSyncStartTime(double syncStartTime) {
        this.syncStartTime = syncStartTime;
    }

    /*
     * Backward-compatible synchronization getter.
     * It returns the actual offline waiting interval when available.
     */
    public double getSyncTime() {
        if (offlineStartTime >= 0 && syncStartTime >= 0) {
            return syncStartTime - offlineStartTime;
        }
        return 0.0;
    }

    public void setSyncTime(double syncTime) {
        /*
         * Kept for source compatibility with older code.
         * New code should use offlineStartTime/syncStartTime.
         */
        if (offlineStartTime >= 0) {
            syncStartTime = offlineStartTime + syncTime;
        }
    }

    /* EDGE */
    public double getEdgeStartTime() {
        return edgeStartTime;
    }

    public void setEdgeStartTime(double edgeStartTime) {
        this.edgeStartTime = edgeStartTime;
    }

    public double getEdgeCompletionTime() {
        return edgeCompletionTime;
    }

    public void setEdgeCompletionTime(double edgeCompletionTime) {
        this.edgeCompletionTime = edgeCompletionTime;
    }

    /* CLOUD ARRIVAL */
    public double getCloudArrivalTime() {
        return cloudArrivalTime;
    }

    public void setCloudArrivalTime(double cloudArrivalTime) {
        this.cloudArrivalTime = cloudArrivalTime;
    }

    /* CLOUD */
    public double getCloudStartTime() {
        return cloudStartTime;
    }

    public void setCloudStartTime(double cloudStartTime) {
        this.cloudStartTime = cloudStartTime;
    }

    public double getCloudCompletionTime() {
        return cloudCompletionTime;
    }

    public void setCloudCompletionTime(double cloudCompletionTime) {
        this.cloudCompletionTime = cloudCompletionTime;
    }
}