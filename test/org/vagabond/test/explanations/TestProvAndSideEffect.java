package org.vagabond.test.explanations;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.vagabond.explanation.generation.prov.AlterSourceProvenanceSideEffectGenerator;
import org.vagabond.explanation.generation.prov.AttrGranularitySourceProvenanceSideEffectGenerator;
import org.vagabond.explanation.generation.prov.ProvenanceGenerator;
import org.vagabond.explanation.generation.prov.SourceProvenanceSideEffectGenerator;
import org.vagabond.explanation.marker.IAttributeValueMarker;
import org.vagabond.explanation.marker.IMarkerSet;
import org.vagabond.explanation.marker.MarkerFactory;
import org.vagabond.explanation.marker.MarkerParser;
import org.vagabond.explanation.model.prov.MapAndWLProvRepresentation;
import org.vagabond.mapping.model.MapScenarioHolder;
import org.vagabond.test.AbstractVagabondTest;
import org.vagabond.util.CollectionUtils;
import org.vagabond.util.Pair;
import org.vagabond.util.QueryTemplate;
import org.vagabond.xmlmodel.MappingType;

public class TestProvAndSideEffect extends AbstractVagabondTest {

	static Logger log = Logger.getLogger(TestProvAndSideEffect.class);
	
	private static ProvenanceGenerator pGen;
	private static SourceProvenanceSideEffectGenerator seGen;
	private static AlterSourceProvenanceSideEffectGenerator altGen;
	private static AttrGranularitySourceProvenanceSideEffectGenerator attrGen;
	
	private boolean compareQuery (QueryTemplate q, String[] param, String comp) {
		for(String[] p: createPerm(param)) {
			if (q.parameterize(p).equals(comp))
				return true;
		}
		return false;
	}
	
	private List<String[]> createPerm (String[] orig) {
		List<String[]> result = new ArrayList<String[]> ();
		
		perm(orig, new String[] {}, result);
		
		return result;
	}
	
	private void perm(String[] prefix, String[] s, List<String[]> result) {
        int N = s.length;
        if (N == 0) 
        	result.add(prefix);
        else {
            for (int i = 0; i < N; i++)
               perm(arrConcat(prefix, new String[] {s[i]}), arrConcat(subArray(s, 0, i), subArray(s, i+1, N)), result);
        }
	}
	
	private String[] arrConcat (String[] l, String[] r) {
		String[] result = new String[l.length + r.length];
		
		System.arraycopy(l, 0, result, 0, l.length);
		System.arraycopy(r, 0, result, l.length, r.length);
		
		return result;
	}
	
	private String[] subArray (String[] arr, int start, int length) {
		String[] result = new String[length];
		
		System.arraycopy(arr, start, result, 0, length);
		
		return result;
	}
	
	@Before
	public void setUp () throws Exception {
		loadToDB("resource/test/simpleTest.xml");
		
		pGen = ProvenanceGenerator.getInstance();
		seGen = new SourceProvenanceSideEffectGenerator();
		altGen = new AlterSourceProvenanceSideEffectGenerator();
		attrGen = new AttrGranularitySourceProvenanceSideEffectGenerator();
	}
	
	@Test
	public void testMapProvQuery () throws Exception {
		Set<MappingType> result;
		Set<MappingType> exp;
		
		exp = new HashSet<MappingType>();
		exp.add(MapScenarioHolder.getInstance().getMapping("M2"));
		
		result = pGen.computeMapProv(MarkerFactory.
				newAttrMarker("employee", "2|2", "city"));
		assertEquals(result, exp);
	}
	
