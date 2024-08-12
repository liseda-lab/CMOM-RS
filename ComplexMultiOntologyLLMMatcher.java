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

public class MultiplexLLMMatcher {

	protected static final String NAME = "Complex Multi-Ontology LLM Matcher";
	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	private HashMap<String,float[]> embeddings;
	private HashMap<String, Set<String>> src_name2uri;
	private HashMap<String, Set<String>> tgt_name2uri;
	private HashMap<String, HashSet<String>> tgt_name2namespace = new HashMap<String, HashSet<String>>();
	private HashMap<String, HashSet<String>> tgt_name2combos = new HashMap<String, HashSet<String>>();
	private int maxCardinality;

	SemanticMap sm = SemanticMap.getInstance();
	Settings settings = Settings.getInstance();

	public MultiplexLLMMatcher_clean(int maxCardinality) {
		this.maxCardinality = maxCardinality;
		StopList.init(ResourceManager.getStopSet());
	}

	public Alignment match(List<Ontology> srcs, List<Ontology> tgts, HashMap<String,float[]> embeddings, double thresh) throws IOException {
		System.out.println(dtf.format(LocalDateTime.now()) + " | Running " + NAME + " in match mode");
		long time = System.currentTimeMillis()/1000;

		this.embeddings = embeddings;

		Alignment a = new Alignment(srcs, tgts);
		src_name2uri = getNames(srcs);
		tgt_name2uri = getNamesAndNamespaces(tgts);

		// check that all embeddings are present
		embeddings.putAll(EmbeddingsParser.findMissingEmbeddings(embeddings, src_name2uri.keySet()));
		embeddings.putAll(EmbeddingsParser.findMissingEmbeddings(embeddings, tgt_name2uri.keySet()));

		// Finding target combinations for each source name
		HashMap<String, String> candidate_combinations = new HashMap<String, String>();
		System.out.println("Finding target word combinations for each source");
		long combos_time = System.currentTimeMillis()/1000;
		for(String src_name : src_name2uri.keySet()) {
			String best_combination = createBestCombination(src_name);
			if(!best_combination.isBlank()) {
				// check URIs for duplicates
				HashSet<String> uri_combinations = new HashSet<String>();
				uri_combinations = CMOMextras.findURIcombinations(tgt_name2uri, best_combination);

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
				String tgt_name_combination = candidate_combinations.get(src_name);

				for(String tgt_uri_combination : tgt_name2combos.get(tgt_name_combination)) {

					Double sim = calculateSimilarity(src_name, tgt_name_combination);
					if(sim >= thresh) {
						Mapping m = new Mapping(src_uri, createClassIntersection(tgt_uri_combination), sim, MappingRelation.EQUIVALENCE);
						a.add(m);
					}
				}
			}
		}
		a.sortDescending();

		time = System.currentTimeMillis()/1000 - time;
		System.out.println(dtf.format(LocalDateTime.now()) + " | " + NAME + " finished in " + time + " seconds");

		return a;
	}
	
	private Double calculateSimilarity(String src_name, String tgt_name_combination) {

		float[] src_emb = embeddings.get(src_name);

		String[] name_array = tgt_name_combination.split(" # "); // split target combination into names
		float[] sum = embeddings.get(name_array[0]); // initialise product w/ first embedding

		for(int i = 1; i < name_array.length; i++) { // iterate through all targets in combination
			float[] emb = embeddings.get(name_array[i]);
			float[] calc = new float[emb.length];

			for(int j = 0; j < sum.length; j++) { // iterate through positions in the vector
				calc[j] = sum[j]+emb[j];
			}
			sum = calc; // update combined embedding
		}

		return Similarity.cosineSimilarity(src_emb, sum);
	}
	
	private String createClassIntersection(String tgt_uri_combination) {
		String[] split = tgt_uri_combination.split(" # ");
		List<ClassExpression> listCE = new ArrayList<ClassExpression>();

		for(String uri : split) {
			listCE.add(new SimpleClass(uri));
		}
		ClassIntersection ci = new ClassIntersection(listCE);
		sm.addExpression(ci);
		return ci.toString();
	}
	
