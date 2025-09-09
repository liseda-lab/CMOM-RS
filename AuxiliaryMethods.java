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

		temp = AuxiliaryMethods.fixAlignment(temp);

		// tgts namespaces
		HashSet<String> tgt_namespaces = new HashSet<String>();
		for(Ontology tgt : tgts) {
			String ns = AuxiliaryMethods.getNamespace(tgt.getURI(), true);
			if(!tgt_namespaces.contains(ns)) {
				tgt_namespaces.add(ns);
			}
		}

		// only use LDs that have the same tgts
		here: for(Mapping m : temp) {

			HashSet<String> targets = new HashSet<String>();
			for(String t : m.getEntity2().split(", ")) {
				String t_uri = t.replace("AND[", "").replace("]","");
				if(tgt_namespaces.contains(AuxiliaryMethods.getNamespace(t_uri, false))) { // namespace matches tgts
						targets.add(t_uri);
				}
				else
					continue here;
			}
			a.add(m);
		}
		a = AuxiliaryMethods.fixAlignment(a);
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
