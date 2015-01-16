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
package org.traccar.protocol;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.database.DataManager;
import org.traccar.helper.Crc;
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

import java.util.Calendar;
import java.util.Properties;
import java.util.TimeZone;

public class Gt06ProtocolDecoderCopy extends BaseProtocolDecoder {

    private Long deviceId;
    private final TimeZone timeZone = TimeZone.getTimeZone("UTC");

    public Gt06ProtocolDecoderCopy(DataManager dataManager, String protocol, Properties properties) {
        super(dataManager, protocol, properties);

        if (properties != null) {
            if (properties.containsKey(protocol + ".timezone")) {
                timeZone.setRawOffset(
                        Integer.valueOf(properties.getProperty(protocol + ".timezone")) * 1000);
            }
        }
    }

    private String readImei(ChannelBuffer buf) {
        int b = buf.readUnsignedByte();
        StringBuilder imei = new StringBuilder();
        imei.append(b & 0x0F);
        for (int i = 0; i < 7; i++) {
            b = buf.readUnsignedByte();
            imei.append((b & 0xF0) >> 4);
            imei.append(b & 0x0F);
        }
        return imei.toString();
    }

    private static final int MSG_LOGIN = 0x01;
    private static final int MSG_GPS = 0x10;
    private static final int MSG_LBS = 0x11;
    private static final int MSG_GPS_LBS_1 = 0x12;
    private static final int MSG_GPS_LBS_2 = 0x22;
    private static final int MSG_STATUS = 0x13;
    private static final int MSG_SATELLITE = 0x14;
    private static final int MSG_STRING = 0x15;
    private static final int MSG_GPS_LBS_STATUS_1 = 0x16;
    private static final int MSG_GPS_LBS_STATUS_2 = 0x26;
    private static final int MSG_GPS_LBS_STATUS_3 = 0x27;
    private static final int MSG_LBS_PHONE = 0x17;
    private static final int MSG_LBS_EXTEND = 0x18;
    private static final int MSG_LBS_STATUS = 0x19;
    private static final int MSG_GPS_PHONE = 0x1A;
    private static final int MSG_GPS_LBS_EXTEND = 0x1E;
    private static final int MSG_COMMAND_0 = 0x80;
    private static final int MSG_COMMAND_1 = 0x81;
    private static final int MSG_COMMAND_2 = 0x82;
    private static final int MSG_COMMAND_RES = 0x79;

