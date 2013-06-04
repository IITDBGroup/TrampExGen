package org.vagabond.test.explanations.model.query;

import static org.junit.Assert.*;
import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.vagabond.explanation.marker.IMarkerSet;
import org.vagabond.explanation.marker.MarkerParser;
import org.vagabond.explanation.marker.MarkerSet;
import org.vagabond.explanation.marker.query.QueryMarkerSetGenerator;
import org.vagabond.test.AbstractVagabondTest;
import org.vagabond.test.explanations.model.TestBasicAndExplanationSets;

public class TestQueryMarkerSetGeneration extends AbstractVagabondTest {
	
	static Logger log = Logger.getLogger(AbstractVagabondTest.class);
	
	@Before
	public void setUp () throws Exception {
		loadToDB("resource/exampleScenarios/homelessDebugged.xml");
	}
		
	@Test
	public void testUsingSimpleQuery () throws Exception {
		IMarkerSet expected = MarkerParser.getInstance().parseSet("");
		String query = "SELECT 'person' AS rel,'name' AS attr, tid" +
						"FROM target.person WHERE livesIn = 'Toronto'";
		IMarkerSet actual = QueryMarkerSetGenerator.getInstance().genMSetFromQuery (query);
		
		assertEquals(expected, actual);
	}
}
