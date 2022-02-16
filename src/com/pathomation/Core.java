package com.pathomation;

import java.awt.Image;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <h1>Java SDK</h1>
 * <p>
 * Java wrapper library for PMA.start, a universal viewer for whole slide
 * imaging and microscopy
 * </p>
 *
 * @author Yassine Iddaoui
 * @version 2.0.0.96
 */
public class Core {
	/**
	 * So afterwards we can look up what username actually belongs to a sessions
	 */
	private static Map<String, Object> pmaSessions = new HashMap<String, Object>();
	/**
	 * So afterwards we can determine the PMA.core URL to connect to for a given
	 * SessionID
	 */
	private static Map<String, String> pmaUsernames = new HashMap<>();
	/**
	 * A caching mechanism for slide information; obsolete and should be improved
	 * through httpGet()
	 */
	private static Map<String, Object> pmaSlideInfos = new HashMap<String, Object>();
	private static final String pmaCoreLiteURL = "http://localhost:54001/";
	private static final String pmaCoreLiteSessionID = "SDK.Java";
	private static Boolean pmaUseCacheWhenRetrievingTiles = true;
	/**
	 * Keep track of how much data was downloaded
	 */
	@SuppressWarnings("serial")
	private static Map<String, Integer> pmaAmountOfDataDownloaded = new HashMap<String, Integer>() {
		{
			put(pmaCoreLiteSessionID, 0);
		}
	};

	/**
	 * Object Mapper for Jackson library
	 */
	private static ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * @return the pmaSessions
	 */
	public static Map<String, Object> getPmaSessions() {
		return pmaSessions;
	}

	/**
	 * @return the pmaUsernames
	 */
	public static Map<String, String> getPmaUsernames() {
		return pmaUsernames;
	}

	/**
	 * @return the pmaSlideInfos
	 */
	public static Map<String, Object> getPmaSlideInfos() {
		return pmaSlideInfos;
	}

	/**
	 * @return the pmaCoreLiteURL
	 */
	public static String getPmaCoreLiteURL() {
		return pmaCoreLiteURL;
	}

	/**
	 * @return the pmaCoreLiteSessionID
	 */
	public static String getPmaCoreLiteSessionID() {
		return pmaCoreLiteSessionID;
	}

	/**
	 * @return the pmaAmountOfDataDownloaded
	 */
	public static Map<String, Integer> getPmaAmountOfDataDownloaded() {
		return pmaAmountOfDataDownloaded;
	}

	/**
	 * This method is used to determine whether the Java SDK runs in debugging mode
	 * or not. When in debugging mode (flag = true), extra output is produced when
	 * certain conditions in the code are not met
	 *
	 * @param flag Debugging mode (activated or deactivated)
	 */
	public static void setDebugFlag(boolean flag) {
		PMA.setDebugFlag(flag);
		if (flag) {
			System.out.println(
					"Debug flag enabled. You will receive extra feedback and messages from the Java SDK (like this one)");
			if (PMA.logger != null) {
				PMA.logger.severe(
						"Debug flag enabled. You will receive extra feedback and messages from the Java SDK (like this one)");
			}
		}
	}

	/**
	 * This method is used to get the session's ID
	 *
	 * @param sessionID sessionID : First optional argument(String)
	 *
	 * @return The same sessionID if explicited, otherwise it recovers a session's
	 *         ID
	 */
	private static String sessionId(String sessionID) {
		return sessionID != null ? sessionID : firstSessionId();
	}

	private static String sessionId() {
		return sessionId(null);
	}

	/**
	 * This method is used to get PMA.core active session
	 *
	 * @return PMA.core active session
	 */
	private static String firstSessionId() {
		// do we have any stored sessions from earlier login events?
		if (pmaSessions.size() > 0) {
			// yes we do! This means that when there's a PMA.core active session AND
			// PMA.core.lite version running,
			// the PMA.core active will be selected and returned
			return pmaSessions.keySet().toArray()[0].toString();
		} else {
			// ok, we don't have stored sessions; not a problem per se...
			if (pmaIsLite()) {
				if (!pmaSlideInfos.containsKey(pmaCoreLiteSessionID)) {
					pmaSlideInfos.put(pmaCoreLiteSessionID, new HashMap<String, Object>());
				}
				if (!pmaAmountOfDataDownloaded.containsKey(pmaCoreLiteSessionID)) {
					pmaAmountOfDataDownloaded.put(pmaCoreLiteSessionID, 0);
				}
				return pmaCoreLiteSessionID;
			} else {
				// no stored PMA.core sessions found NOR PMA.core.lite
				return null;
			}
		}
	}

	/**
	 * This method is used to get the url related to the session's ID
	 *
	 * @param sessionID session's ID
	 * @return Url related to the session's ID
	 * @throws Exception if sessionID is invalid
	 */
	public static String pmaUrl(String sessionID) throws Exception {
		sessionID = sessionId(sessionID);
		if (sessionID == null) {
			// sort of a hopeless situation; there is no URL to refer to
			return null;
		} else if (sessionID.equals(pmaCoreLiteSessionID)) {
			return pmaCoreLiteURL;
		} else {
			// assume sessionID is a valid session; otherwise the following will generate an
			// error
			if (pmaSessions.containsKey(sessionID)) {
				String url = pmaSessions.get(sessionID).toString();
				if (!url.endsWith("/")) {
					url = url + "/";
				}
				return url;
			} else {
				if (PMA.logger != null) {
					PMA.logger.severe("Invalid sessionID:" + sessionID);
				}
				throw new Exception("Invalid sessionID:" + sessionID);
			}
		}
	}

	public static String pmaUrl() throws Exception {
		return pmaUrl(null);
	}

