package org.traccar.model;

/**
 * Created by Ankit on 1/28/2015.
 */
public class DeviceSettings {

    /**
     * Id
     */
    private Long id;


    String status;

    Double refreshInterval;

    private Long isuraksha_devices_id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Double refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public Long getIsuraksha_devices_id() {
        return isuraksha_devices_id;
    }

    public void setIsuraksha_devices_id(Long isuraksha_devices_id) {
        this.isuraksha_devices_id = isuraksha_devices_id;
    }
}
