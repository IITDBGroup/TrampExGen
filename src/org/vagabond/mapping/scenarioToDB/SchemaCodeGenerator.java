package org.vagabond.mapping.scenarioToDB;

import java.io.File;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import org.apache.log4j.Logger;
import org.vagabond.commandline.loader.LoaderOptions;
import org.vagabond.explanation.generation.QueryHolder;
import org.vagabond.util.LogProviderHolder;
import org.vagabond.xmlmodel.AttrDefType;
import org.vagabond.xmlmodel.AttrListType;
import org.vagabond.xmlmodel.ForeignKeyType;
import org.vagabond.xmlmodel.MappingScenarioDocument.MappingScenario;
import org.vagabond.xmlmodel.RelInstanceFileType;
import org.vagabond.xmlmodel.RelInstanceType;
import org.vagabond.xmlmodel.RelInstanceType.Row;
import org.vagabond.xmlmodel.RelationType;
import org.vagabond.xmlmodel.SchemaType;
import org.vagabond.xmlmodel.TransformationType;
import org.vagabond.xmlmodel.TransformationsType;

/**
 * 
 * Singlton class used to create DDL code from mapping scenario schema information.
 * 
 * @author Boris Glavic
 *
 */

public class SchemaCodeGenerator {

	static Logger log = LogProviderHolder.getInstance().getLogger(SchemaCodeGenerator.class);

	public static String EXTRA_TARGET_REL_POSTFIX = "_load";
	public static String SOURCE_SCHEMA_NAME = "source";
	public static String TARGET_SCHEMA_NAME = "target";
	
	private static SchemaCodeGenerator instance;
	
	private SchemaCodeGenerator () {
		
	}
	
	public static SchemaCodeGenerator getInstance () {
		if (instance == null) 
			instance = new SchemaCodeGenerator();
		return instance;
	}
	
	/**
	 * Given a mapping scenario return a DDL script that creates the source and target schema and 
	 * loads instance data (if present in the scenario).
	 * 
	 * @param map
	 * @return DDL script as a String.
	 * @throws Exception 
	 */
	
	public String getSchemaPlusInstanceCode (MappingScenario map) throws Exception {
		StringBuffer result = new StringBuffer ();
		
		getSchemasCode(map, result, false);
		getInstanceCode(map, SOURCE_SCHEMA_NAME, result);
		getAllSourceForeignKeysCode(map.getSchemas().getSourceSchema(), 
				SOURCE_SCHEMA_NAME, result);
		
		return result.toString();
	}
	
	public String getCheckCode(MappingScenario map) {		
		return QueryHolder.getQuery("Loader.CheckScenario")
				.parameterize(
				getSchemaCheckCode(map.getSchemas().getSourceSchema(), SOURCE_SCHEMA_NAME),
				getSchemaCheckCode(map.getSchemas().getTargetSchema(), TARGET_SCHEMA_NAME));
	}
	
	public String getSchemaCheckCode (SchemaType schema, String name) {
		int numRel = schema.getRelationArray().length;
		StringBuffer cond = new StringBuffer();
		
		for(RelationType rel: schema.getRelationArray()) {
			cond.append("(relname = '" + rel.getName() + "' AND nspname = '" + name + 
					"') OR " );
		}
		cond.delete(cond.length() - 4, cond.length());
		
		return QueryHolder.getQuery("Loader.CheckSchema").parameterize
				(numRel + "", cond.toString());
	}
	
	public String getInstanceDelCode (MappingScenario map) {
		StringBuffer result = new StringBuffer();
		boolean hasTargetData = map.getData().isSetLoadTargetData();
		boolean exchangeTargetData = map.getData().isSetExchangeData();
		
		for(RelationType rel: map.getSchemas().getSourceSchema()
				.getRelationArray()) {
			result.append(QueryHolder.getQuery("Loader.EmptyRel")
					.parameterize(SOURCE_SCHEMA_NAME, rel.getName()));
		}
		
		if (hasTargetData) {
			for(RelationType rel: map.getSchemas().getSourceSchema()
					.getRelationArray()) {
				String relName = exchangeTargetData ? getExtraTargetDataRelName(rel.getName()) : rel.getName(); 
				result.append(QueryHolder.getQuery("Loader.EmptyRel")
						.parameterize(TARGET_SCHEMA_NAME, relName));
			}				
		}
		
		return result.toString();
	}
	
