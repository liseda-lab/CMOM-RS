package liseda.matcha.match.compound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import liseda.matcha.alignment.Alignment;
import liseda.matcha.alignment.Mapping;
import liseda.matcha.alignment.MappingRelation;
import liseda.matcha.ontology.Ontology;
import liseda.matcha.ontology.ReferenceMap;
import liseda.matcha.ontology.lexicon.Lexicon;
import liseda.matcha.semantics.EntityType;
import liseda.matcha.semantics.SemanticMap;
import liseda.matcha.semantics.owl.ClassExpression;
import liseda.matcha.semantics.owl.ClassIntersection;
import liseda.matcha.semantics.owl.SimpleClass;

public class AuxiliaryMethods {

	static SemanticMap sm = SemanticMap.getInstance();

	public static HashSet<String> findURIcombinations(HashMap<String, Set<String>> tgt_name2uri, String line) {

		HashSet<String> uri_combinations = new HashSet<String>();
		HashMap<String, Set<String>> tgt_name2uri_edited = new HashMap<String, Set<String>>(tgt_name2uri);

		String[] split = line.split(" # ");

		for(int i = 0; i < split.length-1; i++) {
			Set<String> tgt1_uris = tgt_name2uri_edited.get(split[i]);
			for(int j = i+1; j < split.length; j++) {
				Set<String> tgt2_uris = tgt_name2uri_edited.get(split[j]);

				// compare each set of URIs and check if there is any in common
				if(!Collections.disjoint(tgt1_uris, tgt2_uris)) { // if yes,
					// remove common URIs from largest set of URIs
					if(tgt1_uris.size() >= tgt2_uris.size()) {
						tgt1_uris.removeAll(tgt2_uris);
						// if not empty save new set
						if(!tgt1_uris.isEmpty())
							tgt_name2uri_edited.put(split[i], tgt1_uris);
						// if empty, label combination becomes invalid as it completely removes a target name, exit
						else
							return new HashSet<String>();
					}
					if(tgt2_uris.size() >= tgt1_uris.size()) {
						tgt2_uris.removeAll(tgt1_uris);
						if(!tgt2_uris.isEmpty())
							tgt_name2uri_edited.put(split[j], tgt2_uris);
						else
							return new HashSet<String>();
					}
				}
			}
		}

		if(split.length == 2) {
			for(String uri1 : tgt_name2uri_edited.get(split[0])) {
				for(String uri2 : tgt_name2uri_edited.get(split[1])) {
					String uri_combination = uri1 + " # " + uri2;
					if(!uri_combinations.contains(uri_combination))
						uri_combinations.add(uri_combination);
				}
			}
		}

		if(split.length == 3) {
			for(String uri1 : tgt_name2uri_edited.get(split[0])) {
				for(String uri2 : tgt_name2uri_edited.get(split[1])) {
					for(String uri3 : tgt_name2uri_edited.get(split[2])) {
						String uri_combination = uri1 + " # " + uri2 + " # " + uri3;
						if(!uri_combinations.contains(uri_combination))
							uri_combinations.add(uri_combination);
					}
				}
			}
		}

		if(split.length == 4) {
			for(String uri1 : tgt_name2uri_edited.get(split[0])) {
				for(String uri2 : tgt_name2uri_edited.get(split[1])) {
					for(String uri3 : tgt_name2uri_edited.get(split[2])) {
						for(String uri4 : tgt_name2uri_edited.get(split[3])) {
							String uri_combination = uri1 + " # " + uri2 + " # " + uri3 + " # " + uri4;
							if(!uri_combinations.contains(uri_combination))
								uri_combinations.add(uri_combination);
						}
					}
				}
			}
		}

		if(split.length == 5) {
			for(String uri1 : tgt_name2uri_edited.get(split[0])) {
				for(String uri2 : tgt_name2uri_edited.get(split[1])) {
					for(String uri3 : tgt_name2uri_edited.get(split[2])) {
						for(String uri4 : tgt_name2uri_edited.get(split[3])) {
							for(String uri5 : tgt_name2uri_edited.get(split[4])) {
								String uri_combination = uri1 + " # " + uri2 + " # " + uri3 + " # " + uri4 + " # " + uri5;
								if(!uri_combinations.contains(uri_combination))
									uri_combinations.add(uri_combination);
							}
						}
					}
				}
			}
		}

		if(split.length == 6) {
			for(String uri1 : tgt_name2uri_edited.get(split[0])) {
				for(String uri2 : tgt_name2uri_edited.get(split[1])) {
					for(String uri3 : tgt_name2uri_edited.get(split[2])) {
						for(String uri4 : tgt_name2uri_edited.get(split[3])) {
							for(String uri5 : tgt_name2uri_edited.get(split[4])) {
								for(String uri6 : tgt_name2uri_edited.get(split[5])) {
									String uri_combination = uri1 + " # " + uri2 + " # " + uri3 + " # " + uri4 + " # " + uri5 + " # " + uri6;
									if(!uri_combinations.contains(uri_combination))
										uri_combinations.add(uri_combination);
								}
							}
						}
					}
				}
			}
		}
		if(split.length > 6)
			System.out.println("Combination is longer than 6 entities");
		return uri_combinations;
	}