    private static void sendResponse(Channel channel, int type, int index) {
        if (channel != null) {
            ChannelBuffer response = ChannelBuffers.directBuffer(10);
            response.writeByte(0x78);
            response.writeByte(0x78); // header
            response.writeByte(0x05); // size
            response.writeByte(type);
            response.writeShort(index);
            response.writeShort(Crc.crc16Ccitt(response.toByteBuffer(2, 4)));
            response.writeByte(0x0D);
            response.writeByte(0x0A); // ending
            channel.write(response);
        }
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        ChannelBuffer buf = (ChannelBuffer) msg;

        // Check header
        if (buf.readByte() != 0x78 || buf.readByte() != 0x78) {

            if (buf.readByte() == 0x79 || buf.readByte() == 0x79) {
                buf.skipBytes(2);
                Log.debug("Got Response From Server");
                StringBuilder s = new StringBuilder();
                for (int i = 4; i < buf.capacity(); i++) {
                    byte b = buf.getByte(i);
                    s.append((char) b);

                }

                Log.debug("Response+ " + s);

            }
            return null;
        }

        int length = buf.readUnsignedByte(); // size
        int dataLength = length - 5;

        int type = buf.readUnsignedByte();

        if (type == MSG_LOGIN) {

            String imei = readImei(buf);
            buf.readUnsignedShort(); // type

            // Timezone offset
            if (dataLength > 10) {
                int extensionBits = buf.readUnsignedShort();
                int offset = (extensionBits >> 4) * 36000;
                if ((extensionBits & 0x8) != 0) {
                    offset = -offset;
                }
                timeZone.setRawOffset(offset);
            }

            try {
                deviceId = getDataManager().getDeviceByImei(imei).getId();
                buf.skipBytes(buf.readableBytes() - 6);
                sendResponse(channel, type, buf.readUnsignedShort());
                //
                //Penidng Steting for deviceId

                //
                //deleteSOS
                deleteSOS(channel,1);
//                setSOS(channel, 1);
            } catch (Exception error) {
                Log.warning("Unknown device - " + imei);
            }


        } else if (deviceId != null && (
                type == MSG_GPS ||
                        type == MSG_GPS_LBS_1 ||
                        type == MSG_GPS_LBS_2 ||
                        type == MSG_GPS_LBS_STATUS_1 ||
                        type == MSG_GPS_LBS_STATUS_2 ||
                        type == MSG_GPS_LBS_STATUS_3 ||
                        type == MSG_GPS_PHONE ||
                        type == MSG_GPS_LBS_EXTEND)) {


            Log.info("Just before doing anything");
            //delete SOS


            // Create new position
            Position position = new Position();
            position.setDeviceId(deviceId);
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter(getProtocol());

            // Date and time
            Calendar time = Calendar.getInstance(timeZone);
            time.clear();
            time.set(Calendar.YEAR, 2000 + buf.readUnsignedByte());
            time.set(Calendar.MONTH, buf.readUnsignedByte() - 1);
            time.set(Calendar.DAY_OF_MONTH, buf.readUnsignedByte());
            time.set(Calendar.HOUR_OF_DAY, buf.readUnsignedByte());
            time.set(Calendar.MINUTE, buf.readUnsignedByte());
            time.set(Calendar.SECOND, buf.readUnsignedByte());
            position.setTime(time.getTime());

            // GPS length and Satellites count
            int gpsLength = buf.readUnsignedByte();
            extendedInfo.set("satellites", gpsLength & 0xf);
            gpsLength >>= 4;

            // Latitude
            double latitude = buf.readUnsignedInt() / (60.0 * 30000.0);

            // Longitude
            double longitude = buf.readUnsignedInt() / (60.0 * 30000.0);

            // Speed
            position.setSpeed(buf.readUnsignedByte() * 0.539957);

            // Course and flags
            int union = buf.readUnsignedShort();
            position.setCourse((double) (union & 0x03FF));
            position.setValid((union & 0x1000) != 0);
            if ((union & 0x0400) == 0) latitude = -latitude;
            if ((union & 0x0800) != 0) longitude = -longitude;

            position.setLatitude(latitude);
            position.setLongitude(longitude);
            position.setAltitude(0.0);

            if ((union & 0x4000) != 0) {
                extendedInfo.set("acc", (union & 0x8000) != 0);
            }

            buf.skipBytes(gpsLength - 12); // skip reserved

            if (type == MSG_GPS_LBS_1 || type == MSG_GPS_LBS_2 ||
                    type == MSG_GPS_LBS_STATUS_1 || type == MSG_GPS_LBS_STATUS_2 || type == MSG_GPS_LBS_STATUS_3) {

                int lbsLength = 0;
                if (type == MSG_GPS_LBS_STATUS_1 || type == MSG_GPS_LBS_STATUS_2 || type == MSG_GPS_LBS_STATUS_3) {
                    lbsLength = buf.readUnsignedByte();
                }

                // Cell information
                extendedInfo.set("mcc", buf.readUnsignedShort());
                extendedInfo.set("mnc", buf.readUnsignedByte());
                extendedInfo.set("lac", buf.readUnsignedShort());
                extendedInfo.set("cell", buf.readUnsignedShort() << 8 + buf.readUnsignedByte());
                buf.skipBytes(lbsLength - 9);

                // Status
                if (type == MSG_GPS_LBS_STATUS_1 || type == MSG_GPS_LBS_STATUS_2 || type == MSG_GPS_LBS_STATUS_3) {

                    extendedInfo.set("alarm", true);

                    int flags = buf.readUnsignedByte();

                    extendedInfo.set("acc", (flags & 0x2) != 0);
                    // TODO parse other flags

                    // Voltage
                    extendedInfo.set("power", buf.readUnsignedByte());

                    // GSM signal
                    extendedInfo.set("gsm", buf.readUnsignedByte());
                }
            }

            // Index
            if (buf.readableBytes() > 6) {
                buf.skipBytes(buf.readableBytes() - 6);
            }
            int index = buf.readUnsignedShort();
            extendedInfo.set("index", index);
            sendResponse(channel, type, index);


            //setSOS
            setSOS(channel, index);


            position.setExtendedInfo(extendedInfo.toString());

            return position;
        } else {
            buf.skipBytes(dataLength);
            if (type != MSG_COMMAND_0 && type != MSG_COMMAND_1 && type != MSG_COMMAND_2) {
                sendResponse(channel, type, buf.readUnsignedShort());
            }
        }

        return null;
    }


