#!/usr/bin/env perl

use strict;
use Getopt::Long;
use Data::Dumper;

use JSON;

use DB_CONFIG;


my $species = 'Homo sapiens';
my $chrom = '1';
my $transcript_file = 'Homo sapiens';
my $outdir = "/tmp/$species";
my $verbose = '0';
my $help = '0';

####################################################################
## Parsing command line options	####################################
####################################################################
# USAGE: ./variation.pl --species "Homo sapiens" --outdir ../../appl_db/ird_v1/hsa ...

## Parsing command line
GetOptions ('species=s' => \$species, 'chrom|chromosome=s' => \$chrom, 'o|outdir=s' => \$outdir, 'trans-file|transcript-file=s' => \$transcript_file,
			'ensembl-libs=s' => \$ENSEMBL_LIBS, 'ensembl-registry=s' => \$ENSEMBL_REGISTRY,
			'ensembl-host=s' => \$ENSEMBL_HOST, 'ensembl-port=s' => \$ENSEMBL_PORT,
			'ensembl-user=s' => \$ENSEMBL_USER, 'ensembl-pass=s' => \$ENSEMBL_PASS,
			'v|verbose' => \$verbose, 'h|help' => \$help);

## Checking help parameter
print_usage() if $help;

## Printing parameters
print_parameters() if $verbose;

## Checking outdir parameter exist, otherwise create it
if(-d $outdir){
	print "Writing files to directory '$outdir'...\n" if $verbose;
}else{
	print "Directory '$outdir' does not exist, creating directory...\n" if $verbose;
   	mkdir $outdir or die "Couldn't create dir: [$outdir] ($!)";
}

####################################################################
## Ensembl APIs	####################################################
####################################################################
## loading ensembl libraries
use lib "$ENSEMBL_LIBS/ensembl/modules";
use lib "$ENSEMBL_LIBS/ensembl-variation/modules";
use lib "$ENSEMBL_LIBS/ensembl-compara/modules";
use lib "$ENSEMBL_LIBS/ensembl-functgenomics/modules";
use lib "$ENSEMBL_LIBS/bioperl-live";

## creating ensembl adaptors
use Bio::EnsEMBL::DBSQL::DBAdaptor;
use Bio::EnsEMBL::Variation::DBSQL::DBAdaptor;
use Bio::EnsEMBL::Funcgen::DBSQL::DBAdaptor;
use Bio::EnsEMBL::Compara::DBSQL::DBAdaptor;
use Bio::EnsEMBL::Variation::VariationFeatureOverlap;
use Bio::EnsEMBL::Variation::Utils::Constants qw(%OVERLAP_CONSEQUENCES);

## loading the registry with the adaptors 
Bio::EnsEMBL::Registry->load_all("$ENSEMBL_REGISTRY");
####################################################################

#my $conseq_type_id = 0;
#my %conseq_dicc = ();
## Reading and loading the transcript.txt file into a hash of PK
my %ids_to_pk = {};#file_to_hash($transcript_file, 2, 0);
#print "Lines read: ".keys(%ids_to_pk)."\n" if $verbose;

my $slice_adaptor = Bio::EnsEMBL::Registry->get_adaptor($species, "core", "Slice");
my $gene_adaptor = Bio::EnsEMBL::Registry->get_adaptor($species, "core", "Gene");
my $transc_variation_adaptor = Bio::EnsEMBL::Registry->get_adaptor($species, "variation","TranscriptVariation");


##################################################################
## get all chrom indexes to calculate de PK offset	##############
##################################################################
my @all_chroms = @{$slice_adaptor->fetch_all('chromosome')};
my @chrom_ids = ();
foreach my $chr(@all_chroms) {
	push(@chrom_ids, $chr->seq_region_name);
}
my %chrom_indexes = %{get_sorted_chromosomes_indexes(\@chrom_ids)};
##################################################################