	public String getExtraTargetDataRelName (String relName) {
		return relName + EXTRA_TARGET_REL_POSTFIX;
	}
	
	public String getInstanceCheckCode (MappingScenario map) throws Exception {
		StringBuffer subq = new StringBuffer();
		boolean isExAndTarget = isExangeDataAndLoadTargetData(map);
		
		for (RelInstanceType inst: map.getData().getInstanceArray()) {
			String relName = inst.isSetTargetRelation() && isExAndTarget 
					? getExtraTargetDataRelName(inst.getName()) : inst.getName();
			String schema = inst.isSetTargetRelation() ? TARGET_SCHEMA_NAME : SOURCE_SCHEMA_NAME;
			subq.append(getRelDataCheckCode(relName, schema, 
					inst.getRowArray().length));
			subq.append(" UNION ALL ");
		}
		
		for (RelInstanceFileType inst: map.getData().getInstanceFileArray()) {
			int card = getCSVCard(inst);
			String relName = inst.isSetTargetRelation() && isExAndTarget 
					? getExtraTargetDataRelName(inst.getName()) : inst.getName();
			String schema = inst.isSetTargetRelation() ? TARGET_SCHEMA_NAME : SOURCE_SCHEMA_NAME;
			subq.append(getRelDataCheckCode(relName, schema,
					card));
			subq.append(" UNION ALL ");
		}
		subq.delete(subq.length() - " UNION ALL ".length(), subq.length());
		
		return QueryHolder.getQuery("Loader.CheckData").parameterize(
				subq.toString());
	}
	
	private int getCSVCard(RelInstanceFileType inst) throws Exception {
		String path = getPath(inst);
		File file = new File(path);
		Scanner in;
		int count = 0;
		
		assert(file.exists());
		
		in = new Scanner(file);
		while(in.hasNext()) {
			in.nextLine();
			count++;
		}
			
		return count;
	}

	public String getRelDataCheckCode (String relName, String schema, int card) {
		return QueryHolder.getQuery("Loader.CheckRelData")
				.parameterize("" + card, schema, relName);
	}
	
	/**
	 * Given a mapping scenario return a DDL script that creates the source and target schema.
	 * 
	 * @param map
	 * @return DDL script as a String.
	 */
	
	public String getSchemasCode (MappingScenario map) {
		StringBuffer result = new StringBuffer ();
		
		getSchemasCode (map, result, true);
		
		return result.toString();
	}
	
	/**
	 * Given a mapping scenario return a DDL script that creates the source and target schema leaving
	 * out foreign key constraints. This is useful for scripts that load data. Such scripts would have
	 * to load data in a certain order to obey foreign key constraints otherwise.
	 * 
	 * @param map
	 * @return DDL script as a String.
	 */
	
	public String getSchemaCodeNoFKeys (MappingScenario map) {
		StringBuffer result = new StringBuffer ();
		
		getSchemasCode (map, result, false);
		
		return result.toString();
	}
	 
	/**
	 * Given a mapping scenario return a DDL script that creates the source and target schema. 
	 * Parameter <code>addFKeys</code> determines if foreign keys are created.
	 * 
	 * @param map
	 * @param result StringBuffer to store the code
	 * @param addFKeys
	 */
	
	private void getSchemasCode (MappingScenario map, StringBuffer result, 
			boolean addFKeys) {
		getSchemaCode(map.getSchemas().getSourceSchema(), SOURCE_SCHEMA_NAME, result,
				addFKeys, map);
		if (isExangeDataAndLoadTargetData(map))
			getSchemaCode(map.getSchemas().getTargetSchema(), TARGET_SCHEMA_NAME, 
					result, addFKeys, map);
		result.append('\n');
		if (map.getData().isSetExchangeData())
			getTargetSchemaCode(map, TARGET_SCHEMA_NAME, result);
	}
	
	/**
	 * Given a schema, create a DDL script that generates this schema.
	 * 
	 * @param schema
	 * @return
	 */
	
	public String getSchemaCode (SchemaType schema, MappingScenario map) {
		return getSchemaCode (schema, null, map);
	}
	
