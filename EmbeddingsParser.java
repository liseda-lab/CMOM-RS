package liseda.matcha.io.embeddings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import liseda.matcha.ontology.Ontology;
import liseda.matcha.ontology.lexicon.Lexicon;
import liseda.matcha.semantics.EntityType;
import liseda.matcha.settings.Settings;

public class EmbeddingsParser {
	
	static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	public static HashMap<String,float[]> loadEmbeddings() throws FileNotFoundException {
		String embeddings_file = Settings.getInstance().getEmbeddingsFile();
		System.out.println("Loading embeddings from file " + embeddings_file + ", is file?: " + new File(embeddings_file).isFile());
		HashMap<String,float[]> embeddings = new HashMap<String,float[]>();
		Scanner scanner = new Scanner(new File(embeddings_file));
		while (scanner.hasNextLine()) {
			String next = scanner.nextLine();
			String label = next.split("\t")[0];
			String fullvector = next.split("\t")[1];
			String[] floats = fullvector.split(" ");
			float[] vector = new float[floats.length];
			for(int i = 0; i < vector.length; i++) {
				vector[i] = Float.parseFloat(floats[i].strip());
			}
			if(!embeddings.containsKey(label))
				embeddings.put(label, vector);
		}
		System.out.println("Found " + embeddings.keySet().size() + " names with embeddings");
		return embeddings;	
	}
	
	public static HashMap<String,float[]> loadEmbeddings(String embeddings_file) throws FileNotFoundException {
		System.out.println("Loading embeddings from file " + embeddings_file);
		HashMap<String,float[]> embeddings = new HashMap<String,float[]>();
		Scanner scanner = new Scanner(new File(embeddings_file));
		while (scanner.hasNextLine()) {
			String next = scanner.nextLine();
			String label = next.split("\t")[0];
			String fullvector = next.split("\t")[1];
			String[] floats = fullvector.split(" ");
			float[] vector = new float[floats.length];
			for(int i = 0; i < vector.length; i++) {
				vector[i] = Float.parseFloat(floats[i].strip());
			}
			if(!embeddings.containsKey(label))
				embeddings.put(label, vector);
		}
		System.out.println("Found " + embeddings.keySet().size() + " names with embeddings");
		return embeddings;	
	}

	public static void generateEmbeddings(String labels_file, String embeddings_file) throws IOException {
		System.out.println(dtf.format(LocalDateTime.now()) + " | Generating embeddings");
		long emb = System.currentTimeMillis()/1000;
		String[] arg = new String[] {Settings.getInstance().getPythonPath(), Settings.getInstance().getEmbeddingsProgram(), labels_file, embeddings_file};
		Process p = Runtime.getRuntime().exec(arg);
		String line;
		BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
		while((line = input.readLine()) != null) {
			System.out.println(line);
		}
		while((line = error.readLine()) != null) {
			System.out.println(line);
		}
		emb = System.currentTimeMillis()/1000 - emb;
		System.out.println(dtf.format(LocalDateTime.now()) + " | Finished in " + emb + " seconds");
	}
	
	public static void generateEmbeddings(String labels_file, String embeddings_file, String lang1, String lang2) throws IOException {
		System.out.println("Generating embeddings");
		long emb = System.currentTimeMillis()/1000;
		String[] arg = new String[] {Settings.getInstance().getPythonPath(), Settings.getInstance().getEmbeddingsProgram(), "--input_file", labels_file, "--output_file", embeddings_file, "--src_lang", lang1, "--tgt_lang", lang2};
		Process p = Runtime.getRuntime().exec(arg);
		String line;
		BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
		while((line = input.readLine()) != null) {
			System.out.println(line);
		}
		while((line = error.readLine()) != null) {
			System.out.println(line);
		}
		emb = System.currentTimeMillis()/1000 - emb;
		System.out.println("Finished in " + emb + " seconds");
	}

	public static HashMap<String, float[]> findMissingEmbeddings(HashMap<String, float[]> embeddings, Set<String> labels) throws IOException {
		System.out.println("Finding missing embeddings");
		String missing_labels_file = File.createTempFile("missing_labels", ".txt").getAbsolutePath();
		String new_embeddings_file = File.createTempFile("embeddings", ".tsv").getAbsolutePath();
		PrintWriter out = new PrintWriter(new FileOutputStream(missing_labels_file));
		HashSet<String> missing = new HashSet<String>();
		HashMap<String, float[]> new_embeddings = new HashMap<String, float[]>();

		for(String label : labels) {
			if(!embeddings.containsKey(label) && !missing.contains(label)) {
				out.println(label);
				out.flush();
				missing.add(label);
			}
		}
		out.close();
		System.out.println("Found " + missing.size() + " missing embeddings");
		if(missing.size() > 0) {
			generateEmbeddings(missing_labels_file, new_embeddings_file);
			new_embeddings = loadEmbeddings(new_embeddings_file);
		}
		return new_embeddings;
	}
	
	public static void printLabels(List<Ontology> ontos, String labels_file) throws IOException {
		FileWriter fw = new FileWriter(labels_file, true);
		BufferedWriter bw = new BufferedWriter(fw);
		PrintWriter debug = new PrintWriter(bw);
		for(Ontology o : ontos) {
			System.out.println("Printing labels from " + o.getURI() + " to " + labels_file);
			Lexicon lex = o.getLexicon(EntityType.CLASS);
			for(String name : lex.getNames()) {
				debug.println(name);
				debug.flush();
			}
		}
		debug.close();
	}
}
