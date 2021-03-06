package org.aksw.ore.view;

import com.google.common.collect.Lists;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.FontAwesome;
import com.vaadin.shared.ui.combobox.FilteringMode;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.MenuBar.Command;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.Slider.ValueOutOfBoundsException;
import com.vaadin.ui.Window.CloseEvent;
import com.vaadin.ui.Window.CloseListener;
import com.vaadin.ui.themes.ValoTheme;
import org.aksw.ore.ORESession;
import org.aksw.ore.component.AxiomTypesTable;
import org.aksw.ore.component.CollapsibleBox;
import org.aksw.ore.component.EnrichmentProgressDialog;
import org.aksw.ore.component.EvaluatedAxiomsTable;
import org.aksw.ore.exception.OREException;
import org.aksw.ore.manager.EnrichmentManager;
import org.aksw.ore.model.SPARQLEndpointKnowledgebase;
import org.dllearner.core.EvaluatedAxiom;
import org.dllearner.learningproblems.AxiomScore;
import org.dllearner.utilities.owl.OWL2SPARULConverter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.vaadin.risto.stepper.IntStepper;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

import java.util.*;

public class EnrichmentView extends HorizontalSplitPanel implements View, Refreshable{
	
	private ComboBox resourceURIField;
	private ResourceTypeField resourceTypeField;
	private CheckBox useInferenceBox;
	private IntStepper maxExecutionTimeSpinner;
	private IntStepper maxNrOfReturnedAxiomsSpinner;
	private Slider thresholdSlider;
	
	private AxiomTypesTable axiomTypesTable;
	private AxiomTypesField axiomTypesField;
	
	private Button startButton;
	
	private AbstractLayout axiomsPanel;
	
	private List<AxiomType<OWLAxiom>> pendingAxiomTypes;
	
	private Set<EvaluatedAxiomsTable> tables = new HashSet<>();
	private Button addToKbButton;
	private Button dumpSPARULButton;

	public EnrichmentView() {
		initUI();
	}
	
	private void initUI(){
		addStyleName("enrichment-view");
//		addStyleName("dashboard-view");
		setSizeFull();
		setSplitPosition(25);
		
		Component leftSide = createLeftSide();
		addComponent(leftSide);
		
		Component rightSide = createRightSide();
		addComponent(rightSide);
		
		resourceURIField.focus();
		
		reset();
//		resourceURIField.setValue("http://dbpedia.org/ontology/birthPlace");
//		resourceTypeField.setResourceType(ResourceType.OBJECT_PROPERTY);
//		showDummyTables();
	}
	