	private String createBestCombination(String src_name) {
		HashSet<String> best_combination = new HashSet<String>();
		float[] srcEmb = embeddings.get(src_name);

		// 1
		String first_name = selectBestTarget(srcEmb);
		best_combination.add(first_name);
		float[] newEmb = subtractEmbedding(srcEmb, first_name);

		// 2
		String new_name = selectBestTarget(newEmb);
		if(!new_name.isEmpty()) {
			best_combination.add(new_name);
			newEmb = subtractEmbedding(newEmb, new_name);
		}

		// 3
		if(maxCardinality > 2) {
			new_name = selectBestTarget(newEmb);
			if(!new_name.isEmpty()) {
				best_combination.add(new_name);
				newEmb = subtractEmbedding(newEmb, new_name);

				// 4
				if(maxCardinality > 3) {
					new_name = selectBestTarget(newEmb);
					if(!new_name.isEmpty()) {
						best_combination.add(new_name);
						newEmb = subtractEmbedding(newEmb, new_name);

						// 5
						if(maxCardinality > 4) {
							new_name = selectBestTarget(newEmb);
							if(!new_name.isEmpty()) {
								best_combination.add(new_name);
								newEmb = subtractEmbedding(newEmb, new_name);

								// 6
								if(maxCardinality > 5) {
									new_name = selectBestTarget(newEmb);
									if(!new_name.isEmpty()) {
										best_combination.add(new_name);
										newEmb = subtractEmbedding(newEmb, new_name);
									}
								}
							}
						}
					}
				}
			}
		}
		if(best_combination.isEmpty())
			return "";
		else if(best_combination.size() > maxCardinality)
			return "";
		return String.join(" # ", best_combination);
	}
	
	private String selectBestTarget(float[] srcEmb) {

		double best_sim = 0.0;
		String best_name = "";

		for(String tgt_name : tgt_name2uri.keySet()) {
			float[] tgtEmb = embeddings.get(tgt_name);
			Double sim = Similarity.cosineSimilarity(srcEmb, tgtEmb);

			if(sim > best_sim) {				
				best_sim = sim;
				best_name = tgt_name;
			}
		}
		if(best_sim < 0.2) {
			return "";
		}
		return best_name;
	}
	
	private float[] subtractEmbedding(float[] prevEmb, String tgt_name) {
		float[] newEmb = new float[prevEmb.length];
		float[] tgtEmb = embeddings.get(tgt_name);

		for(int j = 0; j < newEmb.length; j++) {
			newEmb[j] = prevEmb[j]-tgtEmb[j];
		}
		return newEmb;
	}

	private HashMap<String, Set<String>> getNames(List<Ontology> ontologies) {
		HashMap<String, Set<String>> name2uri = new HashMap<String, Set<String>>();
		for(Ontology o : ontologies) {
			Lexicon lex = o.getLexicon(EntityType.CLASS);
			String namespace = CMOMextras.getNamespace(o.getURI(), true);

			for(String uri : o.getEntities(EntityType.CLASS)) {
				if(uri.toLowerCase().contains(namespace)) { // only internal classes
					for(String name : lex.getNames(uri)) {
						if(!name.contains("/") && name.length() > 2 && !name.contains("[") && !name.contains("{") && !name.contains(":") && !name.contains("&") && !name.contains("=") && !name.equals("[0-9]+")) {
							if(name2uri.containsKey(name)) {
								Set<String> set = name2uri.get(name);
								set.add(uri);
								name2uri.replace(name, set);
							}
							else {
								Set<String> set = new HashSet<String>();
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

	private HashMap<String, Set<String>> getNamesAndNamespaces(List<Ontology> ontologies) {
		HashMap<String, Set<String>> name2uri = new HashMap<String, Set<String>>();
		for(Ontology o : ontologies) {
			Lexicon lex = o.getLexicon(EntityType.CLASS);
			String namespace = CMOMextras.getNamespace(o.getURI(), true);

			for(String uri : o.getEntities(EntityType.CLASS)) {
				if(uri.toLowerCase().contains(namespace)) { // only internal classes
					for(String name : lex.getNames(uri)) {
						if(!name.contains("/") && name.length() > 2 && !name.contains("[") && !name.contains("{") && !name.contains(":") && !name.contains("&") && !name.contains("=") && !name.equals("[0-9]+")) {
							// save name and uris
							if(name2uri.containsKey(name)) {
								Set<String> set = name2uri.get(name);
								set.add(uri);
								name2uri.replace(name, set);
							}
							else {
								Set<String> set = new HashSet<String>();
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
