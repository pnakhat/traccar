package org.traccar.model;

/**
 * Created by Ankit on 1/28/2015.
 */
public class FriendsAndFamily {

    Long id;
    String number;
    String status;
    Long device_settings_id;
    Long rank;
    String name;


    public Long getRank() {
        return rank;
    }

    public void setRank(Long rank) {
        this.rank = rank;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDevice_settings_id() {
        return device_settings_id;
    }

    public void setDevice_settings_id(Long device_settings_id) {
        this.device_settings_id = device_settings_id;
    }
}
