package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import net.i2p.router.RouterContext;

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
            List<String> results = actionToAll(action);
            if (results != null) {
                outParams.put("status", "success - " + action);
                outParams.put("results", results);
                return new JSONRPC2Response(outParams, req.getID());
            }
            outParams.put("status", "error - no action");
            return new JSONRPC2Response(outParams, req.getID());
        }

        TunnelRequestParser parser = new TunnelRequestParser(_group);
        TunnelSupport support = new TunnelSupport(_context, _group, parser);
        ClientTunnelCreator clientCreator = new ClientTunnelCreator(_context, _group, parser, support);
        ServiceTunnelCreator serviceCreator = new ServiceTunnelCreator(_context, _group, parser, support);

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
            outParams.put("i2p.router.net.tunnels.i2ptunnel.options", extractTunnelOptions(controllerForGet));
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
        Map<String, String> optionConfig = new TreeMap<String, String>();
        Map<String, String> baseConfig = new TreeMap<>();
        Map<String, Object> controllerConfig = new LinkedHashMap<>();

        for (Map.Entry<Object, Object> entry : config.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            rawConfig.put(key, value);
            if (key.startsWith(TunnelController.PFX_OPTION))
                optionConfig.put(key.substring(TunnelController.PFX_OPTION.length()), value);
            else
                baseConfig.put(key, value);
        }

        tunnelInfo.put("name", tc.getName());
        tunnelInfo.put("type", tc.getType());
        tunnelInfo.put("client", tc.isClient());
        tunnelInfo.put("description", tc.getDescription());
        tunnelInfo.put("status", getTunnelStatusForOptions(tc));
        tunnelInfo.put("starting", tc.getIsStarting());
        tunnelInfo.put("standby", tc.getIsStandby());
        tunnelInfo.put("startOnLoad", tc.getStartOnLoad());
        tunnelInfo.put("sharedClient", tc.getSharedClient());
        tunnelInfo.put("persistentClientKey", tc.getPersistentClientKey());
        tunnelInfo.put("offlineKeys", tc.getIsOfflineKeys());
        tunnelInfo.put("listenOnInterface", tc.getListenOnInterface());
        tunnelInfo.put("listenPort", tc.getListenPort());
        tunnelInfo.put("targetHost", tc.getTargetHost());
        tunnelInfo.put("targetPort", tc.getTargetPort());
        tunnelInfo.put("targetDestination", tc.getTargetDestination());
        tunnelInfo.put("proxyList", tc.getProxyList());
        tunnelInfo.put("privateKeyFile", tc.getPrivKeyFile());
        tunnelInfo.put("destination", tc.getMyDestination());
        tunnelInfo.put("destinationB32", tc.getMyDestHashBase32());
        tunnelInfo.put("clientOptionsString", tc.getClientOptions());
        controllerConfig.put("i2cpHost", tc.getI2CPHost());
        controllerConfig.put("i2cpPort", tc.getI2CPPort());
        controllerConfig.put("listenOnInterface", tc.getListenOnInterface());
        controllerConfig.put("listenPort", tc.getListenPort());
        controllerConfig.put("targetHost", tc.getTargetHost());
        controllerConfig.put("targetPort", tc.getTargetPort());
        controllerConfig.put("targetDestination", tc.getTargetDestination());
        controllerConfig.put("proxyList", tc.getProxyList());
        controllerConfig.put("sharedClient", tc.getSharedClient());
        controllerConfig.put("startOnLoad", tc.getStartOnLoad());
        controllerConfig.put("persistentClientKey", tc.getPersistentClientKey());
        controllerConfig.put("privateKeyFile", tc.getPrivKeyFile());
        controllerConfig.put("filter", tc.getFilter());
        controllerConfig.put("spoofedHost", tc.getSpoofedHost());
        tunnelInfo.put("controller", controllerConfig);
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
}
