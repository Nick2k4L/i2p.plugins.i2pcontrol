package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import net.i2p.I2PException;
import net.i2p.data.Destination;
import net.i2p.data.PrivateKeyFile;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import net.i2p.router.RouterContext;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class TunnelManagerHandler implements RequestHandler {
    private static final String[] REQUIRED_ARGS = {"Name", "Action"};
    private final JSONRPC2Helper _helper;
    private final RouterContext _context;
    private TunnelControllerGroup _group;

    public TunnelManagerHandler(RouterContext ctx, JSONRPC2Helper helper) {
        _context = ctx;
        _helper = helper;
    }

    public String[] handledRequests() {
        return new String[] {"TunnelManager"};
    }

    public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {
        if (!req.getMethod().equals("TunnelManager"))
            return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());

        Map<String, Object> inParams = req.getNamedParams();
        JSONRPC2Error err = _helper.validateParams(REQUIRED_ARGS, req, JSONRPC2Helper.USE_NO_AUTH);
        if (err != null)
            return new JSONRPC2Response(err, req.getID());

        String name = (String) inParams.get("Name");
        String action = (String) inParams.get("Action");
        Map<String, Object> outParams = new HashMap<>();

        if (_group == null)
            _group = TunnelControllerGroup.getInstance(_context);

        if (_group == null) {
            outParams.put("status", "error - tunnel controller not available, group is null");
            return new JSONRPC2Response(outParams, req.getID());
        }

        if (inParams.containsKey("All")) {
            Boolean all = (Boolean) inParams.get("All");
            if (all) {
                List<String> results = actionToAll(action);
                if (results != null) {
                    outParams.put("status", "success - " + action);
                    outParams.put("results", results);
                    return new JSONRPC2Response(outParams, req.getID());
                }
                outParams.put("status", "error - no action");
                return new JSONRPC2Response(outParams, req.getID());
            }
        }

        TunnelRequestParser parser = new TunnelRequestParser(_group);
        TunnelSupport support = new TunnelSupport(_context, _group, parser);
        ClientTunnelCreator clientCreator = new ClientTunnelCreator(_context, _group, parser, support);
        ServiceTunnelCreator serviceCreator = new ServiceTunnelCreator(_context, _group, parser, support);

        if ("edit".equals(action)) {
            try {
                TunnelController controllerForEdit = findTunnelControllerByName(name.trim());
                if (controllerForEdit == null) {
                    outParams.put("status", "error - tunnel " + name.trim() + " not found");
                    return new JSONRPC2Response(outParams, req.getID());
                }

                if (controllerForEdit.isClient()) {
                    clientCreator.edit(inParams, name);
                } else {
                    serviceCreator.edit(inParams, name);
                }

            } catch (IOException ioe) {
                outParams.put("status", "error - failed to save tunnel: " + ioe.getMessage());
                return new JSONRPC2Response(outParams, req.getID());
            }
            outParams.put("status", "success - edited tunnel " + name.trim());
            return new JSONRPC2Response(outParams, req.getID());
        }

        if ("create".equals(action)) {
            try {
                String type = parser.getType(inParams);
                switch (type) {
                    case TunnelController.TYPE_STD_CLIENT:
                    case TunnelController.TYPE_HTTP_CLIENT:
                    case TunnelController.TYPE_IRC_CLIENT:
                    case TunnelController.TYPE_SOCKS_IRC:
                    case TunnelController.TYPE_SOCKS:
                    case TunnelController.TYPE_CONNECT:
                    case TunnelController.TYPE_STREAMR_CLIENT:
                        outParams.put("status", "success - created tunnel " + name.trim());
                        outParams.put("results", clientCreator.create(inParams, type));
                        return new JSONRPC2Response(outParams, req.getID());

                    case TunnelController.TYPE_HTTP_SERVER:
                    case TunnelController.TYPE_STD_SERVER:
                    case TunnelController.TYPE_HTTP_BIDIR_SERVER:
                    case TunnelController.TYPE_IRC_SERVER:
                    case TunnelController.TYPE_STREAMR_SERVER:
                        outParams.put("status", "success - created tunnel " + name.trim());
                        outParams.put("results", serviceCreator.create(inParams, type));
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
            outParams.put("info", extractTunnelOptions(controllerForGet));
            return new JSONRPC2Response(outParams, req.getID());
        }

        TunnelController controller = findTunnelControllerByName(name.trim());
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

        if (inParams.containsKey("Config")) {
            outParams.put("status", "error - tunnel " + name.trim() + " not found");
            return new JSONRPC2Response(outParams, req.getID());
        }

        outParams.put("status", "error - tunnel controller not available");
        return new JSONRPC2Response(outParams, req.getID());
    }

    private List<String> actionToAll(String action) {
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

        for (Map.Entry<Object, Object> entry : config.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            rawConfig.put(key, value);
        }

        tunnelInfo.put("client", tc.isClient());
        tunnelInfo.put("status", getTunnelStatusForOptions(tc));

        tunnelInfo.put("persistentClientKey", tc.getPersistentClientKey());
        tunnelInfo.put("offlineKeys", tc.getIsOfflineKeys());

        Destination localDestination = getDestination(tc.getName());
        String localDestinationBase64 = localDestination != null ? localDestination.toBase64() : null;

        tunnelInfo.put("targetDestination", tc.getTargetDestination());
        tunnelInfo.put("localDestination", localDestinationBase64);
        tunnelInfo.put("destination", localDestinationBase64);
        tunnelInfo.put("destinationB32", localDestination != null ? localDestination.toBase32() : null);


        //tunnelInfo.put("clientOptions", tc.getClientOptionProps());
        tunnelInfo.put("rawConfig", rawConfig);

        return tunnelInfo;
    }


    // from `GeneralHelper.java`
    private Destination getDestination(String name) {
        TunnelController tun = findTunnelControllerByName(name);
        if (tun != null) {
            Destination rv = tun.getDestination();
            if (rv != null)
                return rv;
            // if not running, do this the hard way
            File keyFile = tun.getPrivateKeyFile();
            if (keyFile != null) {
                PrivateKeyFile pkf = new PrivateKeyFile(keyFile);
                try {
                    rv = pkf.getDestination();
                    if (rv != null)
                        return rv;
                } catch (I2PException e) {
                } catch (IOException e) {}
            }
        }
        return null;
    }

    private static String getTunnelStatusForOptions(TunnelController tc) {
        if (tc.getIsStandby())
            return "standby";
        if (tc.getIsRunning())
            return "running";
        return "stopped";
    }
}
