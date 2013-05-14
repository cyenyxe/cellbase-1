package org.bioinfo.cellbase.parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.bioinfo.cellbase.lib.common.GenericFeature;
import org.bioinfo.cellbase.lib.common.GenericFeatureChunk;

import com.google.gson.Gson;

/**
 * User: fsalavert
 * Date: 4/10/13
 * Time: 10:14 AM
 */

public class RegulatoryParser {

	static int CHUNKSIZE = 2000;

	private static Gson gson = new Gson();

	public static void createSQLiteRegulatoryFiles(Path regulatoryRegionPath) throws SQLException, IOException, ClassNotFoundException, NoSuchMethodException {
		List<String> GFFColumnNames = Arrays.asList("seqname", "source", "feature", "start", "end", "score", "strand", "frame", "group");
		List<String> GFFColumnTypes = Arrays.asList("TEXT", "TEXT", "TEXT", "INT", "INT", "TEXT", "TEXT", "TEXT", "TEXT");

		//        Path regulatoryRegionPath = regulationDir.toPath();

		Path filePath;

		filePath = regulatoryRegionPath.resolve("AnnotatedFeatures.gff.gz");
		RegulatoryParser.createSQLiteRegulatoryFiles(filePath, "annotated_features", GFFColumnNames, GFFColumnTypes, true);

		
		filePath = regulatoryRegionPath.resolve("MotifFeatures.gff.gz");
		RegulatoryParser.createSQLiteRegulatoryFiles(filePath, "motif_features", GFFColumnNames, GFFColumnTypes, true);

		
		filePath = regulatoryRegionPath.resolve("RegulatoryFeatures_MultiCell.gff.gz");
		RegulatoryParser.createSQLiteRegulatoryFiles(filePath, "regulatory_features_multicell", GFFColumnNames, GFFColumnTypes, true);


//		GFFColumnNames = Arrays.asList("seqname", "source", "feature", "start", "end", "score", "strand", "frame");
//		GFFColumnTypes = Arrays.asList("TEXT", "TEXT", "TEXT", "INT", "INT", "TEXT", "TEXT", "TEXT");
		filePath = regulatoryRegionPath.resolve("mirna_uniq.gff.gz");
		RegulatoryParser.createSQLiteRegulatoryFiles(filePath, "mirna_uniq", GFFColumnNames, GFFColumnTypes, true);

	}

