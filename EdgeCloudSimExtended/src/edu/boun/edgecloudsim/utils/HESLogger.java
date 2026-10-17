package edu.boun.edgecloudsim.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.cloudbus.cloudsim.core.CloudSim;

/*
 * =========================================================
 * HESLogger
 * =========================================================
 *
 * HES-specific metrics logger.
 *
 * Metrics are calculated from timestamps stored in EdgeTask.
 * This avoids passing pre-calculated timing values between
 * simulation components.
 *
 */
public class HESLogger {

    private static HESLogger instance = null;

    public static HESLogger getInstance() {
        if (instance == null) {
            instance = new HESLogger();
        }
        return instance;
    }

    /* Counters */
    private int totalTasks;
    private int offlineTasks;
    private int synchronizedTasks;
    private int edgeCompletedTasks;
    private int cloudCompletedTasks;
    private int failedTasks;
    private int discardedTasks;
    private int totalCompletedTasks;

    /* Aggregated metrics */
    private double totalLatency;
    private double totalEdgeTime;
    private double totalCloudTime;
    private double totalSyncDelay;
    private double totalEdgeToCloudDelay;

    /* Output */
    private BufferedWriter csvWriter;
    private String outputFolder;
    private String outputFile;

    private HESLogger() {
        // Initialization is explicitly performed by MainApp.
    }

    /*
     * Initializes one HES log for one simulation run.
     */
    public void initialize(String folder, String filePrefix) {
        close();

        totalTasks = 0;
        offlineTasks = 0;
        synchronizedTasks = 0;
        edgeCompletedTasks = 0;
        cloudCompletedTasks = 0;
        failedTasks = 0;
        discardedTasks = 0;
        totalCompletedTasks = 0;

        totalLatency = 0.0;
        totalEdgeTime = 0.0;
        totalCloudTime = 0.0;
        totalSyncDelay = 0.0;
        totalEdgeToCloudDelay = 0.0;

        outputFolder = folder;
        outputFile = filePrefix + "_HES.csv";

        createOutputFolder();
        createCSV();
    }

    /*
     * Backward-compatible initialization.
     * Prefer initialize(folder, filePrefix).
     */
    public void initialize() {
        initialize("hes_results", "hes_metrics");
    }

    private void createOutputFolder() {
        if (outputFolder == null || outputFolder.isEmpty()) {
            outputFolder = "hes_results";
        }

        File folder = new File(outputFolder);

        if (!folder.exists() && !folder.mkdirs()) {
            throw new RuntimeException(
                "Could not create HES output folder: " + outputFolder);
        }
    }

    private void createCSV() {
        try {
            File file = new File(outputFolder, outputFile);

            /*
             * false = create a new file for this exact simulation run.
             * MainApp supplies a unique prefix for scenario/policy/device count.
             */
            csvWriter = new BufferedWriter(new FileWriter(file, false));

            csvWriter.write(
                "task_id,event,clock,stage,mobile_device,"
                + "creation_time,offline_start_time,sync_start_time,"
                + "edge_start_time,edge_completion_time,"
                + "cloud_arrival_time,cloud_start_time,cloud_completion_time,"
                + "edge_processing_time,sync_delay,"
                + "edge_to_cloud_delay,cloud_processing_time,"
                + "end_to_end_latency\n"
            );

            csvWriter.flush();

        } catch (IOException e) {
            throw new RuntimeException(
                "Could not create HES CSV file", e);
        }
    }

    public void taskSubmitted(EdgeTask task) {
        totalTasks++;

        writeCSV(task, "SUBMIT");
    }

    public void taskStoredOffline(EdgeTask task) {
        offlineTasks++;

        /*
         * The offline interval starts when the task is actually placed
         * in the local queue, not when the task was originally created.
         */
        task.setOfflineStartTime(CloudSim.clock());
        task.setSyncStartTime(-1.0);

        writeCSV(task, "OFFLINE");
    }

    public void taskSynchronized(EdgeTask task) {
        task.setSyncStartTime(CloudSim.clock());

        double syncDelay = getSyncDelay(task);

        if (syncDelay >= 0.0) {
            synchronizedTasks++;
            totalSyncDelay += syncDelay;
        }

        writeCSV(task, "SYNC");
    }

    public void taskCompletedAtEdge(EdgeTask task) {
        edgeCompletedTasks++;

        double edgeProcessing = getEdgeProcessingTime(task);

        if (edgeProcessing >= 0.0) {
            totalEdgeTime += edgeProcessing;
        }

        writeCSV(task, "EDGE_COMPLETE");
    }

    public void taskCompletedAtCloud(EdgeTask task) {
        cloudCompletedTasks++;
        totalCompletedTasks++;

        double latency = getEndToEndLatency(task);
        double cloudProcessing = getCloudProcessingTime(task);
        double edgeToCloudDelay = getEdgeToCloudDelay(task);

        if (latency >= 0.0) {
            totalLatency += latency;
        }

        if (cloudProcessing >= 0.0) {
            totalCloudTime += cloudProcessing;
        }

        if (edgeToCloudDelay >= 0.0) {
            totalEdgeToCloudDelay += edgeToCloudDelay;
        }

        writeCSV(task, "CLOUD_COMPLETE");
    }

    public void taskDiscarded(EdgeTask task) {
        discardedTasks++;
        writeCSV(task, "DISCARDED");
    }