    private void deleteSOS(Channel channel, int index) {
        Log.info("Sending Device Command to Delete SOS");
        ChannelBuffer response = ChannelBuffers.directBuffer(27);
        response.writeByte(0x78);
        response.writeByte(0x78); // header
        response.writeByte(0x16); // size
        response.writeByte(MSG_COMMAND_0);

        response.writeByte(0x10); //content length;

        //Server flag
        response.writeByte(0x00);
        response.writeByte(0x00);
        response.writeByte(0x00);
        response.writeByte(0x01);
        //
        //SOS Command Start
        response.writeByte(0x53);
        response.writeByte(0x4f);
        response.writeByte(0x53);
        //
        //
        response.writeByte(0x2c);/*, */
        response.writeByte(0x44);//D
        response.writeByte(0x2c);//,
        response.writeByte(0x31);//1
        response.writeByte(0x2c);//,
        response.writeByte(0x32);//2
        response.writeByte(0x2c);//,
        response.writeByte(0x33);//3

        response.writeByte(0x23);//#

        //Serial Number

        response.writeByte(0x00);
        response.writeByte(0x01);


        response.writeShort(Crc.crc16Ccitt(response.toByteBuffer(2, 21)));
        response.writeByte(0x0D);
        response.writeByte(0x0A); // ending
        ChannelFuture responseFromDevice = channel.write(response);
        Log.info("Response: " + response.toString());
        if (responseFromDevice.isSuccess()) {

            Log.debug("Success");
        }
    }


    private void setSOS(Channel channel, int index) {
        Log.info("Sending Device Command");
        ChannelBuffer response = ChannelBuffers.directBuffer(57);
        response.writeByte(0x78);
        response.writeByte(0x78); // header
        response.writeByte(0x34); // size     //CRc Start
        response.writeByte(MSG_COMMAND_0);

        response.writeByte(0x2E); //content length;
        //Server
        response.writeByte(0x00);
        response.writeByte(0x00);
        response.writeByte(0x00);
        response.writeByte(0x01);
        //
        //SOS Command Startflag
        response.writeByte(0x53); //S
        response.writeByte(0x4f);  //0
        response.writeByte(0x53);  //S
        //
        response.writeByte(0x2c);/*, */
        response.writeByte(0x41);//A

        response.writeByte(0x2c);/*, */


        // Update number - 9799056600  : 39373939303536363030
        response.writeByte(0x30);
        response.writeByte(0x39);
        response.writeByte(0x37);
        response.writeByte(0x39);
        response.writeByte(0x39);
        response.writeByte(0x30);
        response.writeByte(0x35);
        response.writeByte(0x36);
        response.writeByte(0x36);
        response.writeByte(0x30);
        response.writeByte(0x30);

        response.writeByte(0x2c);/*, */

        // Update number - 9799056601  : 39373939303536363030
        response.writeByte(0x30);
        response.writeByte(0x39);
        response.writeByte(0x37);
        response.writeByte(0x39);
        response.writeByte(0x39);
        response.writeByte(0x30);
        response.writeByte(0x35);
        response.writeByte(0x36);
        response.writeByte(0x36);
        response.writeByte(0x30);
        response.writeByte(0x31);


        response.writeByte(0x2c);/*, */


        // 3 Update number - 9001818606 : 39303031383138363036

        response.writeByte(0x30);
        response.writeByte(0x39);
        response.writeByte(0x30);
        response.writeByte(0x30);
        response.writeByte(0x31);
        response.writeByte(0x38);
        response.writeByte(0x31);
        response.writeByte(0x38);
        response.writeByte(0x36);
        response.writeByte(0x30);
        response.writeByte(0x36);

        response.writeByte(0x23);//#

        //Serial Number

        response.writeByte(0x00);
        response.writeByte(0xA0);    //CRc ENd Here


        int cap = response.capacity() - 1;
        Log.info("Total Capacity + " + cap);
        response.writeShort(Crc.crc16Ccitt(response.toByteBuffer(2, 51)));

//        response.writeByte(0x1D);
//        response.writeByte(0x0C); // ending


        response.writeByte(0x0D);
        response.writeByte(0x0A); // ending
        ChannelFuture responseFromDevice = channel.write(response);
        Log.info("Response: " + response.toString());
        if (responseFromDevice.isSuccess()) {

            Log.debug("Success");
        }
    }

}