	/**
	 * Given a schema, create a DDL script that generates this schema.
	 * 
	 * @param schema
	 * @param schemaName
	 * @return
	 */
	
	public String getSchemaCode (SchemaType schema, String schemaName, MappingScenario map) {
		StringBuffer result = new StringBuffer();
		
		getSchemaCode(schema, schemaName, result, true, map);
		
		return result.toString();
	}
	
	/**
	 * 
	 * Given a schema, create a DDL script that generates this schema.
	 * 
	 * @param schema
	 * @param schemaName
	 * @param result StringBuffer to store the code
	 * @param addForeignKeys Create foreign keys?
	 */
	
	private void getSchemaCode (SchemaType schema, String schemaName, 
			StringBuffer result, boolean addForeignKeys, MappingScenario map) {
		boolean isSource = schemaName != null 
				&& schemaName.equals(SOURCE_SCHEMA_NAME);
		boolean loadTarget = !isSource && isExangeDataAndLoadTargetData(map);

				
		if (schemaName == null) {
			for(RelationType rel: schema.getRelationArray()) {
				String relName = loadTarget ? 
						getExtraTargetDataRelName(rel.getName()) : rel.getName();
				result.append("DROP TABLE IF EXISTS " + rel +" CASCADE;\n");
			}
			result.append('\n');
		}
		else {
			result.append(getCreateSchemaCode(schemaName));
		}
		
		for(RelationType rel: schema.getRelationArray()) {
			getRelationCode(rel, schemaName, result, loadTarget);
			result.append("\n");
		}
		
		if (addForeignKeys && isSource)
			getAllSourceForeignKeysCode(schema, schemaName, result);
		//TODO ok not to create FKs for target?
		if (log.isDebugEnabled()) {log.debug("created DDL script for schema " + schemaName + ":\n" + result.toString());};
	}
	
	/**
	 * Returns code to drop and create a schema named <code>schemaName</code>.
	 * 
	 * @param schemaName
	 * @return DDL code.
	 */
	
	private String getCreateSchemaCode (String schemaName) {
		return QueryHolder.getQuery("Loader.CreateSchema").parameterize(schemaName);
//		return "DROP SCHEMA IF EXISTS " + schemaName + " CASCADE;\n" +
//				"CREATE SCHEMA " + schemaName + ";\n\n";
	}
	
	/**
	 * Returns code to create a relation given as <code>rel</code>.
	 * 
	 * @param rel
	 * @param schemaName Name of the schema the relation is created in.
	 * @param result StringBuffer to hold the code.
	 */
	
	private void getRelationCode (RelationType rel, String schemaName, 
			StringBuffer result, boolean loadTarget) {
		String relName = loadTarget ? 
				getExtraTargetDataRelName(rel.getName()) : rel.getName();
		schemaName = getSchemaString (schemaName);
		
		result.append("CREATE TABLE " + schemaName + relName + "(\n");
		
		result.append("tid INT8 NOT NULL,\n");
		for(AttrDefType attr : rel.getAttrArray()) {
			result.append(attr.getName() + " " + attr.getDataType());
			result.append(attr.getNotNull() == null ? ",\n" : " NOT NULL,\n");
		}
		
		if (rel.isSetPrimaryKey())
			getPrimKey(rel.getPrimaryKey(), result);
		else
			result.delete(result.length() - 2,result.length() - 1);
		result.append(") WITH OIDS;\n");
	}
	
	/**
	 * Returns code to create a primary key constraint given as <code>primKey</code>. 
	 * 
	 * @param primKey
	 * @param result
	 */
	
	private void getPrimKey (AttrListType primKey, StringBuffer result) {
		char delim = ',';
		
		result.append("PRIMARY KEY (");
		for(String attr : primKey.getAttrArray()) {
			result.append(attr + delim);
		}
		result.deleteCharAt(result.length() - 1);
		result.append(")\n");
	}
	
	/**
	 * Returns code to create all foreign key constraints of a schema.
	 * 
	 * @param schema The schema description.
	 * @param schemaName Name of the schema.
	 * @return DDL code as a String.
	 */
	
	public String getAllSourceForeignKeysCode (SchemaType schema, String schemaName) {
		StringBuffer result;
		
		result = new StringBuffer();
		getAllSourceForeignKeysCode(schema, schemaName, result);
		
		return result.toString();
	}
	
