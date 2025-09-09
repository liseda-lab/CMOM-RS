package liseda.matcha.match.compound;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import liseda.matcha.similarity.Similarity;

public class GeometricOperations {

	private HashMap<String, float[]> embeddings;
	private Set<String> tgt_names;
	private HashSet<String> target_name_selection;
	private HashSet<HashSet<String>> IRIcombinations;
	private Double stop = 0.3;

	// finds best target name combination for a source embedding (output as a string)
	public HashSet<String> findBestTargetNameCombination(HashMap<String, float[]> embeddings, float[] src_emb, Set<String> tgt_names) {
		this.embeddings = embeddings;
		this.tgt_names = tgt_names;
		target_name_selection = new HashSet<String>();
		selectBestTarget(src_emb);
		return target_name_selection;
	}

	// converts sets of target names to a list of sets of target IRIs without repetition
	public HashSet<HashSet<String>> convertNameSetstoIRIsets(HashSet<String> tgt_name_selection, HashMap<String, HashSet<String>> tgt_name2iri) {
		HashMap<String, Set<String>> tgt_name2iri_edited = new HashMap<String, Set<String>>();
		String[] split = tgt_name_selection.toArray(new String[tgt_name_selection.size()]);

		for(int i = 0; i < split.length-1; i++) {
			Set<String> tgt1_iris = tgt_name2iri.get(split[i]);
			for(int j = i+1; j < split.length; j++) {
				Set<String> tgt2_iris = tgt_name2iri.get(split[j]);

				// compare each set of IRIs and check if there is any in common
				if(!Collections.disjoint(tgt1_iris, tgt2_iris)) { // if yes,
					// remove common IRIs from largest set of URIs
					if(tgt1_iris.size() >= tgt2_iris.size()) {
						tgt1_iris.removeAll(tgt2_iris);
						// if not empty save new set
						if(!tgt1_iris.isEmpty())
							tgt_name2iri_edited.put(split[i], tgt1_iris);
						// if empty, label combination becomes invalid as it completely removes a target name, exit
						else
							return new HashSet<HashSet<String>>();
					}
					if(tgt2_iris.size() >= tgt1_iris.size()) {
						tgt2_iris.removeAll(tgt1_iris);
						if(!tgt2_iris.isEmpty())
							tgt_name2iri_edited.put(split[j], tgt2_iris);
						else
							return new HashSet<HashSet<String>>();
					}
				}
				else {
					tgt_name2iri_edited.put(split[i], tgt1_iris);
					tgt_name2iri_edited.put(split[j], tgt2_iris);
				}
			}
		}
		IRIcombinations = new HashSet<HashSet<String>>();
		createURIcombinations(new HashSet<String>(Arrays.asList(split)), tgt_name2iri_edited, new HashSet<String>());
		return IRIcombinations;
	}

	private void createURIcombinations(HashSet<String> tgt_name_selection, HashMap<String, Set<String>> tgt_name2iri_edited, HashSet<String> solution) {
		if(tgt_name_selection.size() == 0) { // solution found
			IRIcombinations.add(solution);
			return;
		}
		if(tgt_name2iri_edited.size() == 0) // ran out of options for target names
			return;
		String tgt_name = tgt_name_selection.iterator().next(); // choose a target name
		for(String tgt_iri : tgt_name2iri_edited.get(tgt_name)) { // choose an IRI for this target name
			if(!solution.contains(tgt_iri)) {
				HashSet<String> new_solution = new HashSet<String>(solution);
				HashSet<String> new_tgt_name_selection = new HashSet<String>(tgt_name_selection);
				HashMap<String, Set<String>> new_tgt_name2iri_edited = new HashMap<String, Set<String>>(tgt_name2iri_edited);
				new_solution.add(tgt_iri); // add IRI to solution
				new_tgt_name_selection.remove(tgt_name); // remove target name from selection (it has been matched)
				new_tgt_name2iri_edited.remove(tgt_name); // remove target name from name to IRI (prevent repeats)
				createURIcombinations(new_tgt_name_selection, new_tgt_name2iri_edited, new_solution);
			}
		}
	}

	private void selectBestTarget(float[] prev_emb) {
		double best_sim = 0.0;
		String best_name = "";
		for(String tgt_name : tgt_names) {
			float[] tgt_emb = embeddings.get(tgt_name.trim());
			if(tgt_emb == null)
				System.out.println("target embedding is null: " + tgt_name + "| " + tgt_emb);
			else {
				Double sim = Similarity.cosineSimilarity(prev_emb, tgt_emb);
				if(sim > best_sim) {				
					best_sim = sim;
					best_name = tgt_name;
				}
			}
		}
		if(best_sim < stop) // we have reached the stopping point
			return;
		else {
			target_name_selection.add(best_name);
			selectBestTarget(subtractEmbeddings(prev_emb, embeddings.get(best_name.trim())));
		}
	}

	public static float[] subtractEmbeddings(float[] prevEmb, float[] tgtEmb) {
		float[] new_emb = new float[prevEmb.length];
		for(int j = 0; j < new_emb.length; j++) {
			new_emb[j] = prevEmb[j]-tgtEmb[j];
		}
		return new_emb;
	}

	public double calculateSimilarity(HashMap<String, float[]> embeddings, String src_name, HashSet<String> tgt_name_combination) {
		float[] src_emb = embeddings.get(src_name);
		float[] sum = new float[src_emb.length];
		for(String tgt_name : tgt_name_combination) {
			float[] tgt_emb = embeddings.get(tgt_name);
			float[] calc = new float[tgt_emb.length];
			for(int j = 0; j < sum.length; j++) { // iterate through positions in the vector
				calc[j] = sum[j]+tgt_emb[j];
			}
			sum = calc; // update combined embedding
		}
		return Similarity.cosineSimilarity(src_emb, sum);
	}

}
