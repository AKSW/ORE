import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.aksw.mole.ore.impact.ClassificationImpactChecker;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.OWLEntityCollector;

import uk.ac.manchester.cs.owlapi.modularity.ModuleType;

import com.clarkparsia.modularity.IncrementalClassifier;
import com.clarkparsia.modularity.ModularityUtils;
import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;


public class DebuggingTest extends TestCase{
	
	private static final String ONTOLOGY_IRI = "http://protege.stanford.edu/plugins/owl/owl-library/koala.owl";
	private static final String ONTOLOGY_BASE = "http://protege.stanford.edu/plugins/owl/owl-library/koala.owl#";
	
	private OWLOntology ontology;
	private OWLOntologyManager manager;
	private OWLReasoner reasoner;
	private OWLReasonerFactory reasonerFactory;
	private OWLDataFactory dataFactory;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		manager = OWLManager.createOWLOntologyManager();
		ontology = manager.loadOntology(IRI.create(ONTOLOGY_IRI));
		dataFactory = manager.getOWLDataFactory();
		reasonerFactory = PelletReasonerFactory.getInstance();
		reasoner = reasonerFactory.createNonBufferingReasoner(ontology);
		reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY, InferenceType.CLASS_ASSERTIONS);
		
	}
	
	
	public void testModuleExtraction(){
		Set<OWLEntity> signature = new HashSet<OWLEntity>();
		signature.add(dataFactory.getOWLClass(IRI.create(ONTOLOGY_BASE + "KoalaWithPhD")));
		
		Set<OWLAxiom> module = ModularityUtils.extractModule(ontology, signature, ModuleType.TOP);
	}
	
	public void testIncrementalClassifier(){
		IncrementalClassifier incReasoner = new IncrementalClassifier(ontology);
		incReasoner.classify();
	}
	
	public void testIncrementalClassifierWithAnnotations(){
		OWLAnnotationProperty annoProp = dataFactory.getOWLAnnotationProperty(IRI.create(ONTOLOGY_BASE + "annoProp"));
		OWLAxiom ax = dataFactory.getOWLAnnotationPropertyDomainAxiom(annoProp, IRI.create(ONTOLOGY_BASE + "annoDomain"));
		manager.addAxiom(ontology, ax);
		IncrementalClassifier incReasoner = new IncrementalClassifier(ontology);
		incReasoner.classify();
	}
	
	public void testClassificationImpactChecker(){
		IncrementalClassifier incReasoner = new IncrementalClassifier(ontology);
		incReasoner.classify();
		ClassificationImpactChecker checker = new ClassificationImpactChecker(incReasoner);
		OWLOntologyChange change = new RemoveAxiom(ontology, 
				dataFactory.getOWLObjectPropertyDomainAxiom(
						dataFactory.getOWLObjectProperty(IRI.create(ONTOLOGY_BASE + "isHardWorking")),
						dataFactory.getOWLClass(IRI.create(ONTOLOGY_BASE + "Person"))));
		System.out.println(checker.getImpact(Collections.singletonList(change)));
	}

}