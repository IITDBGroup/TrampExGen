package org.vagabond.commandline.explgen;

import java.io.File;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.vagabond.explanation.ranking.RankerFactory;
import org.vagabond.xmlmodel.ConnectionInfoType;
import org.vagabond.xmlmodel.MappingScenarioDocument.MappingScenario;

/**
 * @author lord_pretzel
 *
 */
public class ExplGenOptions {


	@Option(name = "-u", usage = "user name for connecting to the database")
	private String dbUser = "postgres";

	@Option(name = "-p", usage = "password for database user")
	private String dbPassword = "";

	@Option(name = "-P", usage = "port for database connection")
	private int port = 5432;
	
	@Option(name = "-d", usage = "name of the database to connect to")
	private String dbName = "tramptest";

	@Option(name = "-h", usage = "URL of the database to connect to")
	private String dbURL = "localhost";

	@Option(name = "-x", usage = "xml mapping scenario document")
	private File xmlDoc;

	@Option(name = "-m", usage = "File that stores markers")
	private File markerFile = null;

	@Option(name = "-M", usage = "List of error markers")
	private String markers = null;

	@Option(name = "-loadScen", usage = "Load the scenario to the database")
	private boolean loadScen = false;

	@Option(name="-c", usage="data files (CSV) are load from this directory")
	private File csvLoadPath = null;
		
	@Option(name = "-ranker", usage = "Select the type of ranker to use {SideEffect, Size}")
	private String rankerScheme = "Dummy";

	@Option(name = "-lazy", usage = "Use together with -loadScen. Check if " +
			"relations are already populated before loading data.")
	private boolean lazy = false;
	
	@Option(name = "-rankExpls", usage = "Rank the generated explanations")
	private boolean useRanker = false;
	
	@Option(name = "-noPart", usage = "Rank the generated explanations without partitioning")
	private boolean nousePart = false;
	
	@Option(name = "-rankSkyline", 
			usage = "Use Skyline ranker with this ranking schemes", 
			metaVar = "[scheme 1] [scheme 2] ...")
	private String[] skylineRankers = new String[0];

	@Option(name = "-nonInteractive", usage = "rank explanations and output them without user interaction")
	private boolean rankNonInteractive = false;
	
	@Option(name = "-maxRank", usage = "set maximum number of ranked CES to produce. The program terminates after generating this many explanations")
	private int maxRank = -1;
	
	@Option(name = "-noShowCES", usage = "show ranking results")
	private boolean noShowSets = false;
	
	@Option(name = "-goldStandard", usage = "loads expected explanations from this file and compared ranked explanatios to that")
	private File goldStandard = null;
	
	@Option(name = "-timeLimit", usage = "stops ranking after time limit (in sec) is reached")
	private int timeLimit = -1;
	
	@Option(name="-help", usage="show this help message")
	private boolean showHelp = false;
	
	public ExplGenOptions() {
		CmdLineParser.registerHandler(String[].class, StringArrayOptionHandler.class);
	}

	public void setDBOptions(MappingScenario map) {
		ConnectionInfoType con = map.getConnectionInfo();

		if (con != null) {
			dbUser = con.getUser();
			dbPassword = con.getPassword();
			dbName = con.getDB();
			dbURL = con.getHost();
		}
	}

	public String getDbUser() {
		return dbUser;
	}

	public void setDbUser(String dbUser) {
		this.dbUser = dbUser;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public void setDbPassword(String dbPassword) {
		this.dbPassword = dbPassword;
	}

	public String getDbName() {
		return dbName;
	}

	public void setDbName(String dbName) {
		this.dbName = dbName;
	}

	public String getDbURL() {
		return dbURL;
	}

	public void setDbURL(String dbURL) {
		this.dbURL = dbURL;
	}

	public File getXmlDoc() {
		return xmlDoc;
	}

	public void setXmlDoc(File xmlDoc) {
		this.xmlDoc = xmlDoc;
	}

	public File getMarkerFile() {
		return markerFile;
	}

	public void setMarkerFile(File markerFile) {
		this.markerFile = markerFile;
	}

	public String getMarkers() {
		return markers;
	}

	public void setMarkers(String markers) {
		this.markers = markers;
	}

	public void setLoadScen(boolean loadScen) {
		this.loadScen = loadScen;
	}

	public boolean isLoadScen() {
		return loadScen;
	}

	public String getRankerScheme() {
		return rankerScheme;
	}

	public void setRankerScheme(String rankerScheme) throws Exception {
		if (RankerFactory.getRankerSchemes().contains(rankerScheme))
			this.rankerScheme = rankerScheme;
		throw new Exception("Unknown ranker scheme <" + rankerScheme
				+ "> expected one of " + RankerFactory.getRankerSchemes());
	}

	public boolean isLazy() {
		return lazy;
	}

	public void setLazy(boolean lazy) {
		this.lazy = lazy;
	}

	public boolean isUseRanker() {
		return useRanker;
	}

	public boolean noUsePart() {
		return nousePart;
	}

	public void setUseRanker(boolean useRanker) {
		this.useRanker = useRanker;
	}

	public String[] getSkylineRankers() {
		return skylineRankers;
	}

	public void setSkylineRankers(String[] skylineRankers) {
		this.skylineRankers = skylineRankers;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isRankNonInteractive() {
		return rankNonInteractive;
	}

	public void setRankNonInteractive(boolean rankNonInteractive) {
		this.rankNonInteractive = rankNonInteractive;
	}

	public int getMaxRank() {
		return maxRank;
	}

	public void setMaxRank(int maxRank) {
		this.maxRank = maxRank;
	}

	public boolean isNoShowSets() {
		return noShowSets;
	}

	public void setNoShowSets(boolean showSets) {
		this.noShowSets = showSets;
	}

	public File getCsvLoadPath() {
		return csvLoadPath;
	}

	public void setCsvLoadPath(File csvLoadPath) {
		this.csvLoadPath = csvLoadPath;
	}

	public File getGoldStandard() {
		return goldStandard;
	}

	public void setGoldStandard(File goldStandard) {
		this.goldStandard = goldStandard;
	}

	public int getTimeLimit() {
		return timeLimit;
	}

	public void setTimeLimit(int timeLimit) {
		this.timeLimit = timeLimit;
	}

	public boolean isShowHelp() {
		return showHelp;
	}

	public void setShowHelp(boolean showHelp) {
		this.showHelp = showHelp;
	}
	
	

}
