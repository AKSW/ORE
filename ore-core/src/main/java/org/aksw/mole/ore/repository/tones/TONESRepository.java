package org.aksw.mole.ore.repository.tones;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.aksw.mole.ore.repository.OntologyRepository;
import org.aksw.mole.ore.repository.OntologyRepositoryEntry;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.util.OntologyIRIShortFormProvider;

public class TONESRepository implements OntologyRepository{
	
	private final String repositoryName = "TONES";

    private final URI repositoryLocation = URI.create("http://owl.cs.manchester.ac.uk/repository");

    private List<RepositoryEntry> entries;

    private OWLOntologyIRIMapper iriMapper;


    public TONESRepository() {
        entries = new ArrayList<RepositoryEntry>();
        iriMapper = new RepositoryIRIMapper();
    }

    @Override
    public void initialize() {
    	refresh();
    }


    public String getName() {
        return repositoryName;
    }


    public String getLocation() {
        return repositoryLocation.toString();
    }


    public void refresh() {
        fillRepository();
    }


    public Collection<OntologyRepositoryEntry> getEntries() {
        List<OntologyRepositoryEntry> ret = new ArrayList<OntologyRepositoryEntry>();
        ret.addAll(entries);
        return ret;
    }


    public List<Object> getMetaDataKeys() {
        return Collections.emptyList();
    }


    public void dispose() throws Exception {
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Implementation details


    private void fillRepository() {
        try {
            entries.clear();
            URI listURI = URI.create(repositoryLocation + "/list");
            BufferedReader br = new BufferedReader(new InputStreamReader(listURI.toURL().openStream()));
            String line;
            while((line = br.readLine()) != null) {
                try {
                    entries.add(new RepositoryEntry(new URI(line)));
                }
                catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class RepositoryEntry implements OntologyRepositoryEntry {

        private String shortName;

        private URI ontologyURI;

        private URI physicalURI;

        public RepositoryEntry(URI ontologyIRI) {
            this.ontologyURI = ontologyIRI;
            OntologyIRIShortFormProvider sfp = new OntologyIRIShortFormProvider();
            shortName = sfp.getShortForm(IRI.create(ontologyIRI));
            physicalURI = URI.create(repositoryLocation + "/download?ontology=" + ontologyIRI);
        }


        public String getOntologyShortName() {
            return shortName;
        }


        public URI getOntologyURI() {
            return ontologyURI;
        }


        public URI getPhysicalURI() {
            return physicalURI;
        }


        public String getMetaData(Object key) {
            return null;
        }

    }


    private class RepositoryIRIMapper implements OWLOntologyIRIMapper {

        public IRI getDocumentIRI(IRI iri) {
            for(RepositoryEntry entry : entries) {
                if(entry.getOntologyURI().equals(iri.toURI())) {
                    return IRI.create(entry.getPhysicalURI());
                }
            }
            return null;
        }
    }

}