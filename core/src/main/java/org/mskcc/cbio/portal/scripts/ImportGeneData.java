/*
 * Copyright (c) 2015 Memorial Sloan-Kettering Cancer Center.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY OR FITNESS
 * FOR A PARTICULAR PURPOSE. The software and documentation provided hereunder
 * is on an "as is" basis, and Memorial Sloan-Kettering Cancer Center has no
 * obligations to provide maintenance, support, updates, enhancements or
 * modifications. In no event shall Memorial Sloan-Kettering Cancer Center be
 * liable to any party for direct, indirect, special, incidental or
 * consequential damages, including lost profits, arising out of the use of this
 * software and its documentation, even if Memorial Sloan-Kettering Cancer
 * Center has been advised of the possibility of such damage.
 */

/*
 * This file is part of cBioPortal.
 *
 * cBioPortal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.mskcc.cbio.portal.scripts;

import org.mskcc.cbio.portal.dao.*;
import org.mskcc.cbio.portal.model.CanonicalGene;
import org.mskcc.cbio.portal.util.*;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.*;
import java.util.*;

/**
 * Command Line Tool to Import Background Gene Data.
 */
public class ImportGeneData extends ConsoleRunnable {

    /**
     * 
     * @param geneFile
     * @throws IOException
     * @throws DaoException
     */
    public static void importData(File geneFile) throws IOException, DaoException {
        Map<String, Set<CanonicalGene>> genesWithSymbolFromNomenClatureAuthority = new LinkedHashMap<>();
        Map<String, Set<CanonicalGene>> genesWithoutSymbolFromNomenClatureAuthority = new LinkedHashMap<>();
        try (FileReader reader = new FileReader(geneFile)) {
            BufferedReader buf = new BufferedReader(reader);
            String line;
            
            while ((line = buf.readLine()) != null) {
            	ProgressMonitor.incrementCurValue();
            	ConsoleUtil.showProgress();
                if (line.startsWith("#")) {
                    continue;
                }
                
                String parts[] = line.split("\t");
                int taxonimy = Integer.parseInt(parts[0]);
                if (taxonimy!=9606) {
                    // only import human genes
                    continue;
                }
                
                int entrezGeneId = Integer.parseInt(parts[1]);
                String geneSymbol = parts[2];
                String locusTag = parts[3];
                String strAliases = parts[4];
                //String strXrefs = parts[5];
                String cytoband = parts[7];
                //String desc = parts[8];
                String type = parts[9];
                String mainSymbol = parts[10]; // use 10 instead of 2 since column 2 may have duplication
                Set<String> aliases = new HashSet<String>();
                if (!locusTag.equals("-")) {
                    aliases.add(locusTag);
                }
                if (!strAliases.equals("-")) {
                    aliases.addAll(Arrays.asList(strAliases.split("\\|")));
                }
                
                if (geneSymbol.startsWith("MIR") && type.equalsIgnoreCase("miscRNA")) {
                    line = buf.readLine();
                    continue; // ignore miRNA; process separately
                }
                
                CanonicalGene gene = null;
                if (!mainSymbol.equals("-")) {
                    //try the main symbol:
                    gene = new CanonicalGene(entrezGeneId, mainSymbol, aliases);
                    Set<CanonicalGene> genes = genesWithSymbolFromNomenClatureAuthority.get(mainSymbol);
                    if (genes==null) {
                        genes = new HashSet<CanonicalGene>();
                        genesWithSymbolFromNomenClatureAuthority.put(mainSymbol, genes);
                    }
                    genes.add(gene);
                } else if (!geneSymbol.equals("-")) {
                    //there is no main symbol, so import using the temporary/unnoficial(?) symbol:
                    gene = new CanonicalGene(entrezGeneId, geneSymbol, aliases);
                    Set<CanonicalGene> genes = genesWithoutSymbolFromNomenClatureAuthority.get(geneSymbol);
                    if (genes==null) {
                        genes = new HashSet<CanonicalGene>();
                        genesWithoutSymbolFromNomenClatureAuthority.put(geneSymbol, genes);
                    }
                    genes.add(gene);
                }
                
                if (gene!=null) {
                    if (!cytoband.equals("-")) {
                        gene.setCytoband(cytoband);
                    }
                    gene.setType(type);
                }
            }
            addGenesToDB(genesWithSymbolFromNomenClatureAuthority, genesWithoutSymbolFromNomenClatureAuthority);
        }
    }
         
    
    /**
     * Iterate over the genes found in the given maps and try to add them to the DB. 
     * 
     * @param genesWithSymbolFromNomenClatureAuthority: genes with official hugo symbol
     * @param genesWithoutSymbolFromNomenClatureAuthority: genes without official hugo symbol (can happen, some entrez genes 
     * 				have no hugo symbol yet, but a temporary symbol)
     * 
     * @throws DaoException
     */
    private static void addGenesToDB(Map<String, Set<CanonicalGene>> genesWithSymbolFromNomenClatureAuthority,
		Map<String, Set<CanonicalGene>> genesWithoutSymbolFromNomenClatureAuthority) throws DaoException {

    	DaoGeneOptimized daoGene = DaoGeneOptimized.getInstance();
		int nrExisting = 0;
        // Add genes with symbol from nomenclature authority
        for (Map.Entry<String, Set<CanonicalGene>> entry : genesWithSymbolFromNomenClatureAuthority.entrySet()) {
            Set<CanonicalGene> genes = entry.getValue();
            if (genes.size()==1) {
                CanonicalGene gene = genes.iterator().next();
                // first check if gene exists. If exists, give warning and skip record: 
                if (daoGene.getGene(gene.getEntrezGeneId()) != null) {
                	ProgressMonitor.logWarning("Gene is already in table. Updates are not allowed. If you need to update the `gene` table, please remove all studies first and empty the `gene` table.");
                	nrExisting++;
                	continue;
                }
                daoGene.addGene(gene);
                ProgressMonitor.logWarning("New gene with official symbol added");
            }
            else {
            	//TODO - is unexpected for official symbols...raise Exception instead?
                logDuplicateGeneSymbolWarning(entry.getKey(), genes, true);
            }
        }

        // Add genes without symbol from nomenclature authority
        if (genesWithoutSymbolFromNomenClatureAuthority.keySet().size() > 0) {
        	int nrImported = 0;
        	int nrSkipped = 0;
	        for (Map.Entry<String, Set<CanonicalGene>> entry : genesWithoutSymbolFromNomenClatureAuthority.entrySet()) {
	            Set<CanonicalGene> genes = entry.getValue();
	            String symbol = entry.getKey();
	            if (genes.size()==1) {
	                CanonicalGene gene = genes.iterator().next();
	                //only add the gene if it does not conflict with an official symbol:
	                if (!genesWithSymbolFromNomenClatureAuthority.containsKey(symbol)) {
	                    // first check if gene exists. If exists, give warning and skip record since we don't allow updates in this process: 
	                    if (daoGene.getGene(gene.getEntrezGeneId()) != null) {
	                    	ProgressMonitor.logWarning("Gene is already in table. Updates are not allowed. If you need to update the `gene` table, please remove all studies first and empty the `gene` table.");
	                    	nrSkipped++;
	                    	nrExisting++;
	                    	continue;
	                    }
	                    daoGene.addGene(gene);
	                    ProgressMonitor.logWarning("New gene with *no* official symbol added");
	                    nrImported++;
	                } else {
	                    // ignore entries with a symbol that have the same value as stardard one
	                    ProgressMonitor.logWarning("Ignored line with entrez gene id "+gene.getEntrezGeneId() + " because its 'unnoficial' symbol " +
	                            symbol+" is already an 'official symbol' of another gene");
	                    nrSkipped++;
	                    continue;
	                }
	            } else {
	                logDuplicateGeneSymbolWarning(symbol, genes, false);
	                nrSkipped++;
	                continue;
	            }
	        }
	        ProgressMonitor.logWarning("There were " +genesWithoutSymbolFromNomenClatureAuthority.keySet().size() + 
	        		" genes names in this file without an official symbol from nomenclature authority. Imported: " + nrImported + 
	        		". Gene names skipped (because of duplicate symbol entry or because symbol is an 'official symbol' of another gene): " + nrSkipped);
        }
        if (nrExisting > 0) {
        	ProgressMonitor.logWarning("Number of records skipped because the gene was already in the gene table (updates are not allowed - see specific warnings above): " + nrExisting);
        }
       
    }