##################################################################
## selecting chromosomes	######################################
##################################################################
my @chroms;
if($chrom eq 'all') {
#	my @chroms_unsorted = @{$slice_adaptor->fetch_all('chromosome')};
	my @chrom_ids_sorted = @{sort_chromosomes(\@chrom_ids)};
	print ">>>>".@chrom_ids_sorted."\n";
	foreach my $chrom_id(@chrom_ids_sorted) {
		push(@chroms, $slice_adaptor->fetch_by_region( 'chromosome', $chrom_id));
	}
	#print ">>>>".@chroms."\n";
	#print to_json(\@chroms. "\n");
	
print Dumper \@chroms if $verbose;
}else {
	my @chroms_ids = split(",", $chrom);
	foreach my $chroms_id(@chroms_ids) {
		push(@chroms, $slice_adaptor->fetch_by_region( 'chromosome', $chroms_id));
	}
}
##################################################################


my @snps = ();
my @structural_variants = ();
#my @result = ();
my $snp_cont = 0;
my $snp_phen_annot_cont = 0;
my $snp_xref_cont = 0;
my $snp_pop_gen_cont = 0;
my $snp_to_trans_cont = 0;
my $snp_to_trans_cont_consq_type_cont = 0;
my $struct_variant_cont = 0;

my %consequence_type_ids = ();
my ($source_version, $cdna_start, $cdna_end, $translation_start, $translation_end, $cds_start, $cds_end, $pep_allele_string);
my ($reference_codon, $codon, $allele_pep_allele_string, $polyphen_pred, $polyphen_score, $sift_pred, $sift_score);
my ($variation, @trans_snps, @var_annots, @all_syns, @syn_sources, @pop_genotypes, %snp_freqs, @trans_var_alleles);
my ($snp_slice_left, $snp_slice_right, $trans_stable_id, $trans);

##################### DECLARE VARIATION HASH ####################

my %jsonStructural = ();

my %jsonConsequence = ();

my %jsonVariation = ();

my %jsonTranscript = ();

my %jsonphenannot = ();

my %jsonxrefs = ();

my %jsonpopulation = ();

my %hstudy =(); 

my %hVariation = ();

######## END DECLARE VARIATION HASH #############################


## constants
my $slice_start = 1;
my $slice_max_length = 200000;
my $slice_end = $slice_start + $slice_max_length - 1;
my $chrom_snp_size_offset = 20000000;


## Opening files for writing
open (SNP,">$outdir/snp.txt") || die "Cannot open $outdir file";
open (SNP2TRANS,">$outdir/snp_to_transcript.txt") || die "Cannot open $outdir file";
open (SNP2TRANS_CONSEQ_TYPE,">$outdir/snp_to_transcript_consequence_type.txt") || die "Cannot open $outdir file";
open (SNP_POP_FREQ,">$outdir/snp_population_frequency.txt") || die "Cannot open $outdir file";
open (SNP_PHEN_ANNOT,">$outdir/snp_phenotype_annotation.txt") || die "Cannot open $outdir file";
open (SNP_XREF,">$outdir/snp_xref.txt") || die "Cannot open $outdir file";
open (STRUCT_VAR,">$outdir/structural_variation.txt") || die "Cannot open $outdir file";
## my JSON file
open (CONSEQUENCE,">$outdir/consequence.json") || die "Cannot open $outdir file";
open (STRUCTURAL,">$outdir/structural.json") || die "Cannot open $outdir file";
open (VARIATION,">$outdir/variation.json") || die "Cannot open $outdir file";