	/**
	 * This method is used to check to see if PMA.core.lite (server component of
	 * PMA.start) is running at a given endpoint. if pmaCoreURL is omitted, default
	 * check is to see if PMA.start is effectively running at localhost (defined by
	 * pmaCoreLiteURL). note that PMA.start may not be running, while it is actually
	 * installed. This method doesn't detect whether PMA.start is installed; merely
	 * whether it's running! if pmaCoreURL is specified, then the method checks if
	 * there's an instance of PMA.start (results in True), PMA.core (results in
	 * False) or nothing (at least not a Pathomation software platform component) at
	 * all (results in None)
	 *
	 * @param pmaCoreURL  First optional argument(String), default
	 *                	value(Class field pmaCoreLiteURL), url of PMA.core instance
	 * @return True if an instance of PMA.core.lite is running, false otherwise
	 */
	private static Boolean pmaIsLite(String pmaCoreURL) {
		pmaCoreURL = pmaCoreURL != null ? pmaCoreURL : pmaCoreLiteURL;
		String url = PMA.join(pmaCoreURL, "api/json/IsLite");
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			return jsonString.equals("true");
		} catch (Exception e) {
			// this happens when NO instance of PMA.core is detected
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	private static Boolean pmaIsLite(){
		return pmaIsLite(null);
	}

	/**
	 * This method is used to define which content will be received "XML" or "Json"
	 * for "API" Web service calls
	 *
	 * @param sessionID session's ID
	 *@param  xml XML or Json content
	 * @return Add a sequence to the url to specify which content to be received
	 *         (XML or Json)
	 */
	private static String apiUrl(String sessionID, Boolean xml) {
		// let's get the base URL first for the specified session
		xml = xml == null ? false : xml;
		String url;
		try {
			url = pmaUrl(sessionID);
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			url = null;
		}
		if (url == null) {
			// sort of a hopeless situation; there is no URL to refer to
			return null;
		}
		// remember, _pma_url is guaranteed to return a URL that ends with "/"
		if (xml) {
			return PMA.join(url, "api/xml/");
		} else {
			return PMA.join(url, "api/json/");
		}
	}

	private static String apiUrl(String sessionID) {
		return apiUrl(sessionID, false);
	}

	private static String apiUrl() {
		return apiUrl(null, false);
	}

	/**
	 * This method is used to create the query URL for a session ID
	 *
	 * @param sessionID : session's ID
	 * @return Query URL
	 */
	public static String queryUrl(String sessionID) {
		try {
			String url = pmaUrl(sessionID);
			if (url == null) {
				// sort of a hopeless situation; there is no URL to refer to
				return null;
			}
			// remember, pmaUrl is guaranteed to return a URL that ends with "/"
			return PMA.join(url, "query/json/");
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static String queryUrl() {
		return queryUrl(null);
	}

	/**
	 * checks to see if PMA.core.lite (server component of PMA.start) is running at
	 * a given endpoint. if pmaCoreURL is omitted, default check is to see if
	 * PMA.start is effectively running at localhost (defined by pmaCoreLiteURL).
	 * note that PMA.start may not be running, while it is actually installed. This
	 * method doesn't detect whether PMA.start is installed; merely whether it's
	 * running! if pmaCoreURL is specified, then the method checks if there's an
	 * instance of PMA.start (results in True), PMA.core (results in False) or
	 * nothing (at least not a Pathomation software platform component) at all
	 * (results in None)
	 *
	 * @param pmaCoreURL default value(Class field pmaCoreLiteURL), url of
	 *                      PMA.core instance
>
	 * @return Checks if there is a PMA.core.lite or PMA.core instance running
	 */
	public static Boolean isLite(String pmaCoreURL) {
		pmaCoreURL = pmaCoreURL == null ? pmaCoreLiteURL: pmaCoreURL;
		// See if there's a PMA.core.lite or PMA.core instance running at pmacoreURL
		return pmaIsLite(pmaCoreURL);
	}

	public static Boolean isLite() {
		return isLite(null);
	}

	/**
	 * This method is used to get the version number
	 *
	 * @param pmaCoreURL url of PMA.core instance
	 * @return Version number
	 */
	public static String getVersionInfo(String pmaCoreURL) {
		pmaCoreURL = pmaCoreURL == null ? pmaCoreLiteURL : pmaCoreURL;
		// Get version info from PMA.core instance running at pmacoreURL.
		// Return null if PMA.core not found running at pmacoreURL endpoint
		// purposefully DON'T use helper function apiUrl() here:
		// why? because GetVersionInfo can be invoked WITHOUT a valid SessionID;
		// apiUrl() takes session information into account
		String url = PMA.join(pmaCoreURL, "api/json/GetVersionInfo");
		String version = null;
		if (PMA.debug) {
			System.out.println(url);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("getVersionInfo failed : " + jsonResponse.get("Message"));
					}
					throw new Exception("getVersionInfo failed : " + jsonResponse.get("Message"));
				} else if (jsonResponse.has("d")) {
					version = jsonResponse.getString("d");
				} else {
					return null;
				}
			} else {
				version = jsonString;
			}
			return version;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static String getVersionInfo() {
		return getVersionInfo(null);
	}

	/**
	 * This method is used to get the API version in a list fashion
	 *
	 * @param pmaCoreURL pmaCoreLiteURL), url of PMA.core instance
	 * @return API version in a list fashion
	 * @throws Exception If GetAPIVersion isn't available on the API
	 */
	public static List<Integer> getAPIVersion(String pmaCoreURL) throws Exception {
		pmaCoreURL = pmaCoreURL == null ? pmaCoreLiteURL : pmaCoreURL;
		String url = PMA.join(pmaCoreURL, "api/json/GetAPIVersion");
		if (PMA.debug) {
			System.out.println(url);
		}

		String jsonString = null;
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			jsonString = PMA.getJSONAsStringBuffer(con).toString();
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		List<Integer> version = null;
		try {
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_api_version resulted in: " + jsonResponse.get("Message"));
					}
					throw new Exception("get_api_version resulted in: " + jsonResponse.get("Message"));
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					version = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						version.add(array.optInt(i));
					}
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				version = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					version.add(jsonResponse.optInt(i));
				}
			}
			return version;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
				PMA.logger.severe("GetAPIVersion method not available at " + pmaCoreURL);
			}
			throw new Exception("GetAPIVersion method not available at " + pmaCoreURL);
		}
	}

	public static List<Integer> getAPIVersion() throws Exception {
		return getAPIVersion(null);
	}

	/**
	 * This method is used to get the API version in a single string
	 *
	 * @param pmaCoreURL default value(Class field pmaCoreLiteURL), url of
	 *                      PMA.core instance
	 * @return API version in a single string
	 * @throws Exception If GetAPIVersion isn't available on the API
	 *
	 */
	public static String getAPIVersionString(String pmaCoreURL) throws Exception {
		pmaCoreURL = pmaCoreURL == null ? pmaCoreLiteURL : pmaCoreURL;
		List<Integer> version = getAPIVersion(pmaCoreURL);
		String versionString = version.stream().map(n -> (n + ".")).collect(Collectors.joining("", "", ""));
		return versionString.substring(0, versionString.length() - 1);
	}

	public static String getAPIVersionString() throws Exception {
		return getAPIVersionString(null);
	}

	/**
	 * This method is used to authenticate &amp; connect to a PMA.core instance
	 * using credentials
	 *
	 * @param pmaCoreURL  url of PMA.core instance
	 * @param pmaCoreUsername : username for PMA.core instance
	 * @param pmaCorePassword password for PMA.core instance
	 * @return session's ID if session was created successfully, otherwise null
	 */
	public static String connect(String pmaCoreURL, String pmaCoreUsername, String pmaCorePassword) {
		pmaCoreURL = pmaCoreURL == null  ? pmaCoreLiteURL : pmaCoreURL;
		pmaCoreUsername = pmaCoreUsername == null ? "" : pmaCoreUsername;
		pmaCorePassword = pmaCorePassword == null ? "" : pmaCorePassword;
		// Attempt to connect to PMA.core instance; success results in a SessionID
		if (pmaCoreURL.equals(pmaCoreLiteURL)) {
			if (isLite()) {
				// no point authenticating localhost / PMA.core.lite
				return pmaCoreLiteSessionID;
			} else {
				return null;
			}
		}
		// purposefully DON'T use helper function apiUrl() here:
		// why? Because apiUrl() takes session information into account (which we
		// don't have yet)
		String url = PMA.join(pmaCoreURL, "api/json/authenticate?caller=SDK.Java");
		if (!pmaCoreUsername.equals("")) {
			url = url.concat("&username=").concat(PMA.pmaQ(pmaCoreUsername));
		}
		if (!pmaCorePassword.equals("")) {
			url = url.concat("&password=").concat(PMA.pmaQ(pmaCorePassword));
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			String sessionID = null;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				if (!jsonResponse.get("Success").toString().toLowerCase().equals("true")) {
					return null;
				} else {
					sessionID = jsonResponse.getString("SessionId");
					pmaUsernames.put(sessionID, pmaCoreUsername);
					pmaSessions.put(sessionID, pmaCoreURL);
					if (!pmaSlideInfos.containsKey(sessionID)) {
						pmaSlideInfos.put(sessionID, new HashMap<String, Object>());
					}
					pmaAmountOfDataDownloaded.put(sessionID, jsonResponse.length());
					return sessionID;
				}
			} else {
				return null;
			}
		} catch (Exception e) {
			// Something went wrong; unable to communicate with specified endpoint
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static String connect(String pmaCoreURL, String pmaCoreUsername) {
		return connect(pmaCoreURL, pmaCoreUsername, null);
	}

	public static String connect(String pmaCoreURL) {
		return connect(pmaCoreURL, null, null);
	}

	public static String connect() {
		return connect(null, null, null);
	}

	/**
	 * This method is used to disconnect from a running PMA.core instance
	 *
	 * @param sessionID  default session's ID
	 * @return true if there was a PMA.core instance running to disconnect from,
	 *         false otherwise
	 */
	public static Boolean disconnect(String sessionID) {
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID, false) + "DeAuthenticate?sessionID=" + PMA.pmaQ((sessionID));
		String contents = PMA.httpGet(url, "application/json");
		pmaAmountOfDataDownloaded.put(sessionID, pmaAmountOfDataDownloaded.get(sessionID) + contents.length());
		if (pmaSessions.size() > 0) {
			// yes we do! This means that when there's a PMA.core active session AND
			// PMA.core.lite version running,
			// the PMA.core active will be selected and returned
			pmaSessions.remove(sessionID);
			pmaSlideInfos.remove(sessionID);
			return true;
		} else {
			return false;
		}
	}

	public static Boolean disconnect() {
		return disconnect(null);
	}

	/**
	 * This method is used to test if sessionID is valid and the server is online
	 * and reachable This method works only for PMA.core, don't use it for PMA.start
	 * for it will return always false
	 *
	 * @param sessionID session's ID
	 * @return true if sessionID is valid and the server is online and reachable,
	 *         false otherwise
	 */
	public static boolean ping(String sessionID) {
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID, false) + "Ping?sessionID=" + PMA.pmaQ(sessionID);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			con.setRequestProperty("Accept", "application/json");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			return jsonString.equals("true") ? true : false;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return false;
		}
	}

	public static boolean ping() {
		return ping(null);
	}

	/**
	 * This method is used to get root-directories available for a sessionID
	 *
	 * @param sessionID session's ID
	 * @return Array of root-directories available to a session's ID
	 */
	public static List<String> getRootDirectories(String sessionID) {
		// Return a list of root-directories available to sessionID
		sessionID = sessionId(sessionID);
		try {
			String url = apiUrl(sessionID, false) + "GetRootDirectories?sessionID=" + PMA.pmaQ(sessionID);
			String jsonString = PMA.httpGet(url, "application/json");
			List<String> rootDirs;
			if (PMA.isJSONArray(jsonString)) {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				rootDirs = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					rootDirs.add(jsonResponse.optString(i));
				}
				// return dirs;
			} else {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("getrootdirectories() failed with error " + jsonResponse.get("Message"));
					}
					// throw new Exception("getrootdirectories() failed with error " +
					// jsonResponse.get("Message"));
				}
				return null;
			}
			return rootDirs;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static List<String> getRootDirectories() {
		return getRootDirectories(null);
	}

	/**
	 * This method is used to get sub-directories available to sessionID in the
	 * start directory following a recursive (or not) approach
	 *
	 * @param startDir Start directory
	 * @param sessionID default value(null), session's ID
	 * @param recursive
	 * @return Sub-directories available to a session's ID in a start directory
	 */
	public static List<String> getDirectories(String startDir,
											  String sessionID,
											  Boolean recursive
											  ) {
		recursive = recursive == null ? false : recursive;
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID, false) + "GetDirectories?sessionID=" + PMA.pmaQ(sessionID) + "&path="
				+ PMA.pmaQ(startDir);
		if (PMA.debug) {
			System.out.println(url);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			List<String> dirs;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_directories to " + startDir + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("get_directories to " + startDir + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					dirs = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						dirs.add(array.optString(i));
					}
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				dirs = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					dirs.add(jsonResponse.optString(i));
				}
			}
			if (recursive) {
				for (String dir : getDirectories(startDir, sessionID)) {
					dirs.addAll(getDirectories(dir, sessionID, recursive));
				}
			}
			return dirs;

		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static List<String> getDirectories(String startDir,
											  String sessionID,
											  Integer integerRecursive) {
		sessionID = sessionId(sessionID);
		Boolean recursive;
		if (integerRecursive != null) {
			recursive = integerRecursive > 0;
		} else {
			recursive = false;
		}
		String url = apiUrl(sessionID, false) + "GetDirectories?sessionID=" + PMA.pmaQ(sessionID) + "&path="
				+ PMA.pmaQ(startDir);
		if (PMA.debug) {
			System.out.println(url);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			List<String> dirs;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_directories to " + startDir + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("get_directories to " + startDir + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					dirs = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						dirs.add(array.optString(i));
					}
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				dirs = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					dirs.add(jsonResponse.optString(i));
				}
			}

			if (recursive) {
				for (String dir : getDirectories(startDir, sessionID)) {
					dirs.addAll(getDirectories(dir, sessionID, integerRecursive - 1));
				}
			}
			return dirs;

		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static List<String> getDirectories(String startDir, String sessionID) {
		return getDirectories(startDir, sessionID, 0);
	}

	public static List<String> getDirectories(String startDir) {
		return getDirectories(startDir, null, 0);
	}

	/**
	 * This method is used to get the first non empty directory
	 *
	 * @param startDir start directory
	 * @param sessionID session's ID
	 * @return Path to the first non empty directory found
	 */
	public static String getFirstNonEmptyDirectory(String startDir, String sessionID) {
		if ((startDir == null) || (startDir.equals(""))) {
			startDir = "/";
		}
		List<String> slides = null;
		try {
			slides = getSlides(startDir, sessionID);
		} catch (Exception e) {
			if (PMA.debug) {
				System.out.println("Unable to examine " + startDir);
				if (PMA.logger != null) {
					PMA.logger.severe("Unable to examine " + startDir);
				}
			}
			if (!startDir.equals("/")) {
				return null;
			}
		}
		if ((slides != null) && (slides.size() > 0)) {
			return startDir;
		} else {
			if (startDir.equals("/")) {
				for (String dir : getRootDirectories(sessionID)) {
					String nonEmptyDir = getFirstNonEmptyDirectory(dir, sessionID);
					if (nonEmptyDir != null) {
						return nonEmptyDir;
					}
				}
			} else {
				boolean success = true;
				List<String> dirs = null;
				try {
					dirs = getDirectories(startDir, sessionID);
				} catch (Exception e) {
					System.out.println("Unable to examine " + startDir);
					if (PMA.logger != null) {
						PMA.logger.severe(
								"Debug flag enabled. You will receive extra feedback and messages from the Java SDK (like this one)");
					}
					success = false;
				}
				if (success) {
					for (String dir : dirs) {
						String nonEmptyDir = getFirstNonEmptyDirectory(dir, sessionID);
						if (nonEmptyDir != null) {
							return nonEmptyDir;
						}
					}
				}
			}
			return null;
		}
	}

	public static String getFirstNonEmptyDirectory(String startDir) {
		return getFirstNonEmptyDirectory(startDir, null);
	}

	public static String getFirstNonEmptyDirectory() {
		return getFirstNonEmptyDirectory(null, null);
	}

	/**
	 * This method is used to get a list of slides available to sessionID in the
	 * start directory following a recursive (or not) approach
	 *
	 * @param startDir Start directory
	 * @param sessionID session's ID
	 * @param recursive if it's a Boolean if defines
	 *                 either no recursive or a limitless recursivity, if
	 *                     it's an
	 *                 Integer it defines a limited in depth recursivity or no
	 *                 recursivity at all if this Integer equals 0
	 * @return List of slides available to a session's ID in a start directory
	 */
	public static List<String> getSlides(String startDir, String sessionID, Boolean recursive) {
		// Return a list of slides available to sessionID in the startDir directory
		recursive = recursive == null ? false : recursive;
		sessionID = sessionId(sessionID);
		if (startDir.startsWith("/")) {
			startDir = startDir.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFiles?sessionID=" + PMA.pmaQ(sessionID) + "&path="
				+ PMA.pmaQ(startDir);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			List<String> slides;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_slides from " + startDir + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("get_slides from " + startDir + " resulted in: " + jsonResponse.get("Message")
							+ " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					slides = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						slides.add(array.optString(i));
					}
					// return slides;
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				slides = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					slides.add(jsonResponse.optString(i));
				}
				// return slides;
			}

			// we test if call is recursive, and if yes to which depth
			if (recursive) {
				for (String dir : getDirectories(startDir, sessionID)) {
					slides.addAll(getSlides(dir, sessionID, recursive));
				}
			}
			return slides;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static List<String> getSlides(String startDir, String sessionID, Integer integerRecursive) {
		Boolean recursive;
		if (integerRecursive != null) {
			recursive = integerRecursive > 0;
		} else {
			recursive = false;
		}
		// Return a list of slides available to sessionID in the startDir directory
		sessionID = sessionId(sessionID);
		if (startDir.startsWith("/")) {
			startDir = startDir.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFiles?sessionID=" + PMA.pmaQ(sessionID) + "&path="
				+ PMA.pmaQ(startDir);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			List<String> slides;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_slides from " + startDir + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("get_slides from " + startDir + " resulted in: " + jsonResponse.get("Message")
							+ " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					slides = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						slides.add(array.optString(i));
					}
					// return slides;
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				slides = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					slides.add(jsonResponse.optString(i));
				}
				// return slides;
			}

			// we test if call is recursive, and if yes to which depth
			if (recursive) {
				for (String dir : getDirectories(startDir, sessionID)) {
					slides.addAll(getSlides(dir, sessionID, integerRecursive - 1));
				}
			}
			return slides;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static List<String> getSlides(String startDir, String sessionID) {
		return getSlides(startDir, sessionID, 0);
	}


	public static List<String> getSlides(String startDir) {
		return getSlides(startDir, null, 0);
	}

	/**
	 * This method is used to determine the file extension for a slide's path
	 *
	 * @param slideRef slide's path
	 * @return File extension extracted from a slide's path
	 */
	public static String getSlideFileExtension(String slideRef) {
		// Determine the file extension for this slide
		return FilenameUtils.getExtension(slideRef);
	}

	/**
	 * This method is used to determine file name (with extension) for a slide's
	 * path
	 *
	 * @param slideRef slide's path
	 * @return File name extracted from a slide's path
	 */
	public static String getSlideFileName(String slideRef) {
		// Determine the file name (with extension) for this slide
		return FilenameUtils.getName(slideRef);
	}

	/**
	 * This method is used to get the UID for a defined slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return UID for a defined slide's path
	 * @throws Exception if PMA.core not found
	 */
	public static String getUid(String slideRef, String sessionID) throws Exception {
		sessionID = sessionId(sessionID);
		if (sessionID.equals(pmaCoreLiteSessionID)) {
			if (isLite()) {
				if (PMA.logger != null) {
					PMA.logger.severe(
							"PMA.core.lite found running, but doesn't support UID generation. For advanced anonymization, please upgrade to PMA.core.");
				}
				throw new Exception(
						"PMA.core.lite found running, but doesn't support UID generation. For advanced anonymization, please upgrade to PMA.core.");

			} else {
				if (PMA.logger != null) {
					PMA.logger.severe(
							"PMA.core.lite not found, and besides; it doesn't support UID generation. For advanced anonymization, please upgrade to PMA.core.");
				}
				throw new Exception(
						"PMA.core.lite not found, and besides; it doesn't support UID generation. For advanced anonymization, please upgrade to PMA.core.");
			}
		}
		String url = apiUrl(sessionID, false) + "GetUID?sessionID=" + PMA.pmaQ(sessionID) + "&path="
				+ PMA.pmaQ(slideRef);
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			pmaAmountOfDataDownloaded.put(sessionID, pmaAmountOfDataDownloaded.get(sessionID) + jsonString.length());
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("getUid() on  " + slideRef + " resulted in: " + jsonResponse.get("Message"));
					}
					// throw new Exception("getUid() on " + slideRef + " resulted in: " +
					// jsonResponse.get("Message"));
				}
				return null;
			} else {
				return jsonString;
			}
		} catch (Exception e) {
			// this happens when NO instance of PMA.core is detected
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static String getUid(String slideRef) throws Exception {
		return getUid(slideRef, null);
	}

	/**
	 * This method is used to get the fingerprint for a specific slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Fingerprint of the slide
	 */
	public static String getFingerPrint(String slideRef, String sessionID) {
		// Get the fingerprint for a specific slide
		sessionID = sessionId(sessionID);
		String fingerprint;
		String url = apiUrl(sessionID, false) + "GetFingerprint?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_fingerprint on " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("get_fingerprint on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else {
					return jsonResponse.getString("d");
				}
			} else {
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonString.length());
				fingerprint = jsonString.replace("\"", "");
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return fingerprint;
	}

	public static String getFingerPrint(String slideRef) {
		return getFingerPrint(null);
	}

	/**
	 * This method is used to get information about a session
	 *
	 * @param sessionID session's ID
	 * @return Information about a session
	 */
	public static Map<String, String> whoAmI(String sessionID) {
		// Getting information about your Session
		sessionID = sessionId(sessionID);
		Map<String, String> retval = null;
		if (sessionID.equals(pmaCoreLiteSessionID)) {
			retval = new HashMap<>();
			retval.put("sessionID", pmaCoreLiteSessionID);
			retval.put("username", null);
			retval.put("url", pmaCoreLiteURL);
			retval.put("amountOfDataDownloaded", pmaAmountOfDataDownloaded.get(pmaCoreLiteSessionID).toString());
		} else if (sessionID != null) {
			retval = new HashMap<>();
			retval.put("sessionID", sessionID);
			retval.put("username", pmaUsernames.get(sessionID));
			retval.put("amountOfDataDownloaded", pmaAmountOfDataDownloaded.get(sessionID).toString());
			try {
				retval.put("url", pmaUrl(sessionID));
			} catch (Exception e) {
				if (PMA.logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					PMA.logger.severe(sw.toString());
				}
			}
		}
		return retval;
	}

	public static Map<String, String> whoAmI() {
		return whoAmI(null);
	}

	/**
	 * This method is used to get tile size information for sessionID
	 *
	 * @param sessionID session's ID
	 * @return A list of two items (duplicated) relative to the tile size
	 *         information for a session's ID
	 */
	@SuppressWarnings("unchecked")
	public static List<Integer> getTileSize(String sessionID) {
		sessionID = sessionId(sessionID);
		Map<String, Object> info;
		if (((Map<String, Object>) pmaSlideInfos.get(sessionID)).size() < 1) {
			String dir = getFirstNonEmptyDirectory(sessionID);
			List<String> slides = getSlides(dir, sessionID);
			info = getSlideInfo(slides.get(0), sessionID);
		} else {
			int getLength = ((Map<String, Object>) pmaSlideInfos.get(sessionID)).values().toArray().length;
			info = (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).values()
					.toArray()[new Random().nextInt(getLength)];
		}
		List<Integer> result = new ArrayList<>();
		result.add(Integer.parseInt(info.get("TileSize").toString()));
		result.add(Integer.parseInt(info.get("TileSize").toString()));
		return result;
	}

	public static List<Integer> getTileSize(){
		return getTileSize(null);
	}

	/**
	 * This method is used to get a raw image in the form of nested maps
	 *
	 * @param slideRef slide's path or UID
	 * @param sessionID
	 * @return Nested maps forming a raw image
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> getSlideInfo(String slideRef, String sessionID) {
		// Return raw image information in the form of nested maps
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (!((Map<String, Object>) pmaSlideInfos.get(sessionID)).containsKey(slideRef)) {
			try {
				String url = apiUrl(sessionID, false) + "GetImageInfo?SessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
						+ PMA.pmaQ(slideRef);
				if (PMA.debug) {
					System.out.println(url);
				}
				URL urlResource = new URL(url);
				HttpURLConnection con;
				if (url.startsWith("https")) {
					con = (HttpsURLConnection) urlResource.openConnection();
				} else {
					con = (HttpURLConnection) urlResource.openConnection();
				}
				con.setRequestMethod("GET");
				String jsonString = PMA.getJSONAsStringBuffer(con).toString();
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("ImageInfo to " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("ImageInfo to " + slideRef + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that slideRef is case sensitive!)");
					} else if (jsonResponse.has("d")) {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
						}).with(DeserializationFeature.USE_LONG_FOR_INTS).readValue(jsonResponse.get("d").toString());
						// we store the map created for both the slide name & the UID
						((Map<String, Object>) pmaSlideInfos.get(sessionID))
								.put(jsonResponse.getJSONObject("d").optString("Filename"), jsonMap);
						// ((Map<String, Object>) pmaSlideInfos.get(sessionID))
						// .put(jsonResponse.getJSONObject("d").optString("UID"), jsonMap);
					} else {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
						}).with(DeserializationFeature.USE_LONG_FOR_INTS).readValue(jsonResponse.toString());
						// we store the map created for both the slide name & the UID
						((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(jsonResponse.getString("Filename"),
								jsonMap);
						if (!sessionID.equals(pmaCoreLiteSessionID)) {
							((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(jsonResponse.getString("UID"),
									jsonMap);
						}
					}
				} else {
					// JSONArray jsonResponse = getJSONArrayResponse(jsonString);
					// pmaAmountOfDataDownloaded.put(sessionID,
					// pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					// ((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(slideRef,
					// jsonResponse);
					return null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				if (PMA.logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					PMA.logger.severe(sw.toString());
				}
				return null;
			}
		}
		return (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slideRef);
	}

	public static Map<String, Object> getSlideInfo(String slideRef) {
		return getSlideInfo(null);
	}

	/**
	 * This method is used to get raw images in the form of nested maps
	 *
	 * @param slideRefs List of slides' path or UID
	 * @param sessionID  session's ID
	 * @return Nested maps forming raw images
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Map<String, Object>> getSlidesInfo(List<String> slideRefs, String sessionID) {
		// Return raw image information in the form of nested maps
		sessionID = sessionId(sessionID);
		List<String> slideRefsNew = new ArrayList<>();
		for (String slideRef : slideRefs) {
			if (slideRef.startsWith("/")) {
				slideRef = slideRef.substring(1);
			}
			if (!((Map<String, Object>) pmaSlideInfos.get(sessionID)).containsKey(slideRef)) {
				slideRefsNew.add(slideRef);
			}
		}
		if (slideRefsNew.size() > 0) {
			try {
				String url = apiUrl(sessionID, false) + "GetImagesInfo";
				URL urlResource = new URL(url);
				HttpURLConnection con;
				if (url.startsWith("https")) {
					con = (HttpsURLConnection) urlResource.openConnection();
				} else {
					con = (HttpURLConnection) urlResource.openConnection();
				}
				con.setRequestMethod("POST");
				con.setRequestProperty("Content-Type", "application/json");
				con.setUseCaches(true);
				con.setDoOutput(true);
				// we convert the list of slide to a string of this fashion :
				// ["slide1","slide2"....]
				String slideRefsNewForJson = slideRefsNew.stream().map(n -> ("\"" + n + "\""))
						.collect(Collectors.joining(",", "[", "]"));
				String input = "{ \"sessionID\": \"" + sessionID + "\", \"pathOrUids\": " + slideRefsNewForJson + "}";
				OutputStream os = con.getOutputStream();
				os.write(input.getBytes("UTF-8"));
				os.close();
				String jsonString = PMA.getJSONAsStringBuffer(con).toString();
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("ImageInfos to " + slideRefs.toString() + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("ImageInfos to " + slideRefs.toString() + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else if (jsonResponse.has("d")) {
						JSONArray jsonArrayResponse = jsonResponse.getJSONArray("d");
						for (int i = 0; i < jsonArrayResponse.length(); i++) {
							// we convert the Json object to a Map<String, Object>
							Map<String, Object> jsonMap = objectMapper
									.readerFor(new TypeReference<Map<String, Object>>() {
									}).with(DeserializationFeature.USE_LONG_FOR_INTS)
									.readValue(jsonArrayResponse.getJSONObject(i).toString());
							// we store the map created for both the slide name & the UID
							((Map<String, Object>) pmaSlideInfos.get(sessionID))
									.put(jsonArrayResponse.getJSONObject(i).getString("Filename"), jsonMap);
							// ((Map<String, Object>) pmaSlideInfos.get(sessionID))
							// .put(jsonArrayResponse.getJSONObject(i).getString("UID"), jsonMap);
						}
					} else {
						return null;
					}
				} else {
					JSONArray jsonArrayResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonArrayResponse.length());
					for (int i = 0; i < jsonArrayResponse.length(); i++) {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
						}).with(DeserializationFeature.USE_LONG_FOR_INTS)
								.readValue(jsonArrayResponse.getJSONObject(i).toString());
						// we store the map created for both the slide name & the UID
						((Map<String, Object>) pmaSlideInfos.get(sessionID))
								.put(jsonArrayResponse.getJSONObject(i).getString("Filename"), jsonMap);
						if (!sessionID.equals(pmaCoreLiteSessionID)) {
							((Map<String, Object>) pmaSlideInfos.get(sessionID))
									.put(jsonArrayResponse.getJSONObject(i).getString("UID"), jsonMap);
						}
					}
				}
				Map<String, Map<String, Object>> results = new HashMap<String, Map<String, Object>>();
				for (String slide : slideRefs) {
					results.put(slide,
							(Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slide));
				}
				return results;
			} catch (Exception e) {
				e.printStackTrace();
				if (PMA.logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					PMA.logger.severe(sw.toString());
				}
				return null;
			}
		}
		// if for all the slides, the image info data has been already stored on
		// pmaSlideInfos
		Map<String, Map<String, Object>> results = new HashMap<String, Map<String, Object>>();
		for (String slide : slideRefs) {
			results.put(slide, (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slide));
		}
		return results;
	}

	public static Map<String, Map<String, Object>> getSlidesInfo(List<String> slideRefs) {
		return getSlidesInfo(slideRefs, null);
	}

	/**
	 * This method is used to determine the maximum zoom level that still represents
	 * an optical magnification
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Max zoom level that still represents an optical magnification
	 */
	public static int getMaxZoomLevel(String slideRef, String sessionID) {
		// Determine the maximum zoomlevel that still represents an optical
		// magnification
		sessionID = sessionId(sessionID);
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		if (info == null) {
			System.out.print("Unable to get information for " + slideRef + " from " + sessionID);
			return 0;
		} else if (info.containsKey("MaxZoomLevel")) {
			try {
				return Integer.parseInt(info.get("MaxZoomLevel").toString());
			} catch (Exception e) {
				System.out.print("Something went wrong consulting the MaxZoomLevel key in info Map; value ="
						+ info.get("MaxZoomLevel").toString());
				e.printStackTrace();
				if (PMA.logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					PMA.logger.severe(sw.toString());
					PMA.logger.severe("Something went wrong consulting the MaxZoomLevel key in info Map; value ="
							+ info.get("MaxZoomLevel").toString());
				}
				return 0;
			}
		} else {
			try {
				return Integer.parseInt(info.get("NumberOfZoomLevels").toString());
			} catch (Exception e) {
				System.out.print("Something went wrong consulting the NumberOfZoomLevels key in info Map; value ="
						+ info.get("NumberOfZoomLevels").toString());
				e.printStackTrace();
				if (PMA.logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					PMA.logger.severe(sw.toString());
					PMA.logger.severe("Something went wrong consulting the NumberOfZoomLevels key in info Map; value ="
							+ info.get("NumberOfZoomLevels").toString());
				}
				return 0;
			}
		}
	}

	public static int getMaxZoomLevel(String slideRef) {
		return getMaxZoomLevel(null);
	}

	/**
	 * This method is used to get list with all zoom levels, from 0 to max zoom
	 * level
	 *
	 * @param slideRef slide's path
	 * @param sessionID default value(null), session's ID
	 * @param minNumberOfTiles default value(0), minimal number of tiles used
	 * @return List with all zoom levels, from 0 to max zoom level
	 */
	public static List<Integer> getZoomLevelsList(String slideRef,
												  String sessionID,
												  Integer minNumberOfTiles) {
		minNumberOfTiles = minNumberOfTiles == null ? 0 : minNumberOfTiles;
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsList() : Invalid argument");
				}
				else {
					throw new IllegalArgumentException("...");
				}
		// Obtain a list with all zoom levels, starting with 0 and up to and including
		// max zoom level
		// Use min_number_of_tiles argument to specify that you're only interested in
		// zoom levels that include at lease a given number of tiles
		List<Integer> result = new ArrayList<>();
		Set<Integer> set = getZoomLevelsDict(slideRef, sessionID,
				minNumberOfTiles).keySet();
		for (Integer i : set) {
			result.add(i);
		}
		Collections.sort(result);
		return result;
	}

	public static List<Integer> getZoomLevelsList(String slideRef) {
		return getZoomLevelsList(slideRef, null, null);
	}

	public static List<Integer> getZoomLevelsList(String slideRef, String sessionID) {
		return getZoomLevelsList(slideRef, sessionID, null);
	}


	/**
	 * This method is used to get a map with the number of tiles per zoom level
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @param minNumberOfTiles minimal number of tiles used to specify that you're
	 *                 only interested in zoom levels that include at least a given
	 *                 number of tiles
	 * @return Map with the number of tiles per zoom level
	 */
	public static Map<Integer, List<Integer>> getZoomLevelsDict(String slideRef, String sessionID, Integer minNumberOfTiles) {
		minNumberOfTiles = minNumberOfTiles == null ? 0 : minNumberOfTiles;
		// Obtain a map with the number of tiles per zoom level.
		// Information is returned as (x, y, n) lists per zoom level, with
		// x = number of horizontal tiles,
		// y = number of vertical tiles,
		// n = total number of tiles at specified zoom level (x * y)
		// Use min_number_of_tiles argument to specify that you're only interested in
		// zoom levels that include at least a given number of tiles
		List<Integer> zoomLevels = new ArrayList<Integer>();
		IntStream.range(0, getMaxZoomLevel(slideRef, sessionID) + 1).forEach(n -> {
			zoomLevels.add(n);
		});
		List<List<Integer>> dimensions = new ArrayList<>();
		for (int z : zoomLevels) {
			if (getNumberOfTiles(slideRef, z, sessionID).get(2) > minNumberOfTiles) {
				dimensions.add(getNumberOfTiles(slideRef, z, sessionID));
			}
		}
		List<Integer> zoomLevelsSubList = (List<Integer>) zoomLevels.subList(zoomLevels.size() - dimensions.size(),
				zoomLevels.size());
		Map<Integer, List<Integer>> d = new HashMap<>();
		for (int i = 0; i < zoomLevelsSubList.size() && i < dimensions.size(); i++) {
			d.put(zoomLevelsSubList.get(i), dimensions.get(i));
		}
		return d;
	}

	public static Map<Integer, List<Integer>> getZoomLevelsDict(String slideRef, String sessionID) {
		return getZoomLevelsDict(slideRef, sessionID, null);
	}

	public static Map<Integer, List<Integer>> getZoomLevelsDict(String slideRef) {
		return getZoomLevelsDict(slideRef, null, null);
	}

	/**
	 * This method is used to get the physical dimension in terms of pixels per
	 * micrometer of a slide
	 *
	 * @param slideRef slide's path
	 * @param zoomLevel :zoom level
	 * @param  sessionID session's ID
	 * @return Two items list containing the physical dimension in terms of pixels
	 *         per micrometer of a slide
	 */
	public static List<Float> getPixelsPerMicrometer(String slideRef,
													 Integer zoomLevel,
													 String sessionID) {
		zoomLevel = zoomLevel == null ? 0 : zoomLevel;
		// Retrieve the physical dimension in terms of pixels per micrometer.
		// When zoom level is left to its default value of None, dimensions at the
		// highest zoom level are returned
		// (in effect returning the "native" resolution at which the slide was
		// registered)
		int maxZoomLevel = getMaxZoomLevel(slideRef, sessionID);
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		float xppm = (float) info.get("MicrometresPerPixelX");
		float yppm = (float) info.get("MicrometresPerPixelY");
		List<Float> result = new ArrayList<>();
		if ((zoomLevel == null) || (zoomLevel == maxZoomLevel)) {
			result.add(xppm);
			result.add(yppm);
			return result;
		} else {
			double factor = Math.pow(2, zoomLevel - maxZoomLevel);
			result.add((float) (xppm / factor));
			result.add((float) (yppm / factor));
			return result;
		}
	}

	public static List<Float> getPixelsPerMicrometer(String slideRef,
													 Integer zoomLevel) {
		return getPixelsPerMicrometer(slideRef, zoomLevel, null);
	}

	public static List<Float> getPixelsPerMicrometer(String slideRef) {
		return getPixelsPerMicrometer(slideRef, null, null);
	}

	public static List<Float> getPixelsPerMicrometer(String slideRef,
													 String sessionID) {
		return getPixelsPerMicrometer(slideRef, null, sessionID);
	}

	/**
	 * This method is used to get the total dimensions of a slide image at a given
	 * zoom level
	 *
	 * @param slideRef slide's path
	 * @param zoomLevel zoom level
	 * @param sessionID session's ID
	 * @return Two items list with the total dimensions of a slide image at a given
	 *         zoom level
	 */
	public static List<Integer> getPixelDimensions(String slideRef,
												   Integer zoomLevel,
												   String sessionID) {
		zoomLevel = zoomLevel == null ? 0 : zoomLevel;
		// Get the total dimensions of a slide image at a given zoom level
		int maxZoomLevel = getMaxZoomLevel(slideRef, sessionID);
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		List<Integer> result = new ArrayList<>();
		if (zoomLevel == null || zoomLevel == maxZoomLevel) {
			result.add(Integer.parseInt(info.get("Width").toString()));
			result.add(Integer.parseInt(info.get("Height").toString()));
			return result;
		} else {
			double factor = Math.pow(2, zoomLevel - maxZoomLevel);
			result.add((int) (Integer.parseInt(info.get("Width").toString()) * factor));
			result.add((int) (Integer.parseInt(info.get("Height").toString()) * factor));
			return result;
		}
	}

	public static List<Integer> getPixelDimensions(String slideRef,
												   Integer zoomLevel) {
		return getPixelDimensions(slideRef, zoomLevel, null);
	}

	public static List<Integer> getPixelDimensions(String slideRef) {
		return getPixelDimensions(slideRef, null, null);
	}

	public static List<Integer> getPixelDimensions(String slideRef,
												   String sessionID) {
		return getPixelDimensions(slideRef, null, sessionID);
	}

	/**
	 * This method is used to determine the number of tiles needed to reconstitute a
	 * slide at a given zoom level
	 *
	 * @param slideRef slide's path
	 * @param zoomLevel  zoom level
	 * @param sessionID session's ID
	 * @return Three items list to determine the number of tiles needed to
	 *         reconstitute a slide at a given zoom level
	 */
	public static List<Integer> getNumberOfTiles(String slideRef,
												 Integer zoomLevel,
												 String sessionID) {
		zoomLevel = zoomLevel == null ? 0 : zoomLevel;
		// Determine the number of tiles needed to reconstitute a slide at a given
		// zoomlevel
		List<Integer> pixels = getPixelDimensions(slideRef, zoomLevel, sessionID);
		List<Integer> sz = getTileSize(sessionID);
		int xTiles = (int) Math.ceil((double) pixels.get(0) / (double) sz.get(0));
		int yTiles = (int) Math.ceil((double) pixels.get(1) / (double) sz.get(0));
		int nTiles = xTiles * yTiles;
		List<Integer> result = new ArrayList<>();
		result.add(xTiles);
		result.add(yTiles);
		result.add(nTiles);
		return result;
	}

	public static List<Integer> getNumberOfTiles(String slideRef,
												 Integer zoomLevel) {
		return getNumberOfTiles(slideRef, zoomLevel, null);
	}

	public static List<Integer> getNumberOfTiles(String slideRef) {
		return getNumberOfTiles(slideRef, null, null);
	}

	/**
	 * This method is used to Determine the physical dimensions of the sample
	 * represented by the slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Two items list to determine the physical dimensions of the sample
	 *         represented by the slide
	 */
	public static List<Float> getPhysicalDimensions(String slideRef, String sessionID) {
		// Determine the physical dimensions of the sample represented by the slide.
		// This is independent of the zoom level: the physical properties don't change
		// because the magnification changes
		List<Float> ppmData = getPixelsPerMicrometer(slideRef, sessionID);
		List<Integer> pixelSz = getPixelDimensions(slideRef, sessionID);
		List<Float> result = new ArrayList<>();
		result.add(pixelSz.get(0) * ppmData.get(0));
		result.add(pixelSz.get(1) * ppmData.get(1));
		return result;
	}

	public static List<Float> getPhysicalDimensions(String slideRef) {
		return getPhysicalDimensions(slideRef, null);
	}

	/**
	 * This method is used to get the number of channels for a slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Number of channels for a slide (1 when slide is brightfield)
	 */
	@SuppressWarnings("unchecked")
	public static int getNumberOfChannels(String slideRef, String sessionID) {
		// Number of fluorescent channels for a slide (when slide is bright field, return
		// is always 1)
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		return ((List<Object>) ((List<Map<String, Object>>) ((List<Map<String, Object>>) info.get("TimeFrames")).get(0)
				.get("Layers")).get(0).get("Channels")).size();
	}

	public static int getNumberOfChannels(String slideRef) {
		return getNumberOfChannels(slideRef, null);
	}

	/**
	 * This method is used to get the number of (z-stacked) layers for a slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Number of layers for a slide
	 */
	@SuppressWarnings("unchecked")
	public static int getNumberOfLayers(String slideRef, String sessionID) {
		// Number of (z-stacked) layers for a slide
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		return ((List<Object>) ((List<Map<String, Object>>) info.get("TimeFrames")).get(0).get("Layers")).size();
	}

	public static int getNumberOfLayers(String slideRef) {
		return getNumberOfLayers(slideRef, null);
	}

	/**
	 * This method is used to get the number of (z-stacked) layers for a slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Number of Z-Stack layers for a slide
	 */
	public static int getNumberOfZStackLayers(String slideRef, String sessionID) {
		return getNumberOfLayers(slideRef, sessionID);
	}

	public static int getNumberOfZStackLayers(String slideRef) {
		return getNumberOfLayers(slideRef, null);
	}

	/**
	 * This method is used to determine whether a slide is a fluorescent image or
	 * not
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return True if slide is a fluorescent image, false otherwise
	 */
	public static Boolean isFluorescent(String slideRef, String sessionID) {
		// Determine whether a slide is a fluorescent image or not
		return getNumberOfChannels(slideRef, sessionID) > 1;
	}

	public static Boolean isFluorescent(String slideRef) {
		return isFluorescent(slideRef, null);
	}

	/**
	 * This method is used to determine whether a slide contains multiple (stacked)
	 * layers or not
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return True if slide contains multiple (stacked) layers, false otherwise
	 */
	public static Boolean isMultiLayer(String slideRef, String sessionID) {
		// setting the default value when arguments' value is omitted
		// Determine whether a slide contains multiple (stacked) layers or not
		return getNumberOfLayers(slideRef, sessionID) > 1;
	}

	public static Boolean isMultiLayer(String slideRef) {
		return isMultiLayer(slideRef, null);
	}

	/**
	 * This method is used to convert the slide last modified time stamp into a
	 * human readable format
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Slide's last modification date
	 */
	public static String getLastModifiedDate(String slideRef, String sessionID) {
		// setting the default value when arguments' value is omitted
		String modificationDate = null;
		modificationDate = String.valueOf(Core.getSlideInfo(slideRef, sessionID).get("LastModified"));
		modificationDate = modificationDate.substring(6, modificationDate.length() - 2);
		// Convert the time stamp to a date
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		modificationDate = simpleDateFormat.format(new Date(new Timestamp(Long.parseLong(modificationDate)).getTime()));
		return modificationDate;
	}

	public static String getLastModifiedDate(String slideRef) {
		return getLastModifiedDate(slideRef, null);
	}

	/**
	 * This method is used to determine whether a slide is a z-stack or not
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return True if slide is a z-stack, false otherwise
	 */
	public static Boolean isZStack(String slideRef, String sessionID ) {
		// Determine whether a slide is a z-stack or not
		return isMultiLayer(slideRef, sessionID);
	}

	public static Boolean isZStack(String slideRef) {
		return isZStack(slideRef, null);
	}

	/**
	 * This method is used to get the magnification represented at a certain zoom
	 * level
	 *
	 * @param slideRef slide's path
	 * @param zoomLevel zoom level
	 * @param exact defines if exact or not
	 * @param sessionID session's ID
	 * @return Magnification represented at a certain zoom level
	 */
	public static int getMagnification(String slideRef,
									   Integer zoomLevel,
									   Boolean exact,
									   String sessionID) {
		zoomLevel = zoomLevel == null ? 0 : zoomLevel;
		exact = exact == null ? false : exact;

		// Get the magnification represented at a certain zoom level
		float ppm = getPixelsPerMicrometer(slideRef, zoomLevel, sessionID).get(0);
		if (ppm > 0) {
			if (exact == true) {
				return (int) (40 / (ppm / 0.25));
			} else {
				return (int) (40 / ((int) (ppm / 0.25)));
			}

		} else {
			return 0;
		}
	}

	public static int getMagnification(String slideRef,
									   Integer zoomLevel,
									   Boolean exact) {
		return getMagnification(slideRef, zoomLevel, exact, null);
	}

	public static int getMagnification(String slideRef,
									   Integer zoomLevel) {
		return getMagnification(slideRef, zoomLevel, false, null);
	}

	public static int getMagnification(String slideRef) {
		return getMagnification(slideRef, null, false, null);
	}

	/**
	 * This method is used to return the list of image types associated with a slide
	 * (thumbnail, barcode...)
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return List of associated image types
	 */
	@SuppressWarnings("unchecked")
	public static List<String> getAssociatedImageTypes(String slideRef, String sessionID) {
		// Determine the maximum zoomlevel that still represents an optical
		// magnification
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		if (info == null) {
			return null;
		} else if (info.containsKey("AssociatedImageTypes")) {
			List<String> result = new ArrayList<>();
			List<String> associatedImageTypes = ((List<String>) info.get("AssociatedImageTypes"));
			for (String associatedImage : associatedImageTypes) {
				result.add(associatedImage);
			}
			return result;
		} else {
			return null;
		}
	}

	public static List<String> getAssociatedImageTypes(String slideRef) {
		return getAssociatedImageTypes(slideRef, null);
	}

	/**
	 * This method is used to get the URL that points to the barcode (alias for
	 * "label") for a slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return URL that points to the barcode (alias for "label") for a slide
	 */
	public static String getBarcodeUrl(String slideRef, String sessionID) {
		// Get the URL that points to the barcode (alias for "label") for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url;
		try {
			url = pmaUrl(sessionID) + "barcode" + "?SessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
					+ PMA.pmaQ(slideRef);
			return url;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static String getBarcodeUrl(String slideRef) {
		return getBarcodeUrl(slideRef, null);
	}

	/**
	 * This method is used to get the barcode (alias for "label") image for a slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Barcode (alias for "label") image for a slide
	 */
	public static Image getBarcodeImage(String slideRef, String sessionID) {
		// Get the barcode (alias for "label") image for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			URL urlResource = new URL(getBarcodeUrl(slideRef, sessionID));
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static Image getBarcodeImage(String slideRef) {
		return getBarcodeImage(slideRef, null);
	}

	/**
	 * This method is used to get the text encoded by the barcode
	 *
	 * @param slideRef slide's path or UID
	 * @param sessionID session's ID
	 * @return The barcode text
	 */
	public static String getBarcodeText(String slideRef, String sessionID) {
		// Get the text encoded by the barcode (if there IS a barcode on the slide to
		// begin with)
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String barcode;
		String url = apiUrl(sessionID, false) + "GetBarcodeText?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_barcode_text on " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("get_barcode_text on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else {
					return jsonResponse.getString("d").equals("null") ? null : jsonResponse.getString("d");
				}
			} else {
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonString.length());
				barcode = jsonString.replace("\"", "");
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return barcode;
	}

	public static String getBarcodeText(String slideRef) {
		return getBarcodeText(slideRef, null);
	}

	/**
	 * This method is used to get the URL that points to the label for a slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Url that points to the label for a slide
	 */
	public static String getLabelUrl(String slideRef, String sessionID) {
		// Get the URL that points to the label for a slide
		return getBarcodeUrl(slideRef, sessionID);
	}

	public static String getLabelUrl(String slideRef) {
		return getLabelUrl(slideRef, null);
	}

	/**
	 * This method is used to get the label image for a slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Image label for a slide
	 */
	public static Image getLabelImage(String slideRef, String sessionID) {
		// Get the label image for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			URL urlResource = new URL(getLabelUrl(slideRef, sessionID));
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static Image getLabelImage(String slideRef) {
		return getLabelImage(slideRef, null);
	}

	/**
	 * This method is used to get the URL that points to the thumbnail for a slide
	 *
	 * @param slideRef slide's path or UID
	 * @param sessionID session's ID
	 * @param height height of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 * @param  width width of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 * @return URL that points to the thumbnail for a slide
	 */
	public static String getThumbnailUrl(String slideRef,
										 String sessionID,
										 Integer height,
										 Integer width) {
		height = height == null ? 0 : height;
		width = width == null ? 0 : width;
		// Get the URL that points to the thumbnail for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url;
		try {
			url = pmaUrl(sessionID) + "thumbnail" + "?SessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
					+ PMA.pmaQ(slideRef) + ((height > 0) ? "&h=" + height.toString() : "")
					+ ((width > 0) ? "&w=" + width.toString() : "");
			return url;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static String getThumbnailUrl(String slideRef,
										 String sessionID,
										 Integer height) {
		return getThumbnailUrl(slideRef, sessionID, height, null);
	}

	public static String getThumbnailUrl(String slideRef,
										 String sessionID) {
		return getThumbnailUrl(slideRef, sessionID, null, null);
	}

	public static String getThumbnailUrl(String slideRef) {
		return getThumbnailUrl(slideRef, null, null, null);
	}

	/**
	 * This method is used to get the thumbnail image for a slide
	 *
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @param height height of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 *@param width width of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 * @return Image thumbnail for a slide
	 */
	public static Image getThumbnailImage(String slideRef,
										  String sessionID,
										  Integer height,
										  Integer width) {
		height = height == null ? 0 : height;
		width = width == null ? 0 : width;
		// Get the thumbnail image for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			String url = getThumbnailUrl(slideRef, sessionID, height, width);
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static Image getThumbnailImage(String slideRef,
										  String sessionID,
										  Integer height) {
		return getThumbnailImage(slideRef, sessionID, height, null);
	}

	public static Image getThumbnailImage(String slideRef,
										  String sessionID) {
		return getThumbnailImage(slideRef, sessionID, null, null);
	}

	public static Image getThumbnailImage(String slideRef) {
		return getThumbnailImage(slideRef, null, null, null);
	}

	/**
	 * This method is used to create the url to retrieve a single tile at position
	 * (x, y)
	 *
	 * @param slideRef slide's path or UID
	 * @param x  x position
	 * @param y  y position
	 * @param  zoomLevel zoom level
	 * @param  zStack Number of z stacks
	 * @param sessionID session's ID
	 * @param format value(jpg) image format
	 * @param quality value(100), quality
	 * @return Url to retrieve a single tile at position (x, y)
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belong to
	 */
	public static String getTileUrl(String slideRef,
									Integer x,
									Integer y,
									Integer zoomLevel,
									Integer zStack,
									String sessionID,
									String format,
									Integer quality) throws Exception {
		x = x == null ? 0 : x;
		y = y == null ? 0 : y;
		zStack = zStack == null ? 0 : zStack;
		format = format == null ? "jpg" : format;
		quality = quality == null ? 100 : quality;
		// Get a single tile at position (x, y)
		// Format can be 'jpg' or 'png'
		// Quality is an integer value and varies from 0
		// (as much compression as possible; not recommended) to 100 (100%, no
		// compression)
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (zoomLevel == null) {
			zoomLevel = 0;
		}
		String url;
		url = pmaUrl(sessionID);
		if (url == null) {
			if (PMA.logger != null) {
				PMA.logger.severe("Unable to determine the PMA.core instance belonging to " + sessionID);
			}
			throw new Exception("Unable to determine the PMA.core instance belonging to " + sessionID);
		}
		try {
			url += "tile" + "?SessionID=" + PMA.pmaQ(sessionID) + "&channels=" + PMA.pmaQ("0") + "&layer="
					+ zStack.toString() + "&timeframe=" + PMA.pmaQ("0") + "&layer=" + PMA.pmaQ("0") + "&pathOrUid="
					+ PMA.pmaQ(slideRef) + "&x=" + x.toString() + "&y=" + y.toString() + "&z=" + zoomLevel.toString()
					+ "&format=" + PMA.pmaQ(format) + "&quality=" + PMA.pmaQ(quality.toString()) + "&cache="
					+ pmaUseCacheWhenRetrievingTiles.toString().toLowerCase();
			return url;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static String getTileUrl(String slideRef,
									Integer x,
									Integer y,
									Integer zoomLevel,
									Integer zStack,
									String sessionID,
									String format) throws Exception {
		return getTileUrl(slideRef, x, y, zoomLevel, zStack, sessionID,
				format, 100);
	}

	public static String getTileUrl(String slideRef,
									Integer x,
									Integer y,
									Integer zoomLevel,
									Integer zStack,
									String sessionID) throws Exception {
		return getTileUrl(slideRef, x, y, zoomLevel, zStack, sessionID,
				"jpg", 100);
	}

	public static String getTileUrl(String slideRef,
									Integer x,
									Integer y,
									Integer zoomLevel,
									Integer zStack) throws Exception {
		return getTileUrl(slideRef, x, y, zoomLevel, zStack, null,
				"jpg", 100);
	}

	public static String getTileUrl(String slideRef,
									Integer x,
									Integer y,
									Integer zoomLevel) throws Exception {
		return getTileUrl(slideRef, x, y, zoomLevel, 0, null,
				"jpg", 100);
	}

	public static String getTileUrl(String slideRef,
									Integer x,
									Integer y) throws Exception {
		return getTileUrl(slideRef, x, y, null, 0, null,
				"jpg", 100);
	}

	public static String getTileUrl(String slideRef,
									Integer x) throws Exception {
		return getTileUrl(slideRef, x, 0, null, 0, null,
				"jpg", 100);
	}

	public static String getTileUrl(String slideRef) throws Exception {
		return getTileUrl(slideRef, 0, 0, null, 0, null,
				"jpg", 100);
	}

	/**
	 * This method is used to get a single tile at position (x, y)
	 *
	 * @param slideRef slide's path or UID
	 * @param x   x position
	 * @param y   y position
	 * @param  zoomLevel zoom level
	 * @param zStack Number of z stacks
	 * @param sessionID  sessions ID
	 * @param format  value(jpg), image format
	 * @param quality  value(100)
	 * @return Single tile at position (x, y)
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belong to
	 */
	public static Image getTile(String slideRef,
								Integer x,
								Integer y,
								Integer zoomLevel,
								Integer zStack,
								String sessionID,
								String format,
								Integer quality) throws Exception {
		x = x == null ? 0 : x;
		y = y == null ? 0 : y;
		zStack = zStack == null ? 0 : zStack;
		format = format == null ? "jpg" : format;
		quality = quality == null ? 100 : quality;
		// Get a single tile at position (x, y)
		// Format can be 'jpg' or 'png'
		// Quality is an integer value and varies from 0
		// (as much compression as possible; not recommended) to 100 (100%, no
		// compression)
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (zoomLevel == null) {
			zoomLevel = 0;
		}
		try {
			String url = getTileUrl(slideRef, x, y, zoomLevel, zStack, sessionID, format, quality);
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static Image getTile(String slideRef,
								Integer x,
								Integer y,
								Integer zoomLevel,
								Integer zStack,
								String sessionID,
								String format) throws Exception {
		return getTile(slideRef, x, y, zoomLevel, zStack, sessionID, format,
				100);
	}

	public static Image getTile(String slideRef,
								Integer x,
								Integer y,
								Integer zoomLevel,
								Integer zStack,
								String sessionID) throws Exception {
		return getTile(slideRef, x, y, zoomLevel, zStack, sessionID, "jpg",
				100);
	}

	public static Image getTile(String slideRef,
								Integer x,
								Integer y,
								Integer zoomLevel,
								Integer zStack) throws Exception {
		return getTile(slideRef, x, y, zoomLevel, zStack, null, "jpg",
				100);
	}

	public static Image getTile(String slideRef,
								Integer x,
								Integer y,
								Integer zoomLevel) throws Exception {
		return getTile(slideRef, x, y, zoomLevel, null, null, "jpg",
				100);
	}

	public static Image getTile(String slideRef,
								Integer x,
								Integer y) throws Exception {
		return getTile(slideRef, x, y, null, null, null, "jpg",
				100);
	}

	public static Image getTile(String slideRef,
								Integer x) throws Exception {
		return getTile(slideRef, x, null, null, null, null, "jpg",
				100);
	}

	public static Image getTile(String slideRef) throws Exception {
		return getTile(slideRef, null, null, null, null, null, "jpg",
				100);
	}

	/**
	 * Gets a region of the slide at the specified scale Format can be 'jpg' or
	 * 'png' Quality is an integer value and varies from 0 (as much compression as
	 * possible; not recommended) to 100 (100%, no compression) x,y,width,height is
	 * the region to get rotation is the rotation in degrees of the slide to get
	 *
	 * @param slideRef slide's path or UID
	 * @param x : First optional argument(Integer), default value(0),
	 *                 starting x position
	 *                 </p>
	 *                 <p>
	 *                 y : Second optional argument(Integer), default value(0),
	 *                 starting y position
	 *                 </p>
	 *                 <p>
	 *                 width : Third optional argument(Integer), default value(0),
	 *                 ending width position
	 *                 </p>
	 *                 <p>
	 *                 height : Fourth optional argument(Integer), default value(0),
	 *                 height
	 *                 </p>
	 *                 <p>
	 *                 scale : Fifth optional argument(Integer), default value(1),
	 *                 scale
	 *                 </p>
	 *                 <p>
	 *                 zStack : Sixth optional argument(Integer), default value(0),
	 *                 Number of z stacks
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Seventh optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 format : Eighth optional argument(String), default
	 *                 value(jpg), image format
	 *                 </p>
	 *                 <p>
	 *                 quality : Ninth optional argument(Integer), default
	 *                 value(100), quality
	 *                 </p>
	 *                 <p>
	 *                 rotation : Tenth optional argument(Integer), default
	 *                 value(0), rotation
	 *                 </p>
	 *                 <p>
	 *                 contrast : Eleventh optional argument(Integer), default
	 *                 value(null), contrast
	 *                 </p>
	 *                 <p>
	 *                 brightness : Twelfth optional argument(Integer), default
	 *                 value(null), brightness
	 *                 </p>
	 *                 <p>
	 *                 dpi : Thirteenth optional argument(Integer), default
	 *                 value(300), dpi
	 *                 </p>
	 *                 <p>
	 *                 flipVertical : Fourteenth optional argument(Boolean), default
	 *                 value(false), flip vertical
	 *                 </p>
	 *                 <p>
	 *                 flipHorizontal : Fifteenth optional argument(Boolean),
	 *                 default value(false), flip horizontal
	 *                 </p>
	 *                 <p>
	 *                 annotationsLayerType : Sixteenth optional argument(String),
	 *                 default value(null), annotations layer type
	 *                 </p>
	 *                 <p>
	 *                 drawFilename : Seventeenth optional argument(Integer),
	 *                 default value(0), draw filename
	 *                 </p>
	 *                 <p>
	 *                 downloadInsteadOfDisplay : Eighteenth optional
	 *                 argument(Boolean), default value(false), download instead of
	 *                 display
	 *                 </p>
	 *                 <p>
	 *                 drawScaleBar : Nineteenth optional argument(Boolean), default
	 *                 value(false), draw scale bar
	 *                 </p>
	 *                 <p>
	 *                 gamma : Twentieth optional argument(ArrayList), default
	 *                 value([]), gamma
	 *                 </p>
	 *                 <p>
	 *                 channelClipping : Twenty-first optional argument(ArrayList),
	 *                 default value([]), channel clipping
	 *                 </p>
	 * @return Gets a region of the slide at the specified scale
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType,
								  Integer drawFilename,
								  Boolean downloadInsteadOfDisplay,
								  Boolean drawScaleBar,
								  List<String> gammaList,
								  List<String> channelClippingList) {
		x = x == null ? 0 : x;
		y = y == null ? 0 : y;
		width = width == null ? 0 : width;
		height = height == null ? 0 : height;
		zStack = zStack == null ? 0 : zStack;
		sessionID = null;
		format = format == null ? "jpg" : format;
		quality = quality == null ? 100 : quality;
		rotation = rotation == null ? 0 : rotation;
		contrast = contrast == null ? 0 : contrast;
		brightness = brightness == null ? 0 : brightness;
		postGamma = postGamma == null ? 0 : postGamma;
		dpi = dpi == null ? 300 : dpi;
		flipVertical = flipVertical == null ? false : flipVertical;
		flipHorizontal = flipHorizontal == null ? false : flipHorizontal;
		//rawtypes
		annotationsLayerType = "";
		drawFilename = 0;
		downloadInsteadOfDisplay = downloadInsteadOfDisplay == null ? false :
				downloadInsteadOfDisplay;
		drawScaleBar = drawScaleBar == null ? false : drawScaleBar;
		gammaList = new ArrayList<String>();
		channelClippingList = new ArrayList<String>();
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			String url = getRegionUrl(slideRef, x, y, width, height, zStack,
					sessionID);
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType,
								  Integer drawFilename,
								  Boolean downloadInsteadOfDisplay,
								  Boolean drawScaleBar,
								  List<String> gammaList
								  ) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				drawFilename, downloadInsteadOfDisplay, drawScaleBar,
				gammaList, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType,
								  Integer drawFilename,
								  Boolean downloadInsteadOfDisplay,
								  Boolean drawScaleBar) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				drawFilename, downloadInsteadOfDisplay, drawScaleBar,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType,
								  Integer drawFilename,
								  Boolean downloadInsteadOfDisplay) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				drawFilename, downloadInsteadOfDisplay, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType,
								  Integer drawFilename) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				drawFilename, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				format,
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID) {
		return getRegion(slideRef, x, y, width, height, zStack, sessionID,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack) {
		return getRegion(slideRef, x, y, width, height, zStack, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height) {
		return getRegion(slideRef, x, y, width, height, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width) {
		return getRegion(slideRef, x, y, width, 0, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x,
								  Integer y) {
		return getRegion(slideRef, x, y, 0, 0, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef,
								  Integer x) {
		return getRegion(slideRef, x, 0, 0, 0, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static Image getRegion(String slideRef) {
		return getRegion(slideRef, 0, 0, 0, 0, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	/**
	 * This method is used to create the url to retrieve a region of the slide at
	 * the specified scale (x,y,width,height)
	 *
	 * @param slideRef slide's path or UID
	 * @param x : First optional argument(Integer), default value(0),
	 *                 starting x position
	 *                 </p>
	 *                 <p>
	 *                 y : Second optional argument(Integer), default value(0),
	 *                 starting y position
	 *                 </p>
	 *                 <p>
	 *                 width : Third optional argument(Integer), default value(0),
	 *                 ending width position
	 *                 </p>
	 *                 <p>
	 *                 height : Fourth optional argument(Integer), default value(0),
	 *                 height
	 *                 </p>
	 *                 <p>
	 *                 scale : Fifth optional argument(Integer), default value(1),
	 *                 scale
	 *                 </p>
	 *                 <p>
	 *                 zStack : Sixth optional argument(Integer), default value(0),
	 *                 Number of z stacks
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Seventh optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 format : Eighth optional argument(String), default
	 *                 value(jpg), image format
	 *                 </p>
	 *                 <p>
	 *                 quality : Ninth optional argument(Integer), default
	 *                 value(100), quality
	 *                 </p>
	 *                 <p>
	 *                 rotation : Tenth optional argument(Integer), default
	 *                 value(0), rotation
	 *                 </p>
	 *                 <p>
	 *                 contrast : Eleventh optional argument(Integer), default
	 *                 value(null), contrast
	 *                 </p>
	 *                 <p>
	 *                 brightness : Twelfth optional argument(Integer), default
	 *                 value(null), brightness
	 *                 </p>
	 *                 <p>
	 *                 dpi : Thirteenth optional argument(Integer), default
	 *                 value(300), dpi
	 *                 </p>
	 *                 <p>
	 *                 flipVertical : Fourteenth optional argument(Boolean), default
	 *                 value(false), flip vertical
	 *                 </p>
	 *                 <p>
	 *                 flipHorizontal : Fifteenth optional argument(Boolean),
	 *                 default value(false), flip horizontal
	 *                 </p>
	 *                 <p>
	 *                 annotationsLayerType : Sixteenth optional argument(String),
	 *                 default value(null), annotations layer type
	 *                 </p>
	 *                 <p>
	 *                 drawFilename : Seventeenth optional argument(Integer),
	 *                 default value(0), draw filename
	 *                 </p>
	 *                 <p>
	 *                 downloadInsteadOfDisplay : Eighteenth optional
	 *                 argument(Boolean), default value(false), download instead of
	 *                 display
	 *                 </p>
	 *                 <p>
	 *                 drawScaleBar : Nineteenth optional argument(Boolean), default
	 *                 value(false), draw scale bar
	 *                 </p>
	 *                 <p>
	 *                 gamma : Twentieth optional argument(ArrayList), default
	 *                 value([]), gamma
	 *                 </p>
	 *                 <p>
	 *                 channelClipping : Twenty-first optional argument(ArrayList),
	 *                 default value([]), channel clipping
	 *                 </p>
	 * @return Url to retrieve a region at position (x, y, width, height)
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belong to
	 */
	@SuppressWarnings({ "unchecked" })
	public static String getRegionUrl(String slideRef,
									  Integer x,
									  Integer y,
									  Integer width,
									  Integer height,
									  Integer zStack,
									  String sessionID,
									  String format,
									  Integer quality,
									  Integer rotation,
									  Integer contrast,
									  Integer brightness,
									  Integer postGamma,
									  Integer dpi,
									  Boolean flipVertical,
									  Boolean flipHorizontal,
									  String annotationsLayerType,
									  Integer drawFilename,
									  Boolean downloadInsteadOfDisplay,
									  Boolean drawScaleBar,
									  List<String> gammaList,
									  List<String> channelClippingList
									  ) throws Exception {

		x = x == null ? 0 : x;
		y = y == null ? 0 : y;
		width = width == null ? 0 : width;
		height = height == null ? 0 : height;
		Integer scale = 1;
		zStack = zStack == null ? 0 : zStack;
		format = format == null ? "jpg" : format;
		quality = quality == null ? 100: quality;
		rotation = rotation == null ? 0 : rotation;
		contrast = contrast == null ? 0 : contrast;
		brightness = brightness == null ? 0 : brightness;
		postGamma = postGamma == null ? 0 : postGamma;
		dpi = dpi == null ? 300 : dpi;
		flipVertical = flipVertical == null ? false : flipVertical;
		flipHorizontal = flipHorizontal == null ? false : flipHorizontal;
		annotationsLayerType = null;
		drawFilename = drawFilename == null ? 0 : drawFilename;
		downloadInsteadOfDisplay = downloadInsteadOfDisplay == null ? false :
				downloadInsteadOfDisplay;
		drawScaleBar = drawScaleBar == null ? false : drawScaleBar;
		String gamma = (gammaList == null) || (gammaList.isEmpty()) ? null :
				String.join(",", gammaList);
		String channelClipping =
				(channelClippingList == null) || (channelClippingList.isEmpty()) ? null :
						String.join(
				",",
				channelClippingList);
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}

		String url;
		url = pmaUrl(sessionID);
		if (url == null) {
			if (PMA.logger != null) {
				PMA.logger.severe("Unable to determine the PMA.core instance belonging to " + sessionID);
			}
			throw new Exception("Unable to determine the PMA.core instance belonging to " + sessionID);
		}
		try {
			url += "region" + "?SessionID=" + PMA.pmaQ(sessionID) + "&channels=" + PMA.pmaQ("0") + "&layer="
					+ zStack.toString() + "&timeframe=" + PMA.pmaQ("0") + "&layer=" + PMA.pmaQ("0") + "&pathOrUid="
					+ PMA.pmaQ(slideRef) + "&x=" + x.toString() + "&y=" + y.toString() + "&width=" + width.toString()
					+ "&height=" + height.toString() + "&scale=" + scale.toString() + "&format=" + PMA.pmaQ(format)
					+ "&quality=" + PMA.pmaQ(quality.toString()) + "&rotation=" + rotation.toString() + "&contrast="
					+ contrast.toString() + "&brightness=" + brightness.toString() + "&postGamma="
					+ postGamma.toString() + "&dpi=" + dpi.toString() + "&flipVertical=" + flipVertical.toString()
					+ "&flipHorizontal=" + flipHorizontal.toString() + "&annotationsLayerType="
					+ PMA.pmaQ(annotationsLayerType) + "&drawFilename=" + drawFilename.toString()
					+ "&downloadInsteadOfDisplay=" + downloadInsteadOfDisplay.toString() + "&drawScaleBar="
					+ drawScaleBar.toString() + "&gamma=" + PMA.pmaQ(gamma) + "&channelClipping="
					+ PMA.pmaQ(channelClipping) + "&cache=" + pmaUseCacheWhenRetrievingTiles.toString().toLowerCase();
			return url;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType,
								  Integer drawFilename,
								  Boolean downloadInsteadOfDisplay,
								  Boolean drawScaleBar,
								  List<String> gammaList
	) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				drawFilename, downloadInsteadOfDisplay, drawScaleBar,
				gammaList, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType,
								  Integer drawFilename,
								  Boolean downloadInsteadOfDisplay,
								  Boolean drawScaleBar) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				drawFilename, downloadInsteadOfDisplay, drawScaleBar,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType,
								  Integer drawFilename,
								  Boolean downloadInsteadOfDisplay) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				drawFilename, downloadInsteadOfDisplay, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType,
								  Integer drawFilename) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				drawFilename, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal,
								  String annotationsLayerType) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, annotationsLayerType,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical,
								  Boolean flipHorizontal) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, flipHorizontal, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi,
								  Boolean flipVertical) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				flipVertical, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma,
								  Integer dpi) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, dpi,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness,
								  Integer postGamma) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, postGamma, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast,
								  Integer brightness) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, brightness, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation,
								  Integer contrast) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, contrast, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality,
								  Integer rotation) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, rotation, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				quality, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID,
								  String format) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				format,
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack,
								  String sessionID) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, sessionID,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height,
								  Integer zStack) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, zStack, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width,
								  Integer height) throws Exception {
		return getRegionUrl(slideRef, x, y, width, height, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y,
								  Integer width) throws Exception {
		return getRegionUrl(slideRef, x, y, width, 0, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x,
								  Integer y) throws Exception {
		return getRegionUrl(slideRef, x, y, 0, 0, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef,
								  Integer x) throws Exception {
		return getRegionUrl(slideRef, x, 0, 0, 0, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}

	public static String getRegionUrl(String slideRef) throws Exception {
		return getRegionUrl(slideRef, 0, 0, 0, 0, 0, null,
				"jpg",
				100, 0, null, null, null, 300,
				false, false, null,
				0, false, false,
				null, null);
	}


	/**
	 * This method is used to get all tiles with a (fromX, fromY, toX, toY)
	 * rectangle
	 *
	 * @param slideRef slide's path or UID
	 * @param  fromX  First optional argument(Integer), default value(0),
	 *                 starting x position
	 *                 </p>
	 *                 <p>
	 *                 fromY : Second optional argument(Integer), default value(0),
	 *                 starting y position
	 *                 </p>
	 *                 <p>
	 *                 toX : Third optional argument(Integer), default value(0),
	 *                 ending x position
	 *                 </p>
	 *                 <p>
	 *                 toY : Fourth optional argument(Integer), default value(0),
	 *                 ending y position
	 *                 </p>
	 *                 <p>
	 *                 zoomLevel : Fifth optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 zStack : Sixth optional argument(Integer), default value(0),
	 *                 Number of z stacks
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Seventh optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 format : Eigth optional argument(String), default value(jpg),
	 *                 image format
	 *                 </p>
	 *                 <p>
	 *                 quality : Ninth optional argument(Integer), default
	 *                 value(100), quality
	 *                 </p>
	 * @return All tiles with a (fromX, fromY, toX, toY) rectangle
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Stream getTiles(String slideRef,
								  Integer fromX,
								  Integer fromY,
								  Integer toX,
								  Integer toY,
								  Integer zoomLevel,
								  Integer zStack,
								  String sessionID,
								  String format,
								  Integer quality) {
		fromX = fromX == null ? 0 : fromX;
		fromY = fromY == null ? 0 : fromY;
		zoomLevel = zoomLevel == null ? 0 : zoomLevel;
		zStack = zStack == null ? 0 : zStack;
		sessionID = sessionId(sessionID);
		format = format == null ? "jpg" : format;
		quality = quality == null ? 100 : quality;

		// Get all tiles with a (fromX, fromY, toX, toY) rectangle. Navigate left to
		// right, top to bottom
		// Format can be 'jpg' or 'png'
		// Quality is an integer value and varies from 0
		// (as much compression as possible; not recommended) to 100 (100%, no
		// compression)
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (zoomLevel == null) {
			zoomLevel = 0;
		}
		if (toX == null) {
			toX = getNumberOfTiles(slideRef, zoomLevel, sessionID).get((0));
		}
		if (toY == null) {
			toY = getNumberOfTiles(slideRef, zoomLevel, sessionID).get((1));
		}
		// we declare final variable to use them in the enclosing scope for new
		// Supplier()
		final int varFromX = fromX;
		final int varToX = toX;
		final int varFromY = fromY;
		final int varToY = toY;
		final int varZoomLevel = zoomLevel;
		final int varZStack = zStack;
		final String varSessionID = sessionID;
		final String varSlideRef = slideRef;
		final String varFormat = format;
		final Integer varQualty = quality;
		// we use Stream to simulate the behavior of "yield" in Python
		return Stream.generate(new Supplier() {
			int x = varFromX;
			int y = varFromY - 1;

			@Override
			public Image get() {
				if (x <= varToX) {
					if (y < varToY) {
						y++;
					} else {
						y = varFromY - 1;
						x++;
					}
				}
				try {
					return getTile(varSlideRef, x, y, varZoomLevel, varZStack, varSessionID, varFormat, varQualty);
				} catch (Exception e) {
					e.printStackTrace();
					if (PMA.logger != null) {
						StringWriter sw = new StringWriter();
						e.printStackTrace(new PrintWriter(sw));
						PMA.logger.severe(sw.toString());
					}
					return null;
				}
			}
		}).limit((varToX - varFromX + 1) * (varToY - varFromY + 1));
	}

	public static Stream getTiles(String slideRef,
								  Integer fromX,
								  Integer fromY,
								  Integer toX,
								  Integer toY,
								  Integer zoomLevel,
								  Integer zStack,
								  String sessionID,
								  String format) {
		return getTiles(slideRef, fromX, fromY, toX,
				toY, zoomLevel, zStack, sessionID, format, 100);
	}

	public static Stream getTiles(String slideRef,
								  Integer fromX,
								  Integer fromY,
								  Integer toX,
								  Integer toY,
								  Integer zoomLevel,
								  Integer zStack,
								  String sessionID) {
		return getTiles(slideRef, fromX, fromY, toX,
				toY, zoomLevel, zStack, sessionID, "jpg", 100);
	}

	public static Stream getTiles(String slideRef,
								  Integer fromX,
								  Integer fromY,
								  Integer toX,
								  Integer toY,
								  Integer zoomLevel,
								  Integer zStack) {
		return getTiles(slideRef, fromX, fromY, toX,
				toY, zoomLevel, zStack, null, "jpg", 100);
	}

	public static Stream getTiles(String slideRef,
								  Integer fromX,
								  Integer fromY,
								  Integer toX,
								  Integer toY,
								  Integer zoomLevel) {
		return getTiles(slideRef, fromX, fromY, toX,
				toY, zoomLevel, null, null, "jpg", 100);
	}

	public static Stream getTiles(String slideRef,
								  Integer fromX,
								  Integer fromY,
								  Integer toX,
								  Integer toY) {
		return getTiles(slideRef, fromX, fromY, toX,
				toY, null, null, null, "jpg", 100);
	}

	public static Stream getTiles(String slideRef,
								  Integer fromX,
								  Integer fromY,
								  Integer toX) {
		return getTiles(slideRef, fromX, fromY, toX,
				null, null, null, null, "jpg", 100);
	}

	public static Stream getTiles(String slideRef,
								  Integer fromX,
								  Integer fromY) {
		return getTiles(slideRef, fromX, fromY, null,
				null, null, null, null, "jpg", 100);
	}

	public static Stream getTiles(String slideRef,
								  Integer fromX) {
		return getTiles(slideRef, fromX, 0, null,
				null, null, null, null, "jpg", 100);
	}

	public static Stream getTiles(String slideRef) {
		return getTiles(slideRef, 0, 0, null,
				null, null, null, null, "jpg", 100);
	}

	/**
	 * This method is used to find out what forms where submitted for a specific
	 * slide
	 *
	 * @param slideRef slide's path or UID
	 * @param sessionID session's ID
	 * @return Map of forms submitted for a defined slide
	 */
	public static Map<String, String> getSubmittedForms(String slideRef, String sessionID) {
		// Find out what forms where submitted for a specific slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFormSubmissions?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUids="
				+ PMA.pmaQ(slideRef);
		Map<String, String> forms = new HashMap<>();
		Map<String, String> allForms = getAvailableForms(slideRef, sessionID);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("getSubmittedForms on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getSubmittedForms on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						forms = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					for (int i = 0; i < jsonResponse.length(); i++) {
						if (!forms.containsKey(jsonResponse.optJSONObject(i).get("FormID").toString())
								&& allForms != null) {
							forms.put(jsonResponse.optJSONObject(i).get("FormID").toString(),
									allForms.get(jsonResponse.optJSONObject(i).get("FormID").toString()));
						}
					}
					// should probably do some post-processing here, but unsure what that would
					// actually be??

				}
			} else {
				forms = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return forms;
	}

	public static Map<String, String> getSubmittedForms(String slideRef) {
		return getSubmittedForms(slideRef, null);
	}

	/**
	 * This method is used to get submitted forms data in json Array format
	 *
	 * @param slideRef slide's path or UID
	 * @param sessionID session's ID
	 * @return Submitted forms data in json Array format
	 */
	public static JSONArray getSubmittedFormData(String slideRef, String sessionID) {
		// Get all submitted form data associated with a specific slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		JSONArray data; // new HashMap<>();
		String url = apiUrl(sessionID, false) + "GetFormSubmissions?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUids="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("getSubmittedFormData on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getSubmittedFormData on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						data = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					data = jsonResponse;
				}
				// should probably do some post-processing here, but unsure what that would
				// actually be??
			} else {
				data = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return data;
	}

	public static JSONArray getSubmittedFormData(String slideRef) {
		return getSubmittedFormData(slideRef, null);
	}

	/**
	 * This method is used to prepare a form-dictionary that can be used later on to
	 * submit new form data for a slide
	 *
	 * @param formID  Form's ID
	 * @param sessionID session's ID
	 * @return Form-map that can be used later on to submit new form data for a
	 *         slide
	 */
	public static Map<String, String> prepareFormMap(String formID, String sessionID) {
		// Prepare a form-dictionary that can be used later on to submit new form data
		// for a slide
		if (formID == null) {
			return null;
		}
		sessionID = sessionId(sessionID);
		Map<String, String> formDef = new HashMap<>();
		String url = apiUrl(sessionID, false) + "GetFormDefinitions?sessionID=" + PMA.pmaQ(sessionID);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("" + jsonResponse.get("Message") + "");
						}
						throw new Exception("" + jsonResponse.get("Message") + "");
					} else {
						formDef = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					for (int i = 0; i < jsonResponse.length(); i++) {
						if ((jsonResponse.optJSONObject(i).get("FormID").toString().equals(formID))
								|| (jsonResponse.optJSONObject(i).get("FormName").toString().equals(formID))) {
							for (int j = 0; j < jsonResponse.optJSONObject(i).getJSONArray("FormFields")
									.length(); j++) {
								formDef.put(jsonResponse.optJSONObject(i).getJSONArray("FormFields").getJSONObject(j)
										.getString("Label"), null);
							}
						}

					}
				}
			} else {
				formDef = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return formDef;
	}

	public static Map<String, String> prepareFormMap(String formID) {
		return prepareFormMap(formID, null);
	}

	/**
	 * This method is used to get a Map of the forms available to fill out, either
	 * system-wide (leave slideref to "null"), or for a particular slide
	 * @param slideRef slide's path
	 * @param sessionID session's ID
	 * @return Map of the forms available to fill out, either system-wide (leave
	 *         slideref to "null"), or for a particular slide
	 */
	public static Map<String, String> getAvailableForms(String slideRef, String sessionID) {
		// See what forms are available to fill out, either system-wide (leave slideref
		// to None), or for a particular slide
		sessionID = sessionId(sessionID);
		String url;
		Map<String, String> forms = new HashMap<>();
		if (slideRef != null) {
			if (slideRef.startsWith("/")) {
				slideRef = slideRef.substring(1);
			}
			String dir = FilenameUtils.getFullPath(slideRef).substring(0,
					FilenameUtils.getFullPath(slideRef).length() - 1);
			url = apiUrl(sessionID, false) + "GetForms?sessionID=" + PMA.pmaQ(sessionID) + "&path=" + PMA.pmaQ(dir);
		} else {
			url = apiUrl(sessionID, false) + "GetForms?sessionID=" + PMA.pmaQ(sessionID);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("getAvailableForms on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getAvailableForms on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						forms = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					for (int i = 0; i < jsonResponse.length(); i++) {
						forms.put(jsonResponse.optJSONObject(i).get("Key").toString(),
								jsonResponse.optJSONObject(i).getString("Value"));
					}
				}
			} else {
				forms = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return forms;
	}

	public static Map<String, String> getAvailableForms(String slideRef) {
		return getAvailableForms(slideRef, null);
	}

	public static Map<String, String> getAvailableForms() {
		return getAvailableForms(null, null);
	}

	/**
	 * To be elaborated later
	 *
	 * @param slideRef To be elaborated later
	 * @param formID   To be elaborated later
	 * @param formMap  To be elaborated later
	 * @param sessionID  To be elaborated later
	 * @return To be elaborated later
	 */
	public static String submitFormData(String slideRef, String formID, String formMap, String sessionID) {
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		return null;
	}

	public static String submitFormData(String slideRef, String formID, String formMap) {
		return submitFormData(slideRef, formID, formMap, null);
	}

	/**
	 * This method is used to retrieve the annotations for slide slideRef
	 *
	 * @param slideRef slide's path
	 * @param  sessionID session's ID
	 * @return Annotations for a slide in a json Array format
	 */
	public static JSONArray getAnnotations(String slideRef, String sessionID) {
		// Retrieve the annotations for slide slideRef
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		// String dir = FilenameUtils.getFullPath(slideRef).substring(0,
		// FilenameUtils.getFullPath(slideRef).length() - 1);
		JSONArray data;
		String url = apiUrl(sessionID, false) + "GetAnnotations?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("getAnnotations() on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getAnnotations() on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						data = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					data = jsonResponse;
				}
			} else {
				data = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return data;
	}

	public static JSONArray getAnnotations(String slideRef) {
		return getAnnotations(slideRef, null);
	}

	/**
	 * This method is used to launch the default web browser and load a web-based
	 * viewer for the slide
	 *
	 * @param slideRef slide's path or UID
	 * @param sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belongs to
	 */
	public static void showSlide(String slideRef, String sessionID) throws Exception {
		// Launch the default web browser and load a web-based viewer for the slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String osCmd;
		if (System.getProperty("os.name").toLowerCase().equals("posix")) {
			osCmd = "open ";
		} else {
			osCmd = "start ";
		}
		String url;
		if (sessionID == pmaCoreLiteSessionID) {
			url = "http://free.pathomation.com/pma-view-lite/?path=" + PMA.pmaQ(slideRef);
		} else {
			url = pmaUrl(sessionID);
			if (url == null) {
				if (PMA.logger != null) {
					PMA.logger.severe("Unable to determine the PMA.core instance belonging to " + sessionID);
				}
				throw new Exception("Unable to determine the PMA.core instance belonging to " + sessionID);
			}
			url = "viewer/index.htm" + "?sessionID=" + PMA.pmaQ(sessionID) + "^&pathOrUid=" + PMA.pmaQ(slideRef); // note
																													// the
																													// ^&
			// to escape
			// a regular
			// &
			if (PMA.debug) {
				System.out.println(url);
			}
		}
		try {
			Runtime.getRuntime().exec(osCmd + url);
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
		}
	}

	public static void showSlide(String slideRef) throws Exception {
		showSlide(slideRef, null);
	}

	/**
	 * This method is used to map of files related to a slide
	 *
	 * @param slideRef slide's path or UID
	 * @param sessionID session's ID
	 * @return Map of all files related to a slide
	 */
	@SuppressWarnings("serial")
	public static Map<String, Map<String, String>> getFilesForSlide(String slideRef, String sessionID) {
		// Obtain all files actually associated with a specific slide
		// This is most relevant with slides that are defined by multiple files, like
		// MRXS or VSI
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url;
		if (sessionID.equals(pmaCoreLiteSessionID)) {
			url = apiUrl(sessionID, false) + "EnumerateAllFilesForSlide?sessionID=" + PMA.pmaQ(sessionID)
					+ "&pathOrUid=" + PMA.pmaQ(slideRef);
		} else {
			url = apiUrl(sessionID, false) + "GetFilenames?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
					+ PMA.pmaQ(slideRef);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			JSONArray resultsArray;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("getFilesForSlide on " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("getFilesForSlide on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					resultsArray = jsonResponse.getJSONArray("d");
				} else {
					return null;
				}
			} else {
				resultsArray = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + resultsArray.length());
			}
			Map<String, Map<String, String>> result = new HashMap<>();
			for (int i = 0; i < resultsArray.length(); i++) {
				final int finalI = i;
				if (sessionID == pmaCoreLiteSessionID) {
					result.put(resultsArray.getString(i), new HashMap<String, String>() {
						{
							put("Size", "0");
							put("LastModified", null);
						}
					});
				} else {
					result.put(resultsArray.getJSONObject(finalI).getString("Path"), new HashMap<String, String>() {
						{
							put("Size", String.valueOf(resultsArray.getJSONObject(finalI).getLong("Size")));
							put("LastModified", resultsArray.getJSONObject(finalI).getString("LastModified"));
						}
					});
				}
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static Map<String, Map<String, String>> getFilesForSlide(String slideRef) {
		return getFilesForSlide(slideRef, null);
	}

	/**
	 * This method is used to get list of files related to a slide for PMA.start
	 * ONLY
	 *
	 * @param slideRef slide's path or UID
	 * @param sessionID session's ID
	 * @return List of all files related to a selected slide for PMA.start ONLY
	 */
	public static List<String> enumerateFilesForSlide(String slideRef, String sessionID) {
		// Obtain all files actually associated with a specific slide
		// This is most relevant with slides that are defined by multiple files, like
		// MRXS or VSI
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url = apiUrl(sessionID, false) + "EnumerateAllFilesForSlide?sessionID=" + PMA.pmaQ(sessionID)
				+ "&pathOrUid=" + PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("enumerateFilesForSlide on " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("enumerateFilesForSlide on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					List<String> files = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						files.add(array.optString(i));
					}
					return files;
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				List<String> files = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					files.add(jsonResponse.optString(i));
				}
				return files;
			}

		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static List<String> enumerateFilesForSlide(String slideRef) {
		return enumerateFilesForSlide(slideRef, null);
	}

	/**
	 * This method is used to get list of files related to a slide for PMA.core ONLY
	 *
	 * @param slideRef slide's path or UID
	 * @param sessionID session's ID
	 * @return List of all files related to a selected slide for PMA.core ONLY
	 */
	@SuppressWarnings("serial")
	public static List<Map<String, String>> enumerateFilesForSlidePMACore(String slideRef, String sessionID) {
		// Obtain all files actually associated with a specific slide
		// This is most relevant with slides that are defined by multiple files, like
		// MRXS or VSI
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFilenames?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONArray(jsonString)) {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				List<Map<String, String>> result = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					final int finalI = i;
					result.add(new HashMap<String, String>() {
						{
							put("LastModified", jsonResponse.getJSONObject(finalI).getString("LastModified"));
							put("Path", jsonResponse.getJSONObject(finalI).getString("Path"));
							put("Size", String.valueOf(jsonResponse.getJSONObject(finalI).getLong("Size")));
						}
					});
				}
				return result;
			} else {
				if (PMA.logger != null) {
					PMA.logger.severe("enumerateFilesForSlidePMACore() : Failure to get related files");
				}
				return null;
			}

		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static List<Map<String, String>> enumerateFilesForSlidePMACore(String slideRef) {
		return enumerateFilesForSlidePMACore(slideRef, null);
	}

	/**
	 * This method is used to search for slides in a directory that satisfy a
	 * certain search pattern
	 *
	 * @param startDir Start directory
	 * @param pattern  Search pattern
	 * @param sessionID session's ID
	 * @return List of slides in a directory that satisfy a certain search pattern
	 * @throws Exception If called on PMA.start
	 */
	public static List<String> searchSlides(String startDir, String pattern, String sessionID) throws Exception {
		sessionID = sessionId(sessionID);
		if (sessionID.equals(pmaCoreLiteSessionID)) {
			if (isLite()) {
				throw new Exception("PMA.core.lite found running, but doesn't support searching.");
			} else {
				throw new Exception("PMA.core.lite not found, and besides; it doesn't support searching.");
			}
		}
		if (startDir.startsWith("/")) {
			startDir = startDir.substring(1);
		}
		String url = queryUrl(sessionID) + "Filename?sessionID=" + PMA.pmaQ(sessionID) + "&path=" + PMA.pmaQ(startDir)
				+ "&pattern=" + PMA.pmaQ(pattern);
		if (PMA.debug) {
			System.out.println("url = " + url);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			List<String> files = null;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("searchSlides on " + pattern + " in " + startDir + "resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("searchSlides on " + pattern + " in " + startDir + "resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					files = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						files.add(array.optString(i));
					}
				} else {
					files = null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				files = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					files.add(jsonResponse.optString(i));
				}
			}
			return files;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	public static List<String> searchSlides(String startDir, String pattern) throws Exception {
		return searchSlides(startDir, pattern, null);
	}
}