	public static void parseRegulatoryGzipFilesToJson(Path regulatoryRegionPath, int chunksize, Path outputRegulatoryRegionJsonPath) throws SQLException, IOException, ClassNotFoundException, NoSuchMethodException {
		// Create the SQLite databases
		createSQLiteRegulatoryFiles(regulatoryRegionPath);

		Path annotatedFilePath = regulatoryRegionPath.resolve("AnnotatedFeatures.gff.gz.db");
		Path motifFilePath = regulatoryRegionPath.resolve("MotifFeatures.gff.gz.db");
		Path regulatoryFilePath = regulatoryRegionPath.resolve("RegulatoryFeatures_MultiCell.gff.gz.db");
		Path mirnaFilePath = regulatoryRegionPath.resolve("mirna_uniq.gff.gz.db");

		List<Path> filePaths = Arrays.asList(annotatedFilePath, motifFilePath, regulatoryFilePath, mirnaFilePath);
		List<String> tableNames = Arrays.asList("annotated_features", "motif_features", "regulatory_features_multicell", "mirna_uniq");
		
		// Ouput JSON file
		//		Path outJsonPath = regulatoryRegionPath.resolve("regulatory_region.json");
		if(Files.exists(outputRegulatoryRegionJsonPath)) {
			Files.delete(outputRegulatoryRegionJsonPath);
		}
		BufferedWriter bw = Files.newBufferedWriter(outputRegulatoryRegionJsonPath, Charset.defaultCharset(), StandardOpenOption.CREATE);

		// Fetching and joining all chromosomes dounf in the different databases
		Set<String> setChr = new HashSet<String>();
		setChr.addAll(RegulatoryParser.getChromosomesList(annotatedFilePath, "annotated_features"));
		setChr.addAll(RegulatoryParser.getChromosomesList(motifFilePath, "motif_features"));
		setChr.addAll(RegulatoryParser.getChromosomesList(regulatoryFilePath, "regulatory_features_multicell"));
		setChr.addAll(RegulatoryParser.getChromosomesList(mirnaFilePath, "mirna_uniq"));
		List<String> chromosomes = new ArrayList<>();
		chromosomes.addAll(setChr);

		//		Collections.sort(chromosomes, new Comparator<String>() {
		//			@Override
		//			public int compare(String o1, String o2) {
		//				if (o1.equals("X")) o1 = "23";
		//				if (o2.equals("X")) o2 = "23";
		//				if (o1.equals("Y")) o1 = "24";
		//				if (o2.equals("Y")) o2 = "24";
		//				return Integer.parseInt(o1) - Integer.parseInt(o2);
		//			}
		//		});

//		List<GenericFeature> annotatedGenericFeatures = new ArrayList<>();
		List<GenericFeature> genericFeatures = new ArrayList<>();
//		List<GenericFeature> regulatoryGenericFeatures = new ArrayList<>();
//		List<GenericFeature> mirnaGenericFeatures = new ArrayList<>();

		Map<Integer, GenericFeatureChunk> genericFeatureChunks = null;
		for (String chromosome : chromosomes) {

			for (int i=0; i<tableNames.size(); i++) {
				genericFeatureChunks = new HashMap<>();
				genericFeatures = RegulatoryParser.queryChromosomesRegulatoryDB(filePaths.get(i), tableNames.get(i), chromosome);
				int c = 0;
				for(GenericFeature genericFeature: genericFeatures) {
					int firstChunkId =  getChunkId(genericFeature.getStart(), chunksize);
					int lastChunkId  = getChunkId(genericFeature.getEnd(), chunksize);

					// remove 'chr' prefix
					if(genericFeature.getChromosome() != null) {
						genericFeature.setChromosome(genericFeature.getChromosome().replace("chr", ""));						
					}else {
						System.out.println((++c)+" => "+genericFeature.getChromosome()+":"+genericFeature.getStart()+"   "+genericFeature.getId()+" => "+chromosome+" "+genericFeatures.size()+"  "+tableNames.get(i));
					}
					for(int j=firstChunkId; j<=lastChunkId; j++) {
						if(genericFeatureChunks.get(j)==null) {
							int chunkStart = getChunkStart(j, chunksize);
							int chunkEnd = getChunkEnd(j, chunksize);
							genericFeatureChunks.put(j, new GenericFeatureChunk(chromosome, j, chunkStart, chunkEnd, new ArrayList<GenericFeature>()));
						}
						genericFeatureChunks.get(j).getFeatures().add(genericFeature);
					}
				}
				for (Map.Entry<Integer, GenericFeatureChunk> result : genericFeatureChunks.entrySet()) {
					bw.write(gson.toJson(result.getValue()) + "\n");
				}
				
			}

			/*Annotated feature*/
//			genericFeatureChunks = new HashMap<>();
//			annotatedGenericFeatures = RegulatoryParser.queryChromosomesRegulatoryDB(annotatedFilePath, "annotated_features", chromosome);
//			for(GenericFeature genericFeature :annotatedGenericFeatures){
//				int firstChunkId =  getChunkId(genericFeature.getStart(), chunksize);
//				int lastChunkId  = getChunkId(genericFeature.getEnd(), chunksize);
//
//				for(int i=firstChunkId; i<=lastChunkId; i++){
//					if(genericFeatureChunks.get(i)==null){
//						int chunkStart = getChunkStart(i, chunksize);
//						int chunkEnd = getChunkEnd(i, chunksize);
//						genericFeatureChunks.put(i,new GenericFeatureChunk(chromosome,i,chunkStart,chunkEnd,new ArrayList<GenericFeature>()));
//					}
//					genericFeatureChunks.get(i).getFeatures().add(genericFeature);
//				}
//			}
//			for (Map.Entry<Integer, GenericFeatureChunk> result : genericFeatureChunks.entrySet()) {
//				bw.write(gson.toJson(gson.toJson(result.getValue())) + "\n");
//			}
//			/*********/
//
//			/*Regulatory feature*/
//			genericFeatureChunks = new HashMap<>();
//			regulatoryGenericFeatures = RegulatoryParser.queryChromosomesRegulatoryDB(regulatoryFilePath, "regulatory_features_multicell", chromosome);
//			for(GenericFeature genericFeature :regulatoryGenericFeatures){
//				int firstChunkId =  getChunkId(genericFeature.getStart());
//				int lastChunkId  = getChunkId(genericFeature.getEnd());
//
//				for(int i=firstChunkId; i<=lastChunkId; i++){
//					if(genericFeatureChunks.get(i)==null){
//						int chunkStart = getChunkStart(i);
//						int chunkEnd = getChunkEnd(i);
//						genericFeatureChunks.put(i,new GenericFeatureChunk(chromosome,i,chunkStart,chunkEnd,new ArrayList<GenericFeature>()));
//					}
//					genericFeatureChunks.get(i).getFeatures().add(genericFeature);
//				}
//			}
//			for (Map.Entry<Integer, GenericFeatureChunk> result : genericFeatureChunks.entrySet()) {
//				bw.write(gson.toJson(gson.toJson(result.getValue())) + "\n");
//			}
//			/*********/
//
//			/*Mirna feature*/
//			genericFeatureChunks = new HashMap<>();
//			mirnaGenericFeatures = RegulatoryParser.queryChromosomesRegulatoryDB(mirnaFilePath, "mirna_uniq", chromosome);
//			for(GenericFeature genericFeature :mirnaGenericFeatures){
//				int firstChunkId =  getChunkId(genericFeature.getStart());
//				int lastChunkId  = getChunkId(genericFeature.getEnd());
//
//				for(int i=firstChunkId; i<=lastChunkId; i++){
//					if(genericFeatureChunks.get(i)==null){
//						int chunkStart = getChunkStart(i);
//						int chunkEnd = getChunkEnd(i);
//						genericFeatureChunks.put(i,new GenericFeatureChunk(chromosome,i,chunkStart,chunkEnd,new ArrayList<GenericFeature>()));
//					}
//					genericFeatureChunks.get(i).getFeatures().add(genericFeature);
//				}
//			}
//			for (Map.Entry<Integer, GenericFeatureChunk> result : genericFeatureChunks.entrySet()) {
//				bw.write(gson.toJson(gson.toJson(result.getValue())) + "\n");
//			}
			/*********/

		}
		bw.close();

	}


