package org.vagabond.commandline.explgen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.vagabond.explanation.generation.ExplanationSetGenerator;
import org.vagabond.explanation.generation.PartitionExplanationGenerator;
import org.vagabond.explanation.generation.QueryHolder;
import org.vagabond.explanation.marker.IMarkerSet;
import org.vagabond.explanation.marker.MarkerParser;
import org.vagabond.explanation.marker.ScenarioDictionary;
import org.vagabond.explanation.metrics.RankingMetricPrecisionRecall;
import org.vagabond.explanation.model.ExplPartition;
import org.vagabond.explanation.model.ExplanationCollection;
import org.vagabond.explanation.model.ExplanationFactory;
import org.vagabond.explanation.model.IExplanationSet;
import org.vagabond.explanation.model.basic.IBasicExplanation;
import org.vagabond.explanation.ranking.IExplanationRanker;
import org.vagabond.explanation.ranking.IPartitionRanker;
import org.vagabond.explanation.ranking.RankerFactory;
import org.vagabond.explanation.ranking.SkylineRanker;
import org.vagabond.explanation.ranking.scoring.IScoringFunction;
import org.vagabond.mapping.model.MapScenarioHolder;
import org.vagabond.mapping.model.ModelLoader;
import org.vagabond.mapping.scenarioToDB.DatabaseScenarioLoader;
import org.vagabond.mapping.scenarioToDB.DatabaseScenarioLoader.LoadMode;
import org.vagabond.util.ConnectionManager;
import org.vagabond.util.LoggerUtil;
import org.vagabond.util.xmlbeans.ExplanationAndErrorXMLLoader;

public class CommandLineExplGen {

	static Logger log = Logger.getLogger(CommandLineExplGen.class);

	private ExplGenOptions explOptions;
	private IMarkerSet markers;
	private ExplanationSetGenerator explGenerator;
	private Iterator<IExplanationSet> iter;
	private IExplanationRanker explRank;
	private SkylineRanker skyRank;
	private IPartitionRanker partRank;
	private IScoringFunction scoringFunction;
	
	private int whichRanker; // 1 for explanation, 2 for skyline, 3 for partition
	public static final int EXPLANATION_RANKER = 1;
	public static final int SKYLINE_RANKER = 2;
	public static final int PARTITION_RANKER = 3;
	
	public CommandLineExplGen() {
		explOptions = new ExplGenOptions();
		explGenerator = new ExplanationSetGenerator();
	}
	 	
	public void setUpLogger() {
		PropertyConfigurator.configure("resource/log4jproperties.txt");
	}

	public void parseOptionsAndLoadScenario(String[] args) throws Exception {
		parseOptions(args);

		if (explOptions.getXmlDoc() == null)
			throw new Exception("no mapping scenario XML document "
					+ "given (-f option)");
		loadScenario(explOptions.getXmlDoc());
		parseOptions(args);

		// setup DB connection
		ConnectionManager.getInstance().getConnection(explOptions.getDbURL(),
				explOptions.getDbName(), explOptions.getDbUser(),
				explOptions.getDbPassword(), explOptions.getPort());

		if (explOptions.isLoadScen())
			loadScenarioOnDB();
		ScenarioDictionary.getInstance().initFromScenario();
	}

	private void createExpls(PrintStream out) throws Exception {
		if (explOptions.isUseRanker()){ 
			rankExplanations();
			setIterator();
			printExplanations();
		}
		else {
			ExplanationCollection col = explGenerator.findExplanations(markers);
			if (!explOptions.isNoShowSets())
				out.println(col);
		}
	}
	
	private static double getTimeDifference(long startTime){
		long endTime = System.nanoTime();
		long difference = (endTime - startTime);
		return ((double) difference) / 1000000000.0;
	}
	
	private static void printTime(String section, long startTime){
		System.out.printf(section + ": %.2f secs\n", getTimeDifference(startTime));
	}
	
	private void setIterator(){
		switch(whichRanker){
			case EXPLANATION_RANKER:
				iter = explRank;
				scoringFunction = explRank.getScoringFunction();
				break;
			case SKYLINE_RANKER:
				iter = skyRank;
				break;
			case PARTITION_RANKER:
				iter = partRank;
				scoringFunction = partRank.getScoringFunction();
				break;
		}
	}
	
