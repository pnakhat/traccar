/*
 * Copyright 2012 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.database;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.traccar.helper.DriverDelegate;
import org.traccar.helper.Log;
import org.traccar.model.*;
import org.xml.sax.InputSource;
import javax.sql.DataSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;

/**
 * Database abstraction class
 */
public class DataManager {

    public DataManager(Properties properties) throws Exception {
        if (properties != null) {
            initDatabase(properties);
            
            // Refresh delay
            String refreshDelay = properties.getProperty("database.refreshDelay");
            if (refreshDelay != null) {
                devicesRefreshDelay = Long.valueOf(refreshDelay) * 1000;
            } else {
                devicesRefreshDelay = DEFAULT_REFRESH_DELAY * 1000;
            }
        }
    }
    
    private DataSource dataSource;
    
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Database statements
     */
    private NamedParameterStatement queryGetDevices;
    private NamedParameterStatement queryAddPosition;
    private NamedParameterStatement queryUpdateLatestPosition;
    private NamedParameterStatement queryGetDeviceSettings;
    private NamedParameterStatement queryGetSosNumbers;
    private NamedParameterStatement queryGetFriendsAndFamilyNumbers;
    private NamedParameterStatement queryUpdateFriendsAndFamily;
    private NamedParameterStatement queryUpdateSOS;
    private NamedParameterStatement queryUpdateDeviceSettingStatus;

    /**
     * Initialize database
     */
    private void initDatabase(Properties properties) throws Exception {

        // Load driver
        String driver = properties.getProperty("database.driver");
        if (driver != null) {
            String driverFile = properties.getProperty("database.driverFile");

            if (driverFile != null) {
                URL url = new URL("jar:file:" + new File(driverFile).getAbsolutePath() + "!/");
                URLClassLoader cl = new URLClassLoader(new URL[]{url});
                Driver d = (Driver) Class.forName(driver, true, cl).newInstance();
                DriverManager.registerDriver(new DriverDelegate(d));
            } else {
                Class.forName(driver);
            }
        }
        
        // Initialize data source
        ComboPooledDataSource ds = new ComboPooledDataSource();
        ds.setDriverClass(properties.getProperty("database.driver"));
        ds.setJdbcUrl(properties.getProperty("database.url"));
        ds.setUser(properties.getProperty("database.user"));
        ds.setPassword(properties.getProperty("database.password"));
        ds.setIdleConnectionTestPeriod(600);
        ds.setTestConnectionOnCheckin(true);
        dataSource = ds;

        // Load statements from configuration
        String query;

        query = properties.getProperty("database.selectDevice");
        if (query != null) {
            queryGetDevices = new NamedParameterStatement(query, dataSource);
        }

        query = properties.getProperty("database.insertPosition");
        if (query != null) {
            queryAddPosition = new NamedParameterStatement(query, dataSource, Statement.RETURN_GENERATED_KEYS);
        }

        query = properties.getProperty("database.updateLatestPosition");
        if (query != null) {
            queryUpdateLatestPosition = new NamedParameterStatement(query, dataSource);
        }

        query = properties.getProperty("database.getDeviceSettings");
        if (query != null) {
            queryGetDeviceSettings = new NamedParameterStatement(query, dataSource);
        }
        query = properties.getProperty("database.getSosNumbers");
        if (query != null) {
            queryGetSosNumbers = new NamedParameterStatement(query, dataSource);
        }

        query = properties.getProperty("database.getFriendsAndFamilyNumbers");
        if (query != null) {
            queryGetFriendsAndFamilyNumbers = new NamedParameterStatement(query, dataSource);
        }
        query = properties.getProperty("database.updateStatusOFSOS");
        if (query != null) {
            queryUpdateSOS = new NamedParameterStatement(query, dataSource);
        }
        query = properties.getProperty("database.updateStatusOFFriendsAndFamilyNumbers");
        if (query != null) {
            queryUpdateFriendsAndFamily = new NamedParameterStatement(query, dataSource);
        }
        query = properties.getProperty("database.updateDeviceSettingStatus");
        if (query != null) {
            queryUpdateDeviceSettingStatus = new NamedParameterStatement(query, dataSource);
        }
    }

