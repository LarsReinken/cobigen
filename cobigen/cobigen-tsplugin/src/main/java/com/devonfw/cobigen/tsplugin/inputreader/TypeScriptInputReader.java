package com.devonfw.cobigen.tsplugin.inputreader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devonfw.cobigen.api.constants.ExternalProcessConstants;
import com.devonfw.cobigen.api.exception.InputReaderException;
import com.devonfw.cobigen.api.extension.InputReader;
import com.devonfw.cobigen.impl.exceptions.ConnectionExceptionHandler;
import com.devonfw.cobigen.impl.externalprocess.ExternalProcessHandler;
import com.devonfw.cobigen.tsplugin.inputreader.to.InputFileTo;
import com.devonfw.cobigen.tsplugin.merger.constants.Constants;

/**
 *
 */
public class TypeScriptInputReader implements InputReader {

    /** Logger instance. */
    private static final Logger LOG = LoggerFactory.getLogger(TypeScriptInputReader.class);

    /**
     * Instance that handles all the operations performed to the external server, like initializing the
     * connection and sending new requests
     */
    private ExternalProcessHandler request = ExternalProcessHandler
        .getExternalProcessHandler(ExternalProcessConstants.HOST_NAME, ExternalProcessConstants.PORT);

    /**
     * Exception handler related to connectivity to the server
     */
    private ConnectionExceptionHandler connectionExc = new ConnectionExceptionHandler();

    /**
     * Creates a new {@link TypeScriptInputReader}
     */
    public TypeScriptInputReader() {

        try {
            // We first check if the server is already running
            request.startConnection();
            if (request.isNotConnected()) {
                startServerConnection();
            }
        } catch (IOException e) {
            // If it is not currently running, we need to execute it
            LOG.info("Server is not currently running. Let's initialize it");
            startServerConnection();
        }

    }

    /**
     * Deploys the server and tries to initialize a new connection between CobiGen and the server
     */
    private void startServerConnection() {
        request.executingExe(Constants.EXE_NAME, this.getClass());
        request.initializeConnection();
    }

    @Override
    public boolean isValidInput(Object input) {
        String fileContents = null;
        String inputCharset = "UTF-8";

        Path path;

        if (input instanceof Path) {
            path = (Path) input;
        } else if (input instanceof File) {
            path = ((File) input).toPath();
        }

        else {
            // Input corresponds to the parsed file
            Map<String, Object> mapModel = createModel(input);
            path = Paths.get(mapModel.get("path").toString());
            return isValidInput(path);
        }

        if (request.isNotConnected()) {
            startServerConnection();
        }

        // File content is not needed, as only the file extension is checked
        fileContents = new String("");
        // String fileName = path.getFileName().toString();
        String fileName = path.toString();
        InputFileTo inputFile = new InputFileTo(fileName, fileContents, inputCharset);

        HttpURLConnection conn = request.getConnection("POST", "Content-Type", "application/json", "isValidInput");

        if (request.sendRequest(inputFile, conn, "UTF-8")) {

            StringBuffer response = new StringBuffer();
            try (InputStreamReader isr = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(isr);) {

                LOG.info("Receiving response from Server....");
                Stream<String> s = br.lines();
                s.parallel().forEachOrdered((String line) -> {
                    response.append(line);
                });
                return Boolean.parseBoolean(response.toString());
            } catch (Exception e) {

                connectionExc.handle(e);
            }
        }
        return false;
    }

    @Override
    public Map<String, Object> createModel(Object input) {

        try {

            ObjectMapper mapper = new ObjectMapper();
            String json = input.toString();

            Map<String, Object> map = new HashMap<String, Object>();

            // convert JSON string to Map
            map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });

            return map;

        } catch (JsonGenerationException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*
         * HashMap<String, String> map = new HashMap<String, String>(); JSONObject jObject = new
         * JSONObject(input.toString()); Iterator<?> keys = jObject.keys();
         *
         * while (keys.hasNext()) { String key = (String) keys.next();
         *
         * Object currentValue = jObject.get(key); if (currentValue instanceof String) { String value =
         * jObject.getString(key); map.put(key, value); } else { JSONArray array = jObject.getJSONArray(key);
         * for (Object obj : array) { map.put(key, obj.toString()); } }
         *
         * }
         *
         * System.out.println("json : " + jObject); System.out.println("map : " + map);
         *
         */

        return null;

    }

    @Override
    public List<Object> getInputObjects(Object input, Charset inputCharset) {
        return getInputObjects(input, inputCharset, false);
    }

    @Override
    public List<Object> getInputObjectsRecursively(Object input, Charset inputCharset) {
        return getInputObjects(input, inputCharset, true);
    }

    private ArrayList<Object> castToList(Map<String, Object> mapModel, String key) {
        return (ArrayList<Object>) mapModel.get(key);
    }

    /**
     * Returns the first class or interface object.
     * @param input
     *            container input
     * @param inputCharset
     *            {@link Charset} to be used to read the children
     * @param recursively
     *            states, whether the children should be retrieved recursively
     * @return a list containing the first class or interface.
     */
    public List<Object> getInputObjects(Object input, Charset inputCharset, boolean recursively) {

        LOG.debug("Retrieve input object for input {} {}", input, recursively ? "recursively" : "");
        List<Object> tsInputObjects = new LinkedList<>();

        LOG.debug("DEBUG getInputObjects: " + input.toString());

        try {
            if (isValidInput(input)) {
                String inputModel = (String) read(new File(input.toString()).toPath(), inputCharset);
                Map<String, Object> mapModel = createModel(inputModel);

                if (mapModel.containsKey("classes")) {
                    List<Object> classes = castToList(mapModel, "classes");
                    tsInputObjects.add(castToHashMap(classes.get(0)));
                    return tsInputObjects;
                }

                if (mapModel.containsKey("interfaces")) {
                    List<Object> interfaces = castToList(mapModel, "interfaces");
                    tsInputObjects.add(castToHashMap(interfaces.get(0)));
                    return tsInputObjects;
                }
            }

        } finally {
            request.terminateProcessConnection();
        }

        LOG.error("The given input does neither contain classes nor interfaces");
        return tsInputObjects;
    }

    private LinkedHashMap<String, Object> castToHashMap(Object o) {
        return (LinkedHashMap<String, Object>) o;
    }

    @Override
    public Map<String, Object> getTemplateMethods(Object input) {
        Map<String, Object> methodMap = new HashMap<>();
        return methodMap;
    }

    @Override
    public Object read(Path path, Charset inputCharset, Object... additionalArguments) throws InputReaderException {

        if (request.isNotConnected()) {
            startServerConnection();
        }

        String fileContents;
        // String fileName = path.getFileName().toString();
        String fileName = path.toString();
        try {

            fileContents = String.join("", Files.readAllLines(path, inputCharset));
            // System.out.println("DEBUG -- File content");
            // System.out.println(fileContents);
        } catch (IOException e) {
            throw new InputReaderException("Could not read input file!" + fileName, e);
        }

        InputFileTo inputFile = new InputFileTo(fileName, fileContents, inputCharset.name());

        HttpURLConnection conn =
            request.getConnection("POST", "Content-Type", "application/json", "tsplugin/getInputModel");

        if (request.sendRequest(inputFile, conn, "UTF-8")) {

            StringBuffer inputModel = new StringBuffer();

            try (InputStreamReader isr = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(isr);) {

                LOG.info("Receiving output from Server....");
                Stream<String> s = br.lines();
                s.parallel().forEachOrdered((String line) -> {
                    inputModel.append(line);
                });
                return inputModel.toString();

            } catch (Exception e) {

                connectionExc.handle(e);
            }
        }

        return null;
    }

    @Override
    public boolean isMostLikelyReadable(Path path) {
        return isValidInput(path);
    }

}