/*
 * Title:        EdgeCloudSim - Custom Vm Allocation Policy for Edge VMs
 *
 * Description:
 * EdgeVmAllocationPolicy_Custom is responsible for allocating EdgeCloudSim
 * EdgeVM instances to the hosts of a specific edge datacenter.
 *
 * The allocation is performed according to the VM/host structure created
 * by EdgeServerManager. Each allocation policy instance manages only the
 * hosts belonging to its own datacenter.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;

/**
 * Custom VM allocation policy for EdgeCloudSim edge datacenters.
 *
 * <p>
 * This policy allocates EdgeVM instances to hosts belonging to the
 * corresponding edge datacenter.
 * </p>
 *
 * <p>
 * The previous implementation attempted to reconstruct the VM placement
 * from the XML configuration by using a global VM counter. That approach
 * is unsafe when multiple edge datacenters are present because VM IDs are
 * global while host indexes are local to each datacenter.
 * </p>
 *
 * <p>
 * This implementation therefore uses the host list supplied directly by
 * CloudSim/EdgeCloudSim and allocates VMs sequentially to hosts according
 * to their available capacity.
 * </p>
 */
public class EdgeVmAllocationPolicy_Custom extends VmAllocationPolicy {

    /**
     * VM UID -> Host mapping.
     */
    private Map<String, Host> vmTable;

    /**
     * Global number of successfully created Edge VMs.
     *
     * This counter is used only for validation/logging.
     */
    private static int createdVmNum = 0;

    /**
     * Datacenter index associated with this allocation policy.
     */
    private final int dataCenterIndex;

    /**
     * Constructs the allocation policy.
     *
     * @param list list of hosts belonging to this datacenter
     * @param dataCenterIndex index of this datacenter
     */
    public EdgeVmAllocationPolicy_Custom(
            List<? extends Host> list,
            int dataCenterIndex) {

        super(list);

        this.vmTable = new HashMap<String, Host>();
        this.dataCenterIndex = dataCenterIndex;
    }

    /**
     * Allocates an EdgeVM to a suitable host.
     *
     * <p>
     * The method first checks whether the VM has already been allocated.
     * It then searches the hosts belonging to this allocation policy and
     * tries to create the VM on the first host with sufficient resources.
     * </p>
     *
     * <p>
     * Only EdgeVM instances are handled by this policy.
     * </p>
     *
     * @param vm VM to be allocated
     * @return true if allocation succeeds; false otherwise
     */
    @Override
    public boolean allocateHostForVm(Vm vm) {

        /*
         * This allocation policy is intended only for EdgeVMs.
         */
        if (!(vm instanceof EdgeVM)) {
            return false;
        }

        /*
         * Do not allocate the same VM twice.
         */
        if (vmTable.containsKey(vm.getUid())) {
            return true;
        }

        /*
         * Try each host belonging to this datacenter.
         *
         * CloudSim's Host.vmCreate() performs the actual resource
         * validation.
         */
        for (Host host : getHostList()) {

            if (host.vmCreate(vm)) {

                vmTable.put(vm.getUid(), host);

                createdVmNum++;

                Log.formatLine(
                        "%.2f: Edge VM #%d allocated to host #%d "
                        + "(Edge Datacenter #%d)",
                        CloudSim.clock(),
                        vm.getId(),
                        host.getId(),
                        dataCenterIndex);

                return true;
            }
        }

        /*
         * No host had sufficient resources.
         */
        return false;
    }

    /**
     * Allocates a VM to a specific host.
     *
     * @param vm VM to allocate
     * @param host target host
     * @return true if allocation succeeds; false otherwise
     */
    @Override
    public boolean allocateHostForVm(Vm vm, Host host) {

        if (vm == null || host == null) {
            return false;
        }

        /*
         * Do not allocate the same VM twice.
         */
        if (vmTable.containsKey(vm.getUid())) {
            return true;
        }

        /*
         * Make sure the host belongs to this allocation policy.
         */
        if (!getHostList().contains(host)) {
            return false;
        }

        /*
         * Try to create the VM on the requested host.
         */
        if (host.vmCreate(vm)) {

            vmTable.put(vm.getUid(), host);

            createdVmNum++;

            Log.formatLine(
                    "%.2f: Edge VM #%d allocated to host #%d "
                    + "(Edge Datacenter #%d)",
                    CloudSim.clock(),
                    vm.getId(),
                    host.getId(),
                    dataCenterIndex);

            return true;
        }

        return false;
    }

    /**
     * Optimizes VM allocation.
     *
     * <p>
     * Dynamic VM migration/reallocation is not required by the current
     * EdgeCloudSim scenario. Therefore no optimization is performed.
     * </p>
     *
     * @param vmList list of VMs
     * @return null because optimization is not implemented
     */
    @Override
    public List<Map<String, Object>> optimizeAllocation(
            List<? extends Vm> vmList) {

        return null;
    }

    /**
     * Deallocates a VM from its host.
     *
     * @param vm VM to deallocate
     */
    @Override
    public void deallocateHostForVm(Vm vm) {

        if (vm == null) {
            return;
        }

        Host host = vmTable.remove(vm.getUid());

        if (host != null) {
            host.vmDestroy(vm);
        }
    }

    /**
     * Returns the host where a VM is allocated.
     *
     * @param vm VM
     * @return allocated host or null
     */
    @Override
    public Host getHost(Vm vm) {

        if (vm == null) {
            return null;
        }

        return vmTable.get(vm.getUid());
    }

    /**
     * Returns the host where a VM is allocated using VM and user IDs.
     *
     * @param vmId VM identifier
     * @param userId user identifier
     * @return allocated host or null
     */
    @Override
    public Host getHost(int vmId, int userId) {

        return vmTable.get(Vm.getUid(userId, vmId));
    }

    /**
     * Returns the VM-to-host mapping table.
     *
     * @return VM-to-host mapping
     */
    public Map<String, Host> getVmTable() {

        return vmTable;
    }

    /**
     * Sets the VM-to-host mapping table.
     *
     * @param vmTable VM-to-host mapping
     */
    protected void setVmTable(Map<String, Host> vmTable) {

        this.vmTable = vmTable;
    }

    /**
     * Returns the number of successfully created Edge VMs.
     *
     * @return number of created Edge VMs
     */
    public static int getCreatedVmNum() {

        return createdVmNum;
    }

    /**
     * Resets the global VM creation counter.
     *
     * This should be called before starting a new simulation iteration.
     */
    public static void resetCreatedVmNum() {

        createdVmNum = 0;
    }

    /**
     * Returns the datacenter index associated with this policy.
     *
     * @return datacenter index
     */
    public int getDataCenterIndex() {

        return dataCenterIndex;
    }
}