package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import net.i2p.client.I2PClient;
import net.i2p.i2ptunnel.I2PTunnelClientBase;
import net.i2p.i2ptunnel.I2PTunnelConnectClient;
import net.i2p.i2ptunnel.I2PTunnelHTTPClient;
import net.i2p.i2ptunnel.I2PTunnelHTTPClientBase;
import net.i2p.i2ptunnel.I2PTunnelIRCClient;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import net.i2p.i2ptunnel.socks.I2PSOCKSTunnel;
import net.i2p.router.RouterContext;
import net.i2p.util.PasswordManager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

public class TunnelManagerHandler implements RequestHandler {
    private final JSONRPC2Helper _helper;
    private final RouterContext _context;
    private TunnelControllerGroup _group;

    private static final String[] requiredArgs = {"Name", "Action"};


    // Options / hardcoded strings for config keys, allows us to reuse them across different types of tunnels and avoid typos
    private static final String OPT = TunnelController.PFX_OPTION;
    private static final String PROP_STREAMING_CONNECT_DELAY = "i2p.streaming.connectDelay";
    private static final String PROP_STREAMING_MAX_WINDOW_SIZE = "i2p.streaming.maxWindowSize";
    private static final String PROP_REDUCE_ON_IDLE = "i2cp.reduceOnIdle";
    private static final String PROP_CLOSE_ON_IDLE = "i2cp.closeOnIdle";
    private static final String PROP_NEW_DEST_ON_RESUME = "i2cp.newDestOnResume";
    private static final String PROP_PERSISTENT_CLIENT_KEY = "persistentClientKey";
    private static final String PROP_DELAY_OPEN = "i2cp.delayOpen";
    private static final String PROP_REDUCE_QUANTITY = "i2cp.reduceQuantity";
    private static final String PROP_REDUCE_IDLE_TIME = "i2cp.reduceIdleTime";
    private static final String PROP_CLOSE_IDLE_TIME = "i2cp.closeIdleTime";
    private static final String SHARED_CLIENT_NICKNAME = "shared clients";
    private static final String[] NO_SHOW_OPTS = {
        "inbound.length", "outbound.length", "inbound.lengthVariance", "outbound.lengthVariance",
        "inbound.backupQuantity", "outbound.backupQuantity", "inbound.quantity", "outbound.quantity",
        "inbound.nickname", "outbound.nickname", PROP_STREAMING_CONNECT_DELAY, PROP_STREAMING_MAX_WINDOW_SIZE,
        I2PTunnelIRCClient.PROP_DCC
    };
    private static final String[] BOOLEAN_CLIENT_OPTS = {
        PROP_REDUCE_ON_IDLE, PROP_CLOSE_ON_IDLE, PROP_NEW_DEST_ON_RESUME, PROP_PERSISTENT_CLIENT_KEY,
        PROP_DELAY_OPEN, I2PTunnelClientBase.PROP_USE_SSL
    };
    private static final String[] BOOLEAN_PROXY_OPTS = {
        I2PTunnelHTTPClientBase.PROP_OUTPROXY_AUTH,
        I2PTunnelHTTPClientBase.PROP_USE_OUTPROXY_PLUGIN,
        I2PTunnelHTTPClient.PROP_USER_AGENT,
        I2PTunnelHTTPClient.PROP_REFERER,
        I2PTunnelHTTPClient.PROP_ACCEPT,
        I2PTunnelHTTPClient.PROP_INTERNAL_SSL,
        I2PTunnelHTTPClient.PROP_SSL_SET
    };
    private static final String[] OTHER_CLIENT_OPTS = {
        PROP_REDUCE_IDLE_TIME, PROP_REDUCE_QUANTITY, PROP_CLOSE_IDLE_TIME,
        I2PTunnelHTTPClientBase.PROP_OUTPROXY_USER, I2PTunnelHTTPClientBase.PROP_OUTPROXY_PW,
        I2PSOCKSTunnel.PROP_OUTPROXY_TYPE,
        I2PTunnelHTTPClient.PROP_JUMP_SERVERS,
        I2PTunnelHTTPClientBase.PROP_AUTH,
        I2PClient.PROP_SIGTYPE,
        I2PTunnelHTTPClient.PROP_SSL_OUTPROXIES,
        "inbound.randomKey", "outbound.randomKey", "i2cp.leaseSetSigningPrivateKey",
        "i2cp.leaseSetPrivateKey", "i2cp.leaseSetEncType"
    };
    private static final String[] OTHER_PROXY_OPTS = {
        "proxyUsername", "proxyPassword"
    };
    private static final Set<String> NO_SHOW_SET = new HashSet<>(128);
    private static final Set<String> NON_PROXY_NO_SHOW_SET = new HashSet<>(4);
    static {
        NO_SHOW_SET.addAll(Arrays.asList(NO_SHOW_OPTS));
        NO_SHOW_SET.addAll(Arrays.asList(BOOLEAN_CLIENT_OPTS));
        NO_SHOW_SET.addAll(Arrays.asList(BOOLEAN_PROXY_OPTS));
        NO_SHOW_SET.addAll(Arrays.asList(OTHER_CLIENT_OPTS));
        NON_PROXY_NO_SHOW_SET.addAll(Arrays.asList(OTHER_PROXY_OPTS));
    }