	public static Alignment createAlignment(HashMap<String, HashSet<String>> mappings, double sim, List<Ontology> srcs, List<Ontology> tgts, HashMap<String, Set<String>> src_name2uri, HashMap<String, HashSet<String>> tgt_name2combos) {
		System.out.println("Creating alignment from combinations");
		System.out.println("Using combinations for " + mappings.keySet() + " sources");
		Alignment a = new Alignment(srcs,tgts);

		for(String src_name : mappings.keySet()) {
			for(String src_uri : src_name2uri.get(src_name)) {

				for(String combination : mappings.get(src_name)) {
					for(String uri_combo : tgt_name2combos.get(combination)) {

						String[] split = uri_combo.split(" # ");
						List<ClassExpression> listCE = new ArrayList<ClassExpression>();

						for(String uri : split) {
							listCE.add(new SimpleClass(uri));
						}
						ClassIntersection ci = new ClassIntersection(listCE);
						sm.addExpression(ci);
						Mapping x = new Mapping(src_uri, ci.toString(), sim, MappingRelation.EQUIVALENCE);
						a.add(x);
					}
				}
			}
		}
		a = CMOMextras.fixAlignment(a);
		System.out.println("Created " + a.size() + " mappings, with " + a.sourceCount() + " sources");
		return a;
	}

	public static Alignment fixAlignment(Alignment a) {
		Alignment b = new Alignment();

		for(Mapping m : a) {
			if(m.getEntity1().split(",").length == 1) {
				b.add(new Mapping(m.getEntity1(), m.getEntity2(), m.getSimilarity(), m.getRelationship()));
			}
			else {
				b.add(new Mapping(m.getEntity2(), m.getEntity1(), m.getSimilarity(), m.getRelationship()));
			}
		}

		return b;
	}

	public static HashMap<String, HashSet<String>> getLDs(Ontology o, boolean normalise_abnormal) {
		HashMap<String, HashSet<String>> LDs = new HashMap<String, HashSet<String>>();

		Lexicon lex = o.getLexicon(EntityType.CLASS);

		ReferenceMap rm = o.getReferenceMap();
		for(String ent : rm.getEntities()) {
			for(String ref : rm.getReferences(ent)) {
				if(ref.contains("<")) {

					// check abnormal
					boolean flag = false;
					if(normalise_abnormal) {
						for(String name : lex.getNames(ent)) {
							if(name.contains("abnormal"))
								flag = true;
						}
					}
					else {
						flag = true;
					}

					HashSet<String> parts = new HashSet<String>();
					for(String component : ref.split(" ")) {
						String uri = component.replaceAll("<", "").replaceAll(">", "").replaceAll("ObjectSomeValuesFrom", "").replaceAll("ObjectIntersectionOf", "").replaceAll("\\(", "").replaceAll("\\)", "").replaceAll("ObjectUnionOf", "");
						if(!uri.contains("RO_") && !uri.contains("BFO")) {
							if(uri.contains("PATO_0000460") & flag == false)
								continue;
							else {
								parts.add(uri);
								if(o.contains(uri))
									System.out.println(uri);
							}
						}
						LDs.put(ent, parts);
					}
				}
			}
		}
		return LDs;
	}

	public static Alignment createLDalignment(List<Ontology> srcs, List<Ontology> tgts, boolean normalise_abnormal) {
		Alignment a = new Alignment();
		Alignment temp = new Alignment();

		for(Ontology o : srcs) {
			Lexicon lex = o.getLexicon(EntityType.CLASS);
			ReferenceMap rm = o.getReferenceMap();

			for(String ent : rm.getEntities()) {
				for(String ref : rm.getReferences(ent)) {
					if(ref.contains("<")) {

						List<ClassExpression> listCE = new ArrayList<ClassExpression>();

						// check abnormal
						boolean flag = false;
						if(normalise_abnormal) {
							for(String name : lex.getNames(ent)) {
								if(name.contains("abnormal"))
									flag = true;
							}
						}
						else {
							flag = true;
						}

						for(String component : ref.split(" ")) {
							String uri = component.replaceAll("<", "").replaceAll(">", "").replaceAll("ObjectSomeValuesFrom", "").replaceAll("ObjectIntersectionOf", "").replaceAll("\\(", "").replaceAll("\\)", "").replaceAll("ObjectUnionOf", "");
							if(!uri.contains("RO_") && !uri.contains("BFO")) {
								if(uri.contains("PATO_0000460") & flag == false)
									continue;
								else {
									listCE.add(new SimpleClass(uri));
								}
							}
						}
						ClassIntersection ci = new ClassIntersection(listCE);
						sm.addExpression(ci);
						Mapping x = new Mapping(ent, ci.toString(), 1.0, MappingRelation.EQUIVALENCE);
						temp.add(x);
					}
				}
			}
		}

		temp = CMOMextras.fixAlignment(temp);

		// tgts namespaces
		HashSet<String> tgt_namespaces = new HashSet<String>();
		for(Ontology tgt : tgts) {
			String ns = CMOMextras.getNamespace(tgt.getURI(), true);
			if(!tgt_namespaces.contains(ns)) {
				tgt_namespaces.add(ns);
			}
		}

		// only use LDs that have the same tgts
		here: for(Mapping m : temp) {

			HashSet<String> targets = new HashSet<String>();
			for(String t : m.getEntity2().split(", ")) {
				String t_uri = t.replace("AND[", "").replace("]","");
				if(tgt_namespaces.contains(CMOMextras.getNamespace(t_uri, false))) { // namespace matches tgts
						targets.add(t_uri);
				}
				else
					continue here;
			}
			a.add(m);
		}
		a = CMOMextras.fixAlignment(a);
		return a;
	}

	public static String getNamespace(String uri, boolean isOntology) {
		String namespace = "";
		if(isOntology)
			namespace = StringUtils.substringBeforeLast(StringUtils.substringAfterLast(uri, "/"), ".").toLowerCase();
		else
			namespace = StringUtils.substringBeforeLast(StringUtils.substringAfterLast(uri, "/"), "_").toLowerCase();
		return namespace;
	}

}
