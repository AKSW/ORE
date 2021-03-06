package org.aksw.mole.ore.validation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.ExtractionDBCache;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLProperty;

import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.query.QueryException;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;

public class InverseFunctionalityConsistencyValidator extends SPARQLConsistencyValidator<InverseFunctionalityViolation, OWLObjectProperty>{
	
	private static final Logger log = Logger.getLogger(InverseFunctionalityConsistencyValidator.class);
	
	public InverseFunctionalityConsistencyValidator(Model model) {
		super(model);
		queryTemplate = new ParameterizedSparqlString(
				"SELECT * WHERE {" +
				"?s1 ?p ?o." +
				"?s2 ?p ?o." +	
				"FILTER(?s1 != ?s2)" +
				"}"
				);
		countQueryTemplate = new ParameterizedSparqlString(
				"SELECT (COUNT(*) AS ?cnt) WHERE {" +
				"?s1 ?p ?o." +
				"?s2 ?p ?o." +	
				"FILTER(?s1 != ?s2)" +
				"}"
				);
	}
	
	public InverseFunctionalityConsistencyValidator(SparqlEndpoint endpoint) {
		this(endpoint, null);
	}
	
	public InverseFunctionalityConsistencyValidator(SparqlEndpoint endpoint, ExtractionDBCache cache) {
		super(endpoint, cache);
		queryTemplate = new ParameterizedSparqlString(
				"SELECT * WHERE {" +
				"?s1 ?p ?o." +
				"?s2 ?p ?o." +	
				"FILTER(?s1 != ?s2)" +
				"}"
				);
		countQueryTemplate = new ParameterizedSparqlString(
				"SELECT (COUNT(*) AS ?cnt) WHERE {" +
				"?s1 ?p ?o." +
				"?s2 ?p ?o." +	
				"FILTER(?s1 != ?s2)" +
				"}"
				);
	}
	
	public Collection<InverseFunctionalityViolation> getViolations(OWLObjectProperty p){
		queryTemplate.clearParams();
		queryTemplate.setIri("p", p.toStringID());
		
		Set<InverseFunctionalityViolation> violations = new HashSet<InverseFunctionalityViolation>();
		
		try {
			ResultSet rs = executeSelect(queryTemplate.asQuery());
			QuerySolution qs;
			while(rs.hasNext()){
				qs = rs.next();
				OWLObject object = parseNode(qs.get("o"));
				OWLIndividual subject1 = new OWLNamedIndividualImpl(IRI.create(qs.getResource("s1").getURI()));
				OWLIndividual subject2 = new OWLNamedIndividualImpl(IRI.create(qs.getResource("s2").getURI()));
				
				violations.add(new InverseFunctionalityViolation(p, object, subject1, subject2));
			}
		} catch (QueryException e) {
			e.printStackTrace();
			return getViolationsIterative(p);
		}
		return violations;
	}
	
	private Set<InverseFunctionalityViolation> getViolationsIterative(OWLProperty p){
		queryTemplate.clearParams();
		queryTemplate.setIri("p", p.toStringID());
		
		Set<InverseFunctionalityViolation> violations = new HashSet<InverseFunctionalityViolation>();
		
		//get distinct objects
		Set<RDFNode> objects = new HashSet<RDFNode>();
		ParameterizedSparqlString query = new ParameterizedSparqlString("SELECT DISTINCT ?o WHERE {?s ?p ?o.}");
		query.setIri("p", p.toStringID());
		ResultSet rs = executeSelect(query.asQuery());
		QuerySolution qs;
		while(rs.hasNext()){
			objects.add(rs.next().get("o"));
		}
		
		//for each object get the violations
		for(RDFNode o : objects){
			queryTemplate.clearParam("o");
			if(o.isLiteral()){
				queryTemplate.setLiteral("o", o.asLiteral());
			} else if(o.isURIResource()){
				queryTemplate.setIri("o", o.asResource().getURI());
			}
			try {
				rs = executeSelect(queryTemplate.asQuery());
				while(rs.hasNext()){
					qs = rs.next();
					OWLObject object = parseNode(o);
					OWLIndividual subject1 = new OWLNamedIndividualImpl(IRI.create(qs.getResource("s1").getURI()));
					OWLIndividual subject2 = new OWLNamedIndividualImpl(IRI.create(qs.getResource("s2").getURI()));
					
					violations.add(new InverseFunctionalityViolation(p, object, subject1, subject2));
				}
			} catch (QueryException e) {
				e.printStackTrace();
			}
		}
		return violations;
	}
	
	@Override
	public long getNumberOfViolations(OWLProperty p){
		countQueryTemplate.clearParams();
		countQueryTemplate.setIri("p", p.toStringID());
		long cnt = -1;
		try {
			ResultSet rs = executeSelect(countQueryTemplate.asQuery(), 40000, 40000);
			QuerySolution qs;
			while(rs.hasNext()){
				qs = rs.next();
				cnt = qs.getLiteral("cnt").getLong();
			}
		} catch (QueryException e) {
			log.error(p.toStringID() + ": Got exception. Fallback to iterative counting for each distinct object.");
			return getNumberOfViolationsIterative(p);
		}
		return cnt;
	}
	
	private long getNumberOfViolationsIterative(OWLProperty p){
		countQueryTemplate.clearParams();
		countQueryTemplate.setIri("p", p.toStringID());
		
		//get distinct objects
		Set<RDFNode> objects = new HashSet<RDFNode>();
		ParameterizedSparqlString query = new ParameterizedSparqlString("SELECT DISTINCT ?o WHERE {?s ?p ?o.}");
		query.setIri("p", p.toStringID());
		ResultSet rs = executeSelect(query.asQuery());
		QuerySolution qs;
		while(rs.hasNext()){
			objects.add(rs.next().get("o"));
		}
		log.info("Got " + objects.size() + " distinct objects.");
		int cnt = 0;
		//for each object get the violations
		for(RDFNode o : objects){
			countQueryTemplate.clearParam("o");
			if(o.isLiteral()){
				countQueryTemplate.setLiteral("o", o.asLiteral());
			} else if(o.isURIResource()){
				countQueryTemplate.setIri("o", o.asResource().getURI());
			}
			try {
				log.trace("Counting for object " + o); 
				rs = executeSelect(countQueryTemplate.asQuery());
				while(rs.hasNext()){
					qs = rs.next();
					cnt += qs.getLiteral("cnt").getLong();
				}
				log.trace(cnt);
			} catch (QueryException e) {
				e.printStackTrace();
			}
		}
		return cnt;
	}
}