	private void rankExplanations() throws Exception{
		
		// No Partitioning
		if (explOptions.noUsePart()) {
			
			ExplanationSetGenerator noPartGen =	new ExplanationSetGenerator();
			long startTime = System.nanoTime();
			ExplanationCollection col2 = noPartGen.findExplanations(markers);
			printTime("ExplGen", startTime);
	
			if (explOptions.getRankerScheme() != null){

				if (log.isDebugEnabled()) {
					log.debug("Create ranker for scheme without partitioning " + explOptions.getRankerScheme());
				}
				
				explRank = RankerFactory.createInitializedRanker(explOptions.getRankerScheme(), col2);
				whichRanker = EXPLANATION_RANKER;
			}
		}
		
		// Using Partitioning
		else {		
			PartitionExplanationGenerator partGen =	new PartitionExplanationGenerator();
			partGen.init();
			long startTime = System.nanoTime();
			ExplPartition partition = partGen.findExplanations(markers);
			printTime("ExplGen", startTime);
			
			if (explOptions.getSkylineRankers() != null) {
				if (log.isDebugEnabled()) {log.debug("Create skyline ranker for scheme "
						+ Arrays.toString(explOptions.getSkylineRankers()));}
				skyRank = RankerFactory.createSkylineRanker(
								explOptions.getSkylineRankers(),
								explOptions.getRankerScheme(), partition);
				whichRanker = SKYLINE_RANKER;
			}
			else {
				if (log.isDebugEnabled()) {log.debug("Create ranker for scheme "
						+ explOptions.getRankerScheme());}
				partRank = RankerFactory.createPartRanker(
								explOptions.getRankerScheme(), partition);
				whichRanker = PARTITION_RANKER;
			}
		}
	}
		
	private void printExplanations() throws Exception{
		boolean continueExe = true;
		int r = 0;
		
		// if a gold standard is given then we just compute precision and recall metrics
		if (explOptions.getGoldStandard() != null) {
			int max = explOptions.getMaxRank();
			IExplanationSet pre = ExplanationAndErrorXMLLoader.getInstance().loadExplanations(explOptions.getGoldStandard());
			IExplanationSet gold = ExplanationFactory.newExplanationSet();
			RankingMetricPrecisionRecall metric;
			double prec = 0.0, rec = 0.0;
			max = (max == -1) ? Integer.MAX_VALUE : max;
			int real = 0;
			
			// You have to copy the set because computing real target SE changes the hash
			for(IBasicExplanation e: pre) {
				e.computeRealTargetSEAndExplains(markers);
				gold.add(e);
			}
			
			metric = new RankingMetricPrecisionRecall(gold);
			
			// Gather all solutions/explanations
			while(iter.hasNext() && real < max) {
				iter.next();
				real++;
			}
			
			for(int i = 0; i < real; i++) {
				IExplanationSet set;
				double score = 0.0;
				
				if (explOptions.noUsePart()) {
					prec = metric.computePrecision(explRank, i);
					rec = metric.computeRecall(explRank, i);
					set = explRank.getRankedExpl(i);
					score = explRank.getScoringFunction().getScore(set);
				}
				else {
					prec = metric.computePrecision(partRank, i);
					rec = metric.computeRecall(partRank, i);
					set = partRank.getRankedExpl(i);
					score = partRank.getScoringFunction().getScore(set);
				}				
				
				if (!explOptions.isNoShowSets()) {
					System.out.println("\n\n*********************************\n*" +
							"\t\t RANKED " 
							+ ++r + " with score " + score
							+ "\n*********************************\n");
					System.out.println(set.toString());
				}
				
				System.out.printf("top: %d, prec: %f, rec: %f\n", i+1, prec, rec);
				System.out.flush();
			}
		}
		// Use non-interactive ranking where we produced the top maxRank CES (or all if maxRank is -1)
		else if (explOptions.isRankNonInteractive()) {
			int i = 1;
			int max = explOptions.getMaxRank();
			long beforeRank = System.nanoTime();
			long start = System.currentTimeMillis();
			long end = explOptions.getTimeLimit() == -1 ? -1 :  start + explOptions.getTimeLimit()*1000;
			
			// Only 10mins running for ranking
			
			while ((max == -1 || i <= max) && (end < 0 || System.currentTimeMillis() < end)) {
				long lStartTime = System.nanoTime();				
				IExplanationSet set = iter.next();
				double score = -1.0;

				// Do a quick check inside timing, because ranking cost may be hidden in this check
				if (!iter.hasNext())
					break;
				
				if (scoringFunction != null)
					score = scoringFunction.getScore(set);
				
				if (!explOptions.isNoShowSets()) {
					System.out.println("\n\n*********************************\n*" +
							"\t\t RANKED " 
							+ ++r + " with score " + score
							+ "\n*********************************\n");
					System.out.println(set.toString());
				}
				
				long lEndTime = System.nanoTime();
				long difference= (lEndTime - lStartTime);
				double secs = ((double) difference) / 1000000000.0;
				
				System.out.println(String.format("%d: %.8f secs", i, secs));
				System.out.flush();
				i++;
			}
			long afterRank = System.nanoTime();
			double rankSecs1 = ((double) (afterRank - beforeRank)) / 1000000000.0;
			System.out.println(String.format("Ranking(%d): %.8f secs", i-1, rankSecs1));
		}
		
		// Use interactive ranking where the user is asked after each CES whether to continue or not
		
		else {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			while (continueExe && iter.hasNext()) {
				String read;
				IExplanationSet set = iter.next();
				double score = -1.0;
				if (scoringFunction != null)
					score = scoringFunction.getScore(set);
				
				if (!explOptions.isNoShowSets()) {
					System.out.println("\n\n*********************************\n*" +
							"\t\t RANKED " 
							+ ++r + " with score " + score
							+ "\n*********************************\n");
					System.out.println(set.toString());
					System.out.println("\nPress y to continue or v to verify an explanation");
					System.out.println("Press anything else to exit");
				}
				
				while (!in.ready())
					Thread.sleep(100);
				read = in.readLine().trim();
				
				if (log.isDebugEnabled()) {log.debug("user pressed " + read);}
				
				boolean verifyExpl = read.trim().startsWith("v");
				
				if(verifyExpl){
					verifyExplanation(set);
					Thread.sleep(2000); // For you to see the explanations you chose
				} else {
					continueExe = read.trim().startsWith("y");
				}
			}
		}
	}
	