	/**
	 * Generate code to create all foreign key constraints of a schema.
	 * 
	 * @param schema The schema description.
	 * @param schemaName Name of the schema.
	 * @param result StringBuffer to hold the code.
	 */
	
	private void getAllSourceForeignKeysCode (SchemaType schema, 
			String schemaName, StringBuffer result) {
		Set<String> fkeyNames = new HashSet<String> ();
		for(ForeignKeyType fkey: schema.getForeignKeyArray()) {
			result.append("\n");
			getForeignKeyCode(fkey, schemaName, result, fkeyNames);
		}
	}
	
	/**
	 * Generate code to create a foreign key constraint.
	 * 
	 * @param fkey Foreign key specification.
	 * @param schemaName Name of the schema.
	 * @param result StringBuffer to hold the code.
	 */
	
	private void getForeignKeyCode (ForeignKeyType fkey, String schemaName, 
			StringBuffer result, Set<String> fkeyNames) {
		char delim = ',';
		
		schemaName = getSchemaString (schemaName);
		
		result.append("ALTER TABLE " + schemaName + fkey.getFrom().getTableref() + 
				" ADD FOREIGN KEY (");
		for(String attr: fkey.getFrom().getAttrArray()) {
			result.append(attr + delim);		
		}
		result.deleteCharAt(result.length() - 1);
		
		result.append(") REFERENCES " + schemaName + fkey.getTo().getTableref() + " (");
		
		for(String attr: fkey.getTo().getAttrArray()) {
			result.append(attr + delim);
		}
		result.deleteCharAt(result.length() - 1);
		result.append(");\n");
		
		// create index
		String fkeyName = fkey.getId();
		if (fkeyName == null) {
			fkeyName = fkey.getFrom().getTableref();
			for(String attr: fkey.getFrom().getAttrArray()) {
				fkeyName += "_" + attr;
			}
		}
		int i = 0;
		String newFkName = fkeyName;
		while(fkeyNames.contains(newFkName))
			newFkName = fkeyName + i++;
		fkeyNames.add(newFkName);
		
		result.append("CREATE INDEX " +  "source_fkey_index_" + newFkName + " ON ");
		result.append(schemaName + fkey.getFrom().getTableref());
		result.append("(");
		for(String attr: fkey.getFrom().getAttrArray()) {
			result.append(attr + delim);
		}
		result.deleteCharAt(result.length() - 1);
		result.append(");\n");
	}
	
	/**
	 * Return code to generate a target schema (the views that implement the transformations).
	 *  
	 * @param scenario Mapping scenario.
	 * @param schemaName Name for the target schema.
	 * @return DDL code as a String.
	 */
	
	public String getTargetSchemaCode (MappingScenario scenario, String schemaName) {
		StringBuffer result = new StringBuffer();
		
		getTargetSchemaCode(scenario, schemaName, result);
		
		return result.toString();
	}
	
	/**
	 * Generates code to generate a target schema (the views that implement the transformations).
	 *  
	 * @param scenario Mapping scenario.
	 * @param schemaName Name for the target schema.
	 * @param result StringBuffer to hold the code.
	 */
	
	private void getTargetSchemaCode (MappingScenario scenario, String schemaName, 
			StringBuffer result) {
		TransformationsType transes;

//		schemaName = getSchemaString(schemaName);
		if (!isExangeDataAndLoadTargetData(scenario)) {
			result.append(getCreateSchemaCode(schemaName));		
		}
		
		transes = scenario.getTransformations();
		
		if (scenario.isSetTransformations())
			for(TransformationType trans: transes.getTransformationArray()) {
				getTransViewCode(trans, schemaName, result, scenario);
			}
	}
	
	/**
	 * Generates code to generate a target schema view from an SQL transformation.
	 * 
	 * @param trans The SQL transformation.
	 * @param schemaName Name of the target schema.
	 * @param result DDL code as a String
	 */
	
