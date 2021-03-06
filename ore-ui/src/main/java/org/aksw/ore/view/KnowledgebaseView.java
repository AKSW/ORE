/**
 * 
 */
package org.aksw.ore.view;

import com.google.common.base.Joiner;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.*;
import com.vaadin.server.StreamResource.StreamSource;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.MenuBar.Command;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.Window.CloseEvent;
import com.vaadin.ui.Window.CloseListener;
import com.vaadin.ui.themes.ValoTheme;
import org.aksw.mole.ore.repository.tones.TONESRepository;
import org.aksw.ore.ORESession;
import org.aksw.ore.component.*;
import org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener;
import org.aksw.ore.model.Knowledgebase;
import org.aksw.ore.model.OWLOntologyKnowledgebase;
import org.aksw.ore.model.SPARQLEndpointKnowledgebase;
import org.aksw.ore.model.SPARQLKnowledgebaseStats;
import org.aksw.ore.util.URLParameters;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.utilities.owl.OWL2SPARULConverter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.profiles.OWL2Profile;
import org.semanticweb.owlapi.profiles.OWLProfile;
import org.semanticweb.owlapi.profiles.OWLProfileReport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author Lorenz Buehmann
 *
 */
public class KnowledgebaseView extends VerticalLayout implements View, KnowledgebaseLoadingListener {
	
	enum Tooltip{
		OWL_2_PROFILE("An OWL 2 profile (commonly called a fragment or a sublanguage in computational logic) "
				+ "is a trimmed down version of OWL 2 that trades some expressive power for the efficiency of reasoning."),
				OWL_2(""),
				OWL_2_EL("<em>OWL 2 EL</em> is particularly useful in applications employing ontologies that contain very large numbers of properties and/or classes. "
						+ "This profile captures the expressive power used by many such ontologies and is a subset of OWL 2 for which the basic reasoning problems "
						+ "can be performed in time that is polynomial with respect to the size of the ontology . Dedicated reasoning algorithms for this profile are"
						+ " available and have been demonstrated to be implementable in a highly scalable way. The EL acronym reflects the profile's basis "
						+ "in the EL family of description logics, logics that provide only Existential quantification."),
				OWL_2_QL("<em>OWL 2 QL</em> is aimed at applications that use very large volumes of instance data, and where query answering is the most important reasoning task. "
						+ "In OWL 2 QL, conjunctive query answering can be implemented using conventional relational database systems. Using a suitable reasoning technique, "
						+ "sound and complete conjunctive query answering can be performed in LOGSPACE with respect to the size of the data (assertions). As in OWL 2 EL, "
						+ "polynomial time algorithms can be used to implement the ontology consistency and class expression subsumption reasoning problems. The expressive "
						+ "power of the profile is necessarily quite limited, although it does include most of the main features of conceptual models such as UML class diagrams"
						+ " and ER diagrams. The QL acronym reflects the fact that query answering in this profile can be implemented by rewriting queries into a standard "
						+ "relational Query Language."),
				OWL_2_RL("<em>OWL 2 RL</em> is aimed at applications that require scalable reasoning without sacrificing too much expressive power. It is designed to accommodate OWL 2 "
						+ "applications that can trade the full expressivity of the language for efficiency, as well as RDF(S) applications that need some added expressivity. OWL 2 RL "
						+ "reasoning systems can be implemented using rule-based reasoning engines. The ontology consistency, class expression satisfiability, class expression subsumption, "
						+ "instance checking, and conjunctive query answering problems can be solved in time that is polynomial with respect to the size of the ontology. The RL acronym reflects "
						+ "the fact that reasoning in this profile can be implemented using a standard Rule Language.");
		
		String description;
				
		Tooltip(String description){
			this.description = description;
		}
		
		/**
		 * @return the description
		 */
		public String getDescription() {
			return description;
		}
	}
	
	private Label kbInfoLabel;
	private OntologyRepositoryDialog repositoryDialog;
	private KnowledgebaseChangesTable table;
	private VerticalLayout kbInfoPanel;
	private VerticalLayout changesPanel;
	private Button applyChangesButton;
	private Label noKBLabel;
	