    private static void logDuplicateGeneSymbolWarning(String symbol, Set<CanonicalGene> genes, boolean isOfficialSymbol) {
        StringBuilder sb = new StringBuilder();
        if (isOfficialSymbol)
            sb.append("More than 1 gene has the same (official) symbol ");
        else
            sb.append("More than 1 gene has the same (unofficial) symbol ");
            
        sb.append(symbol)
                .append(":");
        for (CanonicalGene gene : genes) {
            sb.append(" ")
                    .append(gene.getEntrezGeneId())
                    .append(". Ignore...");
        }
        ProgressMonitor.logWarning(sb.toString());
    }

    /**
     * This method imports the gene lengths of the file stated. This file must be an "exon-loci" file (bed file).
     * 
     * @param geneFile
     * @throws IOException
     * @throws DaoException
     */
    public static void importGeneLength(File geneFile) throws IOException, DaoException {
    	//Set the variables needed for the method
        DaoGeneOptimized daoGeneOptimized = DaoGeneOptimized.getInstance();
        FileReader reader = new FileReader(geneFile);
        BufferedReader buf = new BufferedReader(reader);
        String line;
        Set<String> genesNotFound = new HashSet<String>();
        ProgressMonitor.setCurrentMessage("\n\nUpdating gene lengths: \n\n"); //Display a message in the console
        String geneEnsembl = "";
    	String exonEnsembl = "";
    	String symbol = null;
    	String parts[] = null;
    	List<String> savedEnsembl = new ArrayList<String>();
        List<long[]> loci = new ArrayList<long[]>();
        int nrGenesUpdated = 0;
        CanonicalGene currentGene = null;
        
        //Iterate over the file and fill the hash map with the max and min values of each gene (start and end position)
        while ((line=buf.readLine()) != null) {
        	if(line.charAt(0) == '#'){
        		continue;
        	}
        	parts = line.split("\t");
        	if (parts[2].contains("exon") || parts[2].contains("CDS")) {
	        	String info[] = parts[8].split(";");

	        	//Retrieve the ensembl ID
	        	for (String i : info) {
	        		if (i.contains("gene_id")) {
	        			String j[] = i.split(" ");
	        			exonEnsembl = j[1].replaceAll("\"", "");
	        		}
	        		else if (i.contains("gene_name")) {
	        			String j[] = i.split(" ");
	        			symbol = j[2].replaceAll("\"", ""); 
	        		}
	        	}
                if (!geneEnsembl.equals(exonEnsembl)) { /// Check if there is switch from Ensembl ID 
                    if (!geneEnsembl.equals("")) { /// Only in case of the first line, do not write anything, and go to end to make ensemble and add loci 
                    	
                    	/// Switched to new ensembl id, so calculate gene length

                        
                        /// Calc length
                        int length = calculateGeneLength(loci);
                        
                        /// Obtain cytoband
                    	String cytoband = currentGene.getCytoband();
                    	
                    	/// If there is no cytoband in the database, just write it (can also be an overwrite)
                    	if (cytoband == null) {
                    		currentGene.setLength(length);
                    		System.out.print(length);
                    		daoGeneOptimized.updateGene(currentGene);
                    		nrGenesUpdated++;
                    		//ProgressMonitor.logWarning(symbol+" has no cytoband information, length saved.");
                    		
                    		if (savedEnsembl.contains(geneEnsembl)) {
                        		ProgressMonitor.logWarning(geneEnsembl + "already is double in inputfile");	
                    		} else {
                        		savedEnsembl.add(geneEnsembl);
                    		}
                    	}
                    	
                    	/// If there is a cytoband in database, check if cytoband-chr matches input-chr
                    	else {
                        	String chromosome = "chr"+cytoband.split("p|q")[0];
                        	if (chromosome.equals(parts[0])) {
                        		currentGene.setLength(length);
                        		System.out.print(length);
                        		daoGeneOptimized.updateGene(currentGene);
                        		nrGenesUpdated++;
                        		//ProgressMonitor.logWarning(symbol+" had already a length or there are multiple genes with the same Entrez ID on the same chromosome. In that case, only the last length will be stored.");
                        		
                        		if (savedEnsembl.contains(geneEnsembl)) {
                            		ProgressMonitor.logWarning(geneEnsembl + "already is double in inputfile");	
                        		} else {
                            		savedEnsembl.add(geneEnsembl);
                        		}
                        	}
                        	/// If it doesn't match, don't write it
                        	else {
                        		//System.err.println(symbol+" is found on multiple chromosomes, saving the length of the Entrez ID chromosome");
                        	}	
                    	}

                        
                    }
                    /// At the end of writing a new gene, clear the loci and save the new ensembl.
                    loci.clear();
                    geneEnsembl = exonEnsembl;
                    
                    /// Check if the current gene is actually in DB
    	            currentGene = daoGeneOptimized.getNonAmbiguousGene(symbol, parts[0], false); //Identify unambiguously the gene (with the symbol and the chromosome)
                    if (currentGene==null) {
                    	genesNotFound.add(symbol);
    	                continue;
                    }
                }
                
                /// At the end of every line, add the loci
                loci.add(new long[]{Long.parseLong(parts[3]), Long.parseLong(parts[4])}); //Add the new positions
            }
        }
        
        /// Write the last gene
        /// First check if the gene exists in the database
        currentGene = daoGeneOptimized.getNonAmbiguousGene(symbol, parts[0], false); //Identify unambiguously the gene (with the symbol and the chromosome)
        if (currentGene==null) {
        	genesNotFound.add(symbol);
        }
        /// If it does exist, write it
        else {
            int length = calculateGeneLength(loci);
            currentGene.setLength(length);
    		System.out.print(length);
            daoGeneOptimized.updateGene(currentGene);
            nrGenesUpdated++;
            
    		if (savedEnsembl.contains(geneEnsembl)) {
        		ProgressMonitor.logWarning(geneEnsembl + "already is double in inputfile");	
    		} else {
        		savedEnsembl.add(geneEnsembl);
    		}	
        }
        

		
	    
    	//Report non ambiguous genes
    	if (genesNotFound.size() > 0) {
    		String genesString = genesNotFound.toString();
    		// limit size:
    		if (genesString.length() > 100)
    			genesString = genesString.substring(0, 100) + "...";
            ProgressMonitor.logWarning("Genes not found, or symbol found to be ambiguous ("+ genesNotFound.size() +" genes in total): " + genesString);
    	}
        ProgressMonitor.setCurrentMessage("\n\nUpdated length info for " + nrGenesUpdated + " genes\n");
    }
    

