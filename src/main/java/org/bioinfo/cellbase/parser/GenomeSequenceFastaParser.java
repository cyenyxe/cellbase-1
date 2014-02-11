package org.bioinfo.cellbase.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.bioinfo.cellbase.lib.common.core.Chromosome;
import org.bioinfo.cellbase.lib.common.core.Cytoband;
import org.bioinfo.cellbase.lib.common.core.GenomeSequenceChunk;
import org.bioinfo.cellbase.lib.common.core.InfoStats;

public class GenomeSequenceFastaParser {

	private int chunkSize = 2000;

	Gson gson = new GsonBuilder().create(); // .setPrettyPrinting()

	public GenomeSequenceFastaParser() {

	}

	public void parseFastaGzipFilesToJson(File genomeReferenceFastaDir, File outJsonFile) {
		try {
			StringBuilder sequenceStringBuilder;
			File[] files = genomeReferenceFastaDir.listFiles();
			BufferedWriter bw = Files.newBufferedWriter(Paths.get(outJsonFile.toURI()), Charset.defaultCharset(), StandardOpenOption.CREATE);
			for(File file: files) {
				if(file.getName().endsWith(".fa.gz")) {
					System.out.println(file.getAbsolutePath());

					String chromosome = "";
					String line;
					sequenceStringBuilder = new StringBuilder();
					// Java 7 IO code
					BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
					while ((line = br.readLine()) != null) {
						if (!line.startsWith(">")) {
							sequenceStringBuilder.append(line);
						} else {
							// new chromosome
							// save data
							if (sequenceStringBuilder.length() > 0) {
								System.out.println(chromosome);
								writeGenomeChunks(chromosome, sequenceStringBuilder.toString(), bw);
							}

							// initialize data structures
							chromosome = line.replace(">", "").split(" ")[0];
							sequenceStringBuilder.delete(0, sequenceStringBuilder.length());
						}
					}
					// Last chromosome must be processed
					writeGenomeChunks(chromosome, sequenceStringBuilder.toString(), bw);
					br.close();
				}
			}
			bw.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void parseToJson(File genomeReferenceFastaFile, File outJsonFile) {
		try {
			String chromosome = "";
			String line;
			StringBuilder sequenceStringBuilder = new StringBuilder();
			// Java 7 IO code
			BufferedWriter bw = Files.newBufferedWriter(Paths.get(outJsonFile.toURI()), Charset.defaultCharset(), StandardOpenOption.CREATE);
			BufferedReader br = Files.newBufferedReader(Paths.get(genomeReferenceFastaFile.toURI()), Charset.defaultCharset());
			while ((line = br.readLine()) != null) {
				if (!line.startsWith(">")) {
					sequenceStringBuilder.append(line);
				} else {
					// new chromosome
					// save data
					if (sequenceStringBuilder.length() > 0) {
						System.out.println(chromosome);
						writeGenomeChunks(chromosome, sequenceStringBuilder.toString(), bw);
					}

					// initialize data structures
					chromosome = line.replace(">", "").split(" ")[0];
					sequenceStringBuilder.delete(0, sequenceStringBuilder.length());
				}
			}
			// Last chromosome must be processed
			writeGenomeChunks(chromosome, sequenceStringBuilder.toString(), bw);
			br.close();
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void parseToJsonCclementina(File genomeReferenceFastaFile, File outJsonFile) {
		/*infoStats*/
		List<Chromosome> chromosomes = new ArrayList<Chromosome>();
		InfoStats infoStats = new InfoStats("cclementine", chromosomes);
		/*infoStats*/
		try {
			String chromosome = "";
			String line;
			StringBuilder sequenceStringBuilder = new StringBuilder();
			// Java 7 IO code
			BufferedWriter bw = Files.newBufferedWriter(Paths.get(outJsonFile.toURI()), Charset.defaultCharset(), StandardOpenOption.CREATE);
			BufferedReader br = Files.newBufferedReader(Paths.get(genomeReferenceFastaFile.toURI()), Charset.defaultCharset());
			/*info_stats*/
			BufferedWriter bw_stats = Files.newBufferedWriter(Paths.get(outJsonFile.getParent()).resolve("cclementina_info_stats.json"), Charset.defaultCharset(), StandardOpenOption.CREATE);
			/*info_stats*/
			while ((line = br.readLine()) != null) {
				if (!line.startsWith(">")) {
					sequenceStringBuilder.append(line);
				} else {
					// new chromosome
					// save data
					if (sequenceStringBuilder.length() > 0) {
						writeGenomeChunks(chromosome, sequenceStringBuilder.toString(), bw);

						/*infoStats*/
						int len = sequenceStringBuilder.length();
						Chromosome chromosomeObj = new Chromosome();
						chromosomeObj.setName(chromosome);
						chromosomeObj.setStart(1);
						chromosomeObj.setEnd(len);
						chromosomeObj.setSize(len);
						chromosomeObj.setIsCircular(0);
						chromosomeObj.setNumberGenes(0);
						List<Cytoband> cytobands = new ArrayList<Cytoband>();
						cytobands.add(new Cytoband("", "clementina", 1, len));
						chromosomeObj.setCytobands(cytobands);
						chromosomes.add(chromosomeObj);
						/*infoStats*/
					}

					// initialize data structures
					chromosome = line.replace(">", "").split(" ")[0];
					sequenceStringBuilder.delete(0, sequenceStringBuilder.length());


				}
			}
			// Last chromosome must be processed
			writeGenomeChunks(chromosome, sequenceStringBuilder.toString(), bw);
			br.close();
			bw.close();

			/*info_stats*/
			bw_stats.write(gson.toJson(infoStats));
			bw_stats.flush();
			bw_stats.close();
			/*info_stats*/
		} catch (IOException e) {
			e.printStackTrace();
		}

	}


//	public void parseToJsonWithConservedRegions(File genomeReferenceFastaFile, File outJsonFile, Path phastConsFolderPath, Path phylopConsFolderPath) {
//		try {
//			String chromosome = "";
//			String line;
//			StringBuilder sequenceStringBuilder = new StringBuilder();
//			// Java 7 IO code
//			BufferedWriter bw = Files.newBufferedWriter(Paths.get(outJsonFile.toURI()), Charset.defaultCharset(), StandardOpenOption.CREATE);
//			BufferedReader br = Files.newBufferedReader(Paths.get(genomeReferenceFastaFile.toURI()), Charset.defaultCharset());
//			while ((line = br.readLine()) != null) {
//				if (!line.startsWith(">")) {
//					sequenceStringBuilder.append(line);
//				} else {
//					// new chromosome
//					// save data
//					if (sequenceStringBuilder.length() > 0) {
//						System.out.println(chromosome);
//
//						if(!chromosome.equalsIgnoreCase("mt")){
//							writeGenomeChunksWithConservedRegions(chromosome, sequenceStringBuilder.toString(), bw, phastConsFolderPath, phylopConsFolderPath);
//						}
//					}
//
//					// initialize data structures
//					chromosome = line.replace(">", "").split(" ")[0];
//					sequenceStringBuilder.delete(0, sequenceStringBuilder.length());
//				}
//			}
//			// Last chromosome must be processed
//			if(!chromosome.equalsIgnoreCase("mt")){
//				writeGenomeChunksWithConservedRegions(chromosome, sequenceStringBuilder.toString(), bw, phastConsFolderPath, phylopConsFolderPath);
//			}
//			br.close();
//			bw.close();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//	}


	private void writeGenomeChunks(String chromosome, String sequence, BufferedWriter bw) throws IOException {
		int chunkId = 0;
		int start = 1;
		int end = chunkSize - 1;
		String chunkSequence;

		if (sequence.length() < chunkSize) {//chromosome sequence length can be less than chunkSize
			chunkSequence = sequence;
			GenomeSequenceChunk chunk = new GenomeSequenceChunk(chromosome, 0, start, sequence.length() - 1, chunkSequence);
			bw.write(gson.toJson(chunk) + "\n");
			start += chunkSize - 1;
		} else {
			while (start < sequence.length()) {
				if (chunkId % 10000 == 0) {
					System.out.println("Chr:" + chromosome + " chunkId:" + chunkId);
				}
				// First chunk of the chromosome
				if (start == 1) {
					// First chunk contains chunkSize-1 nucleotides as index start at position 1 but must end at 1999
					chunkSequence = sequence.substring(start - 1, chunkSize - 1);
					GenomeSequenceChunk chunk = new GenomeSequenceChunk(chromosome, chunkId, start, end, chunkSequence);
					bw.write(gson.toJson(chunk) + "\n");
					start += chunkSize - 1;

				} else {
					// Regular chunk
					if ((start + chunkSize) < sequence.length()) {
						chunkSequence = sequence.substring(start - 1, start + chunkSize - 1);
						GenomeSequenceChunk chunk = new GenomeSequenceChunk(chromosome, chunkId, start, end, chunkSequence);
						bw.write(gson.toJson(chunk) + "\n");
						start += chunkSize;

					} else {
						// Last chunk of the chromosome
						chunkSequence = sequence.substring(start - 1, sequence.length());
						GenomeSequenceChunk chunk = new GenomeSequenceChunk(chromosome, chunkId, start, sequence.length(), chunkSequence);
						bw.write(gson.toJson(chunk) + "\n");
						start = sequence.length();
					}
				}
				end = start + chunkSize - 1;
				chunkId++;
			}
		}
	}

//	private void writeGenomeChunksWithConservedRegions(String chromosome, String sequence, BufferedWriter bw, Path phastConsFolderPath, Path phylopConsFolderPath) throws IOException {
//
//		//        conservedRegionsSQLite(phastConsFolderPath, "phastCons", chromosome);
//		//        conservedRegionsSQLite(phylopConsFolderPath, "phylop", chromosome);
//
//		int chunkId = 0;
//		int start = 1;
//		int end = chunkSize - 1;
//		String chunkSequence;
//
//		if (sequence.length() < chunkSize) {//chromosome sequence length can be less than chunkSize
//			chunkSequence = sequence;
//			GenomeSequenceChunk chunk = new GenomeSequenceChunk(chromosome, 0, start, sequence.length() - 1, chunkSequence);
//			bw.write(gson.toJson(chunk) + "\n");
//			start += chunkSize - 1;
//		} else {
//			while (start < sequence.length()) {
//				if (chunkId % 1000 == 0) {
//					System.out.println("Chr:" + chromosome + " chunkId:" + chunkId);
//				}
//				// First chunk of the chromosome
//				if (start == 1) {
//
//					// First chunk contains chunkSize-1 nucleotides as index start at position 1 but must end at 1999
//					chunkSequence = sequence.substring(start - 1, chunkSize - 1);
//					GenomeSequenceChunk chunk = new GenomeSequenceChunk(chromosome, chunkId, start, end, chunkSequence);
//
//					/*conserved region*/
//					Map<Integer, Float> phastConsMap = queryConservedRegions(phastConsFolderPath, "phastCons", chromosome, start - 1, chunkSize - 1);
//					Map<Integer, Float> phylopMap = queryConservedRegions(phylopConsFolderPath, "phylop", chromosome, start - 1, chunkSize - 1);
//					/*
//					Float[] phastConsArray = chunk.getPhastCons();
//					Float[] phylopArray = chunk.getPhylop();
//					for (Map.Entry<Integer, Float> result : phastConsMap.entrySet()) {
//						int indexStart = result.getKey() % phastConsArray.length;
//						phastConsArray[indexStart] = result.getValue();
//					}
//					for (Map.Entry<Integer, Float> result : phylopMap.entrySet()) {
//						int indexStart = result.getKey() % phylopArray.length;
//						phylopArray[indexStart] = result.getValue();
//					}
//					*/
//					/*conserved region*/
//
//					bw.write(gson.toJson(chunk) + "\n");
//					start += chunkSize - 1;
//				} else {
//					// Regular chunk
//					if ((start + chunkSize) < sequence.length()) {
//						chunkSequence = sequence.substring(start - 1, start + chunkSize - 1);
//						GenomeSequenceChunk chunk = new GenomeSequenceChunk(chromosome, chunkId, start, end, chunkSequence);
//
//						/*conserved region*/
//						Map<Integer, Float> phastConsMap = queryConservedRegions(phastConsFolderPath, "phastCons", chromosome, start - 1, start + chunkSize - 1);
//						Map<Integer, Float> phylopMap = queryConservedRegions(phylopConsFolderPath, "phylop", chromosome, start - 1, start + chunkSize - 1);
//						/*
//						Float[] phastConsArray = chunk.getPhastCons();
//						Float[] phylopArray = chunk.getPhylop();
//						for (Map.Entry<Integer, Float> result : phastConsMap.entrySet()) {
//							int indexStart = result.getKey() % phastConsArray.length;
//							phastConsArray[indexStart] = result.getValue();
//						}
//						for (Map.Entry<Integer, Float> result : phylopMap.entrySet()) {
//							int indexStart = result.getKey() % phylopArray.length;
//							phylopArray[indexStart] = result.getValue();
//						}
//						*/
//						/*conserved region*/
//
//						bw.write(gson.toJson(chunk) + "\n");
//						start += chunkSize;
//					} else {
//						// Last chunk of the chromosome
//						chunkSequence = sequence.substring(start - 1, sequence.length());
//						GenomeSequenceChunk chunk = new GenomeSequenceChunk(chromosome, chunkId, start, sequence.length(), chunkSequence);
//
//						/*conserved region*/
//						Map<Integer, Float> phastConsMap = queryConservedRegions(phastConsFolderPath, "phastCons", chromosome, start - 1, sequence.length());
//						Map<Integer, Float> phylopMap = queryConservedRegions(phylopConsFolderPath, "phylop", chromosome, start - 1, sequence.length());
//						/*
//						Float[] phastConsArray = chunk.getPhastCons();
//						Float[] phylopArray = chunk.getPhylop();
//						for (Map.Entry<Integer, Float> result : phastConsMap.entrySet()) {
//							int indexStart = result.getKey() % phastConsArray.length;
//							phastConsArray[indexStart] = result.getValue();
//						}
//						for (Map.Entry<Integer, Float> result : phylopMap.entrySet()) {
//							int indexStart = result.getKey() % phylopArray.length;
//							phylopArray[indexStart] = result.getValue();
//						}
//						*/
//						/*conserved region*/
//
//						bw.write(gson.toJson(chunk) + "\n");
//						start = sequence.length();
//					}
//				}
//				end = start + chunkSize - 1;
//				chunkId++;
//			}
//		}
//	}


//	public void conservedRegionsSQLite(Path conservedRegionFolderPath, String conservedRegion, String chrFile) {
//		try {
//			this.getConservedRegionPath(conservedRegionFolderPath, conservedRegion, chrFile);
//			Path filePath = getConservedRegionPath(conservedRegionFolderPath, conservedRegion, chrFile);
//			Path dbPath = Paths.get(filePath.toString() + ".db");
//			BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(filePath))));
//			Class.forName("org.sqlite.JDBC");
//
//			Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
//			conn.setAutoCommit(false);
//
//			Statement createTables = conn.createStatement();
//			createTables.executeUpdate("CREATE TABLE if not exists conserved_region("
//					+ "position INT ,"
//					+ "value REAL)");
//
//			PreparedStatement ps = conn.prepareStatement("INSERT INTO conserved_region("
//					+ "position,"
//					+ "value)"
//					+ "values (?,?)");
//
//			int LIMITROWS = 100000;
//			int start = 0, offset = 0, step = 1, position, BatchCount = 0;
//			String chromosome = "";
//			float value;
//			Map<String, String> attributes = new HashMap<>();
//
//			String line = null;
//			while ((line = br.readLine()) != null) {
//				if (!line.startsWith("fixedStep")) {
//					value = Float.parseFloat(line.trim());
//					position = start + offset;
//					offset += step;
//
//					ps.setInt(1, position);
//					ps.setFloat(2, value);
//					if (BatchCount % LIMITROWS == 0 && BatchCount != 0) {
//						System.out.println(position);
//						ps.executeBatch();
//						conn.commit();
//					}
//					ps.addBatch();
//					BatchCount++;
//				} else { //process fixedStep line
//					offset = 0;
//					attributes.clear();
//					String[] atrrFields = line.split(" ");
//					String[] attrKeyValue;
//					for (String attrField : atrrFields) {
//						if (!attrField.equalsIgnoreCase("fixedStep")) {
//							attrKeyValue = attrField.split("=");
//							attributes.put(attrKeyValue[0].toLowerCase(), attrKeyValue[1]);
//						}
//					}
//					start = Integer.parseInt(attributes.get("start"));
//					step = Integer.parseInt(attributes.get("step"));
//					chromosome = attributes.get("chrom").replace("chr", "");
//				}
//			}
//			br.close();
//
//			ps.executeBatch();
//			conn.commit();
//
//			System.out.println("creando indices");
//			createTables.executeUpdate("CREATE INDEX conserved_region_idx on conserved_region(position)");
//			System.out.println("indices creados");
//			conn.commit();
//
//			conn.close();
//		} catch (ClassNotFoundException | SQLException | IOException e) {
//			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//		}
//	}

//	public Path getConservedRegionPath(Path conservedRegionFolderPath, String conservedRegion, String chrFile) {
//		String file = "";
//		switch (conservedRegion.toLowerCase()) {
//		case "phastcons":
//			file = "chr" + chrFile + ".phastCons46way.primates.wigFix.gz";
//			break;
//		case "phylop":
//			file = "chr" + chrFile + ".phyloP46way.primate.wigFix.gz";
//			break;
//		}
//		return conservedRegionFolderPath.resolve(file);
//	}

//	public Map<Integer, Float> queryConservedRegions(Path conservedRegionFolderPath, String conservedRegion, String chrFile, int start, int end) {
//		this.getConservedRegionPath(conservedRegionFolderPath, conservedRegion, chrFile);
//		Path filePath = getConservedRegionPath(conservedRegionFolderPath, conservedRegion, chrFile);
//		Path dbPath = Paths.get(filePath.toString() + ".db");
//		Connection conn = null;
//		Map<Integer, Float> results = new HashMap<>();
//		try {
//			Class.forName("org.sqlite.JDBC");
//			conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
//
//			Statement query = conn.createStatement();
//			ResultSet rs = query.executeQuery("select * from conserved_region where position>=" + start + " AND position<=" + end);
//
//			while (rs.next()) {
//				int pos = rs.getInt(1);
//				float value = rs.getFloat(2);
//				results.put(pos, value);
//				//            System.out.println(pos+" - "+value);
//			}
//			conn.close();
//
//		} catch (ClassNotFoundException | SQLException e) {
//			e.printStackTrace();
//		}
//		return results;
//	}
}