	public static void createSQLiteRegulatoryFiles(Path filePath, String tableName, List<String> columnNames, List<String> columnTypes, boolean gzip) throws ClassNotFoundException, IOException, SQLException {
		int LIMITROWS = 100000;
		int BatchCount = 0;

		Path dbPath = Paths.get(filePath.toString() + ".db");
		if(Files.exists(dbPath)) {
			Files.delete(dbPath);
		}
		
		BufferedReader br;
		if (gzip) {
			br = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(filePath))));
		} else {
			br = Files.newBufferedReader(filePath, Charset.defaultCharset());
		}

		Class.forName("org.sqlite.JDBC");
		Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
		conn.setAutoCommit(false);//Set false to perform commits manually and increase performance on insertion

		//Create table query
		Statement createTables = conn.createStatement();

		StringBuilder sbQuery = new StringBuilder();
		sbQuery.append("CREATE TABLE if not exists " + tableName + "(");
		for (int i = 0; i < columnNames.size(); i++) {	//columnNames and columnTypes must have the same size
			sbQuery.append("'" + columnNames.get(i) + "' " + columnTypes.get(i) + ",");
		}
		sbQuery.deleteCharAt(sbQuery.length() - 1);
		sbQuery.append(")");

		System.out.println(sbQuery.toString());
		createTables.executeUpdate(sbQuery.toString());

		//Prepare insert query
		sbQuery = new StringBuilder();
		sbQuery.append("INSERT INTO " + tableName + "(");
		for (int i = 0; i < columnNames.size(); i++) {
			sbQuery.append("'" + columnNames.get(i) + "',");
		}
		sbQuery.deleteCharAt(sbQuery.length() - 1);
		sbQuery.append(") values (");
		sbQuery.append(repeat("?,", columnNames.size()));
		sbQuery.deleteCharAt(sbQuery.length() - 1);
		sbQuery.append(")");
		System.out.println(sbQuery.toString());

		PreparedStatement ps = conn.prepareStatement(sbQuery.toString());

		//Read file
		String line = null;
		while ((line = br.readLine()) != null) {

			insertByType(ps, getFields(line, tableName), columnTypes);
			ps.addBatch();
			BatchCount++;

			//commit batch
			if (BatchCount % LIMITROWS == 0 && BatchCount != 0) {
				ps.executeBatch();
				conn.commit();
			}
			
		}
		br.close();

		//Execute last Batch
		ps.executeBatch();
		conn.commit();

		//Create index
		System.out.println("creating indices...");
		createTables.executeUpdate("CREATE INDEX "+tableName+"_seqname_idx on "+tableName+"("+columnNames.get(0)+")");
		System.out.println("indices created.");

		conn.commit();
		conn.close();
	}

	public static List<String> getChromosomesList(Path dbPath, String tableName){
		List<String> chromosomes = new ArrayList<>();
		Connection conn = null;
		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());

			Statement query = conn.createStatement();
			ResultSet rs = query.executeQuery("select distinct(seqname) from "+tableName+" where seqname like 'chr%'");

			while (rs.next()) {
				chromosomes.add(rs.getString(1).replace("chr",""));
			}
			conn.close();

		} catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
		}
		return chromosomes;
	}

	public static List<GenericFeature> queryChromosomesRegulatoryDB(Path dbPath, String tableName, String chromosome) {
		Connection conn = null;
		List<GenericFeature> genericFeatures = new ArrayList<>();
		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());

			Statement query = conn.createStatement();
			ResultSet rs = query.executeQuery("select * from " + tableName + " where seqname='chr"+chromosome+"'");
			while (rs.next()) {
				genericFeatures.add(getGenericFeature(rs, tableName));
			}
			conn.close();

		} catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
		}
		return genericFeatures;
	}

	public static List<GenericFeature> queryRegulatoryDB(Path dbPath, String tableName, String chrFile, int start, int end) {
		Connection conn = null;
		List<GenericFeature> genericFeatures = new ArrayList<>();
		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());

			Statement query = conn.createStatement();
			ResultSet rs = query.executeQuery("select * from " + tableName + " where start<=" + end + " AND end>=" + start);

			while (rs.next()) {
				genericFeatures.add(getGenericFeature(rs, tableName));
			}
			conn.close();

		} catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
		}
		return genericFeatures;
	}

	private static GenericFeature getGenericFeature(ResultSet rs, String tableName) throws SQLException {
		GenericFeature genericFeature = null;
		switch (tableName.toLowerCase()) {
		case "annotated_features":
			genericFeature = getAnnotatedFeature(rs);
			break;
		case "regulatory_features_multicell":
			genericFeature = getRegulatoryFeature(rs);
			break;
		case "motif_features":
			genericFeature = getMotiFeature(rs);
			break;
		case "mirna_uniq":
			genericFeature = getMirnaFeature(rs);
			break;
		}
		return genericFeature;
	}

	private static GenericFeature getAnnotatedFeature(ResultSet rs)throws SQLException  {
		//   GFF     https://genome.ucsc.edu/FAQ/FAQformat.html#format3
		GenericFeature genericFeature = new GenericFeature();
		Map<String, String> groupFields = getGroupFields(rs.getString(9));

		genericFeature.setChromosome(rs.getString(1));
		genericFeature.setSource(rs.getString(2));
		genericFeature.setFeatureType(rs.getString(3));
		genericFeature.setStart(rs.getInt(4));
		genericFeature.setEnd(rs.getInt(5));
		genericFeature.setScore(rs.getString(6));
		genericFeature.setStrand(rs.getString(7));
		genericFeature.setFrame(rs.getString(8));

		genericFeature.setName(groupFields.get("name"));
		genericFeature.setAlias(groupFields.get("alias"));
		genericFeature.setClassStr(groupFields.get("class"));
		genericFeature.getCellTypes().add(groupFields.get("cell_type"));

		return genericFeature;
	}

	private static GenericFeature getRegulatoryFeature(ResultSet rs)throws SQLException  {
		//   GFF     https://genome.ucsc.edu/FAQ/FAQformat.html#format3
		GenericFeature genericFeature = new GenericFeature();
		Map<String, String> groupFields = getGroupFields(rs.getString(9));
		
		genericFeature.setChromosome(rs.getString(1));
		genericFeature.setSource(rs.getString(2));
		genericFeature.setFeatureType(rs.getString(3));
		genericFeature.setStart(rs.getInt(4));
		genericFeature.setEnd(rs.getInt(5));
		genericFeature.setScore(rs.getString(6));
		genericFeature.setStrand(rs.getString(7));
		genericFeature.setFrame(rs.getString(8));
		genericFeature.setFrame(rs.getString(9));
		
		return genericFeature;
	}

	private static GenericFeature getMotiFeature(ResultSet rs) throws SQLException {
		//   GFF     https://genome.ucsc.edu/FAQ/FAQformat.html#format3
		GenericFeature genericFeature = new GenericFeature();
		Map<String, String> groupFields = getGroupFields(rs.getString(9));

		genericFeature.setChromosome(rs.getString(1));
		genericFeature.setSource(rs.getString(2));
		genericFeature.setFeatureType(rs.getString(3)+"_motif");
		genericFeature.setStart(rs.getInt(4));
		genericFeature.setEnd(rs.getInt(5));
		genericFeature.setScore(rs.getString(6));
		genericFeature.setStrand(rs.getString(7));
		genericFeature.setFrame(rs.getString(8));

        String[] split = groupFields.get("name").split(":");
		genericFeature.setName(split[0]);
		genericFeature.setMatrix(split[1]);

		return genericFeature;
	}

	private static GenericFeature getMirnaFeature(ResultSet rs) throws SQLException {
		//   GFF     https://genome.ucsc.edu/FAQ/FAQformat.html#format3
		GenericFeature genericFeature = new GenericFeature();
        Map<String, String> groupFields = getGroupFields(rs.getString(9));

		genericFeature.setChromosome(rs.getString(1));
		genericFeature.setSource(rs.getString(2));
		genericFeature.setFeatureType(rs.getString(3));
		genericFeature.setStart(rs.getInt(4));
		genericFeature.setEnd(rs.getInt(5));
		genericFeature.setScore(rs.getString(6));
		genericFeature.setStrand(rs.getString(7));
		genericFeature.setFrame(rs.getString(8));

        genericFeature.setName(groupFields.get("name"));

		return genericFeature;
	}

	private static Map<String, String> getGroupFields (String group) {
		//process group column
		Map<String, String> groupFields = new HashMap<>();
		String[] attributeFields = group.split(";");
		String[] attributeKeyValue;
		for (String attributeField : attributeFields) {
			attributeKeyValue = attributeField.trim().split("=");
			groupFields.put(attributeKeyValue[0].toLowerCase(), attributeKeyValue[1]);
		}
		return groupFields;
	}


	public static List<String> getFields(String line, String tableName) {
		List<String> fields = new ArrayList<>();
		switch (tableName.toLowerCase()) {
		case "annotated_features":
			fields = getAnnotatedFeaturesFields(line);
			break;
		case "regulatory_features_multicell":
			fields = getRegulatoryFeaturesFields(line);
			break;
		case "motif_features":
			fields = getMotiFeaturesFields(line);
			break;
		case "mirna_uniq":
			fields = getMirnaFeaturesFields(line);
			break;
		}
		return fields;
	}

	public static List<String> getAnnotatedFeaturesFields(String line) {
		String[] fields = line.split("\t");
		return Arrays.asList(fields);
	}

	public static List<String> getRegulatoryFeaturesFields(String line) {
		String[] fields = line.split("\t");
		return Arrays.asList(fields);
	}

	public static List<String> getMotiFeaturesFields(String line) {
		String[] fields = line.split("\t");
		return Arrays.asList(fields);
	}

	public static List<String> getMirnaFeaturesFields(String line) {
		String[] fields = line.split("\t");
		return Arrays.asList(fields);
	}

	public static void insertByType(PreparedStatement ps, List<String> fields, List<String> types) throws SQLException {
		//Datatypes In SQLite Version 3 -> http://www.sqlite.org/datatype3.html
		String raw;
		String type;
		if (types.size() == fields.size()) {
			for (int i = 0; i < fields.size(); i++) {//columnNames and columnTypes must have same size
				int sqliteIndex = i+1;
				raw = fields.get(i);
				type = types.get(i);

				switch (type) {
				case "INTEGER":
				case "INT":
					ps.setInt(sqliteIndex, Integer.parseInt(raw));
					break;
				case "REAL":
					ps.setFloat(sqliteIndex, Float.parseFloat(raw));
					break;
				case "TEXT":
					ps.setString(sqliteIndex, raw);
					break;
				default:
					ps.setString(sqliteIndex, raw);
					break;
				}
			}
		}

	}

	public static String repeat(String s, int n) {
		if (s == null) {
			return null;
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			sb.append(s);
		}
		return sb.toString();
	}

	private static int getChunkId(int position, int chunksize){
		if(chunksize <= 0) {
			return position/CHUNKSIZE;    		
		}else {
			return position/chunksize;
		}
	}
	private static int getChunkStart(int id, int chunksize){
		if(chunksize <= 0) {
			return (id == 0) ? 1 : id*CHUNKSIZE;
		}else {
			return (id==0) ? 1 : id*chunksize;
		}
	}
	private static int getChunkEnd(int id, int chunksize) {
		if(chunksize <= 0) {
			return (id * CHUNKSIZE) + CHUNKSIZE - 1;    		
		}else {
			return (id*chunksize)+chunksize-1;
		}
	}
}
