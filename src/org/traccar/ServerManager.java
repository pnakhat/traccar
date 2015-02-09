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
package org.traccar;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.FixedLengthFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LineBasedFrameDecoder;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.traccar.database.DataManager;
import org.traccar.geocode.GoogleReverseGeocoder;
import org.traccar.geocode.NominatimReverseGeocoder;
import org.traccar.geocode.ReverseGeocoder;
import org.traccar.helper.Log;
import org.traccar.http.WebServer;
import org.traccar.protocol.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Server Manager
 */
public class ServerManager {

    private final List<TrackerServer> serverList = new LinkedList<TrackerServer>();

    public void addTrackerServer(TrackerServer trackerServer) {
        serverList.add(trackerServer);
    }

    private boolean loggerEnabled;

    public boolean isLoggerEnabled() {
        return loggerEnabled;
    }

    private DataManager dataManager;

    public DataManager getDataManager() {
        return dataManager;
    }

    private ReverseGeocoder reverseGeocoder;

    public ReverseGeocoder getReverseGeocoder() {
        return reverseGeocoder;
    }

    private WebServer webServer;

    public WebServer getWebServer() {
        return webServer;
    }

    private Properties properties;

    public Properties getProperties() {
        return  properties;
    }

    public void init(String[] arguments) throws Exception {

        // Load properties
        properties = new Properties();
        if (arguments.length > 0) {
            properties.loadFromXML(new FileInputStream(arguments[0]));
        }

        // Init logger
        loggerEnabled = Boolean.valueOf(properties.getProperty("logger.enable"));
        if (loggerEnabled) {
            Log.setupLogger(properties);
        }

        dataManager = new DataManager(properties);

        initGeocoder(properties);

        initXexunServer("xexun");
        initGps103Server("gps103");
        initTk103Server("tk103");
        initGl100Server("gl100");
        initGl200Server("gl200");
        initT55Server("t55");
        initXexun2Server("xexun2");
        initTotemServer("totem");
        initEnforaServer("enfora");
        initMeiligaoServer("meiligao");
        initMaxonServer("maxon");
        initSuntechServer("suntech");
        initProgressServer("progress");
        initH02Server("h02");
        initJt600Server("jt600");
        initEv603Server("ev603");
        initV680Server("v680");
        initPt502Server("pt502");
        initTr20Server("tr20");
        initNavisServer("navis");
        initMeitrackServer("meitrack");
        initSkypatrolServer("skypatrol");
        initGt02Server("gt02");
        initGt06Server("gt06");
        initMegastekServer("megastek");
        initNavigilServer("navigil");
        initGpsGateServer("gpsgate");
        initTeltonikaServer("teltonika");
        initMta6Server("mta6");
        initMta6CanServer("mta6can");
        initTlt2hServer("tlt2h");
        initSyrusServer("syrus");
        initWondexServer("wondex");
        initCellocatorServer("cellocator");
        initGalileoServer("galileo");
        initYwtServer("ywt");
        initTk102Server("tk102");
        initIntellitracServer("intellitrac");
        initXt7Server("xt7");
        initWialonServer("wialon");
        initCarscopServer("carscop");
        initApelServer("apel");
        initManPowerServer("manpower");
        initGlobalSatServer("globalsat");
        initAtrackServer("atrack");
        initPt3000Server("pt3000");
        initRuptelaServer("ruptela");
        initTopflytechServer("topflytech");
        initLaipacServer("laipac");
        initAplicomServer("aplicom");
        initGotopServer("gotop");
        initSanavServer("sanav");
        initGatorServer("gator");
        initNoranServer("noran");
        initM2mServer("m2m");
        initOsmAndServer("osmand");
        initEasyTrackServer("easytrack");
        initTaipServer("taip");
        initKhdServer("khd");
        initPiligrimServer("piligrim");
        initStl060Server("stl060");
        initCarTrackServer("cartrack");
        initMiniFinderServer("minifinder");
        initHaicomServer("haicom");
        initEelinkServer("eelink");
        initBoxServer("box");
        initFreedomServer("freedom");
        initTelikServer("telik");
        initTrackboxServer("trackbox");
        initVisiontekServer("visiontek");
        initOrionServer("orion");
        initRitiServer("riti");
        
        // Initialize web server
        if (Boolean.valueOf(properties.getProperty("http.enable"))) {
            webServer = new WebServer(properties, dataManager.getDataSource());
        }
    }

    public void start() {
        if (webServer != null) {
            webServer.start();
        }
        for (Object server: serverList) {
            ((TrackerServer) server).start();
        }
    }

