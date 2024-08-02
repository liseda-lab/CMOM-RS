package liseda.matcha.match.compound;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import liseda.matcha.alignment.Alignment;
import liseda.matcha.alignment.Mapping;
import liseda.matcha.alignment.MappingRelation;
import liseda.matcha.data.Map2Set;
import liseda.matcha.io.ResourceManager;
import liseda.matcha.ontology.Ontology;
import liseda.matcha.ontology.lexicon.Lexicon;
import liseda.matcha.semantics.EntityType;
import liseda.matcha.semantics.SemanticMap;
import liseda.matcha.semantics.owl.ClassExpression;
import liseda.matcha.semantics.owl.ClassIntersection;
import liseda.matcha.semantics.owl.SimpleClass;
import liseda.matcha.settings.Settings;
import liseda.matcha.settings.StopList;

public class MultiplexLexicalMatcher_clean {

	protected static final String NAME = "Multiplex Lexical Matcher";
	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	private List<Ontology> tgts;
	private Map2Set<String, String> overlap;
	private Map2Set<String, String> wordNames;
	private Map2Set<String, String> nameWords;
	private HashSet<HashSet<String>> combinations;
	private HashMap<String, Set<String>> src_name2uri;
	private HashMap<String, Set<String>> tgt_name2uri;

	SemanticMap sm = SemanticMap.getInstance();
	Settings settings = Settings.getInstance();
	
	public MultiplexLexicalMatcher_clean() {

		StopList.init(ResourceManager.getStopSet());

	}

	public Alignment match(List<Ontology> srcs, List<Ontology> tgts) {
		System.out.println(dtf.format(LocalDateTime.now()) + " | Running " + NAME + " in match mode");
		long time = System.currentTimeMillis()/1000;

		this.tgts = tgts;
		src_name2uri = getNames(srcs);
		tgt_name2uri = getNames(tgts);

		Alignment a = new Alignment(srcs,tgts);

		here: for(String src_name : src_name2uri.keySet()) {

			// get words
			Set<String> src_words = new HashSet<String>();
			for(Ontology src : srcs) {
				if(src.getLexicon(EntityType.CLASS).contains(src_name)) {
					src_words = src.getWordLexicon(EntityType.CLASS, "en").getWordsByName(src_name);
					continue;
				}
			}
			if(src_words.size() < 2)
				continue here;

			HashSet<String> tgt_names = new HashSet<String>();
			wordNames = new Map2Set<String, String>();
			nameWords = new Map2Set<String, String>();

			// check if target names contains each source word
			for(String src_word : src_words) {
				for(String tgt_name : tgt_name2uri.keySet()) {
					HashSet<String> tgt_words = new HashSet<String>(Arrays.asList(tgt_name.split(" ")));
					if(containsStrict(src_name, tgt_name) && tgt_words.contains(src_word)) {
						tgt_names.add(tgt_name);
						wordNames.add(src_word, tgt_name);
						nameWords.add(tgt_name, src_word);
					}
				}
			}
			if(wordNames.keyCount() < src_words.size())
				continue here;

			// populate overlap lists
			overlap = new Map2Set<String, String>();
			HashSet<String> fixed = new HashSet<String>();
			HashSet<String> remove_srcs = new HashSet<String>();
			HashSet<String> remove_tgts = new HashSet<String>();

			for(String word : src_words) {
				List<String> names = new ArrayList<String>(wordNames.get(word));
				// if word only has one target name this entity must be part of the mapping
				if(names.size() == 1) { 
					fixed.add(names.get(0));
					remove_srcs.addAll(nameWords.get(names.get(0)));
					remove_tgts.add(names.get(0));
				}
				for(int i = 0; i < names.size()-1; i++) {
					for(int j = i+1; j < names.size(); j++) {
						overlap.add(names.get(i), names.get(j));
						overlap.add(names.get(j), names.get(i));
					}
				}
			}

			// if we have two or more fixed target entities with overlap between them, we cannot generate a mapping
			List<String> fixed_list = new ArrayList<String>(fixed);
			for(int i = 0; i < fixed_list.size()-1; i++) {
				for(int j = i + 1; j < fixed_list.size(); j++) {
					if(overlap.contains(fixed_list.get(i), fixed_list.get(j))) {
						continue here;
					}
				}
			}

			for(String f : fixed) {
				if(overlap.get(f) != null)
					remove_tgts.addAll(overlap.get(f));
			}

			// remove fixed targets
			for(String word : remove_srcs) {
				src_words.remove(word);
			}
			for(String tgt_name : remove_tgts) {
				tgt_names.remove(tgt_name);
			}

			// create combinations recursively
			combinations = new HashSet<HashSet<String>>();
			findCombinations(src_words, tgt_names, fixed);

			// create mappings from combinations
			for(Mapping m : createMappings(src_name, combinations)) {
				a.add(m);
			}
		}
		time = System.currentTimeMillis()/1000 - time;
		System.out.println(dtf.format(LocalDateTime.now()) + " | " + NAME + " finished in " + time + " seconds");
		return a;
	}