	private VerticalLayout createLeftSide(){
		VerticalLayout leftSide = new VerticalLayout();
		leftSide.addStyleName("enrichment-left-panel");
		leftSide.setSizeFull();
		leftSide.setCaption("Options");
		leftSide.setSpacing(true);
//		leftSide.setMargin(true);
		
		Label titleLabel = new Label("Options");
        titleLabel.setSizeUndefined();
        titleLabel.addStyleName(ValoTheme.LABEL_H2);
        titleLabel.addStyleName(ValoTheme.LABEL_NO_MARGIN);
        leftSide.addComponent(titleLabel);
		
		Component configForm = createConfigForm();
//		configForm = new Panel(configForm);
//		configForm.setSizeFull();
		leftSide.addComponent(configForm);
		leftSide.setExpandRatio(configForm, 1f);
		
		startButton = new Button("Start");
		startButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
		startButton.setDescription("Click to start the learning process.");
//		startButton.setDisableOnClick(true);
		startButton.addClickListener(new Button.ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				onLearning();
			}
		});
		
		leftSide.addComponent(startButton);
		leftSide.setComponentAlignment(startButton, Alignment.MIDDLE_CENTER);
		
		return leftSide;
	}
	
	private Component createConfigForm(){
		VerticalLayout form = new VerticalLayout();
		form.setSizeFull();
		form.setSpacing(true);
		form.addStyleName("enrichment-options");
		form.setDefaultComponentAlignment(Alignment.MIDDLE_LEFT);
		
		SPARQLEndpointKnowledgebase kb = (SPARQLEndpointKnowledgebase) ORESession.getKnowledgebaseManager().getKnowledgebase();
		resourceURIField = new ComboBox("Resource URI");
//		resourceURIField.addStyleName("entity-combobox");
		resourceURIField.addStyleName(ValoTheme.COMBOBOX_SMALL);
		resourceURIField.setWidth("100%");
		resourceURIField.setFilteringMode(FilteringMode.CONTAINS);
		resourceURIField.setNewItemsAllowed(false);
		Set<String> entities = new TreeSet<>();
		entities.addAll(kb.getStats().getClasses());
		entities.addAll(kb.getStats().getObjectProperties());
		entities.addAll(kb.getStats().getDataProperties());
		IndexedContainer container = new IndexedContainer(entities);
		resourceURIField.setContainerDataSource(container);
		resourceURIField.addValueChangeListener(new ValueChangeListener() {
			
			@Override
			public void valueChange(ValueChangeEvent event) {
				try {
					EntityType<? extends OWLEntity> entityType = ORESession.getEnrichmentManager().getEntityType((String)resourceURIField.getValue());
					onResourceTypeChanged(entityType);
				} catch (OREException e) {
					e.printStackTrace();
				}
			}
		});
		form.addComponent(resourceURIField);
		
		resourceTypeField = new ResourceTypeField();
//		form.addComponent(resourceTypeField);
		
		// advanced options
		Component advancedOptionsPanel = createAdvancedOptionsPanel();
		form.addComponent(advancedOptionsPanel);
		
		// axiom types
		axiomTypesField = new AxiomTypesField();
//		axiomTypesField.setSizeFull();
//		axiomTypesField.setHeight("300px");
//		axiomTypesField.setHeightUndefined();
		form.addComponent(axiomTypesField);
//		form.setExpandRatio(axiomTypesField, 1f);
		
		Label l = new Label("<br/>", ContentMode.HTML);
		form.addComponent(l);
		form.setExpandRatio(l, 1f);
		
		form.setComponentAlignment(resourceURIField, Alignment.TOP_CENTER);
		form.setComponentAlignment(advancedOptionsPanel, Alignment.TOP_CENTER);
		
		return form;
	}
	
	private Component createAdvancedOptionsPanel(){
		VerticalLayout panel = new VerticalLayout();
		panel.setSizeFull();
		panel.setHeightUndefined();
		
		useInferenceBox = new CheckBox();
		useInferenceBox.setCaption("Use Inference");
		useInferenceBox.addStyleName(ValoTheme.CHECKBOX_SMALL);
		useInferenceBox.setDescription("If inference is enabled, some light weight form of reasoning is used to make use of implicit information. "
				+ "Please note that this opton makes the algorihtms more complex and eventually slower!");
		panel.addComponent(useInferenceBox);
		
		maxExecutionTimeSpinner = new IntStepper();
		maxExecutionTimeSpinner.setValue(10);
		maxExecutionTimeSpinner.setStepAmount(1);
		maxExecutionTimeSpinner.setWidth("100%");
		maxExecutionTimeSpinner.setCaption("Max. execution time");
		maxExecutionTimeSpinner.setDescription("The maximum runtime in seconds for each particlar axiom type.");
		panel.addComponent(maxExecutionTimeSpinner);
		
		maxNrOfReturnedAxiomsSpinner = new IntStepper();
		maxNrOfReturnedAxiomsSpinner.setValue(10);
		maxNrOfReturnedAxiomsSpinner.setStepAmount(1);
		maxNrOfReturnedAxiomsSpinner.setWidth("100%");
		maxNrOfReturnedAxiomsSpinner.setCaption("Max. returned axioms");
		maxNrOfReturnedAxiomsSpinner.setDescription("The maximum number of shown axioms per "
				+ "axiom type with a confidence score above the chosen threshold below.");
		panel.addComponent(maxNrOfReturnedAxiomsSpinner);
		
		thresholdSlider = new Slider(1, 100);
		thresholdSlider.setWidth("100%");
		thresholdSlider.setImmediate(true);
		thresholdSlider.setCaption("Threshold");
		thresholdSlider.setDescription("The minimum confidence score for the learned axioms.");
		panel.addComponent(thresholdSlider);
		
		CollapsibleBox collapsibleBox = new CollapsibleBox("Advanced", panel);
		
		return collapsibleBox;
	}
	
	/**
	 * @return
	 */
	private Component createRightSide() {
		VerticalLayout rightSide = new VerticalLayout();
		rightSide.addStyleName("enrichment-right-panel");
//		rightSide.addStyleName("enrichment-axioms-panel");
//		rightSide.addStyleName("dashboard-view");
		rightSide.setSizeFull();
		rightSide.setSpacing(true);
		rightSide.setCaption("Learned axioms");
		
		Label titleLabel = new Label("Learned Axioms");
        titleLabel.setId("dashboard-title");
        titleLabel.setSizeUndefined();
        titleLabel.addStyleName(ValoTheme.LABEL_H2);
        titleLabel.addStyleName(ValoTheme.LABEL_NO_MARGIN);
        rightSide.addComponent(titleLabel);
//		addComponent(new WhitePanel(rightSide));
//		addComponent(createContentWrapper(rightSide));
		
		Component panel = createAxiomsPanel();
		rightSide.addComponent(panel);
		rightSide.setExpandRatio(panel, 1f);
		
		HorizontalLayout buttons = new HorizontalLayout();
		buttons.setSpacing(true);
		buttons.setWidth(null);
		rightSide.addComponent(buttons);
		rightSide.setComponentAlignment(buttons, Alignment.MIDDLE_RIGHT);
		
		addToKbButton = new Button("Add");
		addToKbButton.setHeight(null);
		addToKbButton.setImmediate(true);
		addToKbButton.setDescription("(Virtually) add the selected axioms to the knowledge base.(visible in 'Knowledge Base' view)");
		addToKbButton.addClickListener(new Button.ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				OWLOntology ontology = ((SPARQLEndpointKnowledgebase)ORESession.getKnowledgebaseManager().getKnowledgebase()).getBaseOntology();
				List<OWLOntologyChange> changes = new ArrayList<>();
				for(EvaluatedAxiomsTable table : tables){
					Set<Object> selectedObjects = table.getSelectedObjects();
					for(Object o : selectedObjects){
						changes.add(new AddAxiom(ontology, ((EvaluatedAxiom)o).getAxiom()));
					}
				}
				if(!changes.isEmpty()){
					ORESession.getRepairManager().addToRepairPlan(changes);
					ORESession.getKnowledgebaseManager().addChanges(changes);
					Notification.show("Applied changes.", Type.TRAY_NOTIFICATION);
				}
			}
		});
		buttons.addComponent(addToKbButton);
		buttons.setComponentAlignment(addToKbButton, Alignment.MIDDLE_RIGHT);
		
		dumpSPARULButton = new Button("Export");
		dumpSPARULButton.setHeight(null);
		dumpSPARULButton.setImmediate(true);
		dumpSPARULButton.setDescription("Export the selected axioms as SPARQL 1.1 Update statements.");
		dumpSPARULButton.addClickListener(new Button.ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				onDumpSPARUL();
			}
		});
		buttons.addComponent(dumpSPARULButton);
		buttons.setComponentAlignment(dumpSPARULButton, Alignment.MIDDLE_RIGHT);
		
		return rightSide;
	}

	private void showDummyTables() {
		OWLDataFactory df = new OWLDataFactoryImpl();
		
		OWLObjectProperty p = df.getOWLObjectProperty(IRI.create("http://dbpedia.org/ontology/league"));
		
		List<EvaluatedAxiom<OWLAxiom>> axioms = Lists.newArrayList();
		for (int i = 0; i < 10; i++) {
			double score = 1 - Math.random();
			axioms.add(new EvaluatedAxiom<>(
					df.getOWLObjectPropertyRangeAxiom(
							p,
							df.getOWLClass(IRI.create("http://dbpedia.org/ontology/Class" + i))),
					new AxiomScore(score)));
		}
		showTable(AxiomType.OBJECT_PROPERTY_RANGE, axioms);
		
		axioms = Lists.newArrayList();
		for (int i = 0; i < 20; i++) {
			double score = 1 - Math.random();
			axioms.add(new EvaluatedAxiom<>(
					df.getOWLObjectPropertyDomainAxiom(
							p,
							df.getOWLClass(IRI.create("http://dbpedia.org/ontology/Class" + i))),
					new AxiomScore(score)));
		}
		showTable(AxiomType.OBJECT_PROPERTY_DOMAIN, axioms);
		
		axioms = Lists.newArrayList();
		for (int i = 0; i < 2; i++) {
			double score = 1 - Math.random();
			axioms.add(new EvaluatedAxiom<>(
					df.getOWLObjectPropertyDomainAxiom(
							p,
							df.getOWLClass(IRI.create("http://dbpedia.org/ontology/Class" + i))),
					new AxiomScore(score)));
		}
		showTable(AxiomType.OBJECT_PROPERTY_DOMAIN, axioms);
	}
	
	private Component createAxiomsPanel(){
//		VerticalLayout component = new VerticalLayout();
//		component.setSizeFull();
//		component.setMargin(true);
//		component.setSpacing(true);
//		component.addStyleName("enrichment-axioms-panel");
//		component.addStyleName("dashboard-panels");
//		component.setHeight(null);
		
		CssLayout component = new CssLayout();
//		component.addStyleName("enrichment-axioms-tables");
		component.addStyleName("dashboard-panels");
		component.addStyleName("axiom-panels");
		component.setWidth("100%");
		component.setHeight(null);
		
//		Panel panel = new Panel(component);
//		panel.setSizeFull();
		
		axiomsPanel = component;
		
		return axiomsPanel;
	}
	
	
	
	private void onLearning(){
		axiomsPanel.removeAllComponents();
		tables = new HashSet<>();
		
		final EnrichmentManager man = ORESession.getEnrichmentManager();
		
		final String resourceURI = (String) resourceURIField.getValue();
		
		pendingAxiomTypes = new ArrayList<>(axiomTypesField.getSelectedAxiomsTypes());
		
		boolean useInference = (Boolean) useInferenceBox.getValue();
		man.setUseInference(useInference);
		
		int maxExecutionTimeInSeconds = (Integer) maxExecutionTimeSpinner.getValue();
		man.setMaxExecutionTimeInSeconds(maxExecutionTimeInSeconds);
		
		int maxNrOfReturnedAxioms = (Integer) maxNrOfReturnedAxiomsSpinner.getValue();
		man.setMaxNrOfReturnedAxioms(maxNrOfReturnedAxioms);
		
		double threshold = (Double) thresholdSlider.getValue()/100;
		man.setThreshold(threshold);
		
		EntityType<? extends OWLEntity> resourceType = getSelectedResourceType();
		man.setResourceType(resourceType);
		
		if(resourceType == null){
			try {
				resourceType = man.getEntityType(resourceURI);
				Label label = new Label("Entity <b>" + resourceURI + "</b> is processed as " + resourceType.toString() + ".", ContentMode.HTML);
				label.addStyleName("entity-autodetect-info-label");
				pendingAxiomTypes.retainAll(man.getAxiomTypes(resourceType));
			} catch (OREException e) {
				e.printStackTrace();
			}
		}
		
		EnrichmentProgressDialog progressDialog = new EnrichmentProgressDialog(pendingAxiomTypes);
		man.addProgressMonitor(progressDialog);
		getUI().addWindow(progressDialog);
		
		try {
			final OWLEntity entity = man.getEntity(resourceURI);
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					man.generateEvaluatedAxioms(entity, new HashSet<>(pendingAxiomTypes));
					for (final AxiomType<OWLAxiom> axiomType : pendingAxiomTypes) {
						final List<EvaluatedAxiom<OWLAxiom>> evaluatedAxioms = man.getEvaluatedAxioms(entity, axiomType);
						
						UI.getCurrent().access(new Runnable() {
							@Override
							public void run() {
								showTable(axiomType, evaluatedAxioms);
							}
						});
					}
				}
			}).start();
		} catch (OREException e) {
			e.printStackTrace();
		}
		
