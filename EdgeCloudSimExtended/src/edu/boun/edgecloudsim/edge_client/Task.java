/*
 * /*
 * Title:        EdgeCloudSim - Task
 *
 * Description:
 * Task adds app type, task submission location, mobile device id,
 * host id, VM id, datacenter id and data type information to
 * CloudSim's Cloudlet class.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.utils.Location;

/**
 * EdgeCloudSim task model.
 *
 * <p>
 * Task extends CloudSim's Cloudlet class and adds information required
 * by EdgeCloudSim to identify the mobile device that generated the task,
 * its application type, submission location and execution resources.
 * </p>
 *
 * <p>
 * The {@code dataType} attribute is application-specific and can be used
 * by custom application scenarios to classify the content carried by a
 * task. The meaning of each value is defined by the application-specific
 * components, not by this generic Task class.
 * </p>
 */
public class Task extends Cloudlet {

    /*
     * -------------------------------------------------------------
     * TASK METADATA
     * -------------------------------------------------------------
     */

    /** Location where the task was submitted. */
    private Location submittedLocation;

    /** Simulation time at which the task was created. */
    private double creationTime;

    /** Application/task type identifier. */
    private int type;

    /** ID of the mobile device that generated the task. */
    private int mobileDeviceId;

    /** Host associated with the task. */
    private int hostIndex;

    /** VM associated with the task. */
    private int vmIndex;

    /** Datacenter associated with the task. */
    private int datacenterId;

    /**
     * Application-specific data classification.
     *
     * <p>
     * The actual meaning of the values is defined by the application
     * scenario, e.g., AR content, visitor data, bus information,
     * ticket information or synchronization.
     * </p>
     */
    private int dataType;


    /*
     * -------------------------------------------------------------
     * CONSTRUCTOR
     * -------------------------------------------------------------
     */

    /**
     * Creates an EdgeCloudSim task.
     *
     * @param _mobileDeviceId ID of the mobile device that generated
     *                        the task
     * @param cloudletId unique CloudSim cloudlet identifier
     * @param cloudletLength processing length in Million Instructions (MI)
     * @param pesNumber number of processing elements required
     * @param cloudletFileSize input data size
     * @param cloudletOutputSize output data size
     * @param utilizationModelCpu CPU utilization model
     * @param utilizationModelRam RAM utilization model
     * @param utilizationModelBw bandwidth utilization model
     */
    public Task(
            int _mobileDeviceId,
            int cloudletId,
            long cloudletLength,
            int pesNumber,
            long cloudletFileSize,
            long cloudletOutputSize,
            UtilizationModel utilizationModelCpu,
            UtilizationModel utilizationModelRam,
            UtilizationModel utilizationModelBw) {

        super(
                cloudletId,
                cloudletLength,
                pesNumber,
                cloudletFileSize,
                cloudletOutputSize,
                utilizationModelCpu,
                utilizationModelRam,
                utilizationModelBw
        );

        mobileDeviceId = _mobileDeviceId;

        /*
         * CloudSim.clock() returns the current simulation time.
         * This value is kept independently from the Cloudlet timing
         * attributes because it represents the application-level
         * creation time.
         */
        creationTime = CloudSim.clock();

        /*
         * Explicit defaults.
         *
         * These values are also Java's default values, but explicit
         * initialization makes the intended semantics clear.
         */
        submittedLocation = null;
        type = 0;
        hostIndex = -1;
        vmIndex = -1;
        datacenterId = -1;
        dataType = 0;
    }


    /*
     * -------------------------------------------------------------
     * DATA TYPE
     * -------------------------------------------------------------
     */

    /**
     * Sets the application-specific data type of this task.
     *
     * @param _dataType application-specific data type identifier
     */
    public void setDataType(int _dataType) {
        dataType = _dataType;
    }

    /**
     * Returns the application-specific data type.
     *
     * @return data type identifier
     */
    public int getDataType() {
        return dataType;
    }


    /*
     * -------------------------------------------------------------
     * SUBMISSION LOCATION
     * -------------------------------------------------------------
     */

    /**
     * Sets the location where the task was submitted.
     *
     * @param _submittedLocation geographic location of task submission
     */
    public void setSubmittedLocation(Location _submittedLocation) {
        submittedLocation = _submittedLocation;
    }

    /**
     * Returns the location where the task was submitted.
     *
     * @return task submission location, or null if it has not been set
     */
    public Location getSubmittedLocation() {
        return submittedLocation;
    }


    /*
     * -------------------------------------------------------------
     * EXECUTION DATACENTER
     * -------------------------------------------------------------
     */

    /**
     * Associates this task with a datacenter.
     *
     * @param _datacenterId datacenter identifier
     */
    public void setAssociatedDatacenterId(int _datacenterId) {
        datacenterId = _datacenterId;
    }

    /**
     * Returns the datacenter associated with this task.
     *
     * @return datacenter identifier
     */
    public int getAssociatedDatacenterId() {
        return datacenterId;
    }


    /*
     * -------------------------------------------------------------
     * EXECUTION HOST
     * -------------------------------------------------------------
     */

    /**
     * Associates this task with a host.
     *
     * @param _hostIndex host identifier/index
     */
    public void setAssociatedHostId(int _hostIndex) {
        hostIndex = _hostIndex;
    }

    /**
     * Returns the host associated with this task.
     *
     * @return host identifier/index
     */
    public int getAssociatedHostId() {
        return hostIndex;
    }


    /*
     * -------------------------------------------------------------
     * EXECUTION VM
     * -------------------------------------------------------------
     */

    /**
     * Associates this task with a VM.
     *
     * @param _vmIndex VM identifier/index
     */
    public void setAssociatedVmId(int _vmIndex) {
        vmIndex = _vmIndex;
    }

    /**
     * Returns the VM associated with this task.
     *
     * @return VM identifier/index
     */
    public int getAssociatedVmId() {
        return vmIndex;
    }


    /*
     * -------------------------------------------------------------
     * APPLICATION TASK TYPE
     * -------------------------------------------------------------
     */

    /**
     * Sets the application/task type.
     *
     * @param _type application/task type identifier
     */
    public void setTaskType(int _type) {
        type = _type;
    }

    /**
     * Returns the application/task type.
     *
     * @return application/task type identifier
     */
    public int getTaskType() {
        return type;
    }


    /*
     * -------------------------------------------------------------
     * MOBILE DEVICE
     * -------------------------------------------------------------
     */

    /**
     * Returns the ID of the mobile device that generated the task.
     *
     * @return mobile device identifier
     */
    public int getMobileDeviceId() {
        return mobileDeviceId;
    }


    /*
     * -------------------------------------------------------------
     * CREATION TIME
     * -------------------------------------------------------------
     */

    /**
     * Returns the simulation time at which the task was created.
     *
     * @return task creation time
     */
    public double getCreationTime() {
        return creationTime;
    }
}