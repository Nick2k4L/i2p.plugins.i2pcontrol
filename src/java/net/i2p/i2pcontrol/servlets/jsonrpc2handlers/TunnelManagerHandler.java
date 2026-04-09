package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import net.i2p.client.I2PClient;
import net.i2p.i2ptunnel.I2PTunnelClientBase;
import net.i2p.i2ptunnel.I2PTunnelHTTPClientBase;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import net.i2p.i2ptunnel.socks.I2PSOCKSTunnel;
import net.i2p.router.RouterContext;
import net.i2p.util.PasswordManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class TunnelManagerHandler implements RequestHandler {
    private final JSONRPC2Helper _helper;
    private final RouterContext _context;
    private TunnelControllerGroup _group;

    // this will be changed eventually, but for testing purposes, we are leaving it like this for now.
    private static final String[] requiredArgs = {"Name", "Action"};


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
                    String type = (String) inParams.get("Type");
                    // extract to a function later on
                    switch (type) {
                        case TunnelController.TYPE_STD_CLIENT:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());
                        case TunnelController.TYPE_HTTP_CLIENT:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());
                        case TunnelController.TYPE_IRC_CLIENT:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());


                        case TunnelController.TYPE_SOCKS_IRC:
                        case TunnelController.TYPE_SOCKS:
                            List<String> results = createSocksClient(inParams, type);
                            outParams.put("status", "success - created tunnel " + name.trim());
                            outParams.put("results", results);
                            return new JSONRPC2Response(outParams, req.getID());

                        case TunnelController.TYPE_CONNECT:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());

                        case TunnelController.TYPE_STREAMR_CLIENT:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());

                        case TunnelController.TYPE_HTTP_SERVER:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());

                        case TunnelController.TYPE_STD_SERVER:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());

                        case TunnelController.TYPE_HTTP_BIDIR_SERVER:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());

                        case TunnelController.TYPE_IRC_SERVER:
                            outParams.put("status", "error - not implemented yet");
                            return new JSONRPC2Response(outParams, req.getID());

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
                        try {
                            _group.removeConfig(controller);
                        } catch (Exception e) {
                            outParams.put("status", "error - failed to remove config for tunnel " + controllerName);
                            return new JSONRPC2Response(outParams, req.getID());
                        }
                        outParams.put("status", "success - deleting tunnel " + controllerName);
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



    // this creates us our socks client with parameters
    // still undergoing testing at the moment, yet base creation works perfect
    // testing advanced configuration still needs to happen
    private List<String> createSocksClient(Map<String, Object> inParams, String type) throws IOException {
        String name = (String) inParams.get("Name");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (findTunnelControllerByName(name.trim()) != null) {
            throw new IllegalArgumentException("tunnel " + name.trim() + " already exists");
        }

        Object portObj = inParams.get("Port");
        if (portObj == null) {
            throw new IllegalArgumentException("Port is required");
        }
        int port = ((Number) portObj).intValue();
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }

        boolean shared = Boolean.TRUE.equals(inParams.get("Shared"));
        boolean startOnLoad = Boolean.TRUE.equals(inParams.get("StartOnLoad"));
        Object newDestObj = inParams.get("NewDest");
        Integer newDest = newDestObj != null ? ((Number) newDestObj).intValue() : null;
        boolean allowNewDestOnResume = newDest != null && newDest == 1;
        boolean persistentClientKey = (inParams.containsKey("PersistentClientKey") &&
                                       Boolean.TRUE.equals(inParams.get("PersistentClientKey"))) ||
                                      (newDest != null && newDest == 2);

        Properties config = new Properties();
        config.setProperty(TunnelController.PROP_TYPE, type);
        config.setProperty(TunnelController.PROP_NAME, name.trim());
        config.setProperty(TunnelController.PROP_LISTEN_PORT, Integer.toString(port));

        String reachableBy = (String) inParams.get("ReachableBy");

        config.setProperty(TunnelController.PROP_INTFC, reachableBy != null ? reachableBy : "127.0.0.1");
        config.setProperty(TunnelController.PROP_SHARED, Boolean.toString(shared));
        config.setProperty(TunnelController.PROP_START, Boolean.toString(startOnLoad));

        String description = (String) inParams.get("Description");
        if (description != null)
            config.setProperty(TunnelController.PROP_DESCR, description);

        String nickname = shared ? "shared clients" : name.trim();
        config.setProperty(TunnelController.PFX_OPTION + "inbound.nickname", nickname);
        config.setProperty(TunnelController.PFX_OPTION + "outbound.nickname", nickname);
        config.setProperty(TunnelController.PFX_OPTION + "i2p.streaming.connectDelay",
                           Boolean.TRUE.equals(inParams.get("ConnectDelay")) ? "500" : "0");

        if (Boolean.TRUE.equals(inParams.get("DelayOpen")))
            config.setProperty(TunnelController.PFX_OPTION + "i2cp.delayOpen", "true");
        if (Boolean.TRUE.equals(inParams.get("Reduce")))
            config.setProperty(TunnelController.PFX_OPTION + "i2cp.reduceOnIdle", "true");
        if (Boolean.TRUE.equals(inParams.get("Close")))
            config.setProperty(TunnelController.PFX_OPTION + "i2cp.closeOnIdle", "true");

        if (allowNewDestOnResume)
            config.setProperty(TunnelController.PFX_OPTION + "i2cp.newDestOnResume", "true");
        if (persistentClientKey)
            config.setProperty(TunnelController.PFX_OPTION + "persistentClientKey", "true");
        if (Boolean.TRUE.equals(inParams.get("UseSSL")))
            config.setProperty(TunnelController.PFX_OPTION + I2PTunnelClientBase.PROP_USE_SSL, "true");

        if (Boolean.TRUE.equals(inParams.get("UseOutproxyPlugin")))
            config.setProperty(TunnelController.PFX_OPTION + I2PTunnelHTTPClientBase.PROP_USE_OUTPROXY_PLUGIN, "true");
        if (Boolean.TRUE.equals(inParams.get("OutproxyAuth")))
            config.setProperty(TunnelController.PFX_OPTION + I2PTunnelHTTPClientBase.PROP_OUTPROXY_AUTH, "true");

        String profile = (String) inParams.get("Profile");
        if ("interactive".equals(profile)) {
            config.setProperty(TunnelController.PFX_OPTION + "i2p.streaming.maxWindowSize", "16");
        }



        String customOptions = (String) inParams.get("CustomOptions");
        if (customOptions != null)
            addCustomOptions(config, customOptions);

        String proxyList = (String) inParams.get("ProxyList");
        if (proxyList != null)
            config.setProperty(TunnelController.PROP_PROXIES, proxyList);

        String outproxyType = (String) inParams.get("OutproxyType");
        if (outproxyType != null)
            config.setProperty(TunnelController.PFX_OPTION + I2PSOCKSTunnel.PROP_OUTPROXY_TYPE, outproxyType);

        if (Boolean.TRUE.equals(inParams.get("ProxyAuth"))) {
            String proxyUsername = (String) inParams.get("ProxyUsername");
            String proxyPassword = (String) inParams.get("ProxyPassword");

            if (proxyUsername == null || proxyPassword == null) {
                throw new IllegalArgumentException("ProxyUsername and ProxyPassword are required when ProxyAuth is enabled");
            }

            config.setProperty(TunnelController.PFX_OPTION + I2PTunnelHTTPClientBase.PROP_AUTH, "true");
            config.setProperty(TunnelController.PFX_OPTION +
                               I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_PREFIX + proxyUsername +
                               I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_SHA256_SUFFIX,
                               PasswordManager.sha256Hex(I2PSOCKSTunnel.AUTH_REALM, proxyUsername, proxyPassword));
        }

        String outproxyUsername = (String) inParams.get("OutproxyUsername");
        if (outproxyUsername != null)
            config.setProperty(TunnelController.PFX_OPTION + I2PTunnelHTTPClientBase.PROP_OUTPROXY_USER, outproxyUsername);

        String outproxyPassword = (String) inParams.get("OutproxyPassword");
        if (outproxyPassword != null)
            config.setProperty(TunnelController.PFX_OPTION + I2PTunnelHTTPClientBase.PROP_OUTPROXY_PW, outproxyPassword);

        Object tunnelDepthObj = inParams.get("TunnelDepth");
        Integer tunnelDepth = tunnelDepthObj != null ? ((Number) tunnelDepthObj).intValue() : null;
        if (tunnelDepth != null)
            setTunnelQuantity(config, "length", tunnelDepth, tunnelDepth);

        Object tunnelVarianceObj = inParams.get("TunnelVariance");
        Integer tunnelVariance = tunnelVarianceObj != null ? ((Number) tunnelVarianceObj).intValue() : null;
        if (tunnelVariance != null)
            setTunnelQuantity(config, "lengthVariance", tunnelVariance, tunnelVariance);

        Object tunnelQuantityObj = inParams.get("TunnelQuantity");
        Integer tunnelQuantity = tunnelQuantityObj != null ? ((Number) tunnelQuantityObj).intValue() : null;
        if (tunnelQuantity != null)
            setTunnelQuantity(config, "quantity", tunnelQuantity, tunnelQuantity);

        Object tunnelBackupQuantityObj = inParams.get("TunnelBackupQuantity");
        Integer tunnelBackupQuantity = tunnelBackupQuantityObj != null ? ((Number) tunnelBackupQuantityObj).intValue() : null;
        if (tunnelBackupQuantity != null)
            setTunnelQuantity(config, "backupQuantity", tunnelBackupQuantity, tunnelBackupQuantity);

        Object reduceCountObj = inParams.get("ReduceCount");
        Integer reduceCount = reduceCountObj != null ? ((Number) reduceCountObj).intValue() : null;
        if (reduceCount != null && Boolean.TRUE.equals(inParams.get("Reduce")))
            config.setProperty(TunnelController.PFX_OPTION + "i2cp.reduceQuantity",
                  Integer.toString(reduceCount));

        Object reduceTimeObj = inParams.get("ReduceTime");
        Integer reduceTime = reduceTimeObj != null ? ((Number) reduceTimeObj).intValue() : null;
        if (reduceTime != null && Boolean.TRUE.equals(inParams.get("Reduce")))
            config.setProperty(TunnelController.PFX_OPTION + "i2cp.reduceIdleTime",
                  Integer.toString(reduceTime * 60 * 1000));

        Object closeTimeObj = inParams.get("CloseTime");
        Integer closeTime = closeTimeObj != null ? ((Number) closeTimeObj).intValue() : null;
        if (closeTime != null && Boolean.TRUE.equals(inParams.get("Close")))
            config.setProperty(TunnelController.PFX_OPTION + "i2cp.closeIdleTime",
                  Integer.toString(closeTime * 60 * 1000));

        String privKeyFile = (String) inParams.get("PrivKeyFile");
        if (privKeyFile != null) {
            config.setProperty(TunnelController.PROP_FILE, privKeyFile);
            
        } else if (persistentClientKey) {
            config.setProperty(TunnelController.PROP_FILE, getDefaultPrivateKeyFile());
        }

        String sigType = (String) inParams.get("SigType");
        if (sigType != null)
            config.setProperty(TunnelController.PFX_OPTION + I2PClient.PROP_SIGTYPE, sigType);

        String encType = (String) inParams.get("EncType");
        if (encType != null)
            config.setProperty(TunnelController.PFX_OPTION + "i2cp.leaseSetEncType", encType);

        String clientHost = (String) inParams.get("ClientHost");
        if (clientHost != null)
            config.setProperty(TunnelController.PROP_I2CP_HOST, clientHost);

        String clientPort = (String) inParams.get("ClientPort");
        if (clientPort != null)
            config.setProperty(TunnelController.PROP_I2CP_PORT, clientPort);

        TunnelController controller = new TunnelController(config, "", persistentClientKey);
        _group.addController(controller);
        _group.saveConfig(controller);
        if (controller.getStartOnLoad())
            controller.startTunnelBackground();
        return controller.clearMessages();
    }

    // finds a controller by its name. Allows us to do actions to said controller
    private TunnelController findTunnelControllerByName(String name) {
        for (TunnelController controller : _group.getControllers()) {
            if (controller.getName().equals(name))
                return controller;
        }
        return null;
    }

    // sets the inbound and outbound tunnel count
    private void setTunnelQuantity(Properties config, String name, int inbound, int outbound) {
        config.setProperty(TunnelController.PFX_OPTION + "inbound." + name, Integer.toString(inbound));
        config.setProperty(TunnelController.PFX_OPTION + "outbound." + name, Integer.toString(outbound));
    }

    // when we need to add a custom option
    private void addCustomOptions(Properties config, String customOptions) {
        for (String token : customOptions.split("[,\\s]+")) {
            if (token.trim().isEmpty())
                continue;
            int equals = token.indexOf('=');
            String key = equals >= 0 ? token.substring(0, equals).trim() : token.trim();
            String value = equals >= 0 ? token.substring(equals + 1).trim() : "";
            if (!key.isEmpty())
                config.setProperty(TunnelController.PFX_OPTION + key, value);
        }
    }

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
