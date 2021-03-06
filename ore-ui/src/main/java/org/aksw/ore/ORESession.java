/**
 * 
 */
package org.aksw.ore;

import java.util.Set;

import org.aksw.mole.ore.sparql.generator.SPARQLBasedInconsistencyFinder;
import org.aksw.mole.ore.sparql.trivial_old.SPARQLBasedTrivialInconsistencyFinder;
import org.aksw.ore.manager.ConstraintValidationManager;
import org.aksw.ore.manager.EnrichmentManager;
import org.aksw.ore.manager.ExplanationManager;
import org.aksw.ore.manager.ImpactManager;
import org.aksw.ore.manager.KnowledgebaseManager;
import org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener;
import org.aksw.ore.manager.LearningManager;
import org.aksw.ore.manager.RepairManager;
import org.aksw.ore.manager.SPARQLExplanationManager;
import org.aksw.ore.model.Knowledgebase;
import org.aksw.ore.model.OWLOntologyKnowledgebase;
import org.aksw.ore.model.SPARQLEndpointKnowledgebase;
import org.aksw.ore.rendering.Renderer;
import org.dllearner.core.ComponentInitException;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.ClosedWorldReasoner;
import org.dllearner.reasoning.OWLAPIReasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinSession;

/**
 * @author Lorenz Buehmann
 *
 */
public class ORESession extends VaadinSession implements KnowledgebaseLoadingListener{
	
	private static final Logger logger = LoggerFactory.getLogger(ORESession.class);

	private static final long serialVersionUID = -3399015588524238428L;
	
	public ORESession(VaadinService service) {
		super(service);
	}
	
	public static void init() {
		logger.info("Initializing new session...");
		//KB manager
		KnowledgebaseManager kbMan = new KnowledgebaseManager();
		VaadinSession.getCurrent().setAttribute(KnowledgebaseManager.class, kbMan);
		//			kbMan.addListener(this);

		//			loadDummyKnowledgebases();
	}
	
