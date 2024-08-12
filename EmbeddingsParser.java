package liseda.matcha.io.embeddings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import liseda.matcha.settings.Settings;

public class EmbeddingsParser {

	public static HashMap<String,float[]> loadEmbeddings() throws FileNotFoundException {
		String embeddings_file = Settings.getInstance().getEmbeddingsFile();
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
		System.out.println("Generating embeddings");
		long emb = System.currentTimeMillis()/1000;
		String[] arg = new String[] {Settings.getInstance().getPythonPath(), Settings.getInstance().getEmbeddingsProgram(), labels_file, embeddings_file};
		Process p = Runtime.getRuntime().exec(arg);
		String line;
		BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		while((line = error.readLine()) != null) {
			System.out.println(line);
		}
		emb = System.currentTimeMillis()/1000 - emb;
		System.out.println("Finished in " + emb + " seconds");
	}
	
	public static void generateEmbeddings(String labels_file, String lang1, String lang2, String embeddings_file) throws IOException {
		System.out.println("Generating embeddings");
		long emb = System.currentTimeMillis()/1000;
		String[] arg = new String[] {Settings.getInstance().getPythonPath(), Settings.getInstance().getEmbeddingsProgram(), labels_file, lang1, lang2, embeddings_file};
		Process p = Runtime.getRuntime().exec(arg);
		String line;
		BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		while((line = error.readLine()) != null) {
			System.out.println(line);
		}
		emb = System.currentTimeMillis()/1000 - emb;
		System.out.println("Finished in " + emb + " seconds");
	}

	public static HashMap<String, float[]> findMissingEmbeddings(HashMap<String, float[]> embeddings, Set<String> set) throws IOException {
		System.out.println("Finding missing embeddings");
		String missing_labels_file = File.createTempFile("missing_labels", ".txt").getAbsolutePath();
		String new_embeddings_file = File.createTempFile("embeddings", ".tsv").getAbsolutePath();
		PrintWriter out = new PrintWriter(new FileOutputStream(missing_labels_file));
		HashSet<String> missing = new HashSet<String>();
		HashMap<String, float[]> new_embeddings = new HashMap<String, float[]>();

		for(String label : set) {
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
}