    public TunnelManagerHandler(RouterContext ctx, JSONRPC2Helper helper) {
        _context = ctx;
        _helper = helper;
    }

    public String[] handledRequests() {return new String[]{"TunnelManager"};}

    public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {
        if (req.getMethod().equals("TunnelManager")) {
            Map<String, Object> inParams = req.getNamedParams();

            JSONRPC2Error err = _helper.validateParams(requiredArgs, req, JSONRPC2Helper.USE_NO_AUTH);
            if (err != null)
                return new JSONRPC2Response(err, req.getID());

            String name = (String) inParams.get("Name");
            String action = (String) inParams.get("Action");

            Map<String, Object> outParams = new HashMap<>();

            if (_group == null) {
                _group = TunnelControllerGroup.getInstance(_context); // retry getting it
            }

            if (_group == null) {
                // Still null I2P tunnel manager isn't ready yet
                outParams.put("status", "error - tunnel controller not available, group is null");
                return new JSONRPC2Response(outParams, req.getID());
            }

            if (inParams.containsKey("All")) {
                List<String> results = actionToAll(action);
                if (results != null) {
                    outParams.put("status", "success - " + action);
                    outParams.put("results", results);
                    return new JSONRPC2Response(outParams, req.getID());
                }
                outParams.put("status", "error - no action");
                return new JSONRPC2Response(outParams, req.getID());
            }

            if (action.equals("create")) {
                try {
                    String type = getType(inParams);
                    switch (type) {
                        case TunnelController.TYPE_STD_CLIENT:
                        case TunnelController.TYPE_HTTP_CLIENT:
                        case TunnelController.TYPE_IRC_CLIENT:
                        case TunnelController.TYPE_SOCKS_IRC:
                        case TunnelController.TYPE_SOCKS:
                        case TunnelController.TYPE_CONNECT:
                        case TunnelController.TYPE_STREAMR_CLIENT:
                            List<String> results = createClient(inParams, type);
                            outParams.put("status", "success - created tunnel " + name.trim());
                            outParams.put("results", results);
                            return new JSONRPC2Response(outParams, req.getID());

                        case TunnelController.TYPE_HTTP_SERVER:
                        case TunnelController.TYPE_STD_SERVER:
                        case TunnelController.TYPE_HTTP_BIDIR_SERVER:
                        case TunnelController.TYPE_IRC_SERVER:
                        case TunnelController.TYPE_STREAMR_SERVER:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());


                        default:
                            outParams.put("status", "error - unknown type");
                            return new JSONRPC2Response(outParams, req.getID());
                    }
                } catch (IllegalArgumentException iae) {
                    outParams.put("status", "error - " + iae.getMessage());
                } catch (IOException ioe) {
                    outParams.put("status", "error - failed to save tunnel: " + ioe.getMessage());
                }
                return new JSONRPC2Response(outParams, req.getID());
            }

            if ("get".equalsIgnoreCase(action)) {
                TunnelController controllerForGet = findTunnelControllerByName(name.trim());
                if (controllerForGet == null) {
                    outParams.put("status", "error - tunnel " + name.trim() + " not found");
                    return new JSONRPC2Response(outParams, req.getID());
                }
                outParams.put("status", "success - options for " + controllerForGet.getName());
                outParams.put("i2p.router.net.tunnels.i2ptunnel.options",
                        extractTunnelOptions(controllerForGet));
                return new JSONRPC2Response(outParams, req.getID());
            }

            /// ---- Methods per name ---- \\\
            TunnelController controller = findTunnelControllerByName(name.trim());

            // possibly put create here, for now its okay
            if (controller != null) {
                String controllerName = controller.getName();
                switch (action) {
                    case "start":
                        controller.startTunnel();
                        outParams.put("status", "success - starting tunnel " + controllerName);
                        return new JSONRPC2Response(outParams, req.getID());
                    case "stop":
                        controller.stopTunnel();
                        outParams.put("status", "success - stopping tunnel " + controllerName);
                        return new JSONRPC2Response(outParams, req.getID());
                    case "restart":
                        controller.restartTunnel();
                        outParams.put("status", "success - restarting tunnel " + controllerName);
                        return new JSONRPC2Response(outParams, req.getID());
                    case "delete":
                        _group.removeController(controller);
                        try {
                            _group.removeConfig(controller);
                        } catch (Exception e) {
                            outParams.put("status", "error - failed to remove config for tunnel " + controllerName);
                            return new JSONRPC2Response(outParams, req.getID());
                        }
                        outParams.put("status", "success - deleting tunnel - " + controllerName);
                        return new JSONRPC2Response(outParams, req.getID());

                    default:
                        outParams.put("status", "error - unknown action");
                        return new JSONRPC2Response(outParams, req.getID());
                }
            }

            // Can use this to display config options for tunnel.
            // Exposes a lot of information about a tunnel.
            if (inParams.containsKey("Config")) {
                outParams.put("status", "error - tunnel " + name.trim() + " not found");
                return new JSONRPC2Response(outParams, req.getID());
            }

            outParams.put("status", "error - tunnel controller not available");
            return new JSONRPC2Response(outParams, req.getID());
        } else {
            return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
        }
    }

    // adds an action to all of them
    private List<String> actionToAll(String action) {
        // this type of switch failed since we are on a older version JDK 8
        switch (action) {
            case "start":
                return _group.startAllControllers();
            case "stop":
                return _group.stopAllControllers();
            case "restart":
                return _group.restartAllControllers();
            default:
                return null;
        }
    }


    // TODO: each advanced configuration has some shared
    //  field to it meaning we can break up this function into reusable pieces
    //  allow us to implement logic faster between other client / services types
    //  some I have seen: tunnel length options, tunnel quantity for example.


    // TODO: Non advanced - Name, Description, Port, auto start


    // --- Common Gets, allows us to reuse validation logic across different types of tunnels --- \\\
    private String getType(Map<String, Object> inParams) {
        String type = (String) inParams.get("Type");
        if (type == null || type.trim().isEmpty())
            throw new IllegalArgumentException("Type is required");
        return type.trim();
    }

    private String getName(Map<String, Object> inParams) {
        String name = (String) inParams.get("Name");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (findTunnelControllerByName(name.trim()) != null) {
            throw new IllegalArgumentException("tunnel " + name.trim() + " already exists");
        }
        return name;
    }

    private int getPort(Map<String, Object> inParams) {
        Object portObj = inParams.get("Port");
        if (portObj == null) {
            throw new IllegalArgumentException("Port is required");
        }
        int port = ((Number) portObj).intValue();
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        return port;
    }

    private boolean getShared(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("Shared"));
    }

    private boolean getStartOnLoad(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("StartOnLoad"));
    }

    private String getDescription(Map<String, Object> inParams){
        return (String) inParams.get("Description");
    }

    private String getReachableBy(Map<String, Object> inParams) {
        return (String) inParams.get("ReachableBy");
    }

    private String getProfile(Map<String, Object> inParams) {
        return (String) inParams.get("Profile");
    }

    private boolean getConnectDelay(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("ConnectDelay"));
    }

    private String getSigType(Map<String, Object> inParams) {
        return (String) inParams.get("SigType");
    }

    private String getEncType(Map<String, Object> inParams) {
        String encType = (String) inParams.get("EncType");
        return encType != null ? encType : (String) inParams.get("Encrypted");
    }

    private String getCustomOptions(Map<String, Object> inParams) {
        return (String) inParams.get("CustomOptions");
    }

    private String getTargetDestination(Map<String, Object> inParams) {
        String destination = (String) inParams.get("TargetDestination");
        return destination != null ? destination : (String) inParams.get("Destination");
    }

    private String getTargetHost(Map<String, Object> inParams) {
        String targetHost = (String) inParams.get("TargetHost");
        return targetHost != null ? targetHost : (String) inParams.get("Host");
    }

    private String getSSLProxies(Map<String, Object> inParams) {
        return (String) inParams.get("SSLProxies");
    }

    private String getJumpList(Map<String, Object> inParams) {
        return (String) inParams.get("JumpList");
    }

    private Integer getTunnelLength(Map<String, Object> inParams) {
        Object tunnelLengthObj = inParams.get("TunnelLength");
        return tunnelLengthObj != null ? ((Number) tunnelLengthObj).intValue() : null;
    }

    private Integer getTunnelVariance(Map<String, Object> inParams) {
        Object tunnelVarianceObj = inParams.get("TunnelVariance");
        return tunnelVarianceObj != null ? ((Number) tunnelVarianceObj).intValue() : null;
    }

    private Integer getTunnelQuantity(Map<String, Object> inParams) {
        Object tunnelQuantityObj = inParams.get("TunnelQuantity");
        return tunnelQuantityObj != null ? ((Number) tunnelQuantityObj).intValue() : null;
    }

    private Integer getBackupQuantity(Map<String, Object> inParams) {
        Object backupQuantityObj = inParams.get("TunnelBackupQuantity");
        return backupQuantityObj != null ? ((Number) backupQuantityObj).intValue() : null;
    }

    private boolean getDelayOpen(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("DelayOpen"));
    }

    private boolean getReduce(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("Reduce"));
    }

    private boolean getClose(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("Close"));
    }

    private boolean getUseSSL(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("UseSSL"));
    }

    private boolean getUseOutproxyPlugin(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("UseOutproxyPlugin"));
    }

    private boolean getProxyAuth(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("ProxyAuth"));
    }

    private boolean getOutproxyAuth(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("OutproxyAuth"));
    }

    private boolean getDCC(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("DCC")) || Boolean.TRUE.equals(inParams.get("EnableDCC"));
    }

    private boolean getAllowUserAgent(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("AllowUserAgent"));
    }

    private boolean getAllowReferer(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("AllowReferer"));
    }

    private boolean getAllowAccept(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("AllowAccept"));
    }

    private boolean getAllowInternalSSL(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("AllowInternalSSL"));
    }

    private Integer getNewDest(Map<String, Object> inParams) {
        Object newDestObj = inParams.get("NewDest");
        return newDestObj != null ? ((Number) newDestObj).intValue() : null;
    }

    private boolean getAllowNewDestOnResume(Map<String, Object> inParams) {
        Integer newDest = getNewDest(inParams);
        return newDest != null && newDest == 1;
    }

    private boolean getPersistentClientKey(Map<String, Object> inParams) {
        Integer newDest = getNewDest(inParams);
        return (inParams.containsKey("PersistentClientKey") &&
                Boolean.TRUE.equals(inParams.get("PersistentClientKey"))) ||
               (newDest != null && newDest == 2);
    }

    private int getNewDestMode(Map<String, Object> inParams) {
        if (getPersistentClientKey(inParams))
            return 2;
        if (getAllowNewDestOnResume(inParams))
            return 1;
        return 0;
    }

    private String getProxyList(Map<String, Object> inParams) {
        return (String) inParams.get("ProxyList");
    }

    private String getOutproxyType(Map<String, Object> inParams) {
        return (String) inParams.get("OutproxyType");
    }

    private String getProxyUsername(Map<String, Object> inParams) {
        return (String) inParams.get("ProxyUsername");
    }

    private String getProxyPassword(Map<String, Object> inParams) {
        String proxyPassword = (String) inParams.get("ProxyPassword");
        return proxyPassword != null ? proxyPassword : (String) inParams.get("nofilter_proxyPassword");
    }

    private String getOutproxyUsername(Map<String, Object> inParams) {
        return (String) inParams.get("OutproxyUsername");
    }

    private String getOutproxyPassword(Map<String, Object> inParams) {
        String outproxyPassword = (String) inParams.get("OutproxyPassword");
        return outproxyPassword != null ? outproxyPassword : (String) inParams.get("nofilter_outproxyPassword");
    }

    private Integer getReduceCount(Map<String, Object> inParams) {
        Object reduceCountObj = inParams.get("ReduceCount");
        return reduceCountObj != null ? ((Number) reduceCountObj).intValue() : null;
    }

    private Integer getReduceTime(Map<String, Object> inParams) {
        Object reduceTimeObj = inParams.get("ReduceTime");
        return reduceTimeObj != null ? ((Number) reduceTimeObj).intValue() : null;
    }

    private Integer getCloseTime(Map<String, Object> inParams) {
        Object closeTimeObj = inParams.get("CloseTime");
        return closeTimeObj != null ? ((Number) closeTimeObj).intValue() : null;
    }

    private String getPrivKeyFile(Map<String, Object> inParams) {
        return (String) inParams.get("PrivKeyFile");
    }

    private boolean isProxyClientType(String type) {
        return TunnelController.TYPE_HTTP_CLIENT.equals(type) ||
               TunnelController.TYPE_CONNECT.equals(type) ||
               TunnelController.TYPE_SOCKS.equals(type) ||
               TunnelController.TYPE_SOCKS_IRC.equals(type);
    }

    private boolean usesSharedClientOption(String type) {
        return TunnelController.TYPE_STD_CLIENT.equals(type) ||
               TunnelController.TYPE_IRC_CLIENT.equals(type) ||
               isProxyClientType(type);
    }

    private boolean requiresTargetDestination(String type) {
        return TunnelController.TYPE_STD_CLIENT.equals(type) ||
               TunnelController.TYPE_IRC_CLIENT.equals(type) ||
               TunnelController.TYPE_STREAMR_CLIENT.equals(type);
    }

    private boolean supportsDelayOpen(String type) {
        return !TunnelController.TYPE_STREAMR_CLIENT.equals(type);
    }

    private boolean supportsProfileOptions(String type) {
        return TunnelController.TYPE_STD_CLIENT.equals(type);
    }

    private boolean supportsUseSSL(String type) {
        return TunnelController.TYPE_STD_CLIENT.equals(type) ||
               TunnelController.TYPE_IRC_CLIENT.equals(type);
    }

    private boolean supportsDCC(String type) {
        return TunnelController.TYPE_IRC_CLIENT.equals(type);
    }

    private boolean supportsHTTPFiltering(String type) {
        return TunnelController.TYPE_HTTP_CLIENT.equals(type);
    }

    private boolean supportsHTTPAddressLookup(String type) {
        return TunnelController.TYPE_HTTP_CLIENT.equals(type);
    }

    private boolean supportsOutproxyType(String type) {
        return TunnelController.TYPE_SOCKS.equals(type) ||
               TunnelController.TYPE_SOCKS_IRC.equals(type);
    }

    private boolean supportsSSLProxies(String type) {
        return TunnelController.TYPE_HTTP_CLIENT.equals(type);
    }

    // --- Common Gets, allows us to reuse validation logic across different types of tunnels --- \\\


    // --- Set properties, allows us to set based on the API body --- \\\
    private void setCommon(Properties config, Map<String, Object> inParams, String type) {
        String name = getName(inParams);

        config.setProperty(TunnelController.PROP_TYPE, type);
        config.setProperty(TunnelController.PROP_NAME, name.trim());
        config.setProperty(TunnelController.PROP_LISTEN_PORT, Integer.toString(getPort(inParams)));
        config.setProperty(TunnelController.PROP_START, Boolean.toString(getStartOnLoad(inParams)));

        String description = getDescription(inParams);
        if (description != null)
            config.setProperty(TunnelController.PROP_DESCR, description);
    }

    // tunnel endpoints
    private void setTunnelClientEndpointOptions(Properties config, Map<String, Object> inParams, String type) {
        String name = getName(inParams).trim();
        if (TunnelController.TYPE_STREAMR_CLIENT.equals(type)) {
            String targetHost = getTargetHost(inParams);
            config.setProperty(TunnelController.PROP_TARGET_HOST, targetHost != null ? targetHost : "127.0.0.1");
        } else {
            String reachableBy = getReachableBy(inParams);
            config.setProperty(TunnelController.PROP_INTFC, reachableBy != null ? reachableBy : "127.0.0.1");
        }

        if (usesSharedClientOption(type))
            config.setProperty(TunnelController.PROP_SHARED, Boolean.toString(getShared(inParams)));

        String nickname = usesSharedClientOption(type) && getShared(inParams) ? SHARED_CLIENT_NICKNAME : name;
        config.setProperty(OPT + "inbound.nickname", nickname);
        config.setProperty(OPT + "outbound.nickname", nickname);

        if (supportsUseSSL(type))
            config.setProperty(OPT + I2PTunnelClientBase.PROP_USE_SSL, Boolean.toString(getUseSSL(inParams)));

        if (supportsDCC(type)) {
            boolean dcc = getDCC(inParams);
            config.setProperty(OPT + I2PTunnelIRCClient.PROP_DCC, Boolean.toString(dcc));
            if (dcc) {
                config.setProperty(OPT + TunnelController.PROP_MAX_CONNS_MIN, "3");
                config.setProperty(OPT + TunnelController.PROP_MAX_CONNS_HOUR, "10");
                config.setProperty(OPT + TunnelController.PROP_MAX_TOTAL_CONNS_MIN, "5");
                config.setProperty(OPT + TunnelController.PROP_MAX_TOTAL_CONNS_HOUR, "25");
            }
        }
    }

    // Tunnel Destination Options - for clients that require a target destination to connect to
    private void setTunnelDestinationOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!requiresTargetDestination(type))
            return;

        String targetDestination = getTargetDestination(inParams);
        if (targetDestination == null || targetDestination.trim().isEmpty()) {
            throw new IllegalArgumentException("TargetDestination is required for " + type);
        }
        config.setProperty(TunnelController.PROP_DEST, targetDestination.trim());
    }

    // Tunnel Length Options
    private void setTunnelLengthOptions(Properties config, Map<String, Object> inParams) {
        Integer tunnelLength = getTunnelLength(inParams);
        Integer tunnelVariance = getTunnelVariance(inParams);

        if (tunnelLength != null)
            setTunnelQuantity(config, "length", tunnelLength, tunnelLength);

        if (tunnelVariance != null)
            setTunnelQuantity(config, "lengthVariance", tunnelVariance, tunnelVariance);
    }

    // Tunnel Quantity Options
    private void setTunnelQuantityOptions(Properties config, Map<String, Object> inParams) {
        Integer tunnelQuantity = getTunnelQuantity(inParams);
        Integer backupQuantity = getBackupQuantity(inParams);

        if (tunnelQuantity != null)
            setTunnelQuantity(config, "quantity", tunnelQuantity, tunnelQuantity);

        if (backupQuantity != null)
            setTunnelQuantity(config, "backupQuantity", backupQuantity, backupQuantity);
    }

    // Tunnel Management Options
    private void setTunnelManagementOptions(Properties config, Map<String, Object> inParams, String type) {
        boolean persistentClientKey = getPersistentClientKey(inParams);
        Integer reduceCount = getReduceCount(inParams);
        Integer reduceTime = getReduceTime(inParams);
        Integer closeTime = getCloseTime(inParams);
 
        if (supportsProfileOptions(type)) {
            config.setProperty(OPT + PROP_STREAMING_CONNECT_DELAY, getConnectDelay(inParams) ? "500" : "0");
            String profile = getProfile(inParams);
            if ("interactive".equals(profile))
                config.setProperty(OPT + PROP_STREAMING_MAX_WINDOW_SIZE, "16");
            else
                config.remove(OPT + PROP_STREAMING_MAX_WINDOW_SIZE);
        }

        if (supportsDelayOpen(type))
            config.setProperty(OPT + PROP_DELAY_OPEN, Boolean.toString(getDelayOpen(inParams)));

        config.setProperty(OPT + PROP_REDUCE_ON_IDLE, Boolean.toString(getReduce(inParams)));
        config.setProperty(OPT + PROP_CLOSE_ON_IDLE, Boolean.toString(getClose(inParams)));

        int newDestMode = getNewDestMode(inParams);
        config.setProperty(OPT + PROP_NEW_DEST_ON_RESUME, Boolean.toString(newDestMode == 1));
        config.setProperty(OPT + PROP_PERSISTENT_CLIENT_KEY, Boolean.toString(newDestMode == 2));

        if (reduceCount != null)
            config.setProperty(OPT + PROP_REDUCE_QUANTITY, Integer.toString(reduceCount));

        if (reduceTime != null)
            config.setProperty(OPT + PROP_REDUCE_IDLE_TIME, Integer.toString(reduceTime * 60 * 1000));

        if (closeTime != null)
            config.setProperty(OPT + PROP_CLOSE_IDLE_TIME, Integer.toString(closeTime * 60 * 1000));

        String privKeyFile = getPrivKeyFile(inParams);
        if (privKeyFile != null) {
            config.setProperty(TunnelController.PROP_FILE, privKeyFile);
        } else if (persistentClientKey) {
            config.setProperty(TunnelController.PROP_FILE, getDefaultPrivateKeyFile());
        }
    }

    // Tunnel proxy options
    private void setTunnelProxyOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!isProxyClientType(type))
            return;

        String proxyList = getProxyList(inParams);
        if (proxyList != null)
            config.setProperty(TunnelController.PROP_PROXIES, proxyList);

        config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_USE_OUTPROXY_PLUGIN,
                           Boolean.toString(getUseOutproxyPlugin(inParams)));

        if (supportsOutproxyType(type)) {
            String outproxyType = getOutproxyType(inParams);
            if (outproxyType != null)
                config.setProperty(OPT + I2PSOCKSTunnel.PROP_OUTPROXY_TYPE, outproxyType);
        }

        if (supportsSSLProxies(type)) {
            config.setProperty(OPT + I2PTunnelHTTPClient.PROP_SSL_SET, "true");
            String sslProxies = getSSLProxies(inParams);
            if (sslProxies != null)
                config.setProperty(OPT + I2PTunnelHTTPClient.PROP_SSL_OUTPROXIES,
                                   sslProxies.trim().replace(" ", ","));
        }
    }

    // sets the tunnel filtering options for us.
    private void setTunnelFilteringOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!supportsHTTPFiltering(type))
            return;

        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_USER_AGENT, Boolean.toString(getAllowUserAgent(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_REFERER, Boolean.toString(getAllowReferer(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_ACCEPT, Boolean.toString(getAllowAccept(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_INTERNAL_SSL, Boolean.toString(getAllowInternalSSL(inParams)));
    }


    // creates the jump list for HTTP client address lookup. Expects a comma, space, or newline separated list of jump servers.
    // Will trim whitespace and replace with commas for the config.
    private void setTunnelAddressLookupOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!supportsHTTPAddressLookup(type))
            return;

        String jumpList = getJumpList(inParams);
        if (jumpList != null)
            config.setProperty(OPT + I2PTunnelHTTPClient.PROP_JUMP_SERVERS,
                               jumpList.trim().replace("\r\n", ",").replace("\n", ",").replace(" ", ","));
    }

    // Tunnel Authentication Options
    private void setTunnelAuthenticationOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!isProxyClientType(type))
            return;

        if (inParams.containsKey("ProxyAuth")) {
            boolean proxyAuth = getProxyAuth(inParams);
            config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_AUTH, getProxyAuthType(type, proxyAuth));

            if (proxyAuth) {
                String proxyUsername = getProxyUsername(inParams);
                String proxyPassword = getProxyPassword(inParams);

                if (proxyUsername == null || proxyPassword == null) {
                    throw new IllegalArgumentException("ProxyUsername and ProxyPassword are required when ProxyAuth is enabled");
                }

                if (TunnelController.TYPE_SOCKS.equals(type) || TunnelController.TYPE_SOCKS_IRC.equals(type)) {
                    config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_PREFIX + proxyUsername +
                                       I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_SHA256_SUFFIX,
                                       PasswordManager.sha256Hex(I2PSOCKSTunnel.AUTH_REALM, proxyUsername, proxyPassword));
                } else {
                    String realm = TunnelController.TYPE_HTTP_CLIENT.equals(type) ?
                                   I2PTunnelHTTPClient.AUTH_REALM : I2PTunnelConnectClient.AUTH_REALM;
                    config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_PREFIX + proxyUsername +
                                       I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_SUFFIX,
                                       PasswordManager.md5Hex(realm, proxyUsername, proxyPassword));
                    config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_PREFIX + proxyUsername +
                                       I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_SHA256_SUFFIX,
                                       PasswordManager.sha256Hex(realm, proxyUsername, proxyPassword));
                }
            }
        }

        config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_OUTPROXY_AUTH,
                           Boolean.toString(getOutproxyAuth(inParams)));

        String outproxyUsername = getOutproxyUsername(inParams);
        if (outproxyUsername != null)
            config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_OUTPROXY_USER, outproxyUsername);

        String outproxyPassword = getOutproxyPassword(inParams);
        if (outproxyPassword != null)
            config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_OUTPROXY_PW, outproxyPassword);
    }

    // Tunnel Cryptography Options
    private void setTunnelCryptographyOptions(Properties config, Map<String, Object> inParams) {
        String sigType = getSigType(inParams);
        if (sigType != null)
            config.setProperty(OPT + I2PClient.PROP_SIGTYPE, sigType);

        String encType = getEncType(inParams);
        if (encType != null)
            config.setProperty(OPT + "i2cp.leaseSetEncType", encType);
    }

    // Custom Options
    private void setCustomOptions(Properties config, Map<String, Object> inParams) {
        String customOptions = getCustomOptions(inParams);
        if (customOptions != null)
            addCustomOptions(config, customOptions);
    }

    private List<String> createClient(Map<String, Object> inParams, String type) throws IOException {
        boolean persistentClientKey = getPersistentClientKey(inParams);
        Properties config = new Properties();
        setCommon(config, inParams, type);
        setTunnelClientEndpointOptions(config, inParams, type);
        setTunnelDestinationOptions(config, inParams, type);
        setCustomOptions(config, inParams);
        setTunnelProxyOptions(config, inParams, type);
        setTunnelManagementOptions(config, inParams, type);
        setTunnelFilteringOptions(config, inParams, type);
        setTunnelAddressLookupOptions(config, inParams, type);
        setTunnelAuthenticationOptions(config, inParams, type);
        setTunnelLengthOptions(config, inParams);
        setTunnelQuantityOptions(config, inParams);
        setTunnelCryptographyOptions(config, inParams);

        TunnelController controller = new TunnelController(config, "", persistentClientKey);
        _group.addController(controller);
        _group.saveConfig(controller);
        if (controller.getStartOnLoad())
            controller.startTunnelBackground();
        return controller.clearMessages();
    }

    private String getProxyAuthType(String type, boolean proxyAuth) {
        if (!proxyAuth)
            return "false";
        if (TunnelController.TYPE_SOCKS.equals(type) || TunnelController.TYPE_SOCKS_IRC.equals(type))
            return "true";
        return I2PTunnelHTTPClientBase.DIGEST_AUTH;
    }

    // finds a controller by its name. Allows us to do actions to said controller
    private TunnelController findTunnelControllerByName(String name) {
        for (TunnelController controller : _group.getControllers()) {
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
        tunnelInfo.put("status", getTunnelStatusForOptions(tc));
        tunnelInfo.put("rawConfig", rawConfig);
        tunnelInfo.put("config", baseConfig);
        tunnelInfo.put("options", optionConfig);
        return tunnelInfo;
    }

    private static String getTunnelStatusForOptions(TunnelController tc) {
        if (tc.getIsStandby())
            return "standby";
        if (tc.getIsRunning())
            return "running";
        return "stopped";
    }

    private void setTunnelQuantity(Properties config, String name, int inbound, int outbound) {
        config.setProperty(OPT + "inbound." + name, Integer.toString(inbound));
        config.setProperty(OPT + "outbound." + name, Integer.toString(outbound));
    }

    private void addCustomOptions(Properties config, String customOptions) {
        String type = config.getProperty(TunnelController.PROP_TYPE);
        for (String token : customOptions.split("[,\\s]+")) {
            if (token.trim().isEmpty())
                continue;
            int equals = token.indexOf('=');
            String key = equals >= 0 ? token.substring(0, equals).trim() : token.trim();
            String value = equals >= 0 ? token.substring(equals + 1).trim() : "";
            if (NO_SHOW_SET.contains(key))
                continue;
            if (!TunnelController.TYPE_HTTP_CLIENT.equals(type) &&
                !TunnelController.TYPE_CONNECT.equals(type) &&
                NON_PROXY_NO_SHOW_SET.contains(key))
                continue;
            if (!key.isEmpty())
                config.setProperty(OPT + key, value);
        }
    }

    // --- Set properties, allows us to set based on the API body --- \\\


    // our private file key, it is different for each type of tunnel.
    // usually indexed based by creation, as seen within the tunnelmgr screen
    private String getDefaultPrivateKeyFile() {
        int tunnel = _group == null ? 999 : _group.getControllers().size();
        String rv = "i2ptunnel" + tunnel + "-privKeys.dat";
        int i = 0;
        while ((new File(_context.getConfigDir(), rv)).exists()) {
            rv = "i2ptunnel" + tunnel + '.' + (++i) + "-privKeys.dat";
        }
        return rv;
    }
}