	private static void loadDummyKnowledgebases(){
		//use Pellet as OWL reasoner
		OWLReasonerFactory reasonerFactory = PelletReasonerFactory.getInstance();
		
		//dummy ontology
		OWLOntology ontology = null;
		try {
			OWLOntologyManager man = OWLManager.createOWLOntologyManager();
			ontology = man.loadOntology(IRI.create("http://xmlns.com/foaf/spec/20100809.rdf"));
			
			OWLReasoner reasoner = reasonerFactory.createNonBufferingReasoner(ontology);
			VaadinSession.getCurrent().setAttribute(OWLReasoner.class, reasoner);
			//dummy explanation manager
			ExplanationManager expMan = new ExplanationManager(reasoner, reasonerFactory);
			VaadinSession.getCurrent().setAttribute(ExplanationManager.class, expMan);
			//dummy repair manager
			RepairManager repMan = new RepairManager(ontology);
			VaadinSession.getCurrent().setAttribute(RepairManager.class, repMan);
			//learning manager
			ClosedWorldReasoner closedWorldReasoner = new ClosedWorldReasoner();
			closedWorldReasoner.setReasonerComponent(new OWLAPIReasoner(reasoner));
			try {
				closedWorldReasoner.init();
			} catch (ComponentInitException e) {
				e.printStackTrace();
			}
			//learning manager
			LearningManager learnMan = new LearningManager(closedWorldReasoner);
			VaadinSession.getCurrent().setAttribute(LearningManager.class, learnMan);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//dummy SPARQL endpoint
		try {
			SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
			SparqlEndpointKS ks = new SparqlEndpointKS(endpoint, OREConfiguration.getCacheDirectory());
			
			//dummy constraint manager
			ConstraintValidationManager valMan = new ConstraintValidationManager(ks);
			VaadinSession.getCurrent().setAttribute(ConstraintValidationManager.class, valMan);
			//dummy enrichment manager
			EnrichmentManager enMan = new EnrichmentManager(ks);
			VaadinSession.getCurrent().setAttribute(EnrichmentManager.class, enMan);
			//dummy incremental inconsistency finder
			SPARQLBasedInconsistencyFinder sparqlBasedInconsistencyFinder = new SPARQLBasedInconsistencyFinder(ks, reasonerFactory);
			VaadinSession.getCurrent().setAttribute(SPARQLBasedInconsistencyFinder.class, sparqlBasedInconsistencyFinder);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		//set current KB
		if(ontology != null){
			getKnowledgebaseManager().setKnowledgebase(new OWLOntologyKnowledgebase(ontology));
		}
//		kbMan.setKnowledgebase(new SPARQLEndpointKnowledgebase(endpoint));
	}
	
	public static void initialize(Knowledgebase knowledgebase){
		logger.info("Initializing knowledge base...");
		long t1 = System.currentTimeMillis();
		OWLReasonerFactory reasonerFactory = PelletReasonerFactory.getInstance();
		if(knowledgebase instanceof OWLOntologyKnowledgebase){
			OWLOntologyKnowledgebase ontologyKB = (OWLOntologyKnowledgebase) knowledgebase;
			OWLOntology ontology = ontologyKB.getOntology();
			OWLReasoner reasoner = ontologyKB.getReasoner();
			VaadinSession.getCurrent().setAttribute(OWLReasoner.class, reasoner);
			
			ClosedWorldReasoner closedWorldReasoner = new ClosedWorldReasoner();
			try {
				OWLAPIReasoner rc = new OWLAPIReasoner(reasoner);
				rc.init();
				closedWorldReasoner.setReasonerComponent(rc);
				closedWorldReasoner.init();
			} catch (ComponentInitException e) {
				e.printStackTrace();
			}
			//learning manager
			LearningManager learnMan = new LearningManager(closedWorldReasoner);
			VaadinSession.getCurrent().setAttribute(LearningManager.class, learnMan);
			//explanation manager
			ExplanationManager expMan = new ExplanationManager(reasoner, reasonerFactory);
			VaadinSession.getCurrent().setAttribute(ExplanationManager.class, expMan);
			//repair manager
			RepairManager repMan = new RepairManager(ontology);
			VaadinSession.getCurrent().setAttribute(RepairManager.class, repMan);
			//impact manager
			ImpactManager impMan = new ImpactManager(reasoner);
			VaadinSession.getCurrent().setAttribute(ImpactManager.class, impMan);
			
			
		} else if(knowledgebase instanceof SPARQLEndpointKnowledgebase){
			SparqlEndpointKS ks = ((SPARQLEndpointKnowledgebase) knowledgebase).getEndpoint();
			//constraint manager
			ConstraintValidationManager valMan = new ConstraintValidationManager(ks);
			VaadinSession.getCurrent().setAttribute(ConstraintValidationManager.class, valMan);
			//enrichment manager
			EnrichmentManager enMan = new EnrichmentManager(ks);
			VaadinSession.getCurrent().setAttribute(EnrichmentManager.class, enMan);
			//incremental inconsistency finder
			SPARQLBasedTrivialInconsistencyFinder sparqlBasedInconsistencyFinder = new SPARQLBasedTrivialInconsistencyFinder(ks);
			VaadinSession.getCurrent().setAttribute(SPARQLBasedTrivialInconsistencyFinder.class, sparqlBasedInconsistencyFinder);
//			SPARQLBasedInconsistencyFinder sparqlBasedInconsistencyFinder = new SPARQLBasedInconsistencyFinder(ks, reasonerFactory);
//			VaadinSession.getCurrent().setAttribute(SPARQLBasedInconsistencyFinder.class, sparqlBasedInconsistencyFinder);
			//explanation manager
			OWLOntology ontology = null;
			try {
				ontology = OWLManager.createOWLOntologyManager().createOntology();
			} catch (OWLOntologyCreationException e) {
				e.printStackTrace();
			}
			OWLReasoner reasoner = reasonerFactory.createNonBufferingReasoner(ontology);
			SPARQLExplanationManager expMan = new SPARQLExplanationManager(reasoner, reasonerFactory);
//			ExplanationManager expMan = new ExplanationManager(sparqlBasedInconsistencyFinder.getReasoner(), reasonerFactory);
			VaadinSession.getCurrent().setAttribute(SPARQLExplanationManager.class, expMan);
			//repair manager
			RepairManager repMan = new RepairManager(ontology);
			VaadinSession.getCurrent().setAttribute(RepairManager.class, repMan);
			
			repMan.addListener(expMan);
		}
		long t2 = System.currentTimeMillis();
		logger.info("...done in {}ms.", (t2 -t1));
	}
	
	public static KnowledgebaseManager getKnowledgebaseManager(){
		return VaadinSession.getCurrent().getAttribute(KnowledgebaseManager.class);
	}
	
	public static ExplanationManager getExplanationManager(){
		return VaadinSession.getCurrent().getAttribute(ExplanationManager.class);
	}
	
	public static SPARQLExplanationManager getSPARQLExplanationManager(){
		return VaadinSession.getCurrent().getAttribute(SPARQLExplanationManager.class);
	}
	
	public static RepairManager getRepairManager(){
		return VaadinSession.getCurrent().getAttribute(RepairManager.class);
	}
	
	public static ImpactManager getImpactManager(){
		return VaadinSession.getCurrent().getAttribute(ImpactManager.class);
	}
	
	public static ConstraintValidationManager getConstraintValidationManager(){
		return VaadinSession.getCurrent().getAttribute(ConstraintValidationManager.class);
	}
	
	public static EnrichmentManager getEnrichmentManager(){
		return VaadinSession.getCurrent().getAttribute(EnrichmentManager.class);
	}
	
	public static LearningManager getLearningManager(){
		return VaadinSession.getCurrent().getAttribute(LearningManager.class);
	}
	
//	public static SPARQLBasedInconsistencyFinder getSparqlBasedInconsistencyFinder(){
//		return VaadinSession.getCurrent().getAttribute(SPARQLBasedInconsistencyFinder.class);
//	}
	
	public static SPARQLBasedTrivialInconsistencyFinder getSparqlBasedInconsistencyFinder(){
		return VaadinSession.getCurrent().getAttribute(SPARQLBasedTrivialInconsistencyFinder.class);
	}
	
	public static OWLReasoner getOWLReasoner(){
		return VaadinSession.getCurrent().getAttribute(OWLReasoner.class);
	}
	
	public static Renderer getRenderer(){
		return VaadinSession.getCurrent().getAttribute(Renderer.class);
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#knowledgebaseChanged(org.aksw.ore.model.Knowledgebase)
	 */
	@Override
	public void knowledgebaseChanged(Knowledgebase knowledgebase) {
		
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#knowledgebaseAnalyzed(org.aksw.ore.model.Knowledgebase)
	 */
	@Override
	public void knowledgebaseAnalyzed(Knowledgebase knowledgebase) {
//		initialize(knowledgebase);
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#knowledgebaseStatusChanged(org.aksw.ore.model.Knowledgebase)
	 */
	@Override
	public void knowledgebaseStatusChanged(Knowledgebase knowledgebase) {
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#message(java.lang.String)
	 */
	@Override
	public void message(String message) {
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#knowledgebaseModified(java.util.List)
	 */
	@Override
	public void knowledgebaseModified(Set<OWLOntologyChange> changes) {
	}

}
