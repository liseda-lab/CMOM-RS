# CMOM-RS
Complex Multi-Ontology Matching through Recursive Subtraction (CMOM-RS) is an approach to 1:n complex ontology matching without restrictions on the arity of the mappings or on the number of ontologies.
CMOM-RS combines two recursive strategies for candidate mapping generation: a strategy based on Large Language Models (LLM) that exploits the semantic space conveyed by embeddings through geometric operations; and a lexical strategy that relies on string similarity.

Partial reference alignments were constructed from existing logical definitions for the following tasks:
- Human Phenotype Ontology (HP) vs. Cell Ontology (CL), Chemical Entities of Biological Interest (ChEBI), Gene Ontology (GO), Phenotype and Trait Ontology (PATO), Uber Anatomy Ontology (UBERON)
- Mammalian Phenotype Ontology (MP) vs. Cell Ontology (CL), Chemical Entities of Biological Interest (ChEBI), Gene Ontology (GO), Phenotype and Trait Ontology (PATO), Uber Anatomy Ontology (UBERON)
- Worm Phenotype Ontology (WBP) vs. Chemical Entities of Biological Interest (ChEBI), Gene Ontology (GO), Phenotype and Trait Ontology (PATO), _C.elegans_ Gross Anatomy Ontology (WBbt)

In addition to the code and partial reference alignments, the following files are also shared:
- Alignment composed of manually validated non-reference mappings for the HP task
- Alignments produced by CMOM-RS for all three tasks
- PDF file of the paper and appendix


### Citation:
M. C. Silva, D. Faria, and C. Pesquita. Complex multi-ontology alignment through geometric operations on language embeddings. In 27th European Conference on Artificial Intelligence. 2024