    /**
     * This method uses a list of exon loci from the same gene and it adds the length of all of them to get the gene length. If some of the exons are
     * overlapping, the overlapping part is only counted once in the calculation. For example, if an exon goes from position 3 to 10 and another one from 
     * position 5 to 11, when calculating the length these exons would be considered as a single exon going from position 3 to 11.
     * 
     * @param loci
     * @return
     */
    public static int calculateGeneLength(List<long[]> loci) {
        long min = Long.MAX_VALUE, max=-1;
        for (long[] l : loci) {
            if (l[0]<min) {
                min = l[0];
            }
            if (l[1]>max) {
                max = l[1];
            }
        }
        if (max < min)
        	throw new IllegalArgumentException("Found error: max=" + max + ", min=" + min);
        BitSet bitSet = new BitSet((int)(max-min));
        for (long[] l : loci) {
            bitSet.set((int)(l[0]-min), ((int)(l[1]-min)));
        }
        
        return bitSet.cardinality();
    }
    
    static void importSuppGeneData(File suppGeneFile) throws IOException, DaoException {
        MySQLbulkLoader.bulkLoadOff();
        FileReader reader = new FileReader(suppGeneFile);
        BufferedReader buf = new BufferedReader(reader);
        String line;
        DaoGeneOptimized daoGene = DaoGeneOptimized.getInstance();
        while ((line = buf.readLine()) != null) {
            ProgressMonitor.incrementCurValue();
            ConsoleUtil.showProgress();
            if (!line.startsWith("#")) {
                String parts[] = line.split("\t");
                CanonicalGene gene = new CanonicalGene(parts[0]);
                if (!parts[1].isEmpty()) {
                    gene.setType(parts[1]);
                }
                if (!parts[2].isEmpty()) {
                    gene.setCytoband(parts[2]);
                }
                if (!parts[3].isEmpty()) {
                    gene.setLength(Integer.parseInt(parts[3]));
                }
                daoGene.addGene(gene);
            }
        }
        reader.close(); 
    }

