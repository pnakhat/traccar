package org.traccar.model;

/**
 * Created by Ankit on 1/28/2015.
 */
public class SosNumber {

    Long id;
    String number;
    Long device_settings_id;
    String status;
    Long rank;

    public Long getRank() {
        return rank;
    }

    public void setRank(Long rank) {
        this.rank = rank;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public Long getDevice_settings_id() {
        return device_settings_id;
    }

    public void setDevice_settings_id(Long device_settings_id) {
        this.device_settings_id = device_settings_id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