	private void verifyExplanation(IExplanationSet set){
		List<IBasicExplanation> allExpls = set.getExplanations();
		System.out.println("\nType the explanation numbers between 0 and " + (allExpls.size() - 1));
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		try {
			String read = in.readLine().trim();
			String[] numbers = read.split(" ");
			System.out.println("\nYou successfully verified the following explanations:\n");
			for(String number: numbers){
				int numberInt = Integer.valueOf(number);
				if(numberInt >= 0 && numberInt < allExpls.size()){
					IBasicExplanation expl = allExpls.get(numberInt);
					explRank.confirmExplanation(expl);
					System.out.println(expl.toString());
					System.out.print("\n");
				}
			}
			System.out.println("The explanations will continue\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private IMarkerSet loadMarkers() throws Exception {
		if (explOptions.getMarkers() != null)
			markers = MarkerParser.getInstance().parseSet(explOptions.getMarkers());
		else if (explOptions.getMarkerFile() != null)
			markers = MarkerParser.getInstance().parseMarkers(
							new FileInputStream(explOptions.getMarkerFile()));
		else throw new Exception("either marker file (-m) or markers (-M) have "
					+ "to be specified");
		if (log.isDebugEnabled()) {log.debug("Markers are: <" + markers + ">");};
		return markers;
	}

	private void loadScenarioOnDB() throws Exception {
		if (explOptions.isLazy())
			DatabaseScenarioLoader.getInstance().setOperationalMode(LoadMode.Lazy);
		DatabaseScenarioLoader.getInstance().loadScenario(
				ConnectionManager.getInstance().getConnection(), 
				explOptions.getCsvLoadPath());
	}

	private void loadScenario(File xmlDoc) throws Exception {
		ModelLoader.getInstance().loadToInst(xmlDoc);
		QueryHolder.getInstance().loadFromDir(new File("resource/queries"));
		explOptions.setDBOptions(MapScenarioHolder.getInstance().getScenario());
	}

	private void parseOptions(String[] args) throws CmdLineException {
		CmdLineParser parser;
		if (log.isDebugEnabled()) {log.debug("Command line args are: <" + LoggerUtil.arrayToString(args)
				+ ">");}
		parser = new CmdLineParser(explOptions);
		try {
			parser.parseArgument(args);
		} catch (Exception e) {
			printUsage(parser, System.out);
			throw e;
		}
		if (explOptions.isShowHelp()) {
			printUsage(parser, System.out);
			System.exit(0);
		}
	}

	public void printUsage(CmdLineParser parser, PrintStream out) {
		out.print("explgen.sh [options]\n\n");
		parser.printUsage(out);
	}

	public boolean execute(String[] args) {
		try {
			setUpLogger();
		}
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		try {
			parseOptionsAndLoadScenario(args);
			loadMarkers();
			createExpls(System.out);
		}
		catch (CmdLineException e) {
			LoggerUtil.logException(e, log);
			return false;
		}
		catch (Throwable e) {
			LoggerUtil.logException(e, log);
			return false;
		}

		return true;
	}

	public static void main(String[] args) {
		CommandLineExplGen inst = new CommandLineExplGen();
		long startTime =  System.nanoTime();
		if (!inst.execute(args))
			System.exit(1);
		printTime("Total", startTime);
		System.exit(0);		 	
	}
}