	@Override
    public void run() {
		try {
			SpringUtil.initDataSource();
	
	        String description = "Update gene / gene alias tables ";
	    	
	        // using a real options parser, helps avoid bugs
	 		OptionParser parser = new OptionParser();
	 		OptionSpec<Void> help = parser.accepts( "help", "print this help info" );
	 		parser.accepts( "genes", "ncbi genes file" ).withRequiredArg().describedAs( "ncbi_genes.txt" ).ofType( String.class );
	 		parser.accepts( "supp-genes", "alternative genes file" ).withRequiredArg().describedAs( "supp-genes.txt" ).ofType( String.class );
	 		parser.accepts( "microrna", "microrna file" ).withRequiredArg().describedAs( "microrna.txt" ).ofType( String.class );
	 		parser.accepts( "gtf", "gtf file for calculating and storing gene lengths" ).withRequiredArg().describedAs( "gencode.<version>.annotation.gtf" ).ofType( String.class );
	
	 		String progName = "importGenes";
	 		OptionSet options = null;
			try {
				options = parser.parse( args );
			} catch (OptionException e) {
				throw new UsageException(progName, description, parser,
				        e.getMessage());
			}
			  
			if( options.has( help ) ){
				throw new UsageException(progName, description, parser);
			}
			
	        ProgressMonitor.setConsoleMode(true);
	        
	        File geneFile;
	        int numLines;
			if(options.has("genes")) {
				geneFile = new File((String) options.valueOf("genes"));
			
		        System.out.println("Reading gene data from:  " + geneFile.getAbsolutePath());
		        numLines = FileUtil.getNumLines(geneFile);
		        System.out.println(" --> total number of lines:  " + numLines);
		        ProgressMonitor.setMaxValue(numLines);
		        MySQLbulkLoader.bulkLoadOn();
		        ImportGeneData.importData(geneFile);
		        MySQLbulkLoader.flushAll(); //Gene and gene_alias should be updated before calculating gene length (gtf)!
			}
	        
	        if(options.has("supp-genes")) {
	            File suppGeneFile = new File((String) options.valueOf("genes"));
	            System.out.println("Reading supp. gene data from:  " + suppGeneFile.getAbsolutePath());
	            numLines = FileUtil.getNumLines(suppGeneFile);
	            System.out.println(" --> total number of lines:  " + numLines);
	            ProgressMonitor.setMaxValue(numLines);
	            ImportGeneData.importSuppGeneData(suppGeneFile);
	        }
	        
	        if(options.has("microrna")) {
	            File miRNAFile = new File((String) options.valueOf("microrna"));
	            System.out.println("Reading miRNA data from:  " + miRNAFile.getAbsolutePath());
	            numLines = FileUtil.getNumLines(miRNAFile);
	            System.out.println(" --> total number of lines:  " + numLines);
	            ProgressMonitor.setMaxValue(numLines);
	            ImportMicroRNAIDs.importData(miRNAFile);
	        }
	        
	        if(options.has("gtf")) {
	            File lociFile = new File((String) options.valueOf("gtf"));
	            System.out.println("Reading loci data from:  " + lociFile.getAbsolutePath());
	            numLines = FileUtil.getNumLines(lociFile);
	            System.out.println(" --> total number of lines:  " + numLines);
	            ProgressMonitor.setMaxValue(numLines);
	            ImportGeneData.importGeneLength(lociFile);
	        }
	        MySQLbulkLoader.flushAll();
            System.err.println("Done. Restart tomcat to make sure the cache is replaced with the new data.");

		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
		   throw new RuntimeException(e);
		}
    }

    /**
     * Makes an instance to run with the given command line arguments.
     *
     * @param args  the command line arguments to be used
     */
    public ImportGeneData(String[] args) {
        super(args);
    }

    /**
     * Runs the command as a script and exits with an appropriate exit code.
     *
     * @param args  the arguments given on the command line
     */
    public static void main(String[] args) {
        ConsoleRunnable runner = new ImportGeneData(args);
        runner.runInConsole();
    }

}