	public static boolean containsStrict(String src_name, String tgt_name) {
		int index = src_name.indexOf(tgt_name);

		if(index == -1) // target name not contained in source name
			return false;
		if(index > 0) { // source does not start with target
			if(src_name.charAt(index-1) != ' ') // must be preceded by space
				return false;
		}
		if(index+tgt_name.length() < src_name.length()) { // source does not end with target
			if(src_name.charAt(index+tgt_name.length()) != ' ') // must be followed by space
				return false;
		}
		if(index == 0 && tgt_name.length() == src_name.length()) { // source different from target
			return false;
		}
		return true;
	}

	private void findCombinations(Set<String> src_words, HashSet<String> tgt_names, HashSet<String> solution) {
		if(src_words.size() == 0) { // we found the solution
			combinations.add(solution);
			return;
		}
		if(tgt_names.size() == 0) { // we reached a dead end
			return;
		}

		String src_word = src_words.iterator().next();// choose a source word
		here: for(String tgt_name : wordNames.get(src_word)) { // choose a target name
			if(!tgt_names.contains(tgt_name))
				continue here;

			HashSet<String> new_src_words = new HashSet<String>(src_words);
			new_src_words.removeAll(nameWords.get(tgt_name)); // remove target words from source words

			HashSet<String> new_tgt_names = new HashSet<String>(tgt_names);
			new_tgt_names.removeAll(overlap.get(tgt_name)); // remove overlapping target names
			new_tgt_names.remove(tgt_name);

			HashSet<String> new_solution = new HashSet<String>(solution); // add fixed targets to solution
			new_solution.add(tgt_name); // add target name to solution

			findCombinations(new_src_words, new_tgt_names, new_solution);
		}
	}

	private HashSet<Mapping> createMappings(String src_name, HashSet<HashSet<String>> combinations) {
		HashSet<Mapping> mappings = new HashSet<Mapping>();
		for(String src_uri : src_name2uri.get(src_name)) {
			for(HashSet<String> combination : combinations) {
				HashSet<String> uri_combinations = CMOMextras.findURIcombinations(tgt_name2uri, String.join(" # ", combination));
				for(String uri_combination : uri_combinations)  {
					double sim = calculateSimilarityByProduct(String.join(" # ", combination), uri_combination);
					Mapping m = new Mapping(src_uri, createClassIntersection(uri_combination), sim, MappingRelation.EQUIVALENCE);
					mappings.add(m);
				}
			}
		}
		return mappings;
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

	private Double calculateSimilarityByProduct(String tgt_name_combination, String tgt_uri_combination) {
		double product = 1.0;
		String[] name_array = tgt_name_combination.split(" # ");
		String[] uri_array = tgt_uri_combination.split(" # ");

		here: for(int i = 0; i < name_array.length; i++) {
			String name = name_array[i];
			String uri = uri_array[i];
			String ns_t = CMOMextras.getNamespace(uri, false);

			for(Ontology tgt : tgts) {
				String ns_o = CMOMextras.getNamespace(tgt.getURI(), true);
				if(tgt.contains(uri) && ns_o.equals(ns_t)) {
					Lexicon lex = tgt.getLexicon(EntityType.CLASS);
					product *= (1-lex.getCorrectedWeight(name, uri));
					continue here;
				}
			}
		}
		double sim = 1.0-product;
		return sim;
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
}
