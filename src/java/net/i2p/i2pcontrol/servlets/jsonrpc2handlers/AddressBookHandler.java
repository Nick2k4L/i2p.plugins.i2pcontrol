package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import net.i2p.client.naming.NamingService;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.i2p.router.RouterContext;
import net.i2p.i2pcontrol.servlets.jsonrpc2handlers.AddressBookFiles;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;



// AddressBookHandler handles JSON-RPC requests related to managing address books.
public class AddressBookHandler implements RequestHandler {

    private final JSONRPC2Helper _helper;
    private final RouterContext _context;
    private final AddressBookFiles _files;
    private static final String[] requiredArgs = {"Type", "Hostname", "Destination"};
    private static final Map<String, String> ADDRESS_BOOKS = new LinkedHashMap<>();
    private static final String PUBLISHED = "published";

    static {
        ADDRESS_BOOKS.put("local", "userhosts.txt");
        ADDRESS_BOOKS.put("private", "privatehosts.txt");
        ADDRESS_BOOKS.put("router", "hosts.txt");
    }

    public AddressBookHandler(RouterContext ctx, JSONRPC2Helper helper) {
        _context = ctx;
        _helper = helper;
        _files = new AddressBookFiles(ctx);
    }

    // Reports the method names of the handled requests
    public String[] handledRequests() {
        return new String[]{"AddressBook"};
    }

    // Processes the requests
    public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {
        if (req.getMethod().equals("AddressBook")) {
            Map<String, Object> inParams = req.getNamedParams();

            // check if this is a file request immediately
            JSONRPC2Response fileResponse = processFileRequest(req, inParams);
            if (fileResponse != null)
                return fileResponse;

            JSONRPC2Error err = _helper.validateParams(requiredArgs, req, JSONRPC2Helper.USE_NO_AUTH);
            if (err != null)
                return new JSONRPC2Response(err, req.getID());


            // type: corresponds to address book type (private, local, router)
            String type = (String) inParams.get("Type");
            if (type == null) {
                return new JSONRPC2Response(JSONRPC2Error.INVALID_PARAMS, req.getID());
            }
            type = type.trim();


            // hostname: corresponds to the hostname or domain name associated with the address book entry
            String hostname = (String) inParams.get("Hostname");
            if (hostname == null) {
                return new JSONRPC2Response(JSONRPC2Error.INVALID_PARAMS, req.getID());
            }
            hostname = hostname.trim();

            NamingService namingService = _context.namingService();
            if (namingService == null) {
                return new JSONRPC2Response(new JSONRPC2Error(
                        JSONRPC2Error.INTERNAL_ERROR.getCode(),
                        "NamingService was not initialized. Query failed"),
                        req.getID());
            }

            // destination: corresponds to the destination associated with the address book entry
            String destStr = (String) inParams.get("Destination");
            if (destStr == null) {
                return new JSONRPC2Response(JSONRPC2Error.INVALID_PARAMS, req.getID());
            }
            destStr = destStr.trim();

            // Normalize the destination string to a base64 destination.
            String targetHost = extractDestinationTarget(destStr);

            Destination destination;

            // base64 or i2p host?
            if (NamingService.isI2PHost(targetHost)) {
                destination = namingService.lookup(targetHost.toLowerCase());
            }
            else {
                destination = new Destination();
                try {
                    destination.fromBase64(targetHost);
                } catch (DataFormatException e) {
                    return new JSONRPC2Response(new JSONRPC2Error(
                            JSONRPC2Error.INVALID_PARAMS.getCode(),
                            "Destination must be a resolvable .i2p/.b32.i2p host, URL, or a valid Base64 destination."),
                            req.getID());
                }
            }

            if (destination == null) {
                return new JSONRPC2Response(new JSONRPC2Error(
                        JSONRPC2Error.INVALID_PARAMS.getCode(),
                        "Destination host could not be resolved. Supply the Base64 destination directly if the host is not currently resolvable."),
                        req.getID());
            }


            Map<String, Object> outParams = new HashMap<>(4);

            String message = " in %s addressbook".formatted(type);


            // special case for published address book
            if (PUBLISHED.equals(type)) {
                try {
                    File published = _files.getPublishedAddressBook();
                    if (inParams.containsKey("Delete")) {
                        // delete the hostname and destination from the published address book
                        boolean deletion = _files.removePublishedEntry(published, hostname);
                        outParams.put("success", deletion);
                        outParams.put("message", deletion ? "Deleted %s".formatted(hostname) + message : "Failed to Delete %s".formatted(hostname) + message);
                        return new JSONRPC2Response(outParams, req.getID());
                    }

                    // put the hostname and destination into the published address book
                    boolean success = _files.putPublishedEntry(published, hostname, destination);
                    outParams.put("success", success);
                    outParams.put("message", success ? "Added %s".formatted(hostname) + message : "Failed to Add %s".formatted(hostname) + message);
                    return new JSONRPC2Response(outParams, req.getID());
                } catch (IOException ioe) {
                    return new JSONRPC2Response(new JSONRPC2Error(
                            JSONRPC2Error.INTERNAL_ERROR.getCode(),
                            "Could not access published address book file."),
                            req.getID());
                }
            }

            // type of address book to use
            String listFile = ADDRESS_BOOKS.get(type);
            if (listFile == null) {
                return new JSONRPC2Response(JSONRPC2Error.INVALID_PARAMS, req.getID());
            }

            Properties opts = new Properties();
            opts.setProperty("list", listFile);

            // delete the hostname and destination from the address book
            if (inParams.containsKey("Delete")){
                boolean deletion = namingService.remove(hostname, destination, opts);
                outParams.put("success", deletion);
                outParams.put("message", deletion ? "Deleted %s".formatted(hostname) + message : "Failed to Delete %s".formatted(hostname) + message);

                return new JSONRPC2Response(outParams, req.getID());
            }

            // put the hostname and destination into the address book
            boolean success = namingService.put(hostname, destination, opts);
            outParams.put("success", success);
            outParams.put("message", success ? "Added %s".formatted(hostname) + message : "Failed to Add %s".formatted(hostname) + message);
            return new JSONRPC2Response(outParams, req.getID());
        } else {
            // Method name not supported
            return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
        }
    }