	@Test
	public void testSideEffectQueryGen () throws Exception {
		Set<String> sourceRels;
		Map<String, IMarkerSet> sourceErr;
		IMarkerSet errSet, errSet2;
		String resultQuery;
		String query;
		QueryTemplate q;
		String[] param;

		setTids("employee", new String[] {"1"});
		setTids("address", new String[] {"2","3"});
		
		q = new QueryTemplate("SELECT prov.tid\n" +
				"FROM\n" +
				"(SELECT *\n" +
				"FROM target.employee) AS prov\n" +
				"WHERE NOT EXISTS (SELECT subprov.tid\n" +
				"FROM (SELECT PROVENANCE * FROM target.employee) AS subprov\n" + 
				"WHERE prov.tid = subprov.tid " +
				"AND (prov_source_address_tid IS DISTINCT FROM $1 " +
				"AND prov_source_address_tid IS DISTINCT FROM $2 ))");
		param = new String[] {"2","3"};
		
		errSet = MarkerFactory.newMarkerSet(
				MarkerFactory.newTupleMarker("employee", "1")
				);
		errSet2 = MarkerFactory.newMarkerSet(
				MarkerFactory.newTupleMarker("address", "2"),
				MarkerFactory.newTupleMarker("address", "3")
				);
		sourceErr = new HashMap<String, IMarkerSet> ();
		sourceErr.put("employee", errSet);
		sourceErr.put("address", errSet2);
		
		sourceRels = new HashSet<String> ();
		sourceRels.add("address");
		sourceRels.add("person");
		
		resultQuery = seGen.getSideEffectQuery
				("employee", sourceRels, sourceErr).trim();
		if (log.isDebugEnabled()) {log.debug(resultQuery);};
		
		compareQuery(q, param, resultQuery);
	}
	
	@Test
	public void testBaseRelToMappingMap () throws Exception {
		Vector<Pair<String,Set<MappingType>>> result, expect;
		Set<MappingType> value;
		
		expect = new Vector<Pair<String,Set<MappingType>>>();
		value = new HashSet<MappingType> ();
		value.add(MapScenarioHolder.getInstance().getMapping("M1"));
		value.add(MapScenarioHolder.getInstance().getMapping("M2"));
		expect.add(new Pair<String, Set<MappingType>>("person", value));
		value = new HashSet<MappingType> ();
		value.add(MapScenarioHolder.getInstance().getMapping("M2"));
		expect.add(new Pair<String, Set<MappingType>>("address", value));
		
		result = pGen.getBaseRelAccessToMapping("employee");
		if (log.isDebugEnabled()) {log.debug(result);};
		
		assertEquals(expect, result);
	}
	
	@Test
	public void testMapAndPIProv () throws Exception {
		IAttributeValueMarker error;
		MapAndWLProvRepresentation result, expect;
		
		error = (IAttributeValueMarker) MarkerParser.getInstance()
				.parseMarker("A(employee,3|,name)");
		
		expect = new MapAndWLProvRepresentation();
		expect.setMapProv(CollectionUtils.makeVec(
				MapScenarioHolder.getInstance().getMapping("M1")));
		expect.setRelNames(CollectionUtils.makeList("person","address"));
		expect.setWitnessLists(CollectionUtils.makeVec(
				MarkerParser.getInstance().parseWL("{T(person,3),null}")));
		expect.setTuplesInProv(MarkerParser.getInstance().parseSet("{T(person,3)}"));
		result = pGen.computePIAndMapProv(error);
		if (log.isDebugEnabled()) {log.debug(result);};
		
		assertEquals(expect, result);
		
		error = (IAttributeValueMarker) MarkerParser.getInstance()
				.parseMarker("A(employee,1|1,name)");

		expect = new MapAndWLProvRepresentation();
		expect.setMapProv(CollectionUtils.makeVec(
				MapScenarioHolder.getInstance().getMapping("M2")));
		expect.setRelNames(CollectionUtils.makeList("person","address"));
		expect.setWitnessLists(CollectionUtils.makeVec(
				MarkerParser.getInstance().parseWL(
						"{T(person,1),T(address,1)}")));
		expect.setTuplesInProv(MarkerParser.getInstance().parseSet(
				"{T(person,1),T(address,1)}"));
		result = pGen.computePIAndMapProv(error);
		if (log.isDebugEnabled()) {log.debug(result);};
		
		assertEquals(expect, result);
	}

