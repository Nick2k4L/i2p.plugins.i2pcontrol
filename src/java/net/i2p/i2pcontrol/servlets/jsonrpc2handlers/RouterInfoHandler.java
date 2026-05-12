package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

import net.i2p.I2PAppContext;
import net.i2p.crypto.Blinding;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.i2p.data.Base64;
import net.i2p.data.Hash;
import net.i2p.data.router.RouterAddress;
import net.i2p.data.router.RouterInfo;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import net.i2p.router.*;
import net.i2p.router.networkdb.kademlia.FloodfillNetworkDatabaseFacade;
import net.i2p.router.networkdb.reseed.ReseedChecker;
import net.i2p.router.transport.TransportUtil;
import net.i2p.router.transport.ntcp.NTCPTransport;
import net.i2p.router.transport.udp.UDPTransport;


/*
 *  Copyright 2011 hottuna (dev@robertfoss.se)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

public class RouterInfoHandler implements RequestHandler {
    private final JSONRPC2Helper _helper;
    private final RouterContext _context;
    private final AddressBookFiles _files;
    private final TunnelInfoHelper _tunnelInfoHelper;

    public RouterInfoHandler(RouterContext ctx, JSONRPC2Helper helper) {
        _helper = helper;
        _context = ctx;
        _files = new AddressBookFiles(ctx);
        _tunnelInfoHelper = new TunnelInfoHelper(ctx);
    }

    public String[] handledRequests() {
        return new String[] { "RouterInfo" };
    }

    public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {
        if (req.getMethod().equals("RouterInfo")) {

            return process(req);
        } else {
            // Method name not supported
            return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND,
                                        req.getID());
        }
    }

    @SuppressWarnings("unchecked")
    private JSONRPC2Response process(JSONRPC2Request req) {
        JSONRPC2Error err = _helper.validateParams(null, req);
        if (err != null)
            return new JSONRPC2Response(err, req.getID());

        if (_context == null) {
            return new JSONRPC2Response(new JSONRPC2Error(
                                            JSONRPC2Error.INTERNAL_ERROR.getCode(),
                                            "RouterContext was not initialized. Query failed"),
                                        req.getID());
        }
        Map<String, Object> inParams = req.getNamedParams();
        Map outParams = new HashMap();

        if (inParams.containsKey("i2p.router.version")) {
            try {
                Class rvClass = Class.forName("net.i2p.router.RouterVersion");
                java.lang.reflect.Field field = rvClass.getDeclaredField("FULL_VERSION");
                String fullVersion = (String) field.get(new RouterVersion());
                outParams.put("i2p.router.version", fullVersion);
            } catch (Exception e) {} // Ignore
        }

        if (inParams.containsKey("i2p.router.uptime")) {
            Router router = _context.router();
            if (router == null) {
                outParams.put("i2p.router.uptime", 0);
            } else {
                outParams.put("i2p.router.uptime", router.getUptime());
            }
        }

        if (inParams.containsKey("i2p.router.status")) {
            outParams.put("i2p.router.status", _context.throttle().getTunnelStatus());
        }

        if (inParams.containsKey("i2p.router.net.status")) {
            outParams.put("i2p.router.net.status", getNetworkStatus().ordinal());
        }

        if (inParams.containsKey("i2p.router.net.bw.used.inbound.total")){
            outParams.put("i2p.router.net.bw.used.inbound.total", _context.bandwidthLimiter().getTotalAllocatedInboundBytes());
        }

        if (inParams.containsKey("i2p.router.net.bw.used.outbound.total")){
            outParams.put("i2p.router.net.bw.used.outbound.total", _context.bandwidthLimiter().getTotalAllocatedOutboundBytes());
        }

        if (inParams.containsKey("i2p.router.net.bw.inbound.1s")) {
            outParams.put("i2p.router.net.bw.inbound.1s", _context.bandwidthLimiter().getReceiveBps());
        }

        if (inParams.containsKey("i2p.router.net.bw.outbound.1s")) {
            outParams.put("i2p.router.net.bw.outbound.1s", _context.bandwidthLimiter().getSendBps());
        }

        if (inParams.containsKey("i2p.router.net.bw.inbound.15s")) {
            outParams.put("i2p.router.net.bw.inbound.15s", _context.bandwidthLimiter().getReceiveBps15s());
        }

        if (inParams.containsKey("i2p.router.net.bw.outbound.15s")) {
            outParams.put("i2p.router.net.bw.outbound.15s", _context.bandwidthLimiter().getSendBps15s());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.shareratio")) {
            outParams.put("i2p.router.net.tunnels.shareratio", _context.tunnelManager().getShareRatio());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.participating")) {
            outParams.put("i2p.router.net.tunnels.participating", _tunnelInfoHelper.getParticipatingCount());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.participating.info")) {
            outParams.put("i2p.router.net.tunnels.participating.info", _tunnelInfoHelper.getParticipatingInfo());
        }

        if (inParams.containsKey("i2p.router.news")) {
            try {
                Class<?> news = Class.forName("net.i2p.router.web.NewsFeedHelper");
                Object newsObj = news.getDeclaredConstructor().newInstance();
                Method method = news.getDeclaredMethod("getEntries", I2PAppContext.class, int.class, int.class, long.class);
                method.setAccessible(true);
                Object result = method.invoke(newsObj, I2PAppContext.getCurrentContext(), 0, 0,0);
                outParams.put("i2p.router.news", result);

            } catch (Exception e) {
                outParams.put("i2p.router.news", "failure - " + e.getMessage());
            }

        }

        if (inParams.containsKey("i2p.router.logs")) {
            _context.logManager().flush();
            outParams.put("i2p.router.logs",
                    _context.logManager().getBuffer().getMostRecentMessages());
        }



        // Get
        if (inParams.containsKey("i2p.router.net.tunnels.i2ptunnel")){
            TunnelControllerGroup group = TunnelControllerGroup.getInstance(_context);
            //TunnelController controller = group.getControllers().get(0);
            List<Map<String, Object>> info = new ArrayList<>();
            for (TunnelController tc : group.getControllers()) {
                Map<String, Object> map = new HashMap<>();
                    map.put("name", tc.getName());
                    map.put("type", tc.getType());
                    map.put("interface", tc.getListenOnInterface());
                    map.put("port", tc.getListenPort());
                    map.put("targetHost", tc.getTargetHost());
                    map.put("targetPort", tc.getTargetPort());
                    map.put("status", getTunnelStatus(tc));

                    map.put("isClient", tc.isClient());
                    map.put("hostname", tc.getSpoofedHost());
                    map.put("destination", tc.getMyDestHashBase32());
                    map.put("encrypted", getEncryptedBase32(tc));
                    map.put("ssl", tc.getClientOptionProps().getProperty("useSSL", "false").equals("true"));
                    map.put("sharedClient", tc.getSharedClient());
                    map.put("outproxies", tc.getProxyList());
                    map.put("description", tc.getDescription());

                    map.put("targetDestination", tc.getTargetDestination());


                    info.add(map);
            }
            outParams.put("i2p.router.net.tunnels.i2ptunnel", info);
        }

        if (inParams.containsKey("i2p.router.net.tunnels.i2ptunnel.options")) {
            TunnelControllerGroup group = TunnelControllerGroup.getInstance(_context);
            outParams.put("i2p.router.net.tunnels.i2ptunnel.options",
                          extractTunnelOptions(group, inParams.get("i2p.router.net.tunnels.i2ptunnel.options")));
        }

        if (inParams.containsKey("i2p.router.net.tunnels.exploratory.inbound")) {
            outParams.put("i2p.router.net.tunnels.exploratory.inbound",
                          _tunnelInfoHelper.getExploratoryInboundCount());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.exploratory.outbound")) {
            outParams.put("i2p.router.net.tunnels.exploratory.outbound",
                          _tunnelInfoHelper.getExploratoryOutboundCount());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.exploratory.info.list")) {
            outParams.put("i2p.router.net.tunnels.exploratory.info.list", _tunnelInfoHelper.getExploratoryInfo());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.client.inbound")) {
            outParams.put("i2p.router.net.tunnels.client.inbound",
                          _tunnelInfoHelper.getClientInboundCount());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.client.outbound")) {
            outParams.put("i2p.router.net.tunnels.client.outbound",
                          _tunnelInfoHelper.getClientOutboundCount());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.client.info.list")) {
            outParams.put("i2p.router.net.tunnels.client.info.list", _tunnelInfoHelper.getClientInfo());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.client.inbound.list")) {
            outParams.put("i2p.router.net.tunnels.client.inbound.list",
                          _tunnelInfoHelper.getClientInboundList());
        }

        if (inParams.containsKey("i2p.router.net.tunnels.client.outbound.list")) {
            outParams.put("i2p.router.net.tunnels.client.outbound.list",
                          _tunnelInfoHelper.getClientOutboundList());
        }

            if (inParams.containsKey("i2p.router.netdb.peers")) {
            Set<Hash> allRouters = _context.netDb().getAllRouters();
            List<String> peerList = new ArrayList<>();
            for (Hash h : allRouters) {
                peerList.add(h.toBase64());
            }
            outParams.put("i2p.router.netdb.peers", peerList);
        }

        if (inParams.containsKey("i2p.router.netdb.activepeers.info")) {
            List<Hash> active = _context.commSystem().getEstablished();
            List<String> peerInfoList = new ArrayList<>();
            for (Hash h : active) {
                RouterInfo ri = _context.netDb().lookupRouterInfoLocally(h);
                if (ri != null) {
                    byte[] data = ri.toByteArray();
                    peerInfoList.add(Base64.encode(data));
                }
            }
            outParams.put("i2p.router.netdb.activepeers.info", peerInfoList);
        }

        if (inParams.containsKey("i2p.router.netdb.ntcp.limit")) {
            NTCPTransport ntcp = new NTCPTransport(_context, _context.commSystem().getXDHFactory());
            outParams.put("i2p.router.netdb.ntcp.limit", ntcp.getMaxConnections());
        }

        if (inParams.containsKey("i2p.router.netdb.ssu.limit")) {
           UDPTransport udp = new UDPTransport(_context, _context.commSystem().getXDHFactory());
           outParams.put("i2p.router.netdb.ssu.limit", udp.getMaxConnections());
        }


        if (inParams.containsKey("i2p.router.netdb.bannedpeers")) {
            // TODO: On newer versions ensure we are doing this instead:
            //   Map<Hash, Banlist.Entry> banEntries = new HashMap<Hash, Banlist.Entry>(1024);
            //   _context.banlist().getEntries(banEntries);
            //   and then return banEntries.
            //
            //
            Map<Hash, Banlist.Entry> banEntries = _context.banlist().getEntries();

            outParams.put("i2p.router.netdb.bannedpeers", banEntries);
        }
        if (inParams.containsKey("i2p.router.netdb.knownpeers")) {
            outParams.put("i2p.router.netdb.knownpeers", Math.max(_context.netDb().getKnownRouters() - 1, 0));
        }

        if (inParams.containsKey("i2p.router.netdb.activepeers")) {
            outParams.put("i2p.router.netdb.activepeers", _context.commSystem().countActivePeers());
        }

        if (inParams.containsKey("i2p.router.netdb.fastpeers")) {
            outParams.put("i2p.router.netdb.fastpeers", _context.profileOrganizer().countFastPeers());
        }

        if (inParams.containsKey("i2p.router.netdb.highcapacitypeers")) {
            outParams.put("i2p.router.netdb.highcapacitypeers", _context.profileOrganizer().countHighCapacityPeers());
        }

        if (inParams.containsKey("i2p.router.netdb.isreseeding")) {
            ReseedChecker _reseedChecker = _context.netDb().reseedChecker();
            outParams.put("i2p.router.netdb.isreseeding", _reseedChecker.inProgress());
        }

        if (inParams.containsKey("i2p.router.id")) {
            RouterInfo ri = _context.router().getRouterInfo();
            if (ri != null) {
                Hash hash = ri.getIdentity().getHash();
                outParams.put("i2p.router.id", hash.toBase64());
            } else {
                outParams.put("i2p.router.id", null);
            }
        }

        if (inParams.containsKey("i2p.router.info")) {
            RouterInfo ri = _context.router().getRouterInfo();
            if (ri != null) {
                byte[] data = ri.toByteArray();
                outParams.put("i2p.router.info", Base64.encode(data));
            } else {
                outParams.put("i2p.router.info", null);
            }
        }

        if (inParams.containsKey("i2p.router.clockskew")) {
            RouterInfo ri = _context.router().getRouterInfo();
            outParams.put("i2p.router.clockskew", ri != null ? _context.commSystem().getFramedAveragePeerClockSkew(33) : null);
        }

        if (inParams.containsKey("i2p.router.netdb.activepeers.list")) {
            List<Hash> active = _context.commSystem().getEstablished();
            List<String> peerList = new ArrayList<>();
            for (Hash h : active) peerList.add(h.toBase64());
            outParams.put("i2p.router.netdb.activepeers.list", peerList);
        }

        if (inParams.containsKey("i2p.router.netdb.activepeers.info")) {
            List<Hash> active = _context.commSystem().getEstablished();
            List<String> peerInfoList = new ArrayList<>();
            for (Hash h : active) {
                RouterInfo ri = _context.netDb().lookupRouterInfoLocally(h);
                if (ri != null) peerInfoList.add(Base64.encode(ri.toByteArray()));
            }
            outParams.put("i2p.router.netdb.activepeers.info", peerInfoList);
        }

        if (inParams.containsKey("i2p.router.netdb.peers.list")) {
            Set<Hash> allRouters = _context.netDb().getAllRouters();
            List<String> peerList = new ArrayList<>();
            for (Hash h : allRouters) peerList.add(h.toBase64());
            outParams.put("i2p.router.netdb.peers.list", peerList);
        }

        if (inParams.containsKey("i2p.router.netdb.peers.info")) {
            Set<Hash> allRouters = _context.netDb().getAllRouters();
            List<String> peerInfoList = new ArrayList<>();
            for (Hash h : allRouters) {
                RouterInfo ri = _context.netDb().lookupRouterInfoLocally(h);
                if (ri != null) peerInfoList.add(Base64.encode(ri.toByteArray()));
            }
            outParams.put("i2p.router.netdb.peers.info", peerInfoList);
        }


        // Address booking logic. Originally had 3 pre-existing functions,
        // but they were all doing the same thing with different property keys and file names,
        // so now we loop through a map of the 3 types.
        Map<String, String> addressBooks = new LinkedHashMap<>();
        addressBooks.put("i2p.router.addressbook.private.list", "privatehosts.txt");
        addressBooks.put("i2p.router.addressbook.local.list",   "userhosts.txt");
        addressBooks.put("i2p.router.addressbook.router.list",  "hosts.txt");

        for (Map.Entry<String, String> book : addressBooks.entrySet()) {
            if (inParams.containsKey(book.getKey())) {
                Properties opts = new Properties();
                opts.setProperty("list", book.getValue());
                outParams.put(book.getKey(), extractDestinations(opts));
            }
        }

        if (inParams.containsKey("i2p.router.addressbook.config")) {
            try {
                File config = _files.getAddressBookConfigFile();
                Map<String, Object> configInfo = new HashMap<>();
                configInfo.put("path", config.getAbsolutePath());
                configInfo.put("entries", AddressBookFiles.toStringMap(_files.loadAddressBookConfig()));
                outParams.put("i2p.router.addressbook.config", configInfo);
            } catch (IOException e) {
                outParams.put("i2p.router.addressbook.config", Collections.emptyMap());
            }
        }

        if (inParams.containsKey("i2p.router.addressbook.subscriptions")) {
            try {
                File subscriptions = _files.getSubscriptionsFile();
                Map<String, Object> subscriptionInfo = new HashMap<>();
                subscriptionInfo.put("path", subscriptions.getAbsolutePath());
                subscriptionInfo.put("entries", _files.loadSubscriptions(subscriptions));
                outParams.put("i2p.router.addressbook.subscriptions", subscriptionInfo);
            } catch (IOException e) {
                outParams.put("i2p.router.addressbook.subscriptions", Collections.emptyMap());
            }
        }

        if (inParams.containsKey("i2p.router.addressbook.published.list")) {
            try {
                File published = _files.getPublishedAddressBook();
                outParams.put("i2p.router.addressbook.published.list", extractPublishedDestinations(published));
            } catch (IOException e) {
                outParams.put("i2p.router.addressbook.published.list", Collections.emptyList());
            }
        }


        if (inParams.containsKey("i2p.router.netdb.activepeers.stats")) {
            List<Hash> active = _context.commSystem().getEstablished();
            List<Map<String, Object>> peerStats = new ArrayList<>();
            long now = System.currentTimeMillis();

            for (Hash h : active) {
                Map<String, Object> stat = new HashMap<>();
                stat.put("hash", h.toBase64());
                stat.put("country", _context.commSystem().getCountry(h));

                // Peer software version from RouterInfo options map.
                RouterInfo peerRi = _context.netDb().lookupRouterInfoLocally(h);
                if (peerRi != null) {
                    String v = peerRi.getOption("router.version");
                    stat.put("version", v != null ? v : "unknown");
                } else {
                    stat.put("version", "unknown");
                }

                for (net.i2p.router.transport.Transport t : _context.commSystem().getTransports().values()) {
                    if (!t.isEstablished(h)) continue;

                    stat.put("activeTransport", t.getStyle());

                    Object session = findSession(t, h);
                    if (session == null) {
                        stat.put("sessionError", "not found for " + t.getStyle());
                        break;
                    }

                    String cls = session.getClass().getSimpleName();
                    boolean isSSU = cls.equals("PeerState2") || cls.equals("PeerState");

                    // --- shared fields ---
                    stat.put("inbound",    reflectBoolean(session, "isInbound"));
                    stat.put("ipv6",       reflectBoolean(session, "isIPv6"));
                    stat.put("clockSkew",  reflectLong(session, "getClockSkew"));
                    stat.put("rx",         reflectLong(session, "getMessagesReceived"));
                    stat.put("tx",         reflectLong(session, "getMessagesSent"));
                    stat.put("backlogged", reflectBoolean(session, "isBacklogged"));

                    if (isSSU) {
                        // uptime: now minus the epoch ms when the key was established
                        long keyEstablished = reflectLong(session, "getKeyEstablishedTime");
                        stat.put("uptime", keyEstablished > 0 ? now - keyEstablished : -1L);

                        // idle: ms since last receive / last send
                        long lastRx = reflectLong(session, "getLastReceiveTime");
                        long lastTx = reflectLong(session, "getLastSendTime");
                        stat.put("idleRx", lastRx > 0 ? now - lastRx : -1L);
                        stat.put("idleTx", lastTx > 0 ? now - lastTx : -1L);

                        // smoothed rates not available per-peer on SSU2
                        stat.put("recvRateBps", -1);
                        stat.put("sendRateBps", -1);

                        // dup counts
                        stat.put("dupRx", reflectLong(session, "getPacketsReceivedDuplicate"));
                        stat.put("dupTx", reflectLong(session, "getPacketsRetransmitted"));

                        stat.put("outboundQueue", reflectLong(session, "getOutboundMessageCount"));
                    } else {
                        // NTCP2 (NTCPConnection)
                        stat.put("uptime", reflectLong(session, "getUptime"));

                        // getTimeSinceReceive / getTimeSinceSend take a long arg (now)
                        stat.put("idleRx", reflectLongWithLongArg(session, "getTimeSinceReceive", now));
                        stat.put("idleTx", reflectLongWithLongArg(session, "getTimeSinceSend", now));

                        // smoothed rates (exponential moving average, bytes/sec)
                        stat.put("recvRateBps", reflectFloat(session, "getRecvRate"));
                        stat.put("sendRateBps", reflectFloat(session, "getSendRate"));

                        // dup not tracked per-peer on NTCP2
                        stat.put("dupRx", -1);
                        stat.put("dupTx", -1);

                        stat.put("outboundQueue", reflectLong(session, "getOutboundQueueSize"));
                    }

                    break;
                }

                peerStats.add(stat);
            }
            outParams.put("i2p.router.netdb.activepeers.stats", peerStats);
        }

        return new JSONRPC2Response(outParams, req.getID());
    }

    private static String getEncryptedBase32(TunnelController tc) {
        Destination dest = tc.getDestination(); // running tunnels only
        if (dest == null)
            return "";

        Properties opts = tc.getClientOptionProps();
        int mode = getEncryptMode(opts);

        // Matches IndexBean: only blinded LS2 modes have a encrypted b32 address.
        if (mode > 1 && mode < 10) {
            boolean hasSecret = !opts.getProperty("i2cp.leaseSetSecret", "").isEmpty();
            boolean requireSecret = hasSecret && (mode == 3 || mode == 5 || mode == 7 || mode == 9);
            boolean requireAuth = mode >= 4;

            return Blinding.encode(dest.getSigningPublicKey(), requireSecret, requireAuth);
        }

        return "";
    }

    // used indexBean & general helper as a reference, similar logic
    private static int getEncryptMode(Properties opts) {
        if (Boolean.parseBoolean(opts.getProperty("i2cp.encryptLeaseSet")))
            return 1;

        String leaseSetType = opts.getProperty("i2cp.leaseSetType", "1");
        if ("5".equals(leaseSetType)) {
            int mode;
            String authType = opts.getProperty("i2cp.leaseSetAuthType", "0");

            if ("2".equals(authType)) {
                mode = opts.getProperty("i2cp.leaseSetClient.psk.0") != null ? 6 : 4;
            } else if ("1".equals(authType)) {
                mode = 8;
            } else {
                mode = 2;
            }

            if (!opts.getProperty("i2cp.leaseSetSecret", "").isEmpty())
                mode++;

            return mode;
        }

        if ("3".equals(leaseSetType))
            return 10;

        return 0;
    }



    private List<Map<String, String>> extractDestinations(Properties opts) {
        List<Map<String, String>> list = new ArrayList<>();
        Map<String, Destination> entries = _context.namingService().getEntries(opts);

        for (Map.Entry<String, Destination> entry : entries.entrySet()) {
            Properties stored = new Properties();
            Destination dest = _context.namingService().lookup(entry.getKey(), opts, stored);
            if (dest == null)
                dest = entry.getValue();
            Map<String, String> record = new HashMap<>();
            record.put("hostname", entry.getKey());
            record.put("b32", dest.toBase32());
            record.put("b64", dest.getHash().toBase64());
            record.put("signing public key", dest.getSigningPublicKey().toString());
            record.put("public key", dest.getPublicKey().toString());
            record.put("certificate", dest.getCertificate().toString());
            record.put("destination", dest.toBase64());
            record.put("added", stored.getProperty("a", ""));
            list.add(record);
        }
        return list;
    }

    private List<Map<String, String>> extractPublishedDestinations(File published) {
        List<Map<String, String>> list = new ArrayList<>();

        try {
            Properties props = _files.loadPublishedEntries(published);

            for (Map.Entry<Object, Object> entry : props.entrySet()) {

                try {
                    Destination dest = new Destination((String) entry.getValue());
                    Map<String, String> record = new HashMap<>();
                    record.put("hostname", entry.getKey().toString());
                    record.put("b32", dest.toBase32());
                    record.put("b64", dest.toBase64());
                    record.put("signing public key", dest.getSigningPublicKey().toString());
                    record.put("public key", dest.getPublicKey().toString());
                    record.put("certificate", dest.getCertificate().toString());
                    record.put("destination", dest.toBase64());
                    list.add(record);
                } catch (DataFormatException dfe) {
                    // skip bad entry
                }
            }
        } catch (IOException ioe) {
            // return empty list or handle error
        }

        return list;
    }


    private Object findSession(net.i2p.router.transport.Transport t, net.i2p.data.Hash h) {
        // SSU/SSU2 — public getPeerState(Hash)
        try {
            java.lang.reflect.Method m = t.getClass().getMethod("getPeerState", net.i2p.data.Hash.class);
            Object r = m.invoke(t, h);
            if (r != null) return r;
        } catch (Exception ignored) {}

        // SSU/SSU2 — package-private getPeerState(Hash)
        try {
            java.lang.reflect.Method m = t.getClass().getDeclaredMethod("getPeerState", net.i2p.data.Hash.class);
            m.setAccessible(true);
            Object r = m.invoke(t, h);
            if (r != null) return r;
        } catch (Exception ignored) {}

        // NTCP2 — iterate getPeers(), match via getRemotePeer().getHash()
        try {
            java.lang.reflect.Method getPeers = t.getClass().getMethod("getPeers");
            java.util.Collection<?> peers = (java.util.Collection<?>) getPeers.invoke(t);
            for (Object conn : peers) {
                try {
                    Object identity = conn.getClass().getMethod("getRemotePeer").invoke(conn);
                    if (identity == null) continue;
                    Object peerHash = identity.getClass().getMethod("getHash").invoke(identity);
                    if (h.equals(peerHash)) return conn;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return null;
    }

    private Object extractTunnelOptions(TunnelControllerGroup group, Object selector) {
        if (group == null)
            return Collections.emptyList();
        if (selector instanceof String) {
            TunnelController controller = findTunnelControllerByName(group, ((String) selector).trim());
            return controller != null ? extractTunnelOptions(controller) : null;
        }
        List<Map<String, Object>> info = new ArrayList<>();
        for (TunnelController controller : group.getControllers()) {
            info.add(extractTunnelOptions(controller));
        }
        return info;
    }

    private TunnelController findTunnelControllerByName(TunnelControllerGroup group, String name) {
        for (TunnelController controller : group.getControllers()) {
            if (controller.getName().equals(name))
                return controller;
        }
        return null;
    }

    private Map<String, Object> extractTunnelOptions(TunnelController tc) {
        Map<String, Object> tunnelInfo = new LinkedHashMap<>();
        Properties config = tc.getConfig("");
        Map<String, String> rawConfig = new TreeMap<>();
        Map<String, String> optionConfig = new TreeMap<>();
        Map<String, String> baseConfig = new TreeMap<>();

        for (Map.Entry<Object, Object> entry : config.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            rawConfig.put(key, value);
            if (key.startsWith(TunnelController.PFX_OPTION)) {
                optionConfig.put(key.substring(TunnelController.PFX_OPTION.length()), value);
            } else {
                baseConfig.put(key, value);
            }
        }

        tunnelInfo.put("name", tc.getName());
        tunnelInfo.put("type", tc.getType());
        tunnelInfo.put("client", tc.isClient());
        tunnelInfo.put("description", tc.getDescription());
        tunnelInfo.put("status", getTunnelStatus(tc));
        tunnelInfo.put("rawConfig", rawConfig);
        tunnelInfo.put("config", baseConfig);
        tunnelInfo.put("options", optionConfig);
        return tunnelInfo;
    }

    private static String getTunnelStatus(TunnelController tc) {
        if (tc.getIsStandby()) return "standby";
        if (tc.getIsRunning()) return "running";
        if (tc.getIsStarting())  return "starting";
        return "stopped";
    }


    private long reflectLong(Object obj, String... methodNames) {
        for (String name : methodNames) {
            try {
                Object r = obj.getClass().getMethod(name).invoke(obj);
                if (r instanceof Number) return ((Number) r).longValue();
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {}
            try {
                java.lang.reflect.Method m = obj.getClass().getDeclaredMethod(name);
                m.setAccessible(true);
                Object r = m.invoke(obj);
                if (r instanceof Number) return ((Number) r).longValue();
            } catch (Exception ignored) {}
        }
        return -1L;
    }

    private long reflectLongWithLongArg(Object obj, String methodName, long arg) {
        try {
            Object r = obj.getClass().getMethod(methodName, long.class).invoke(obj, arg);
            if (r instanceof Number) return ((Number) r).longValue();
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Method m = obj.getClass().getDeclaredMethod(methodName, long.class);
            m.setAccessible(true);
            Object r = m.invoke(obj, arg);
            if (r instanceof Number) return ((Number) r).longValue();
        } catch (Exception ignored) {}
        return -1L;
    }

    private float reflectFloat(Object obj, String methodName) {
        try {
            Object r = obj.getClass().getMethod(methodName).invoke(obj);
            if (r instanceof Number) return ((Number) r).floatValue();
        } catch (Exception ignored) {}
        return -1f;
    }

    private boolean reflectBoolean(Object obj, String methodName) {
        try {
            Object r = obj.getClass().getMethod(methodName).invoke(obj);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Exception ignored) {}
        return false;
    }

    private static enum NETWORK_STATUS {
        OK,
        TESTING,
        FIREWALLED,
        HIDDEN,
        WARN_FIREWALLED_AND_FAST,
        WARN_FIREWALLED_AND_FLOODFILL,
        WARN_FIREWALLED_WITH_INBOUND_TCP,
        WARN_FIREWALLED_WITH_UDP_DISABLED,
        ERROR_I2CP,
        ERROR_CLOCK_SKEW,
        ERROR_PRIVATE_TCP_ADDRESS,
        ERROR_SYMMETRIC_NAT,
        ERROR_UDP_PORT_IN_USE,
        ERROR_NO_ACTIVE_PEERS_CHECK_CONNECTION_AND_FIREWALL,
        ERROR_UDP_DISABLED_AND_TCP_UNSET,
    };

    private NETWORK_STATUS getNetworkStatus() {
        if (_context.router().getUptime() > 60 * 1000
                && (!_context.router().gracefulShutdownInProgress())
                && !_context.clientManager().isAlive())
            return (NETWORK_STATUS.ERROR_I2CP);
        long skew = _context.commSystem().getFramedAveragePeerClockSkew(33);
        // Display the actual skew, not the offset
        if (Math.abs(skew) > 60 * 1000)
            return NETWORK_STATUS.ERROR_CLOCK_SKEW;
        if (_context.router().isHidden())
            return (NETWORK_STATUS.HIDDEN);

        int status = _context.commSystem().getStatus().getCode();
        switch (status) {
        case CommSystemFacade.STATUS_OK:
            RouterAddress ra = _context.router().getRouterInfo().getTargetAddress("NTCP");
            if (ra == null || TransportUtil.isPubliclyRoutable(ra.getIP(), true))
                return NETWORK_STATUS.OK;
            return NETWORK_STATUS.ERROR_PRIVATE_TCP_ADDRESS;
        case CommSystemFacade.STATUS_DIFFERENT:
            return NETWORK_STATUS.ERROR_SYMMETRIC_NAT;
        case CommSystemFacade.STATUS_REJECT_UNSOLICITED:
            if (_context.router().getRouterInfo().getTargetAddress("NTCP") != null)
                return NETWORK_STATUS.WARN_FIREWALLED_WITH_INBOUND_TCP;
            if (((FloodfillNetworkDatabaseFacade) _context.netDb()).floodfillEnabled())
                return NETWORK_STATUS.WARN_FIREWALLED_AND_FLOODFILL;
            if (_context.router().getRouterInfo().getCapabilities().indexOf('O') >= 0)
                return NETWORK_STATUS.WARN_FIREWALLED_AND_FAST;
            return NETWORK_STATUS.FIREWALLED;
        case CommSystemFacade.STATUS_HOSED:
            return NETWORK_STATUS.ERROR_UDP_PORT_IN_USE;
        case CommSystemFacade.STATUS_UNKNOWN: // fallthrough
        default:
            ra = _context.router().getRouterInfo().getTargetAddress("SSU");
            if (ra == null && _context.router().getUptime() > 5 * 60 * 1000) {
                if (_context.commSystem().countActivePeers() <= 0)
                    return NETWORK_STATUS.ERROR_NO_ACTIVE_PEERS_CHECK_CONNECTION_AND_FIREWALL;
                else if (_context.getProperty(NTCPTransport.PROP_I2NP_NTCP_HOSTNAME) == null || _context.getProperty(NTCPTransport.PROP_I2NP_NTCP_PORT) == null)
                    return NETWORK_STATUS.ERROR_UDP_DISABLED_AND_TCP_UNSET;
                else
                    return NETWORK_STATUS.WARN_FIREWALLED_WITH_UDP_DISABLED;
                }
                return NETWORK_STATUS.TESTING;
        }
    }
}