    // helps us process file requests
    // related to the address book configuration and subscriptions,
    // which are separate from the main address book entry management
    private JSONRPC2Response processFileRequest(JSONRPC2Request req, Map<String, Object> inParams) {
        try {
            if (inParams.containsKey("SetConfig")) {
                Object configObj = inParams.get("SetConfig");
                if (!(configObj instanceof Map)) {
                    return new JSONRPC2Response(JSONRPC2Error.INVALID_PARAMS, req.getID());
                }
                Properties props = _files.loadAddressBookConfig();
                AddressBookFiles.applyConfigUpdates(props, (Map<?, ?>) configObj);
                _files.storeAddressBookConfig(props);
                Map<String, Object> outParams = new HashMap<>(3);
                outParams.put("Path", _files.getAddressBookConfigFile().getAbsolutePath());
                outParams.put("Result", "OK");
                outParams.put("Config", new LinkedHashMap<String, String>((Map) props));
                return new JSONRPC2Response(outParams, req.getID());
            }

            if (inParams.containsKey("SetSubscriptions")) {
                List<String> subscriptions = AddressBookFiles.normalizeSubscriptions(inParams.get("SetSubscriptions"));
                if (subscriptions == null) {
                    return new JSONRPC2Response(JSONRPC2Error.INVALID_PARAMS, req.getID());
                }
                File file = _files.getSubscriptionsFile();
                _files.storeSubscriptions(file, subscriptions);
                Map<String, Object> outParams = new HashMap<>(3);
                outParams.put("Path", file.getAbsolutePath());
                outParams.put("Result", "OK");
                outParams.put("Subscriptions", subscriptions);
                return new JSONRPC2Response(outParams, req.getID());
            }
        } catch (IOException ioe) {
            return new JSONRPC2Response(new JSONRPC2Error(
                    JSONRPC2Error.INTERNAL_ERROR.getCode(),
                    "Could not access address book configuration files."),
                    req.getID());
        }
        return null;
    }

    // Extracts the target part of the destination string handling url formats
    private static String extractDestinationTarget(String destination) {
        if (destination.contains("://")) {
            try {
                java.net.URI uri = new java.net.URI(destination);
                String host = uri.getHost();
                if (host != null) {
                    return host;
                }
            } catch (java.net.URISyntaxException e) {
                // If the URI is not valid, we will just treat the whole string as the destination

            }
        }

        return destination;
    }
}