    private final NamedParameterStatement.ResultSetProcessor<Device> deviceResultSetProcessor = new NamedParameterStatement.ResultSetProcessor<Device>() {
        @Override
        public Device processNextRow(ResultSet rs) throws SQLException {
            Device device = new Device();
            device.setId(rs.getLong("id"));
            device.setImei(rs.getString("imei"));
            return device;
        }
    };

    public List<Device> getDevices() throws SQLException {
        if (queryGetDevices != null) {
            return queryGetDevices.prepare().executeQuery(deviceResultSetProcessor);
        } else {
            return new LinkedList<Device>();
        }
    }


    /**
     * Devices cache
     */
    private Map<String, Device> devices;
    private Calendar devicesLastUpdate;
    private long devicesRefreshDelay;
    private static final long DEFAULT_REFRESH_DELAY = 300;

    public Device getDeviceByImei(String imei) throws SQLException {

        if (devices == null || !devices.containsKey(imei) ||
                (Calendar.getInstance().getTimeInMillis() - devicesLastUpdate.getTimeInMillis() > devicesRefreshDelay)) {
            devices = new HashMap<String, Device>();
            for (Device device : getDevices()) {
                devices.put(device.getImei(), device);
            }
            devicesLastUpdate = Calendar.getInstance();
        }

        return devices.get(imei);
    }





    private NamedParameterStatement.ResultSetProcessor<Long> generatedKeysResultSetProcessor = new NamedParameterStatement.ResultSetProcessor<Long>() {
        @Override
        public Long processNextRow(ResultSet rs) throws SQLException {
            return rs.getLong(1);
        }
    };

    public synchronized Long addPosition(Position position) throws SQLException {
        if (queryAddPosition != null) {
            List<Long> result = assignVariables(queryAddPosition.prepare(), position).executeUpdate(generatedKeysResultSetProcessor);
            if (result != null && !result.isEmpty()) {
                return result.iterator().next();
            }
        }
        return null;
    }

    public void updateLatestPosition(Position position, Long positionId) throws SQLException {
        if (queryUpdateLatestPosition != null) {
            assignVariables(queryUpdateLatestPosition.prepare(), position).setLong("id", positionId).executeUpdate();
        }
    }

    private NamedParameterStatement.Params assignVariables(NamedParameterStatement.Params params, Position position) throws SQLException {

        params.setLong("device_id", position.getDeviceId());
        params.setTimestamp("time", position.getTime());
        params.setBoolean("valid", position.getValid());
        params.setDouble("altitude", position.getAltitude());
        params.setDouble("latitude", position.getLatitude());
        params.setDouble("longitude", position.getLongitude());
        params.setDouble("speed", position.getSpeed());
        params.setDouble("course", position.getCourse());
        params.setString("address", position.getAddress());
        params.setString("extended_info", position.getExtendedInfo());

        // DELME: Temporary compatibility support
        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            InputSource source = new InputSource(new StringReader(position.getExtendedInfo()));
            String index = xpath.evaluate("/info/index", source);
            if (!index.isEmpty()) {
                params.setLong("id", Long.valueOf(index));
            } else {
                params.setLong("id", null);
            }
            source = new InputSource(new StringReader(position.getExtendedInfo()));
            String power = xpath.evaluate("/info/power", source);
            if (!power.isEmpty()) {
                params.setDouble("power", Double.valueOf(power));
            } else {
                params.setLong("power", null);
            }
        } catch (XPathExpressionException e) {
            Log.warning("Error in XML: " + position.getExtendedInfo(), e);
            params.setLong("id", null);
            params.setLong("power", null);
        }

