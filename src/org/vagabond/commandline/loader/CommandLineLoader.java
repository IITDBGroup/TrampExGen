package org.vagabond.commandline.loader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.sql.Connection;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.PropertyConfigurator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.vagabond.explanation.generation.QueryHolder;
import org.vagabond.mapping.model.MapScenarioHolder;
import org.vagabond.mapping.model.ModelLoader;
import org.vagabond.xmlmodel.MappingScenarioDocument.MappingScenario;
import org.vagabond.xmlmodel.ConnectionInfoType;
import org.vagabond.mapping.model.serialize.mapfile.MapFileSerializer;
import org.vagabond.mapping.scenarioToDB.DatabaseScenarioLoader;
import org.vagabond.util.ConnectionManager;
import org.vagabond.util.LoggerUtil;



public class CommandLineLoader {

	static Logger log = Logger.getLogger(CommandLineLoader.class);
	
	public static final String LOG4JPROPERIES_DEFAULT_LOCATION = "resource/log4jproperties.txt";
	
	private LoaderOptions options;
	private MapScenarioHolder map;
	
	public CommandLineLoader () {
		options = new LoaderOptions ();
	}

	public void setUpLogger () {
		defaultLogConfig();
	}
	
	public void defaultLogConfig() {
		// standard appender is console
		ConsoleAppender console = new ConsoleAppender(); 
		String PATTERN = "%d [%p] %l %m%n";
		console.setLayout(new PatternLayout(PATTERN)); 
		console.setThreshold(Level.ERROR);
		console.activateOptions();
		Logger.getRootLogger().addAppender(console);
	}
	
	
	public void parseOptionsAndLoadScenario (String[] args) 
			throws Exception {
		parseOptions(args);
		reconfigLog();	
		
		if (options.getXmlDoc() == null)
			throw new CmdLineException("no mapping scenario XML document " +
					"given (-f option)");
		loadScenario (options.getXmlDoc());

		parseOptions(args);	
	}
	
	/**
	 *  read log4j properties from user defined location or default location
	 * @throws FileNotFoundException 
	 */
	public void reconfigLog() throws FileNotFoundException {
		File location = options.getLogConfig();
		Level llevel = options.getLoglevel();
		Logger.getRootLogger().removeAllAppenders();
		
		// user provided log location?
		if (location != null) {
			if (!location.exists())
			{
				System.err.printf("User provided log location does not exist: %s", location);
				System.exit(1);
			}
			PropertyConfigurator.configure(location.getAbsolutePath());
			log.info("user has provided log level location: " +  location);
		}
		// user has given a global log level
		else if (llevel != null) {
			ConsoleAppender c = new ConsoleAppender();
			c.setLayout(new PatternLayout("%-4r [%t] %-5p %l - %m%n"));
			c.setThreshold(llevel);
			c.activateOptions();
			Logger.getRootLogger().addAppender(c);			
		
			log.info("user set log level to " + llevel.toString());
		}
		// do we have a log file at the default location
		else if (new File(LOG4JPROPERIES_DEFAULT_LOCATION).exists())
		{
			PropertyConfigurator.configure(LOG4JPROPERIES_DEFAULT_LOCATION);
			log.info("use default log properties location " + LOG4JPROPERIES_DEFAULT_LOCATION);
		}
		// just set everything to error log level
		else {
			defaultLogConfig();
		}
	}
	
	
	
	private void loadScenario (File xmlDoc) 
			throws Exception {
		boolean validation = ! options.isNoValidation(); 

		ModelLoader.getInstance().setValidation(validation);
		map = ModelLoader.getInstance().load(xmlDoc);
		QueryHolder.getInstance().loadFromDirFallbackResource(new File("resource/queries"), QueryHolder.DEFAULT_QUERY_LIST_FILE);
		setDBOptions(map.getScenario());
	}
	
	public void setDBOptions (MappingScenario map) {
		ConnectionInfoType con = map.getConnectionInfo();
		
		if (con != null) {
			options.setDbUser(con.getUser());
			options.setDbPassword(con.getPassword());
			options.setDbName(con.getDB());
			options.setDbURL(con.getHost());
		}
	}
	
	private void parseOptions (String[] args) throws CmdLineException {
		CmdLineParser parser;
		
		parser = new CmdLineParser(options);
		if (log.isDebugEnabled()) {log.debug("Command line args are: <" + LoggerUtil.arrayToString(args) + ">");};
		try {
			parser.parseArgument(args);
		} catch (Exception e) {
			printUsage(parser, System.out);
		}
		if(options.isShowHelp()) {
			printUsage(parser, System.out);
			System.exit(0);
		}
	}
	
	private void printUsage (CmdLineParser parser, PrintStream out) {
		out.println("loader.sh [options]\n\n");
		parser.printUsage(out);
	}
	
	
	public void executeOnDB () throws Exception {
		Connection dbCon;
		File csvPath = options.getCsvLoadPath();
		
		dbCon = ConnectionManager.getInstance().getConnection(
				options.getDbURL(), options.getDbName(), options.getDbUser(),
				options.getDbPassword(), options.getPort());
		if (options.isNoData())
			DatabaseScenarioLoader.getInstance().loadScenarioNoData(dbCon, map);
		else
			DatabaseScenarioLoader.getInstance().loadScenario(dbCon, map, csvPath);
		dbCon.close();
	}
	
	public void printUsage (PrintStream out) {
		CmdLineParser parser;
		
		parser = new CmdLineParser(options);
		parser.printUsage(out);
	}
	
	private void output () throws Exception {
		switch(options.getOutForm())
		{
		case none:
			break;
		case map:
			System.out.println(MapFileSerializer.getInstance().transformToMap(map.getScenario()));
			break;
		case xml:
			System.out.println(map.getDocument().toString());
			break;
		}
	}
	
	public boolean execute (String[] args) {
		try {
			setUpLogger();
		} catch (Exception e) {			
			e.printStackTrace();
			return false;
		}
		
		try {
			parseOptionsAndLoadScenario(args);
			if (!options.isOnlyValidate())
				executeOnDB();
			output();
		} catch (CmdLineException e) {			
			LoggerUtil.logException(e, log);
			printUsage(System.err);
			return false;
		} catch (Exception e) {			
			LoggerUtil.logException(e, log);
			return false;
		}

		
		return true;
	}
	
	public static void main (String[] args) {
		CommandLineLoader inst = new CommandLineLoader();
		
		if (!inst.execute(args)) {
			System.exit(1);
		}
		System.exit(0);
	}
	
}