	private void getTransViewCode (TransformationType trans, 
			String schemaName, StringBuffer result, MappingScenario scenario) {
		String code;
		boolean union = isExangeDataAndLoadTargetData(scenario);
		
		if (union) {
			String relName = trans.getCreates();
			StringBuffer projection = new StringBuffer();
			
			RelationType rel = getTargetRel(scenario, relName);
			for(AttrDefType attr : rel.getAttrArray()) {
				if(projection.length() > 1)
					projection.append(", ");
				projection.append(attr.getName());
			}
			
			String loadRelName = getExtraTargetDataRelName(relName); 
			code = QueryHolder.getQuery("Loader.CreateViewUnionTable").parameterize(
					schemaName, relName, trans.getCode().trim(), projection.toString(), loadRelName);
		}
		else {
			trans.getCreates();
			code = QueryHolder.getQuery("Loader.CreateView").parameterize(schemaName, 
					trans.getCreates(), trans.getCode().trim());
		}
		result.append(code +"\n\n");
//		result.append("CREATE VIEW " + schemaName + trans.getCreates() + " AS (\n");
//		result.append(trans.getCode().trim());
//		result.append("\n);\n\n");
	}
	
	private RelationType getTargetRel (MappingScenario map, String name) {
		for(RelationType t: map.getSchemas().getTargetSchema().getRelationArray()) {
			if (t.getName().equals(name))
				return t;
		}
		return null;
	}
	
	/**
	 * Given a schema name or <code>null</code>, append a dot to the name or 
	 * return <code>""</code> if the name is <code>null</code>. 
	 * 
	 * @param schemaName The schema name.
	 * @return
	 */
	
	private String getSchemaString (String schemaName) {
		if (schemaName == null)
			return "public.";
		return schemaName + ".";
	}

	/**
	 * Return code to generate the source instance of a mapping scenario.
	 * 
	 * @param map the mapping scenario.
	 * @return DDL code as a String.
	 * @throws Exception 
	 */
	
	public String getInstanceCode (MappingScenario map) throws Exception {
		return getInstanceCode(map, null);
	}
	
	/**
	 * Return code to generate the source instance of a mapping scenario.
	 * 
	 * @param map the mapping scenario.
	 * @param schemaName The name of the source schema.
	 * @return DDL code as a String
	 * @throws Exception 
	 */
	
	public String getInstanceCode (MappingScenario map, String schemaName) throws Exception {
		StringBuffer result = new StringBuffer();
		
		schemaName = getSchemaString(schemaName);
		getInstanceCode(map, schemaName, result);
		
		return result.toString();
	}
	
	/**
	 * Generate code to create the source instance of a mapping scenario.
	 * 
	 * @param map The mapping scenario.
	 * @param schemaName The name of the source schema.	 
	 * @param result StringBuffer to hold the code.
	 * @throws Exception 
	 */
	
	private void getInstanceCode (MappingScenario map, String schemaName, StringBuffer result) throws Exception {
		schemaName = getSchemaString(schemaName);
	
		for (RelInstanceType inst: map.getData().getInstanceArray()) {
			getInserts(schemaName, inst, result, map);
		}
		
		for (RelInstanceFileType inst: map.getData().getInstanceFileArray()) {
			getCopy(schemaName, inst, result, map);
		}
	}
	
	/**
	 * Generate code to insert data into an relation.
	 * 
	 * @param schemaName Name of the schema the relation belongs too.
	 * @param inst Data to load into the relation.
	 * @param result StringBuffer to hold the code.
	 */
	
	private void getInserts (String schemaName, 
			RelInstanceType inst, StringBuffer result, MappingScenario map) {
		String relName = isExangeDataAndLoadTargetData(map) ? getExtraTargetDataRelName(inst.getName()) : inst.getName();
		for(Row row: inst.getRowArray()) {
			getRowInsert (schemaName, relName, row, result);
		}
		result.append("\n");
	}
	
	/**
	 * Is exchange of data and 
	 * 
	 * @param map
	 * @return
	 */
	
	public boolean isExangeDataAndLoadTargetData (MappingScenario map) {
		return map.getData().isSetLoadTargetData() && map.getData().isSetExchangeData();
	}
	
	/**
	 * Generate a single INSERT command.
	 * 
	 * @param schemaName Name of the schema the relation belongs too.
	 * @param relName Name of the relation to insert into.
	 * @param row One row data.
	 * @return Code as a String.
	 */
	