	public KnowledgebaseView() {
		addStyleName("dashboard-view");
		addStyleName("knowledgebase-view");
		setSizeFull();
		setSpacing(true);
		setMargin(true);
		
		addComponent(createButtons());
		
		Component kbInfoPanel = createKnowledgeBaseInfo();
//		addComponent(kbInfoPanel);
		
		Component changesPanel = createChangesPanel();
//		addComponent(changesPanel);
		
		HorizontalLayout bottom = new HorizontalLayout();
		bottom.setSizeFull();
		bottom.addComponent(kbInfoPanel);
		bottom.addComponent(changesPanel);
		addComponent(bottom);
		
		setExpandRatio(bottom, 1f);
		
		ORESession.getKnowledgebaseManager().addListener(this);
		ORESession.getKnowledgebaseManager().addListener(table);
		
		refresh();
		
		showNoKBInfo();
	}
	
	private Component createChangesPanel(){
		changesPanel = new VerticalLayout();
		changesPanel.setImmediate(true);
		changesPanel.setSizeFull();
		Label label = new Label("<h3>Changes:</h3>", ContentMode.HTML);
		changesPanel.addComponent(label);
		
		table = new KnowledgebaseChangesTable();
		table.setSizeFull();
		changesPanel.addComponent(table);
		changesPanel.setExpandRatio(table, 1f);
		
		applyChangesButton = new Button("Export");
		applyChangesButton.addClickListener(new ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				onExport();
			}
		});
		changesPanel.addComponent(applyChangesButton);
		changesPanel.setComponentAlignment(applyChangesButton, Alignment.MIDDLE_RIGHT);
		
		return changesPanel;
	}
	
	private Component createButtons(){
		VerticalLayout layout = new VerticalLayout();
//		layout.setSizeFull();
		layout.setWidth("100%");
		layout.setHeight(null);
//		layout.setSizeUndefined();
		
		Label label = new Label("<h3>Set up knowledge base:</h3>", ContentMode.HTML);
//		label.setHeight(null);
		layout.addComponent(label);
		
		HorizontalLayout buttons = new HorizontalLayout();
		buttons.setWidth(null);
		buttons.setSpacing(true);
		buttons.setStyleName("kb-info");
		layout.addComponent(buttons);
		layout.setExpandRatio(buttons, 1f);
		
//		Button ontologyButton = new Button("OWL Ontology");
//		ontologyButton.addStyleName("ontology-button");
//		buttons.addComponent(ontologyButton);
//		buttons.setComponentAlignment(ontologyButton, Alignment.MIDDLE_RIGHT);
		
		//OWL Ontology
		ThemeResource icon = new ThemeResource("img/owl-ontology-128.png");
		MenuBar split = new MenuBar();
        MenuBar.MenuItem dropdown = split.addItem("OWL Ontology", null);
        dropdown.setIcon(icon);
        dropdown.addItem("From File", new Command() {
			
			@Override
			public void menuSelected(MenuItem selectedItem) {
				onLoadOntologyFromFile();
			}
		});
        dropdown.addItem("From URI", new Command() {
			
			@Override
			public void menuSelected(MenuItem selectedItem) {
				onLoadOntologyFromURI();
			}
		});
        dropdown.addItem("Ontology Repository", new Command() {
			
			@Override
			public void menuSelected(MenuItem selectedItem) {
				onLoadOntologyFromRepository();
			}
		});
        split.addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
        buttons.addComponent(split);
		buttons.setComponentAlignment(split, Alignment.MIDDLE_RIGHT);
		
		/*
		Button ontologyButton = new PopupButton("OWL Ontology");
		ontologyButton.addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
//		ontologyButton.addStyleName("ontology-button");
		ThemeResource icon = new ThemeResource("img/owl-ontology-128.png");
		ontologyButton.setIcon(icon);
		buttons.addComponent(ontologyButton);
		buttons.setComponentAlignment(ontologyButton, Alignment.MIDDLE_RIGHT);
		VerticalLayout popupLayout = new VerticalLayout();
		popupLayout.setSpacing(true);
		((PopupButton)ontologyButton).setContent(popupLayout);
		Button button = new Button("From file", new ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				onLoadOntologyFromFile();
			}
		});
		popupLayout.addComponent(button);
		button.setWidth("100%");
		button = new Button("From URI", new ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				onLoadOntologyFromURI();
			}
		});
		button.setWidth("100%");
		popupLayout.addComponent(button);
		popupLayout.addComponent(new Button("Ontology repository", new ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				onLoadOntologyFromRepository();
			}
		}));
		*/
		
		//SPARQL endpoint
		Button endpointButton = new Button("SPARQL Endpoint", new ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				onSetSPARQLEndpoint();
			}
		});
		endpointButton.setIcon(new ThemeResource("img/sparql-128.png"));
		endpointButton.addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