## calculate CONSEQUENCE_TYPE
open (CONSEQ_TYPE,">$outdir/consequence_type.txt") || die "Cannot open $outdir file";
my $consq_type_id = 0;
foreach my $key(keys(%OVERLAP_CONSEQUENCES)) {
	$consq_type_id++;
	$consequence_type_ids{$OVERLAP_CONSEQUENCES{$key}->SO_term} = $consq_type_id;
	## saving data into file
	print CONSEQ_TYPE $consq_type_id."\t".$OVERLAP_CONSEQUENCES{$key}->SO_accession."\t".$OVERLAP_CONSEQUENCES{$key}->SO_term."\t".$OVERLAP_CONSEQUENCES{$key}->feature_SO_term."\t";
	print CONSEQ_TYPE $OVERLAP_CONSEQUENCES{$key}->display_term."\t".$OVERLAP_CONSEQUENCES{$key}->rank."\t";
	print CONSEQ_TYPE $OVERLAP_CONSEQUENCES{$key}->NCBI_term."\t".$OVERLAP_CONSEQUENCES{$key}->label."\t".$OVERLAP_CONSEQUENCES{$key}->description."\n";
	
}
close(CONSEQ_TYPE);
print Dumper \%consequence_type_ids if $verbose;


foreach my $chrom_obj(@chroms) {
	$snp_cont = $chrom_snp_size_offset * $chrom_indexes{$chrom_obj->seq_region_name};
	$snp_phen_annot_cont = $chrom_snp_size_offset * $chrom_indexes{$chrom_obj->seq_region_name};
	$snp_xref_cont = $chrom_snp_size_offset * $chrom_indexes{$chrom_obj->seq_region_name};
	$snp_pop_gen_cont = $chrom_snp_size_offset * $chrom_indexes{$chrom_obj->seq_region_name};
	$snp_to_trans_cont = $chrom_snp_size_offset * $chrom_indexes{$chrom_obj->seq_region_name};
	$snp_to_trans_cont_consq_type_cont = $chrom_snp_size_offset * $chrom_indexes{$chrom_obj->seq_region_name};
	$struct_variant_cont = $chrom_snp_size_offset * $chrom_indexes{$chrom_obj->seq_region_name};
	
	print "snp_cont: $snp_cont, chromosome:  ".$chrom_obj->seq_region_name.", end: ".$chrom_obj->end."\n" if $verbose;
	

	
	$slice_start = 1;
	$slice_end = $slice_start + $slice_max_length - 1;
	while($slice_start < $chrom_obj->end) {
		
		print "slice start ---> ". $slice_start . "\n";
		
		## get just until the end ofchromosome
		if($slice_end > $chrom_obj->end) {
			$slice_end = $chrom_obj->end;
		}

		print "slice_region_name: ".$chrom_obj->seq_region_name.", slice_start: $slice_start, slice_end: slice_end: $slice_end\n";
		$chrom = $slice_adaptor->fetch_by_region('chromosome', $chrom_obj->seq_region_name, $slice_start, $slice_end);
		
		###################################################
		## FETCHING STRUCTURAL VARIANTS FROM SLICE	#######
		###################################################
		if($species eq 'Homo sapiens' || $species eq 'Mus musculus') {
			@structural_variants = @{$chrom->get_all_StructuralVariationFeatures()};
			if(@structural_variants > 0){
				foreach my $struc_variation_feature(@structural_variants) {
					$struct_variant_cont++;
					
					#print STRUCT_VAR $struct_variant_cont."\t".
					#$struc_variation_feature->display_id."\t".
					#$struc_variation_feature->seq_region_name."\t".
					#$struc_variation_feature->seq_region_start."\t".
					#$struc_variation_feature->seq_region_end."\t".
					#$struc_variation_feature->strand."\t";
					
					$jsonStructural{'struct_variant_cont'} = $struct_variant_cont;
					$jsonStructural{'id'} = $struc_variation_feature->display_id;
					$jsonStructural{'chromosome'} = $struc_variation_feature->seq_region_name;
					$jsonStructural{'start'} = $struc_variation_feature->seq_region_start;
					$jsonStructural{'end'} = $struc_variation_feature->seq_region_end;
					$jsonStructural{'strand'} = $struc_variation_feature->strand;
					
					#print STRUCT_VAR $struc_variation_feature->class_SO_term."\t".
					#$struc_variation_feature->structural_variation->study->name."\t".
					#$struc_variation_feature->structural_variation->study->url."\t".
					#$struc_variation_feature->structural_variation->study->description."\t";
					
					$jsonStructural{'study'}->{'name'} = $struc_variation_feature->structural_variation->study->name;
					$jsonStructural{'study'}->{'url'} = $struc_variation_feature->structural_variation->study->url;
					$jsonStructural{'study'}->{'description'} = $struc_variation_feature->structural_variation->study->description;
					
					#push(%hVariationFeature,\%hstudy);

					#print STRUCT_VAR $struc_variation_feature->structural_variation->source."\t".
					#$struc_variation_feature->structural_variation->source_description."\n";
					
					$jsonStructural{'source'} = $struc_variation_feature->structural_variation->source;
					$jsonStructural{'sourceDescription'} = $struc_variation_feature->structural_variation->source_description;
					
					$jsonStructural{'displaySoConsequence'} = $struc_variation_feature->class_SO_term;
					
					## this field allow us to discriminate between SNV and Structrural variants
					$jsonStructural{'variationType'} = "structural";
				}
			}	
		}

		###################################################
		## FETCHING SNPs FROM SLICE	#######################
		###################################################
		@snps = @{$chrom->get_all_VariationFeatures()};
		print "$chrom->seq_region_name ---> ".$chrom->seq_region_name."\n";
		print @snps."\n";
		
		
		foreach my $variation_feature(@snps) {

			%jsonVariation = (); print "Limpiando estructura \n";

			$snp_cont++;
			$variation = $variation_feature->variation();
		
		
			##### SNP	####################################################
			## fetching contigous sequences
			## añadir ancestral_allele!!
			$snp_slice_left = $slice_adaptor->fetch_by_region('chromosome',$chrom->seq_region_name,$variation_feature->seq_region_start-22,$variation_feature->seq_region_start-1,$variation_feature->strand);
			$snp_slice_right = $slice_adaptor->fetch_by_region('chromosome',$chrom->seq_region_name,$variation_feature->seq_region_end+1,$variation_feature->seq_region_end+22,$variation_feature->strand);
			$source_version = $variation_feature->source;
			if(defined $variation_feature->source_version && $variation_feature->source_version ne '') {
				$source_version = $source_version."-".$variation_feature->source_version;
			}
			
			#print SNP $snp_cont."\t".
			#$variation_feature->variation_name()."\t".
			#$chrom->seq_region_name."\t".
			#$variation_feature->seq_region_start."\t".
			#$variation_feature->seq_region_end."\t".
			#$variation_feature->strand."\t".
			#$variation_feature->map_weight."\t".
			#$variation_feature->allele_string."\t".
			#$variation->ancestral_allele."\t".
			#$source_version."\t".
			#$variation_feature->display_consequence('SO')."\t".
			#join(",",@{$variation_feature->consequence_type('SO')})."\t".
			#$variation_feature->display_consequence()."\t".
			#$snp_slice_left->seq."[".$variation_feature->allele_string."]".$snp_slice_right->seq."\n";
			
			
			$jsonVariation{'variationType'} = "variation";
			$jsonVariation{'id'} = $variation_feature->variation_name();
			$jsonVariation{'chromosome'} = $chrom->seq_region_name;
			$jsonVariation{'start'} = $variation_feature->seq_region_start;
			$jsonVariation{'end'} = $variation_feature->seq_region_end;
			$jsonVariation{'strand'} = $variation_feature->strand;
			$jsonVariation{'mapWeight'} = $variation_feature->map_weight;
			$jsonVariation{'alleleString'} = $variation_feature->allele_string;
			$jsonVariation{'ancestralAllele'} = $variation->ancestral_allele;
			$jsonVariation{'source'} = $source_version;
			$jsonVariation{'displaySoConsequenceType'} = $variation_feature->display_consequence('SO');
			$jsonVariation{'soConsequenceType'} = join(",",@{$variation_feature->consequence_type('SO')});
			$jsonVariation{'displayEnsemblConsequenceType'} = $variation_feature->display_consequence();
			$jsonVariation{'sequence'} = $snp_slice_left->seq."[".$variation_feature->allele_string."]".$snp_slice_right->seq;
			
			##### SNP phenotype annotation	####################################################
			@var_annots = @{$variation->get_all_VariationAnnotations()};
			if(@var_annots > 0) {
				
				print "\n"."SNP phenotype annotation is TRUE"."\n";
				
				my @snp_phenotype_array;
				foreach my $var_annot(@var_annots) {
					my $assoc_gene = $var_annot->associated_gene();
					$assoc_gene =~ s/ //g;
					my @assoc_genes = split(",", $assoc_gene);
					
					foreach $assoc_gene(@assoc_genes) {
						$snp_phen_annot_cont++;
						#print SNP_PHEN_ANNOT "$snp_phen_annot_cont\t$snp_cont\t".
						#$var_annot->source_name()."\t".$assoc_gene."\t".
						#$var_annot->associated_variant_risk_allele()."\t".
						#$var_annot->risk_allele_freq_in_controls()."\t".
						#$var_annot->p_value()."\t".
						#$var_annot->phenotype_name()."\t".
						#$var_annot->phenotype_description()."\t".
						#$var_annot->study_name()."\t".
						#$var_annot->study_type()."\t".
						#$var_annot->study_url()."\t".
						#$var_annot->study_description()."\n";
						
						$jsonphenannot{'source'} = $var_annot->source_name();
						$jsonphenannot{'associatedVariantRiskAllele'} = $var_annot->associated_variant_risk_allele();
						$jsonphenannot{'riskAlleleFreqInControls'} = $var_annot->risk_allele_freq_in_controls();
						$jsonphenannot{'pValue'} = $var_annot->p_value();
						$jsonphenannot{'name'} = $var_annot->phenotype_name();
						$jsonphenannot{'description'} = $var_annot->phenotype_description();
						$jsonphenannot{'study'}->{'name'} = $var_annot->study_name();
						$jsonphenannot{'study'}->{'type'} = $var_annot->study_type();
						$jsonphenannot{'study'}->{'url'} = $var_annot->study_url();
						$jsonphenannot{'study'}->{'description'} = $var_annot->study_description();
						
						push(@snp_phenotype_array,\%jsonphenannot);									
					}
					
				}
				$jsonVariation{'phenotype'} = \@snp_phenotype_array;
			} else {
				
				print "\n"."SNP phenotype annotation is FALSE"."\n";
			
			}
			
		
			##### XRefs (Synonyms)	####################################################
			@syn_sources = @{$variation->get_all_synonym_sources()};
			if(@syn_sources > 0) {
				
				print "\n"."XRefs (Synonyms) is TRUE"."\n";
				
				my @snp_xref_array;
				foreach my $syn_source(@syn_sources) {
					@all_syns = @{$variation->get_all_synonyms($syn_source)};
					foreach my $syn(@all_syns) {
						$snp_xref_cont++;
						#print SNP_XREF "$snp_xref_cont\t$snp_cont\t$syn\t$syn_source\n";
						
						$jsonxrefs{'id'} = $syn;
						$jsonxrefs{'source'} = $syn_source;
						
						push(@snp_xref_array,\%jsonxrefs);   
					}
				}
				$jsonVariation{'xrefs'} = \@snp_xref_array;
			} else {

				print "\n"."XRefs (Synonyms) is FALSE"."\n";

			}
		
		
			##### Population	####################################################
			%snp_freqs = {};
			@pop_genotypes = @{$variation->get_all_PopulationGenotypes()};
			if(@pop_genotypes > 0) {
				
				print "\n"."Population is TRUE"."\n";
				
				my ($all1, $all2) = split("/", $variation_feature->allele_string);
				
				## getting alleles frequencies
				my @alleles = @{$variation->get_all_Alleles()};
				for my $allele(@alleles) {
					if(defined $allele->population && defined $allele->population->name()) {
						$snp_freqs{$allele->population()->name()}{$allele->allele()} = $allele->frequency();
					}
				}
				
				## getting genotypes frequencies
				foreach my $pop_genotype(@pop_genotypes) {
					if(defined $pop_genotype->population && defined $pop_genotype->population->name()) {
						if($pop_genotype->allele1 eq $all1) {
							$snp_freqs{$pop_genotype->population()->name()}{$pop_genotype->allele1."/".$pop_genotype->allele2} = $pop_genotype->frequency();
						}else {
							$snp_freqs{$pop_genotype->population()->name()}{$pop_genotype->allele2."/".$pop_genotype->allele1} = $pop_genotype->frequency();
						}
					}
				}
	
				## printing frequencies
				foreach my $pop(keys(%snp_freqs)) {
					if(defined $pop) {
						$snp_freqs{$pop}{$all2} = 0 if ($snp_freqs{$pop}{$all2} eq '');
						$snp_freqs{$pop}{$all1} = 0 if ($snp_freqs{$pop}{$all1} eq '');
						$snp_freqs{$pop}{$all1."/".$all1} = 0 if ($snp_freqs{$pop}{$all1."/".$all1} eq '');
						$snp_freqs{$pop}{$all1."/".$all2} = 0 if ($snp_freqs{$pop}{$all1."/".$all2} eq '');
						$snp_freqs{$pop}{$all2."/".$all2} = 0 if ($snp_freqs{$pop}{$all2."/".$all2} eq '');
						if($snp_freqs{$pop}{$all1} != 0 || $snp_freqs{$pop}{$all2} != 0) {
							my ($pop_source, $pop_code) = split(":", $pop);
							$pop_code =~ s/HapMap-//i;
							$snp_pop_gen_cont++;
							
							#print SNP_POP_FREQ "$snp_pop_gen_cont\t$snp_cont\t$pop_code\t$pop_source\t";
							
							#print SNP_POP_FREQ 
							#"$all1\t".$snp_freqs{$pop}{$all1}.
							#"\t$all2\t".$snp_freqs{$pop}{$all2}.
							#"\t$all1/$all1\t".$snp_freqs{$pop}{$all1."/".$all1}.
							#"\t$all1/$all2\t".$snp_freqs{$pop}{$all1."/".$all2}.
							#"\t$all2/$all2\t".$snp_freqs{$pop}{$all2."/".$all2}."\n";
							
							$jsonVariation{'population'}->{'pop_code'} = $pop_code;
							$jsonVariation{'population'}->{'pop_source'} = $pop_source;
							$jsonVariation{'population'}->{$all1} = $snp_freqs{$pop}{$all1};
							$jsonVariation{'population'}->{$all2} = $snp_freqs{$pop}{$all2};
							$jsonVariation{'population'}->{$all1.'/'.$all1} = $snp_freqs{$pop}{$all1."/".$all1};
							$jsonVariation{'population'}->{$all1.'/'.$all2} = $snp_freqs{$pop}{$all1."/".$all2};
							$jsonVariation{'population'}->{$all2.'/'.$all2} = $snp_freqs{$pop}{$all2."/".$all2};
							
						}	
					}
				}
			} else {
				
				print "\n"."Population is FALSE"."\n";
				
			}
		
		
			##### TranscriptVariations	####################################################
			@trans_snps = @{$variation_feature->get_all_TranscriptVariations()};
			foreach my $trans_snp(@trans_snps) {
				my @snp_consequence_array;
				$snp_to_trans_cont++;
				$trans_stable_id = $trans_snp->transcript()->stable_id();
				$trans = $trans_snp->transcript();
					
				$cdna_start = $trans_snp->cdna_start() || -1;
				$cdna_end = $trans_snp->cdna_end() || -1;
				$translation_start = $trans_snp->translation_start() || -1;
				$translation_end = $trans_snp->translation_end() || -1;
				$cds_start = $trans_snp->cds_start() || -1;
				$cds_end = $trans_snp->cds_end() || -1;
				$pep_allele_string = $trans_snp->pep_allele_string() || "-";
		
 				
 				$reference_codon = "";
				$codon = "";
 				$allele_pep_allele_string = "";
 				$polyphen_pred = "";
 				$polyphen_score = "";
 				$sift_pred = "";
 				$sift_score = "";
 				if($species eq 'Homo sapiens') {
 					@trans_var_alleles = @{$trans_snp->get_all_TranscriptVariationAlleles()};
 					if(@trans_var_alleles > 0) {
 						foreach my $trans_anp_allele(@trans_var_alleles) {
# 							$reference_codon = $trans_anp_allele->transcript_variation->get_reference_TranscriptVariationAllele->codon;
							if($trans_anp_allele->polyphen_prediction ne '' || $trans_anp_allele->sift_prediction ne '') {
								$reference_codon = $trans_anp_allele->transcript_variation->get_reference_TranscriptVariationAllele->codon;
								$codon = $trans_anp_allele->codon;
								$allele_pep_allele_string = $trans_anp_allele->pep_allele_string;
 								$polyphen_pred = $trans_anp_allele->polyphen_prediction;
 								$polyphen_score = $trans_anp_allele->polyphen_score;
 								$sift_pred = $trans_anp_allele->sift_prediction;
 								$sift_score = $trans_anp_allele->sift_score;
# 								print $variation_feature->variation_name()."\t$trans_stable_id\t".$reference_codon."\t".$pep_allele_string."\t".$codon."\t".$allele_pep_allele_string."\t".$polyphen_pred."\t".$polyphen_score."\t".$sift_pred."\t".$sift_score."\n";
							}
						}
 					}	
 				}

				#print SNP2TRANS $snp_to_trans_cont."\t".$snp_cont."\t".$ids_to_pk{$trans_stable_id}."\t".$consequence_type_ids{$trans_snp->most_severe_OverlapConsequence()->SO_term}."\t".$cdna_start."\t".$cdna_end."\t".$translation_start."\t".$translation_end."\t".$cds_start."\t".$cds_end."\t".
				
				print 
				"snp_to_trans_cont: ".$snp_to_trans_cont."\n".
				"trans_stable_id: ". $trans_stable_id."\n".
				"snp_cont: ".$snp_cont."\n".
				"ids_to_pk{trans_stable_id}".$ids_to_pk{$trans_stable_id}."\n".
				"consequence_type_ids{trans_snp->most_severe_OverlapConsequence()->SO_term}".$consequence_type_ids{$trans_snp->most_severe_OverlapConsequence()->SO_term}."\n".
				"cdna_start".$cdna_start."\n".
				"cdna_end".$cdna_end."\n".
				"translation_start".$translation_start."\n".
				"translation_end".$translation_end."\n".
				"cds_start".$cds_start."\n".
				"cds_end".$cds_end."\n".
				
				$jsonTranscript{'id'} = $trans_stable_id;
				
				
				
				$pep_allele_string."\t".$reference_codon."\t".$codon."\t".$allele_pep_allele_string."\t".$polyphen_pred."\t".$polyphen_score."\t".$sift_pred."\t".$sift_score."\n";
				foreach(@{$trans_snp->consequence_type('SO')}) {
#					print SNP2TRANS $snp_cont."\t".$ids_to_pk{$trans_stable_id}."\t".$consequence_type_ids{$_}."\t".$cdna_start."\t".$cdna_end."\t".$translation_start."\t".$translation_end."\t".$cds_start."\t".$cds_end."\t".
#					$pep_allele_string."\t".$reference_codon."\t".$codon."\t".$allele_pep_allele_string."\t".$polyphen_pred."\t".$polyphen_score."\t".$sift_pred."\t".$sift_score."\n";
					$snp_to_trans_cont_consq_type_cont++;
					#print SNP2TRANS_CONSEQ_TYPE $snp_to_trans_cont_consq_type_cont."\t".$snp_to_trans_cont."\t".$consequence_type_ids{$_}."\n";
					
					print "************************************************************\n".
					"snp_to_trans_cont_consq_type_cont: ".$snp_to_trans_cont_consq_type_cont."\n".
					"snp_to_trans_cont: ".$snp_to_trans_cont."\n".
					"consequence_type_ids{$_}: ".$consequence_type_ids{$_}."\n".
					"************************************************************\n";
					
				}
			}
			
			my $json = encode_json \%jsonVariation;
			my $json2 = encode_json \%jsonStructural;
			print "---VARIATION---> \n";
			print $json."\n";
			print "---STRUCTURAL---> \n";
			print $json2."\n";
			
			# borro la direccion de mem. El array de ensembl se acumula y revienta la ram
			undef (@trans_snps);
			undef (@trans_var_alleles);
			undef (@var_annots);
			undef (@all_syns);
			undef (@syn_sources);
			undef (@pop_genotypes);
			undef (@trans_var_alleles);
		}
		# tambien para corregir el leak de memoria
		undef (@snps);
		undef (@structural_variants);
#		undef (@{$chrom->get_all_VariationFeatures()});
#		undef (@{$chrom->get_all_StructuralVariationFeatures()});
		
		## update coords
		$slice_start = $slice_start + $slice_max_length;
		$slice_end = $slice_start  + $slice_max_length - 1;
		
		
	} ## while($slice_start < $chrom_obj->end) {


}
close(SNP);
close(SNP2TRANS);
close(SNP2TRANS_CONSEQ_TYPE);
close(SNP_POP_FREQ);
close(SNP_PHEN_ANNOT);
close(SNP_XREF);
close(STRUCT_VAR);

