package org.aksw.ore.model;

import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class OWLOntologyKnowledgebase implements Knowledgebase{
	
	private OWLOntology ontology;
	private OWLReasoner reasoner;
	private boolean consistent = true;
	private boolean coherent = true;
	
	public OWLOntologyKnowledgebase(OWLOntology ontology, boolean consistent, boolean coherent) {
		this.ontology = ontology;
		this.coherent = consistent;
		this.coherent = coherent;
	}
	
	public OWLOntologyKnowledgebase(OWLOntology ontology) {
		this.ontology = ontology;
	}

	@Override
	public boolean canLearn() {
		return consistent && ontology.getIndividualsInSignature().size() > 2;
	}

	@Override
	public boolean canDebug() {
		return !consistent || !coherent;
	}
	
	/* (non-Javadoc)
	 * @see org.aksw.ore.model.Knowledgebase#canValidate()
	 */
	@Override
	public boolean canValidate() {
		return !ontology.getIndividualsInSignature().isEmpty();
	}
	
	public boolean isConsistent() {
		return consistent;
	}
	
	public boolean isCoherent() {
		return coherent;
	}
	
	public void setConsistent(boolean consistent) {
		this.consistent = consistent;
	}
	
	public void setCoherent(boolean coherent) {
		this.coherent = coherent;
	}
	
	public OWLOntology getOntology() {
		return ontology;
	}
	
	public void setReasoner(OWLReasoner reasoner) {
		this.reasoner = reasoner;
	}
	
	public OWLReasoner getReasoner() {
		return reasoner;
	}
	
	public void updateStatus(){
		consistent = reasoner.isConsistent();
		if(consistent){
			coherent = reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom().isEmpty();
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return ontology.toString();
	}

}