//		endpointButton.setHeight("140px");
//		endpointButton.addStyleName("borderless");
//		endpointButton.setHeight("100%");
		buttons.addComponent(endpointButton);
		buttons.setComponentAlignment(endpointButton, Alignment.MIDDLE_LEFT);
		
		return layout;
	}
	
	private void onLoadOntologyFromFile(){
		FileUploadDialog dialog = new FileUploadDialog();
		getUI().addWindow(dialog);
	}
	
	private void onLoadOntologyFromURI(){
		LoadFromURIDialog dialog = new LoadFromURIDialog();
		getUI().addWindow(dialog);
	}
	
	private void onLoadOntologyFromURI(String ontologyURI){
		LoadFromURIDialog dialog = new LoadFromURIDialog(ontologyURI);
		getUI().addWindow(dialog);
		dialog.onLoadOntology();
	}
	
	private void onLoadOntologyFromRepository(){
		if(repositoryDialog == null){
			TONESRepository repository = new TONESRepository();
			repository.initialize();
			repositoryDialog = new OntologyRepositoryDialog(repository);
		}
		getUI().addWindow(repositoryDialog);
	}
	
	private void onSaveOntology(){
		StreamResource res = new StreamResource(new StreamSource() {
			
			@Override
			public InputStream getStream() {
				Knowledgebase knowledgebase = ORESession.getKnowledgebaseManager().getKnowledgebase();
				if(knowledgebase != null){
					if(knowledgebase instanceof OWLOntologyKnowledgebase){
						final OWLOntology ontology = ((OWLOntologyKnowledgebase) knowledgebase).getOntology();
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						ByteArrayInputStream bais;
						try {
							ontology.getOWLOntologyManager().saveOntology(ontology, new RDFXMLDocumentFormat(), baos);
							bais = new ByteArrayInputStream(baos.toByteArray());
							baos.close();
							return bais;
						} catch (OWLOntologyStorageException | IOException e) {
							e.printStackTrace();
						}
					}
				}
				return null;
			}
		},"ontology.owl");
		FileDownloader downloader = new FileDownloader(res);
		downloader.extend(applyChangesButton);
	}
	
	private void onSetSPARQLEndpoint(){
		SPARQLEndpointDialog dialog = new SPARQLEndpointDialog();
		getUI().addWindow(dialog);
	}
	
	private void onSetSPARQLEndpoint(String endpointURL, String defaultGraph){
		SPARQLEndpointDialog dialog = new SPARQLEndpointDialog(endpointURL, defaultGraph);
		getUI().addWindow(dialog);
		dialog.okButton.click();
	}
	
	public void refresh(){
		Knowledgebase knowledgebase = ORESession.getKnowledgebaseManager().getKnowledgebase();
		if(knowledgebase != null){
			kbInfoLabel.setVisible(true);
			noKBLabel.setVisible(false);
			if(knowledgebase instanceof OWLOntologyKnowledgebase){
				visualizeOntology((OWLOntologyKnowledgebase) knowledgebase);
				applyChangesButton.setDescription("Export modified ontology.");
				onSaveOntology();
			} else {
				visualizeSPARQLEndpoint((SPARQLEndpointKnowledgebase) knowledgebase);
				applyChangesButton.setDescription("Export changes as SPARQL 1.1 Update statements.");
			}
		}
		changesPanel.setVisible(false);
	}
	
	private Component createKnowledgeBaseInfo(){
		kbInfoPanel = new VerticalLayout();
		kbInfoPanel.setSizeFull();
		
		Label label = new Label("<h3>Current knowledge base:<h3>", ContentMode.HTML);
		label.setHeight(null);
		kbInfoPanel.addComponent(label);
		
		kbInfoLabel = new Label("</br>");
		kbInfoLabel.addStyleName("kb-info-label");
//		kbInfoLabel.addStyleName(ValoTheme.LABEL_NO_MARGIN);
		kbInfoLabel.setWidthUndefined();
		kbInfoLabel.setContentMode(ContentMode.HTML);
		
		kbInfoPanel.addComponent(kbInfoLabel);
		kbInfoPanel.setExpandRatio(kbInfoLabel, 1f);
		
		// hidden on start
		kbInfoLabel.setVisible(false);
		
		return kbInfoPanel;
	}
	
	private void showNoKBInfo(){
		kbInfoLabel.setVisible(false);
		FontAwesome fontIcon = FontAwesome.FROWN_O;
		String html = "<span class='icon' style=\"display:block;font-family: " + fontIcon.getFontFamily()
                + ";\">&#x" + Integer.toHexString(fontIcon.getCodepoint()) + ";</span>";
		noKBLabel = new Label(html + "<span class='text'>You haven't set up a knowledge base, yet!</span>", ContentMode.HTML);
		noKBLabel.setStyleName("no-kb-label");
		noKBLabel.setWidth(null);
//		noKBLabel.setSizeFull();
		kbInfoPanel.addComponent(noKBLabel);
		kbInfoPanel.setExpandRatio(noKBLabel, 1f);
		kbInfoPanel.setComponentAlignment(noKBLabel, Alignment.MIDDLE_CENTER);
	}
	
	private void onExport(){
		if(ORESession.getKnowledgebaseManager().getKnowledgebase() instanceof SPARQLEndpointKnowledgebase){
			onDumpSPARUL();
		}
	}
	
	private void onDumpSPARUL(){
		try {
			OWLOntologyManager man = OWLManager.createOWLOntologyManager();
			OWLOntology ontology = man.createOntology();
			OWL2SPARULConverter translator = new OWL2SPARULConverter(ontology, false);
			Set<OWLOntologyChange> changes = ORESession.getKnowledgebaseManager().getChanges();
			if(!changes.isEmpty()){
				VerticalLayout content = new VerticalLayout();
				String sparulString = translator.convert(new ArrayList<>(changes));
				content.addComponent(new Label(sparulString, ContentMode.PREFORMATTED));
				final Window window = new Window("SPARQL 1.1 Update statements", content);
				window.setWidth("1000px");
				window.setHeight("400px");
				window.center();
				window.addCloseListener(new CloseListener() {
					
					@Override
					public void windowClose(CloseEvent e) {
						getUI().removeWindow(window);
					}
				});
				getUI().addWindow(window);
				window.focus();
			}
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		}
	}
	
	private void visualizeSPARQLEndpoint(SPARQLEndpointKnowledgebase kb){
		SparqlEndpoint endpoint = kb.getEndpoint().getEndpoint();
		SPARQLKnowledgebaseStats stats = kb.getStats();
		
		String header = "<h4>SPARQL Endpoint</h4><hr>";
		
		
		String url = endpoint.getURL().toString();
		String htmlTable = 
				"<table>" +
				"<tr class=\"even\"><td>URL</td><td><a href=\"" + url + "\">" + url + "</td></tr>";
		if(!endpoint.getDefaultGraphURIs().isEmpty()){
			htmlTable += "<tr class=\"odd\"><td>Default Graph</td><td>" + endpoint.getDefaultGraphURIs().iterator().next() + "</td></tr>";
		}
		if(!endpoint.getNamedGraphURIs().isEmpty()){
			htmlTable += "<tr class=\"odd\"><td>Named Graph(s)</td><td>" + Joiner.on(", ").join(endpoint.getNamedGraphURIs()) + "</td></tr>";
		}
		if(stats != null){
			if(stats.getOwlClassCnt() != -1){
				htmlTable += "<tr class=\"even\"><td title=\"Classes can be understood as sets of individuals.\">Classes</td><td>" + stats.getOwlClassCnt() + "</td></tr>";
			}
			if(stats.getOwlObjectPropertyCnt() != -1){
				htmlTable += "<tr class=\"odd\"><td title=\"Object properties connect pairs of individuals.\">Object Properties</td><td>" + stats.getOwlObjectPropertyCnt() + "</td></tr>";
			}
			if(stats.getOwlDataPropertyCnt() != -1){
				htmlTable += "<tr class=\"even\"><td title=\"Data properties connect individuals with literals. "
						+ "In some knowledge representation systems, functional data properties are called attributes.\">Data Properties</td><td>" + stats.getOwlDataPropertyCnt() + "</td></tr>";
			}
		
		}
		htmlTable += "</table>";	
		
		kbInfoLabel.setValue(header + htmlTable);
	}
	
	private void visualizeOntology(OWLOntologyKnowledgebase kb){
		OWLOntology ontology = kb.getOntology();
		int nrOfClasses = ontology.getClassesInSignature(Imports.INCLUDED).size();
		int nrOfObjectProperties = ontology.getObjectPropertiesInSignature(Imports.INCLUDED).size();
		int nrOfDataProperties = ontology.getDataPropertiesInSignature(Imports.INCLUDED).size();
		int nrOfIndividuals = ontology.getIndividualsInSignature(Imports.INCLUDED).size();
		OWLProfileReport report = new OWL2Profile().checkOntology(ontology);
		OWLProfile profile = report.getProfile();
		
		String header = "<h4>OWL Ontology</h4><hr>";
		
		String htmlTable = 
				"<table>" +
				"<tr class=\"even\"><td>Classes</td><td>" + nrOfClasses + "</td></tr>" +
				"<tr class=\"odd\"><td>Object Properties</td><td>" + nrOfObjectProperties + "</td></tr>" +
				"<tr class=\"even\"><td>Data Properties</td><td>" + nrOfDataProperties + "</td></tr>" +
				"<tr class=\"odd\"><td>Individuals</td><td>" + nrOfIndividuals + "</td></tr>" +
				"<tr class=\"even\"><td title=\"" +  Tooltip.OWL_2_PROFILE.getDescription() + "\">OWL 2 Profile</td><td>" + profile.getName() + "</td></tr>" +
				"<tr class=\"odd\"><td>Consistent</td><td>" + kb.isConsistent() 
				+ (!kb.isConsistent() ? ("<font color=\"red\" style='margin-left: 10px;'>" + FontAwesome.EXCLAMATION_TRIANGLE.getHtml() + "</font>") : "")
				+ "</td></tr>";
		if(kb.isConsistent()){
			htmlTable += "<tr class=\"even\"><td>Coherent</td><td>" + kb.isCoherent() + "</td></tr>";
			if(!kb.isCoherent()){
				htmlTable += "<tr class=\"odd\"><td>Unsatisfiable Classes</td><td>" + kb.getReasoner().getUnsatisfiableClasses().getEntitiesMinusBottom().size()
						+ "<font color=\"red\" style='margin-left: 10px;'>" + FontAwesome.EXCLAMATION_TRIANGLE.getHtml() + "</font>" + "</td></tr>";
			}
					
		}
		htmlTable += "</table>";
		
		kbInfoLabel.setValue(header + htmlTable);
	}

	/* (non-Javadoc)
	 * @see com.vaadin.navigator.View#enter(com.vaadin.navigator.ViewChangeListener.ViewChangeEvent)
	 */
	@Override
	public void enter(ViewChangeEvent event) {
		handleURLRequestParameters();
	}
	
	private void handleURLRequestParameters(){
		Map<String, String[]> parameterMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		parameterMap.putAll(VaadinService.getCurrentRequest().getParameterMap());
		
		String endpointURL = parameterMap.containsKey(URLParameters.ENDPOINT_URL) ? parameterMap.get(URLParameters.ENDPOINT_URL)[0] : null;
		String defaultGraph = parameterMap.containsKey(URLParameters.DEFAULT_GRAPH) ? parameterMap.get(URLParameters.DEFAULT_GRAPH)[0] : null;
		String ontologyURI = parameterMap.containsKey(URLParameters.ONTOLOGY_URL) ? parameterMap.get(URLParameters.ONTOLOGY_URL)[0] : null;
		
		if(endpointURL != null){
			onSetSPARQLEndpoint(endpointURL, defaultGraph);
		} else if(ontologyURI != null){
			onLoadOntologyFromURI(ontologyURI);
		}
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#knowledgebaseChanged(org.aksw.ore.model.Knowledgebase)
	 */
	@Override
	public void knowledgebaseChanged(Knowledgebase knowledgebase) {
		onAnalyzeKnowledgebase();
	}
	
	private void onAnalyzeKnowledgebase(){
		final KnowledgebaseAnalyzationDialog dialog = new KnowledgebaseAnalyzationDialog();
		ORESession.getKnowledgebaseManager().addListener(dialog);
		UI.getCurrent().addWindow(dialog);
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				ORESession.getKnowledgebaseManager().analyzeKnowledgebase();
				ORESession.getKnowledgebaseManager().removeListener(dialog);
				UI.getCurrent().access(new Runnable() {

					@Override
					public void run() {
						dialog.close();
					}
				});
			}
		}).start();
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#knowledgebaseAnalyzed(org.aksw.ore.model.Knowledgebase)
	 */
	@Override
	public void knowledgebaseAnalyzed(Knowledgebase knowledgebase) {
		UI.getCurrent().access(new Runnable() {
			
			@Override
			public void run() {
				refresh();
			}
		});
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
		changesPanel.setVisible(!changes.isEmpty());
	}

}