	@Test
	public void testAlterSourceSideEffectQueryGen() throws Exception {
		Set<String> sourceRels;
		Map<String, IMarkerSet> sourceErr;
		IMarkerSet errSet, errSet2;
		String resultQuery;
		String[] param;
		QueryTemplate q;

		setTids("employee", new String[] {"1"});
		setTids("address", new String[] {"2","3"});
		
		q = new QueryTemplate("SELECT tid\n" +
				"FROM \n" +
				"(SELECT tid, (prov_source_address_tid = $1 OR prov_source_address_tid = $2 ) AS hasSub\n" +
				"FROM (SELECT PROVENANCE * FROM target.employee) p) AS sideeff\n" +
				"GROUP BY tid\n" +
				"HAVING bool_and(hasSub) = true;");
		param = new String[] {"2","3"};
		
		errSet = MarkerFactory.newMarkerSet(
				MarkerFactory.newTupleMarker("employee", "1")
				);
		errSet2 = MarkerFactory.newMarkerSet(
				MarkerFactory.newTupleMarker("address", "2"),
				MarkerFactory.newTupleMarker("address", "3")
				);
		sourceErr = new HashMap<String, IMarkerSet> ();
		sourceErr.put("employee", errSet);
		sourceErr.put("address", errSet2);
		
		sourceRels = new HashSet<String> ();
		sourceRels.add("address");
		sourceRels.add("person");
		
		resultQuery = altGen.getSideEffectQuery
				("employee", sourceRels, sourceErr).trim();
		if (log.isDebugEnabled()) {log.debug(resultQuery);};
		
		compareQuery(q, param, resultQuery);
	}
	
	@Test
	public void testAttrGranSideEffectsQueryGen () throws Exception {
		Set<String> sourceRels;
		Map<String, IMarkerSet> sourceErr;
		IMarkerSet errSet, errSet2;
		String resultQuery;
		String[] param;
		QueryTemplate q;

		setTids("employee", new String[] {"1"});
		setTids("address", new String[] {"2","3"});
		
		q = new QueryTemplate("SELECT realside.tid, prov_source_person_tid,prov_source_address_tid\n" +
				"FROM\n" +
				"(SELECT tid FROM \n" +
				"(SELECT tid, (prov_source_address_tid = $1 OR prov_source_address_tid = $2 ) AS hasSub\n" +
				"FROM (SELECT PROVENANCE * FROM target.employee) p) AS sideeff\n" +
				"GROUP BY sideeff.tid\n" +
				"HAVING bool_and(hasSub) = true) AS realside,\n" +
				"(SELECT PROVENANCE * FROM target.employee) AS prov\n" +
				"WHERE realside.tid = prov.tid\n" +
				"ORDER BY realside.tid");
		param = new String[] {"2","3"};
		
		errSet = MarkerFactory.newMarkerSet(
				MarkerFactory.newTupleMarker("employee", "1")
				);
		errSet2 = MarkerFactory.newMarkerSet(
				MarkerFactory.newTupleMarker("address", "2"),
				MarkerFactory.newTupleMarker("address", "3")
				);
		sourceErr = new HashMap<String, IMarkerSet> ();
		sourceErr.put("employee", errSet);
		sourceErr.put("address", errSet2);
		
		sourceRels = new HashSet<String> ();
		sourceRels.add("address");
		sourceRels.add("person");
		
		resultQuery = attrGen.getSideEffectQuery
				("employee", sourceRels, sourceErr).trim();
		if (log.isDebugEnabled()) {log.debug(resultQuery);};
		
		compareQuery(q, param, resultQuery);	
	}
	
	@Test
	public void testAttrGranSideEffects () throws Exception {
		IAttributeValueMarker error;
		IMarkerSet sourceSE, targetSE, targetExpect;
		
		error = (IAttributeValueMarker) MarkerParser.getInstance()
				.parseMarker("A(employee,2|2,city)");
		sourceSE = MarkerParser.getInstance().parseSet(
				"{A(address,2,city)}");
		targetExpect = MarkerParser.getInstance().parseSet(
				"{A(employee,4|2,city)}"); 
		
		targetSE = attrGen.computeTargetSideEffects(sourceSE, error);
		if (log.isDebugEnabled()) {log.debug("targetSE is:\n" + targetSE);};
		
		assertEquals(targetExpect, targetSE);
	}
}
