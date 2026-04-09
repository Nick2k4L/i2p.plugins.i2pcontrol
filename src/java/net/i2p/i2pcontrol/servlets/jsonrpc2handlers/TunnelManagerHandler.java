package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import net.i2p.router.RouterContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            // testing out starting and stopping a tunnel
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


            if (inParams.containsKey("All")){
                List<String> results = actionToAll(action);
                if (results != null) {
                    outParams.put("status", "success - " + action);
                    outParams.put("results", results);
                    return new JSONRPC2Response(outParams, req.getID());
                }
                outParams.put("status", "error - no action");
                return new JSONRPC2Response(outParams, req.getID());
            }




            /// ---- Methods per name ---- \\\
            TunnelController controller = findTunnelControllerByName(name.trim());

            if (inParams.containsKey("Delete")){
                if (controller != null){
                    List<String> msg = _group.removeController(controller);
                    outParams.put("status", "success - " + action);
                    outParams.put("results", msg);
                    return new JSONRPC2Response(outParams, req.getID());
                }

            }

            if (action.equals("start")) {
                if (controller != null) {
                    controller.startTunnel();
                    outParams.put("status", "success - starting tunnel " + controller.getName());
                    return new JSONRPC2Response(outParams, req.getID());
                }
            }
            else {
                if (controller != null) {
                    controller.stopTunnel();
                    outParams.put("status", "success - stopping tunnel " + controller.getName());
                    return new JSONRPC2Response(outParams, req.getID());
                }
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


    // finds a controller by its name. Allows us to do actions to said controller
    private TunnelController findTunnelControllerByName(String name) {
        for (TunnelController controller : _group.getControllers()) {
            if (controller.getName().equals(name))
                return controller;
        }
        return null;
    }
}