	public String getRowInsert (String schemaName, String relName, Row row) {
		StringBuffer result;
		
//		schemaName = getSchemaString(schemaName);
		result = new StringBuffer();
		getRowInsert(schemaName, relName, row, result);
		
		if (log.isDebugEnabled()) {log.debug("Created INSERT statement:\n" + result);};
		return result.toString();
	}
	
	/**
	 * Generate a single INSERT command.
	 * 
	 * @param schemaName Name of the schema the relation belongs too.
	 * @param relName Name of the relation to insert into.
	 * @param result StringBuffer to hold the code.
	 * @param row One row data.
	 */
	
	private void getRowInsert (String schemaName, String relName, Row row, 
			StringBuffer result) {
		result.append("INSERT INTO " + schemaName + "." + relName + " VALUES (");
		
		for(String val : row.getValueArray()) {
			if(val.equals("NULL"))
				result.append("NULL,");
			else 
				result.append("'" + val + "',");
		}
		result.deleteCharAt(result.length() - 1);
		result.append(");\n");
	}
	
	/**
	 * Generate code to copy a relations data from a csv file.
	 * 
	 * @param schemaName The name of the schema.
	 * @param inst The CSV file to load from.
	 * @return Code as a String.
	 * @throws Exception 
	 */
	
	public String getCopy (String schemaName, RelInstanceFileType inst, File p, MappingScenario map) throws Exception {
		StringBuffer result;
		
		result = new StringBuffer();
		if (p == null)
			getCopy(schemaName, inst, result, map);
		else
			getCopy(schemaName, inst, result, p, map);
		
		if (log.isDebugEnabled()) {log.debug("Created COPY command: " + result.toString());};
		
		return result.toString();
	}
	
	/**
	 * Generate code to copy a relations data from a csv file.
	 * 
	 * @param schemaName The name of the schema.
	 * @param inst The CSV file to load from.
	 * @param result A StringBuffer to hold the code.
	 */
	
	private void getCopy (String schemaName, 
			RelInstanceFileType inst, StringBuffer result, MappingScenario map) throws Exception {
		String delim;
		String path;
		boolean isTarget = inst.isSetTargetRelation();
		String relName = isTarget && isExangeDataAndLoadTargetData(map) ? 
				getExtraTargetDataRelName(inst.getName()) : inst.getName();
		String schema = isTarget ? TARGET_SCHEMA_NAME : SOURCE_SCHEMA_NAME;
		
		delim = inst.getColumnDelim();
		path = getPath(inst);

		result.append(QueryHolder.getQuery("Loader.Copy")
				.parameterize(schema, relName, path, delim));

//		result.append("COPY source."+ inst.getName() + " FROM '" + path + "' " +
//				"WITH CSV DELIMITER '" + delim + "' NULL AS 'NULL';\n");
	}

	private void getCopy (String schemaName, 
			RelInstanceFileType inst, StringBuffer result, File actualPath, MappingScenario map) throws Exception {
		String delim;
		String path;
		boolean isTarget = inst.isSetTargetRelation();
		String relName = isTarget && isExangeDataAndLoadTargetData(map) ? 
				getExtraTargetDataRelName(inst.getName()) : inst.getName();
		String schema = isTarget ? TARGET_SCHEMA_NAME : SOURCE_SCHEMA_NAME;
		
		delim = inst.getColumnDelim();
		path = getPath(inst, actualPath);
		
		result.append(QueryHolder.getQuery("Loader.Copy")
					.parameterize(schema, relName, path, delim));
//		result.append("COPY source."+ relName + " FROM '" + path + "' " +
//				"WITH CSV DELIMITER '" + delim + "' NULL AS 'NULL';\n");
	}
	
	private String getPath(RelInstanceFileType inst, File p) {
		String path;
		
		path = p.getAbsolutePath();
		if (!path.endsWith("/"))
			path += "/";
		path += inst.getFileName();
		
		return path;
	}
	
	private String getPath(RelInstanceFileType inst) throws Exception {
		String path;
		
		path = inst.getPath();
		if (!path.endsWith("/"))
			path += "/";
		path += inst.getFileName();
		
		path = getAbsolutePath(path);
		
		return path;
	}
	
	private String getAbsolutePath (String pathString) throws Exception {
		File path;
		
		path = new File(pathString);
		
		return path.getAbsolutePath();
	}
}

