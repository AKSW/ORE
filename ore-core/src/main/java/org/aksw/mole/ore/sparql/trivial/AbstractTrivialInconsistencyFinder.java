package org.aksw.mole.ore.sparql.trivial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.aksw.mole.ore.sparql.InconsistencyFinder;
import org.aksw.mole.ore.sparql.SPARQLBasedInconsistencyProgressMonitor;
import org.aksw.mole.ore.sparql.TimeOutException;
import org.aksw.mole.ore.sparql.generator.AbstractSPARQLBasedAxiomGenerator;
import org.dllearner.kb.SparqlEndpointKS;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;

import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;

public class AbstractTrivialInconsistencyFinder extends AbstractSPARQLBasedAxiomGenerator implements InconsistencyFinder {
	
	protected String query;
	protected String filter = "";
	protected Map<String, Integer> filterToOffset = new HashMap<String, Integer>();
	protected OWLDataFactory dataFactory = new OWLDataFactoryImpl();
	protected boolean stopIfInconsistencyFound = true;
	protected Set<Explanation<OWLAxiom>> explanations = new HashSet<>();
	
	private List<SPARQLBasedInconsistencyProgressMonitor> progressMonitors = new ArrayList<SPARQLBasedInconsistencyProgressMonitor>();
	
	protected final OWLAxiom inconsistencyEntailment = dataFactory.getOWLSubClassOfAxiom(dataFactory.getOWLThing(), dataFactory.getOWLNothing());
	
	public AbstractTrivialInconsistencyFinder(SparqlEndpointKS ks) {
		super(ks);
		filterToOffset.put("", 0);
	}

	@Override
	public Set<OWLAxiom> getInconsistentFragment() throws TimeOutException {
		Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
		//add filter if exist
		String queryString = query;
		if(filter != null && !filter.isEmpty()){
			queryString = query.trim();
			queryString = query.substring(0, query.lastIndexOf('}'));
			queryString += filter + "}";
		}
		Query q = QueryFactory.create(queryString);
		q.setLimit(limit);
		int offset = filterToOffset.containsKey(filter) ? filterToOffset.get(filter) : 0;
		q.setOffset(offset);
		Model model = executeConstructQuery(q);
		OWLOntology ontology = convert(model);
		axioms.addAll(ontology.getLogicalAxioms());
		filterToOffset.put(filter, offset+limit);
		System.out.println(q);
		return axioms;
	}
	
	protected OWLLiteral getOWLLiteral(Literal lit){
		OWLLiteral literal = null;
		if(lit.getDatatypeURI() != null){
			OWLDatatype datatype = dataFactory.getOWLDatatype(IRI.create(lit.getDatatypeURI()));
			literal = dataFactory.getOWLLiteral(lit.getLexicalForm(), datatype);
		} else {
			if(lit.getLanguage() != null){
				literal = dataFactory.getOWLLiteral(lit.getLexicalForm(), lit.getLanguage());
			} else {
				literal = dataFactory.getOWLLiteral(lit.getLexicalForm());
			}
		}
		return literal;
	}
	
	public Set<Explanation<OWLAxiom>> getExplanations(){
		return explanations;
	}
	
	public void addProgressMonitor(SPARQLBasedInconsistencyProgressMonitor mon) {
		progressMonitors.add(mon);
	}

	public void removeProgressMonitor(SPARQLBasedInconsistencyProgressMonitor mon) {
		progressMonitors.remove(mon);
	}
	
	protected void fireInconsistencyFound(Set<Explanation<OWLAxiom>> explanations) {
		for (SPARQLBasedInconsistencyProgressMonitor mon : progressMonitors) {
			mon.inconsistencyFound(explanations);
		}
	}
	
	protected void fireMessage(String message) {
		for (SPARQLBasedInconsistencyProgressMonitor mon : progressMonitors) {
			mon.message(message);
		}
	}
	
	/**
	 * @param stopIfInconsistencyFound the stopIfInconsistencyFound to set
	 */
	public void setStopIfInconsistencyFound(boolean stopIfInconsistencyFound) {
		this.stopIfInconsistencyFound = stopIfInconsistencyFound;
	}

	@Override
	public void setMaximumRuntime(long duration, TimeUnit timeUnit) {
	}

	@Override
	public void setAxiomsToIgnore(Set<OWLAxiom> axiomsToIgnore) {
	}
}