//		for(final AxiomType<OWLAxiom> axiomType : pendingAxiomTypes){
//			
//			new Thread(new Runnable() {
//				
//				@Override
//				public void run() {
//					try {
//						final List<EvaluatedAxiom<OWLAxiom>> learnedAxioms = ORESession.getEnrichmentManager().getEvaluatedAxioms(resourceURI, axiomType);
//						UI.getCurrent().access(new Runnable() {
//							
//							@Override
//							public void run() {
//								showTable(axiomType, learnedAxioms);
//							}
//						});
//					} catch (OREException e) {
//						e.printStackTrace();
//					}
//				}
//			}).start();
//		}
	}
	
	private void onDumpSPARUL(){
		try {
			OWLOntologyManager man = OWLManager.createOWLOntologyManager();
			OWLOntology ontology = man.createOntology();
			List<OWLOntologyChange> changes = new ArrayList<>();
			OWL2SPARULConverter translator = new OWL2SPARULConverter(ontology, false);
			for(EvaluatedAxiomsTable table : tables){
				Set<Object> selectedObjects = table.getSelectedObjects();
				for(Object o : selectedObjects){
					changes.add(new AddAxiom(ontology, ((EvaluatedAxiom)o).getAxiom()));
				}
			}
			if(!changes.isEmpty()){
				VerticalLayout content = new VerticalLayout();
				String sparulString = translator.convert(changes);
				content.addComponent(new Label(sparulString, ContentMode.PREFORMATTED));
				final Window window = new Window("SPARQL Update statements", content);
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
	
	public void showTable(AxiomType<? extends OWLAxiom> axiomType, List<EvaluatedAxiom<OWLAxiom>> axioms){
		if(!axioms.isEmpty()){
			try {
				EvaluatedAxiomsTable table = new EvaluatedAxiomsTable(axiomType, axioms);
//				table.setWidth("100%");
				String axiomName = axiomType.getName();
				if(axiomType.equals(AxiomType.IRREFLEXIVE_OBJECT_PROPERTY)){
					axiomName = "Irreflexive Object Property";
				}
				axiomName = splitCamelCase(axiomName);
				table.setCaption(axiomName);
				tables.add(table);
//				c.setHeight(null);
//				EvaluatedAxiomsTable table = new EvaluatedAxiomsTable(axiomType, axioms);
				axiomsPanel.addComponent(createContentWrapper(table));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private String splitCamelCase(String s){
		String split = s.replaceAll("([A-Z][a-z]+)", " $1") // Words beginning with UC
        .replaceAll("([A-Z][A-Z]+)", " $1") // "Words" of only UC
        .replaceAll("([^A-Za-z ]+)", " $1") // "Words" of non-letters
        .trim();
		return split;
	}
	
	private EntityType<? extends OWLEntity> getSelectedResourceType(){
		return resourceTypeField.getResourceType();
	}
	
	private void onResourceTypeChanged(EntityType<? extends OWLEntity> entityType){
		if(entityType == null){
//			axiomTypesField.updateVisibleAxiomTypes(Collections.EMPTY_SET);
			axiomTypesField.setVisible(false);
		} else {
			axiomTypesField.setVisible(true);
			axiomTypesTable.show(entityType);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.vaadin.navigator.View#enter(com.vaadin.navigator.ViewChangeListener.ViewChangeEvent)
	 */
	@Override
	public void enter(ViewChangeEvent event) {
	}
	
	public void reset(){
		axiomsPanel.removeAllComponents();
		resourceURIField.setValue("");
		onResourceTypeChanged(null);
		startButton.setEnabled(false);
		try {
			thresholdSlider.setValue(70d);
		} catch (ValueOutOfBoundsException e) {
			e.printStackTrace();
		}
	}
	
	private class AxiomTypesField extends VerticalLayout{
		
		public AxiomTypesField() {
			setCaption("Axiom types");
			setDescription("Choose the types of axioms for which suggestions will be generated.");
			setSizeFull();
//			setHeight("300px");
			setHeightUndefined();
			
			//(de)select all checkbox
			CheckBox allCheckBox = new CheckBox("All");
			allCheckBox.addStyleName(ValoTheme.CHECKBOX_SMALL);
			allCheckBox.addStyleName("select-all-axiomtypes-checkbox");
			allCheckBox.setImmediate(true);
			allCheckBox.addValueChangeListener(new Property.ValueChangeListener() {
				@Override
				public void valueChange(Property.ValueChangeEvent event) {
					axiomTypesTable.selectAll((Boolean) event.getProperty().getValue());
				}
			});
			addComponent(allCheckBox);
			
			//table
			axiomTypesTable = new AxiomTypesTable(new ValueChangeListener() {
				@Override
				public void valueChange(ValueChangeEvent event) {
					startButton.setEnabled(!axiomTypesTable.getSelectedAxiomsTypes().isEmpty());
				}
			});
			addComponent(axiomTypesTable);
			setExpandRatio(axiomTypesTable, 1f);
			
		}
		
		public Set<AxiomType<OWLAxiom>> getSelectedAxiomsTypes() {
			return axiomTypesTable.getSelectedAxiomsTypes();
		}
	}
	
	private class ResourceTypeField extends VerticalLayout{
		
		private CheckBox autoDetectBox;
		private OptionGroup resourceTypeGroup;

		public ResourceTypeField() {
			setCaption("Resource type");
			addStyleName("resource-type");
			setSizeFull();
			setHeight(null);
			
			autoDetectBox = new CheckBox();
			autoDetectBox.setValue(true);
			autoDetectBox.setCaption("Detect automatically");
			autoDetectBox.setImmediate(true);
			autoDetectBox.addValueChangeListener(new ValueChangeListener() {
				@Override
				public void valueChange(ValueChangeEvent event) {
					onResourceTypeChanged(null);
					if((Boolean) event.getProperty().getValue()){
						resourceTypeGroup.setEnabled(false);
					} else {
						resourceTypeGroup.setEnabled(true);
					}
				}
			});
			addComponent(autoDetectBox);
			
			resourceTypeGroup = new OptionGroup();
			resourceTypeGroup.setImmediate(true);
			
			resourceTypeGroup.addItem(EntityType.CLASS);
			resourceTypeGroup.addItem(EntityType.OBJECT_PROPERTY);
			resourceTypeGroup.addItem(EntityType.DATA_PROPERTY);
			
			resourceTypeGroup.setValue(EntityType.CLASS);
			resourceTypeGroup.addValueChangeListener(new ValueChangeListener() {
				@Override
				public void valueChange(
						com.vaadin.data.Property.ValueChangeEvent event) {
					onResourceTypeChanged(getResourceType());
				}
			});
			addComponent(resourceTypeGroup);
		}
		
		public EntityType<? extends OWLEntity> getResourceType(){
			if(autoDetectBox.getValue()){
				return null;
			} else {
				return (EntityType<? extends OWLEntity>) resourceTypeGroup.getValue();
			}
		}
		
		public void setResourceType(EntityType<? extends OWLEntity> resourceType){
			if(resourceType == null){
				resourceTypeGroup.setEnabled(false);
				autoDetectBox.setValue(true);
			} else {
				resourceTypeGroup.setEnabled(true);
				resourceTypeGroup.setValue(resourceType);
				autoDetectBox.setValue(false);
			}
		}
		
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.view.Refreshable#refreshRendering()
	 */
	@Override
	public void refreshRendering() {
		for (EvaluatedAxiomsTable evaluatedAxiomsTable : tables) {
			evaluatedAxiomsTable.refreshRowCache();
		}
	}
	
	private Component createContentWrapper(final Table content) {
        final CssLayout slot = new CssLayout();
        slot.setWidth("100%");
        slot.addStyleName("dashboard-panel-slot");

        CssLayout card = new CssLayout();
        card.setWidth("100%");
        card.addStyleName(ValoTheme.LAYOUT_CARD);

        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.addStyleName("dashboard-panel-toolbar");
        toolbar.setWidth("100%");

        Label caption = new Label(content.getCaption());
        caption.addStyleName(ValoTheme.LABEL_H4);
        caption.addStyleName(ValoTheme.LABEL_COLORED);
        caption.addStyleName(ValoTheme.LABEL_NO_MARGIN);
        content.setCaption(null);
        
        toolbar.addComponent(caption);
        
        final int maxNrOfRowsDefault = 5;
		if(content.getItemIds().size() > maxNrOfRowsDefault ) {
			MenuBar tools = new MenuBar();
	        tools.addStyleName(ValoTheme.MENUBAR_BORDERLESS);
	        MenuItem max = tools.addItem("", FontAwesome.EXPAND, new Command() {

	            @Override
	            public void menuSelected(final MenuItem selectedItem) {
	                if (!slot.getStyleName().contains("max")) {
	                    selectedItem.setIcon(FontAwesome.COMPRESS);
	                    toggleMaximized(slot, true);
	                    content.setPageLength(0);
	                    
	                } else {
	                    slot.removeStyleName("max");
	                    selectedItem.setIcon(FontAwesome.EXPAND);
	                    toggleMaximized(slot, false);
	                    content.setPageLength(maxNrOfRowsDefault);
	                }
	            }
	        });
	        max.setStyleName("icon-only");
//	        MenuItem root = tools.addItem("", FontAwesome.COG, null);
//	        root.addItem("Configure", new Command() {
//	            @Override
//	            public void menuSelected(final MenuItem selectedItem) {
//	                Notification.show("Not implemented in this demo");
//	            }
//	        });
//	        root.addSeparator();
//	        root.addItem("Close", new Command() {
//	            @Override
//	            public void menuSelected(final MenuItem selectedItem) {
//	                Notification.show("Not implemented in this demo");
//	            }
//	        });

	        toolbar.addComponents(tools);
	        
	        content.setPageLength(maxNrOfRowsDefault);
        }

        
        toolbar.setExpandRatio(caption, 1);
        toolbar.setComponentAlignment(caption, Alignment.MIDDLE_LEFT);

        card.addComponents(toolbar, content);
        slot.addComponent(card);
        
        return slot;
    }
	
	private void toggleMaximized(final Component panel, final boolean maximized) {
        if (maximized) {
            panel.addStyleName("max");
        } else {
            panel.removeStyleName("max");
        }
    }
}