    public void stop() {
        for (Object server: serverList) {
            ((TrackerServer) server).stop();
        }

        // Release resources
        GlobalChannelFactory.release();
        GlobalTimer.release();

        if (webServer != null) {
            webServer.stop();
        }
    }

    public void destroy() {
        serverList.clear();
    }

    private void initGeocoder(Properties properties) throws IOException {
        if (Boolean.parseBoolean(properties.getProperty("geocoder.enable"))) {
            String type = properties.getProperty("geocoder.type");
            if (type != null && type.equals("nominatim")) {
                reverseGeocoder = new NominatimReverseGeocoder(
                        getProperties().getProperty("geocoder.url"));
            } else {
                reverseGeocoder = new GoogleReverseGeocoder();
            }
        }
    }

    private boolean isProtocolEnabled(Properties properties, String protocol) {
        String enabled = properties.getProperty(protocol + ".enable");
        if (enabled != null) {
            return Boolean.valueOf(enabled);
        }
        return false;
    }

    private void initXexunServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new XexunFrameDecoder());
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new XexunProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initGps103Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter1[] = { (byte) '\r', (byte) '\n' };
                    byte delimiter2[] = { (byte) '\n' };
                    byte delimiter3[] = { (byte) ';' };
                    pipeline.addLast("frameDecoder", new DelimiterBasedFrameDecoder(1024,
                            ChannelBuffers.wrappedBuffer(delimiter1),
                            ChannelBuffers.wrappedBuffer(delimiter2),
                            ChannelBuffers.wrappedBuffer(delimiter3)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Gps103ProtocolDecoder(dataManager, protocol, properties));
                }
            });
            serverList.add(new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Gps103ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initTk103Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ')' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Tk103ProtocolDecoder(dataManager, protocol, properties));
                }
            });
            serverList.add(new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Tk103ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initGl100Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\0' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Gl100ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initGl200Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter1[] = { (byte) '$' };
                    byte delimiter2[] = { (byte) '\0' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024,
                                    ChannelBuffers.wrappedBuffer(delimiter1),
                                    ChannelBuffers.wrappedBuffer(delimiter2)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Gl200ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initT55Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new T55ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initXexun2Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\n' }; // tracker bug \n\r
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new Xexun2ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initTotemServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new TotemFrameDecoder());
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new TotemProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initEnforaServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 0, 2, -2, 2));
                    pipeline.addLast("objectDecoder", new EnforaProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initMeiligaoServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new MeiligaoFrameDecoder());
                    pipeline.addLast("objectDecoder", new MeiligaoProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initMaxonServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new MaxonProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initSuntechServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new SuntechProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initProgressServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 2, 2, 4, 0));
                    pipeline.addLast("objectDecoder", new ProgressProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initH02Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new H02FrameDecoder());
                    pipeline.addLast("objectDecoder", new H02ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initJt600Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new Jt600FrameDecoder());
                    pipeline.addLast("objectDecoder", new Jt600ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initEv603Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ';' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new Ev603ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initV680Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#', (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new V680ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initPt502Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new Pt502FrameDecoder());
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new Pt502ProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initTr20Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Tr20ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initNavisServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(4 * 1024, 12, 2, 2, 0));
                    pipeline.addLast("objectDecoder", new NavisProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initMeitrackServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new MeitrackFrameDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new MeitrackProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initSkypatrolServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("objectDecoder", new SkypatrolProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initGt02Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(256, 2, 1, 2, 0));
                    pipeline.addLast("objectDecoder", new Gt02ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initGt06Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new Gt06FrameDecoder());
                    pipeline.addLast("objectDecoder", new Gt06ProtocolDecoderCopy(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initMegastekServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new MegastekProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initNavigilServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new NavigilFrameDecoder());
                    pipeline.addLast("objectDecoder", new NavigilProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initGpsGateServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new GpsGateProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initTeltonikaServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new TeltonikaFrameDecoder());
                    pipeline.addLast("objectDecoder", new TeltonikaProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }
    
    private void initMta6Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                    pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                    pipeline.addLast("objectDecoder", new Mta6ProtocolDecoder(dataManager, protocol, properties, false));
                }
            });
        }
    }

    private void initMta6CanServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                    pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                    pipeline.addLast("objectDecoder", new Mta6ProtocolDecoder(dataManager, protocol, properties, true));
                }
            });
        }
    }
    
    private void initTlt2hServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#', (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(32 * 1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Tlt2hProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }
    
    private void initSyrusServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '<' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new SyrusProtocolDecoder(dataManager, protocol, properties, true));
                }
            });
        }
    }

    private void initWondexServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new WondexFrameDecoder());
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new WondexProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initCellocatorServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new CellocatorFrameDecoder());
                    pipeline.addLast("objectDecoder", new CellocatorProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initGalileoServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new GalileoFrameDecoder());
                    pipeline.addLast("objectDecoder", new GalileoProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initYwtServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r', (byte) '\n' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new YwtProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initTk102Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ']' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Tk102ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }
    
    private void initIntellitracServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new IntellitracFrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new IntellitracProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initXt7Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(256, 20, 1, 5, 0));
                    pipeline.addLast("objectDecoder", new Xt7ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initWialonServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new WialonProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initCarscopServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '^' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new CarscopProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initApelServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 2, 2, 4, 0));
                    pipeline.addLast("objectDecoder", new ApelProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initManPowerServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ';' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new ManPowerProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initGlobalSatServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '!' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new GlobalSatProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initAtrackServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new AtrackFrameDecoder());
                    pipeline.addLast("objectDecoder", new AtrackProtocolDecoder(dataManager, protocol, properties));
                }
            });
            serverList.add(new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("objectDecoder", new AtrackProtocolDecoder(dataManager, protocol, properties));
                }
            });

        }
    }

    private void initPt3000Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) 'd' }; // probably wrong
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new Pt3000ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initRuptelaServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 0, 2, 2, 0));
                    pipeline.addLast("objectDecoder", new RuptelaProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initTopflytechServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ')' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new TopflytechProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initLaipacServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new LaipacProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initAplicomServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new AplicomFrameDecoder());
                    pipeline.addLast("objectDecoder", new AplicomProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initGotopServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new GotopProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initSanavServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '*' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new SanavProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initGatorServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("objectDecoder", new GatorProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initNoranServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("objectDecoder", new NoranProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initM2mServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new FixedLengthFrameDecoder(23));
                    pipeline.addLast("objectDecoder", new M2mProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }
    
    private void initOsmAndServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                    pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                    pipeline.addLast("objectDecoder", new OsmAndProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initEasyTrackServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new EasyTrackProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initTaipServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ConnectionlessBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new SyrusProtocolDecoder(dataManager, protocol, properties, false));
                }
            });
        }
    }

    private void initKhdServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(256, 3, 2));
                    pipeline.addLast("objectDecoder", new KhdProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }
    
    private void initPiligrimServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                    pipeline.addLast("httpAggregator", new HttpChunkAggregator(16384));
                    pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                    pipeline.addLast("objectDecoder", new PiligrimProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initStl060Server(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new Stl060FrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new Stl060ProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initCarTrackServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#', (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new CarTrackProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initMiniFinderServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) ';' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new MiniFinderProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initHaicomServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '*' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new HaicomProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initEelinkServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 3, 2));
                    pipeline.addLast("objectDecoder", new EelinkProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initBoxServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\r' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new BoxProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initFreedomServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new FreedomProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initTelikServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '\0' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new TelikProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initTrackboxServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(1024));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("stringEncoder", new StringEncoder());
                    pipeline.addLast("objectDecoder", new TrackboxProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initVisiontekServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            serverList.add(new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    byte delimiter[] = { (byte) '#' };
                    pipeline.addLast("frameDecoder",
                            new DelimiterBasedFrameDecoder(1024, ChannelBuffers.wrappedBuffer(delimiter)));
                    pipeline.addLast("stringDecoder", new StringDecoder());
                    pipeline.addLast("objectDecoder", new VisiontekProtocolDecoder(dataManager, protocol, properties));
                }
            });
        }
    }

    private void initOrionServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new OrionFrameDecoder());
                    pipeline.addLast("objectDecoder", new OrionProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

    private void initRitiServer(final String protocol) throws SQLException {
        if (isProtocolEnabled(properties, protocol)) {
            TrackerServer server = new TrackerServer(this, new ServerBootstrap(), protocol) {
                @Override
                protected void addSpecificHandlers(ChannelPipeline pipeline) {
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 105, 2, 3, 0));
                    pipeline.addLast("objectDecoder", new RitiProtocolDecoder(dataManager, protocol, properties));
                }
            };
            server.setEndianness(ByteOrder.LITTLE_ENDIAN);
            serverList.add(server);
        }
    }

}
