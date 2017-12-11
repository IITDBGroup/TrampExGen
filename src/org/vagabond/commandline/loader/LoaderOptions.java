package org.vagabond.commandline.loader;

import java.io.File;

import org.apache.log4j.Level;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerRegistry;

public class LoaderOptions {

	public enum OutputFormat {
		none,
		xml,
		map
	}
	
	@Option(name="-f", usage="xml mapping scenario document")
	private File xmlDoc;
	
	@Option(name="-u", usage="user name for connecting to the database")
	private String dbUser = "postgres";
	
	@Option(name="-p", usage="password for database user")
	private String dbPassword = "";

	@Option(name = "-P", usage = "port for database connection")
	private int port = 5432;
	
	@Option(name="-d", usage="name of the database to connect to")
	private String dbName = "tramptest";
	
	@Option(name="-h", usage="URL of the database to connect to")
	private String dbURL = "localhost";
	
	@Option(name="--no-data", usage="don't create instance data")
	private boolean noData = false;
	
	@Option(name="--validate-only", usage="only validate mapping XML file")
	private boolean onlyValidate = false;
	
	@Option(name="--output", usage="output mapping in format (xml = Tramp XML mapping file, map = .map file)")
	private OutputFormat outForm = OutputFormat.none;
	
	@Option(name="-c", usage="data files (CSV) are load from this directory")
	private File csvLoadPath = null;
	
	@Option(name="--no-validation", usage="deactivate validations of Tramp XML mapping file")
	private boolean noValidation = false;
	
	@Option(name="--no-target", usage="don't create target schema and instance")
	private boolean noTarget = false;
	
	@Option(name = "-logconfig", usage = "path to log4j configuration file to use")
	private File logConfig = null;
	
	@Option(name = "-loglevel", usage ="set a global log level if no log4j configuration file is specificed")
	private Level loglevel = null;

	@Option(name="-help", usage="show this help message")
	private boolean showHelp = false;
	
	private boolean[] dbOptionsSet = { false, false, false, false };

	// register option handler for log level 
	static {
		OptionHandlerRegistry.getRegistry().registerHandler(Level.class, Log4jLevelOptionHandler.class);
	}
	
	public LoaderOptions() {
		OptionHandlerRegistry.getRegistry().registerHandler(Level.class, Log4jLevelOptionHandler.class);
	}
	
	

	public File getXmlDoc() {
		return xmlDoc;
	}

	public void setXmlDoc(File xmlDoc) {
		this.xmlDoc = xmlDoc;
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

	public boolean isNoData() {
		return noData;
	}

	public void setNoData(boolean noData) {
		this.noData = noData;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isOnlyValidate() {
		return onlyValidate;
	}

	public void setOnlyValidate(boolean onlyValidate) {
		this.onlyValidate = onlyValidate;
	}

	public OutputFormat getOutForm() {
		return outForm;
	}

	public void setOutForm(OutputFormat outForm) {
		this.outForm = outForm;
	}

	public File getCsvLoadPath() {
		return csvLoadPath;
	}

	public void setCsvLoadPath(File csvLoadPath) {
		this.csvLoadPath = csvLoadPath;
	}

	public boolean isNoValidation() {
		return noValidation;
	}

	public void setNoValidation(boolean noValidation) {
		this.noValidation = noValidation;
	}

	public File getLogConfig() {
		return logConfig;
	}

	public void setLogConfig(File logConfig) {
		this.logConfig = logConfig;
	}

	public Level getLoglevel() {
		return loglevel;
	}

	public void setLoglevel(Level loglevel) {
		this.loglevel = loglevel;
	}

	public boolean isShowHelp() {
		return showHelp;
	}

	public void setShowHelp(boolean showHelp) {
		this.showHelp = showHelp;
	}
	
	
}