#sub get_consequence_type_id {
#	my $cons = shift;
#	if(not defined($conseq_dicc{$cons})) {
#		$conseq_type_id++;
#		$conseq_dicc{$cons} = $conseq_type_id;
#		print CONSEQ_TYPE $conseq_dicc{$cons}."\t".$cons."\n";
#		$|++;
#	}
#	return $conseq_dicc{$cons};
#}

sub print_parameters {
	print "Parameters: ";
	print "species: $species, outdir: $outdir, ensembl-libs: $ENSEMBL_LIBS, ";
	print "ensembl-host: $ENSEMBL_HOST, ensembl-port: $ENSEMBL_PORT, ";
	print "ensembl-user: $ENSEMBL_USER, ensembl-pass: $ENSEMBL_PASS, ";
	print "verbose: $verbose, help: $help";
	print "\n";
}

sub print_usage {
	print "Usage:   $0 [--species] [--outdir] [--ensembl-libs] [--ensembl-host] [--ensembl-port] ";
	print "[--ensembl-user] [--ensembl-pass] [--verbose] [--help]\n";
	print "\tDescription:\n";
	print "\t species: scientific name, i.e.: Homo sapiens\n";
	print "\t outdir: default directory /tmp/species\n";
	print "\t ensembl-libs: Ensembl libraries path\n";
	print "\t ensembl-host: Ensembl database host\n";
	print "\t ensembl-port: Ensembl database port\n";
	print "\t ensembl-user: Ensembl database user\n";
	print "\t ensembl-pass: Ensembl database pass\n";
	print "\t verbose: print logs\n";
	print "\t help: print this help\n";
	print "\n";
	exit(0);
}

########################################################################
########################################################################
########################################################################