        return params;
    }



    //Ankit get Device Setting's Work

    public DeviceSettings getDeviceSetting(Long isuraksha_devices_id)throws SQLException{

        if (queryGetDeviceSettings!=null){
            List<DeviceSettings> result=queryGetDeviceSettings.prepare().setLong("isuraksha_devices_id", isuraksha_devices_id).setString("status","UPDATED").executeQuery(generatedDeviceSettingProcessor);
            if(result!=null && !result.isEmpty()){
                return result.iterator().next();
            }
        }
        return null;
    }


    private NamedParameterStatement.ResultSetProcessor<DeviceSettings> generatedDeviceSettingProcessor = new NamedParameterStatement.ResultSetProcessor<DeviceSettings>() {
        @Override
        public DeviceSettings processNextRow(ResultSet rs) throws SQLException {
            DeviceSettings deviceSettings=new DeviceSettings();
            deviceSettings.setId(rs.getLong("id"));
            deviceSettings.setIsuraksha_devices_id(rs.getLong("isuraksha_devices_id"));
            deviceSettings.setRefreshInterval(rs.getDouble("refresh_interval"));
            deviceSettings.setStatus(rs.getString("status"));
            return  deviceSettings;
        }
    };

        // get SOS Numbers
          public List<SosNumber> getSosNumbers(Long device_settings_id) throws SQLException{
              if (queryGetSosNumbers!=null){
                  List<SosNumber> result=queryGetSosNumbers.prepare().setLong("device_settings_id",device_settings_id).setString("status","UPDATED").executeQuery(generatedSosNumberProcessor);
                  return result;

              }else {
                  return new LinkedList<SosNumber>();
              }
          }
     private NamedParameterStatement.ResultSetProcessor<SosNumber> generatedSosNumberProcessor=new NamedParameterStatement.ResultSetProcessor<SosNumber>() {
         @Override
         public SosNumber processNextRow(ResultSet rs) throws SQLException {
             SosNumber sosNumber=new SosNumber();
             sosNumber.setId(rs.getLong("id"));
             sosNumber.setNumber(rs.getString("number"));
             sosNumber.setStatus(rs.getString("status"));
             sosNumber.setDevice_settings_id(rs.getLong("device_settings_id"));
             return  sosNumber;
         }
     };

    //getFriendsAndFamilyNumber
    public List<FriendsAndFamily>getFriendsAndFamilyNumber(Long device_settings_id) throws SQLException{

        if (queryGetFriendsAndFamilyNumbers!=null){
            List<FriendsAndFamily> result=queryGetFriendsAndFamilyNumbers.prepare().setLong("device_settings_id",device_settings_id).setString("status","UPDATED").executeQuery(generateFriendsAndFamilyNumberProcess);
            return result;
        }else {
            return new LinkedList<FriendsAndFamily>();
        }
    }

    private NamedParameterStatement.ResultSetProcessor<FriendsAndFamily> generateFriendsAndFamilyNumberProcess=new NamedParameterStatement.ResultSetProcessor<FriendsAndFamily>() {
        @Override
        public FriendsAndFamily processNextRow(ResultSet rs) throws SQLException {
            FriendsAndFamily friendsAndFamily=new FriendsAndFamily();
            friendsAndFamily.setId(rs.getLong("id"));
            friendsAndFamily.setNumber(rs.getString("number"));
            friendsAndFamily.setStatus(rs.getString("status"));
            friendsAndFamily.setDevice_settings_id(rs.getLong("device_settings_id"));
            return friendsAndFamily;
        }
    };

 //Change Sos Status  to UPDATED On DB
    public void changeSOSStatus(Long sosId) throws SQLException {
        if (queryUpdateSOS != null) {
            queryUpdateSOS.prepare().setLong("id", sosId).setString("status", "UPDATED").executeUpdate();
        }
    }
//Change Friends And Family Status to UPDATED  On Db
public void changeFriendsAndFamilyStatus(Long fnfId) throws SQLException {
    if (queryUpdateFriendsAndFamily != null) {
        queryUpdateFriendsAndFamily.prepare().setLong("id", fnfId).setString("status", "UPDATED").executeUpdate();
    }
}
    public void changeDeviceSettingsStatus(Long deviceSettings_id) throws SQLException {
        if (queryUpdateDeviceSettingStatus != null) {
            queryUpdateDeviceSettingStatus.prepare().setLong("id", deviceSettings_id).setString("status", "UPDATED").executeUpdate();
        }
    }


}