    public void taskFailed(EdgeTask task) {
        failedTasks++;
        writeCSV(task, "FAILED");
    }

    /*
     * =========================================================
     * Metric definitions
     * =========================================================
     */

    public double getEndToEndLatency(EdgeTask task) {
        if (task.getCreationTime() < 0
                || task.getCloudCompletionTime() < 0) {
            return -1.0;
        }

        return task.getCloudCompletionTime()
            - task.getCreationTime();
    }

    public double getEdgeProcessingTime(EdgeTask task) {
        if (task.getEdgeStartTime() < 0
                || task.getEdgeCompletionTime() < 0) {
            return -1.0;
        }

        return task.getEdgeCompletionTime()
            - task.getEdgeStartTime();
    }

    public double getCloudProcessingTime(EdgeTask task) {
        if (task.getCloudStartTime() < 0
                || task.getCloudCompletionTime() < 0) {
            return -1.0;
        }

        return task.getCloudCompletionTime()
            - task.getCloudStartTime();
    }

    public double getSyncDelay(EdgeTask task) {
        if (task.getOfflineStartTime() < 0
                || task.getSyncStartTime() < 0) {
            return -1.0;
        }

        return task.getSyncStartTime()
            - task.getOfflineStartTime();
    }

    public double getEdgeToCloudDelay(EdgeTask task) {
        /*
         * Transfer/arrival delay: Edge completion to Cloud arrival.
         * Cloud VM queueing and Cloud processing are excluded.
         */
        if (task.getEdgeCompletionTime() < 0
                || task.getCloudArrivalTime() < 0) {
            return -1.0;
        }

        return task.getCloudArrivalTime()
            - task.getEdgeCompletionTime();
    }

    private void writeCSV(EdgeTask task, String event) {
        try {
            /*
             * Derived metrics are written only on the event where they
             * are semantically completed. This prevents double/triple
             * counting when the CSV is aggregated.
             */
            double edgeProcessing = -1.0;
            double syncDelay = -1.0;
            double edgeToCloudDelay = -1.0;
            double cloudProcessing = -1.0;
            double endToEnd = -1.0;

            if ("SYNC".equals(event)) {
                syncDelay = getSyncDelay(task);
            }
            else if ("EDGE_COMPLETE".equals(event)) {
                edgeProcessing = getEdgeProcessingTime(task);
            }
            else if ("CLOUD_COMPLETE".equals(event)) {
                /*
                 * Metrics below are determined at CLOUD completion.
                 * Edge processing is emitted at EDGE_COMPLETE and
                 * synchronization delay is emitted at SYNC, avoiding
                 * duplicate observations in the event log.
                 */
                edgeToCloudDelay = getEdgeToCloudDelay(task);
                cloudProcessing = getCloudProcessingTime(task);
                endToEnd = getEndToEndLatency(task);
            }

            csvWriter.write(
                task.getLogicalTaskId() + ","
                + event + ","
                + CloudSim.clock() + ","
                + task.getStage() + ","
                + task.getMobileDeviceId() + ","
                + task.getCreationTime() + ","
                + task.getOfflineStartTime() + ","
                + task.getSyncStartTime() + ","
                + task.getEdgeStartTime() + ","
                + task.getEdgeCompletionTime() + ","
                + task.getCloudArrivalTime() + ","
                + task.getCloudStartTime() + ","
                + task.getCloudCompletionTime() + ","
                + edgeProcessing + ","
                + syncDelay + ","
                + edgeToCloudDelay + ","
                + cloudProcessing + ","
                + endToEnd + "\n"
            );

            csvWriter.flush();

        } catch (IOException e) {
            throw new RuntimeException(
                "Could not write HES CSV event", e);
        }
    }

    /*
     * =========================================================
     * Summary
     * =========================================================
     */

    public void printSummary() {

        System.out.println();
        System.out.println("=================================================");
        System.out.println("EXPERIMENT SUMMARY");
        System.out.println("=================================================");

        System.out.println("Total Tasks: " + totalTasks);
        System.out.println("Offline Tasks: " + offlineTasks);
        System.out.println("Synchronized Tasks: " + synchronizedTasks);
        System.out.println("Discarded Tasks: " + discardedTasks);
        System.out.println("EDGE Completed Tasks: " + edgeCompletedTasks);
        System.out.println("CLOUD Completed Tasks: " + cloudCompletedTasks);
        System.out.println("Failed Tasks: " + failedTasks);

        System.out.println();

        if (totalCompletedTasks > 0) {
            System.out.println(
                "Average End-to-End Latency: "
                + (totalLatency / totalCompletedTasks)
                + " sec");

            System.out.println(
                "Average CLOUD Processing Time: "
                + (totalCloudTime / totalCompletedTasks)
                + " sec");

            System.out.println(
                "Average EDGE-to-CLOUD Delay: "
                + (totalEdgeToCloudDelay / totalCompletedTasks)
                + " sec");
        }

        if (edgeCompletedTasks > 0) {
            System.out.println(
                "Average EDGE Processing Time: "
                + (totalEdgeTime / edgeCompletedTasks)
                + " sec");
        }

        if (synchronizedTasks > 0) {
            System.out.println(
                "Average Offline Synchronization Delay: "
                + (totalSyncDelay / synchronizedTasks)
                + " sec");
        }

        System.out.println("=================================================");
    }

    public void close() {
        try {
            if (csvWriter != null) {
                csvWriter.flush();
                csvWriter.close();
                csvWriter = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
