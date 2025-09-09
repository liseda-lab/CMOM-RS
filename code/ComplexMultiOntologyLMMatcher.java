package liseda.matcha.match.compound;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import liseda.matcha.alignment.Alignment;
import liseda.matcha.alignment.Mapping;
import liseda.matcha.alignment.MappingRelation;
import liseda.matcha.io.ResourceManager;
import liseda.matcha.io.embeddings.EmbeddingsParser;
import liseda.matcha.ontology.Ontology;
import liseda.matcha.ontology.lexicon.Lexicon;
import liseda.matcha.semantics.EntityType;
import liseda.matcha.semantics.SemanticMap;
import liseda.matcha.semantics.owl.ClassExpression;
import liseda.matcha.semantics.owl.ClassIntersection;
import liseda.matcha.semantics.owl.SimpleClass;
import liseda.matcha.settings.Settings;
import liseda.matcha.settings.StopList;
import liseda.matcha.similarity.Similarity;

public class ComplexMultiOntologyLMMatcher {

	protected static final String NAME = "Complex Multi-Ontology LLM Matcher";
	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	private HashMap<String,float[]> embeddings;
	private HashMap<String, HashSet<String>> src_name2uri;
	private HashMap<String, HashSet<String>> tgt_name2uri;
	private HashMap<String, HashSet<String>> tgt_name2namespace = new HashMap<String, HashSet<String>>();
	private HashMap<HashSet<String>, HashSet<HashSet<String>>> tgt_name2combos = new HashMap<HashSet<String>, HashSet<HashSet<String>>>();
	private int maxCardinality;

	SemanticMap sm = SemanticMap.getInstance();
	Settings settings = Settings.getInstance();

	public ComplexMultiOntologyLMMatcher(int maxCardinality) {
		this.maxCardinality = maxCardinality;
		StopList.init(ResourceManager.getStopSet());
	}

	public Alignment match(List<Ontology> srcs, List<Ontology> tgts, HashMap<String,float[]> embeddings, double thresh) throws IOException {
		System.out.println(dtf.format(LocalDateTime.now()) + " | Running " + NAME + " in match mode");
		long time = System.currentTimeMillis()/1000;

		this.embeddings = embeddings;
		GeometricOperations go = new GeometricOperations();

		Alignment a = new Alignment(srcs, tgts);
		src_name2uri = getNames(srcs);
		tgt_name2uri = getNamesAndNamespaces(tgts);

		// check that all embeddings are present
		embeddings.putAll(EmbeddingsParser.findMissingEmbeddings(embeddings, src_name2uri.keySet()));
		embeddings.putAll(EmbeddingsParser.findMissingEmbeddings(embeddings, tgt_name2uri.keySet()));

		// Finding target combinations for each source name
		HashMap<String, HashSet<String>> candidate_combinations = new HashMap<String, HashSet<String>>();
		System.out.println("Finding target word combinations for each source");
		long combos_time = System.currentTimeMillis()/1000;
		for(String src_name : src_name2uri.keySet()) {
			HashSet<String> best_combination = go.findBestTargetNameCombination(embeddings, embeddings.get(src_name), tgt_name2uri.keySet());
			if(!best_combination.isEmpty()) {
				// find URI combinations for name combination
				HashSet<HashSet<String>> uri_combinations = go.convertNameSetstoIRIsets(best_combination, tgt_name2uri);
				if(!uri_combinations.isEmpty()) {
					candidate_combinations.put(src_name, best_combination);
					if(!tgt_name2combos.containsKey(best_combination))
						tgt_name2combos.put(best_combination, uri_combinations);
				}
			}
		}
		combos_time = System.currentTimeMillis()/1000 - combos_time;
		System.out.println("Finished in " + combos_time + " seconds");
		System.out.println("Found combinations for " + candidate_combinations.keySet().size() + " source names");

		// alignment with all possible combinations
		for(String src_name : candidate_combinations.keySet()) {
			for(String src_uri : src_name2uri.get(src_name)) {				
				HashSet<String> tgt_name_combination = candidate_combinations.get(src_name);
				for(HashSet<String> tgt_uri_combination : tgt_name2combos.get(tgt_name_combination)) {
					Double sim = go.calculateSimilarity(embeddings, src_name, tgt_name_combination);
					if(sim >= thresh) {
						a.add(new Mapping(src_uri, tgt_uri_combination, sim, MappingRelation.EQUIVALENCE));
					}
				}
			}
		}
		a.sortDescending();
		time = System.currentTimeMillis()/1000 - time;
		System.out.println(dtf.format(LocalDateTime.now()) + " | " + NAME + " finished in " + time + " seconds");
		return a;
	}

	private HashMap<String, HashSet<String>> getNames(List<Ontology> ontologies) {
		HashMap<String, HashSet<String>> name2uri = new HashMap<String, HashSet<String>>();
		for(Ontology o : ontologies) {
			Lexicon lex = o.getLexicon(EntityType.CLASS);
			String namespace = AuxiliaryMethods.getNamespace(o.getURI(), true);

			for(String uri : o.getEntities(EntityType.CLASS)) {
				if(uri.toLowerCase().contains(namespace)) { // only internal classes
					for(String name : lex.getNames(uri)) {
						if(!name.contains("/") && name.length() > 2 && !name.contains("[") && !name.contains("{") && !name.contains(":") && !name.contains("&") && !name.contains("=") && !name.equals("[0-9]+")) {
							if(name2uri.containsKey(name)) {
								HashSet<String> set = name2uri.get(name);
								set.add(uri);
								name2uri.replace(name, set);
							}
							else {
								HashSet<String> set = new HashSet<String>();
								set.add(uri);
								name2uri.put(name, set);
							}
						}
					}
				}
			}
		}
		System.out.println("Found " + name2uri.size() + " names");
		return name2uri;
	}

	private HashMap<String, HashSet<String>> getNamesAndNamespaces(List<Ontology> ontologies) {
		HashMap<String, HashSet<String>> name2uri = new HashMap<String, HashSet<String>>();
		for(Ontology o : ontologies) {
			Lexicon lex = o.getLexicon(EntityType.CLASS);
			String namespace = AuxiliaryMethods.getNamespace(o.getURI(), true);

			for(String uri : o.getEntities(EntityType.CLASS)) {
				if(uri.toLowerCase().contains(namespace)) { // only internal classes
					for(String name : lex.getNames(uri)) {
						if(!name.contains("/") && name.length() > 2 && !name.contains("[") && !name.contains("{") && !name.contains(":") && !name.contains("&") && !name.contains("=") && !name.equals("[0-9]+")) {
							// save name and uris
							if(name2uri.containsKey(name)) {
								HashSet<String> set = name2uri.get(name);
								set.add(uri);
								name2uri.replace(name, set);
							}
							else {
								HashSet<String> set = new HashSet<String>();
								set.add(uri);
								name2uri.put(name, set);
							}

							// save name and namespaces
							if(tgt_name2namespace.containsKey(name)) {
								HashSet<String> set = tgt_name2namespace.get(name);
								set.add(namespace);
								tgt_name2namespace.put(name, set);
							}
							else {
								HashSet<String> set = new HashSet<String>();
								set.add(namespace);
								tgt_name2namespace.put(name, set);
							}
						}
					}
				}
			}
		}
		System.out.println("Found " + name2uri.size() + " names");
		return name2uri;
	}

}
