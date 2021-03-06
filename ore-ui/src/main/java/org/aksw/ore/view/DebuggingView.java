/**
 * 
 */
package org.aksw.ore.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aksw.mole.ore.explanation.api.ExplanationType;
import org.aksw.ore.ORESession;
import org.aksw.ore.component.ConfigurablePanel;
import org.aksw.ore.component.ExplanationOptionsPanel;
import org.aksw.ore.component.ExplanationProgressDialog;
import org.aksw.ore.component.ExplanationTable;
import org.aksw.ore.component.ExplanationsPanel;
import org.aksw.ore.component.ImpactTable;
import org.aksw.ore.component.RepairPlanTable;
import org.aksw.ore.component.WhitePanel;
import org.aksw.ore.manager.ExplanationManager;
import org.aksw.ore.manager.ExplanationManagerListener;
import org.aksw.ore.manager.ExplanationProgressMonitorExtended;
import org.aksw.ore.model.OWLAxiomBean;
import org.aksw.ore.rendering.Renderer;
import org.apache.log4j.Logger;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.semanticweb.owlapi.reasoner.ReasonerInterruptedException;
import org.semanticweb.owlapi.reasoner.TimeOutException;

import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.BeanItem;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.data.util.MethodProperty;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.ui.AbstractSelect.ItemDescriptionGenerator;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalSplitPanel;
import com.vaadin.ui.Panel;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.ColumnGenerator;
import com.vaadin.ui.Table.ColumnHeaderMode;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.VerticalSplitPanel;

/**
 * @author Lorenz Buehmann
 *
 */
public class DebuggingView extends HorizontalSplitPanel implements View, Refreshable, ExplanationProgressMonitorExtended<OWLAxiom>, ExplanationManagerListener{
	
	
	private static final Logger logger = Logger.getLogger(DebuggingView.class.getName());
	
	private ExplanationOptionsPanel optionsPanel;
	private RepairPlanTable repairPlanTable;
	private ImpactTable impactTable;
	private ExplanationsPanel explanationsPanel;
	
	ExplanationManager expMan;
	ExplanationProgressDialog progressDialog;
	private Table classesTable;
	
	private Set<OWLAxiom> selectedAxioms = new HashSet<>();
	private List<ExplanationTable> explanationTables = new ArrayList<>();
	private Map<ExplanationTable, Property.ValueChangeListener> table2Listener = new HashMap<>();
	
	private int currentLimit = 1;
	private ExplanationType currentExplanationType = ExplanationType.REGULAR;
	
	private boolean firstViewVisit = true;
	
	private Map<OWLAxiom, OWLAxiomBean> axiom2Bean = new HashMap<>();

	private Set<Table> tables = new HashSet<>();

	
	public DebuggingView() {
		addStyleName("dashboard-view");
        addStyleName("debugging-view");
		setSizeFull();
		
		createUnsatisfiableClassesTable();
//		classesTable.sort(new String[]{"root", "class"}, new boolean[]{false, true});
		
		//the classes table on the left side
		setFirstComponent(new ConfigurablePanel(classesTable));
		//right explanations, repair plan and impact on the right side
		setSecondComponent(createRightSide());
		
		setSplitPosition(30, Unit.PERCENTAGE);
	}
	
	private void createUnsatisfiableClassesTable(){
		classesTable = new Table("Unsatisfiable classes");
		classesTable.addStyleName("unsatisfiable-classes-table");
		classesTable.addContainerProperty("root", String.class, null);
		classesTable.addContainerProperty("class", String.class, null);
		classesTable.setColumnHeaderMode(ColumnHeaderMode.HIDDEN);
		classesTable.setSelectable(true);
		classesTable.setMultiSelect(true);
		classesTable.setImmediate(true);
		classesTable.setWidth("100%");
//		classesTable.setPageLength(0);
		classesTable.setHeight("100%");
		classesTable.setColumnExpandRatio("class", 1f);
//		classesTable.addItemClickListener(new ItemClickListener() {
//			@Override
//			public void itemClick(ItemClickEvent event) {
//				computeExplanations((OWLClass) event.getItemId());
//			}
//		});
		classesTable.addValueChangeListener(new ValueChangeListener() {
			
			@Override
			public void valueChange(ValueChangeEvent event) {
				computeExplanations((Set<OWLClass>) event.getProperty().getValue());
			}
		});
		classesTable.setCellStyleGenerator(new Table.CellStyleGenerator() {
			
			@Override
			public String getStyle(Table source, Object itemId, Object propertyId) {
				if(propertyId == null){
					return null;
				} else if(propertyId.equals("root")){
					OWLClass cls = (OWLClass)itemId;
					boolean root = ORESession.getExplanationManager().getRootUnsatisfiableClasses().contains(cls);
					if(root){
						return "root-class";
					} 
				} 
				return null;
			}
		});
		classesTable.setItemDescriptionGenerator(new ItemDescriptionGenerator() {
			ExplanationManager expMan = ORESession.getExplanationManager();
			
			public String generateDescription(Component source, Object itemId, Object propertyId) {
				OWLClass cls = (OWLClass)itemId;
			    if(propertyId == null){
			        return cls.toStringID();
			    } else if(propertyId.equals("root")) {
			    	boolean root = expMan.getRootUnsatisfiableClasses().contains(cls);
			    	if(root){
			    		return "The unsatisfiability of this class is a possible cause for the unsatisfiability of other classes";
			    	}
			    }                                                                       
			    return null;
			}});
		
		classesTable.setVisibleColumns("root", "class");
	}
	
	private Component createRightSide(){
		VerticalSplitPanel rightSide = new VerticalSplitPanel();
		rightSide.setSizeFull();
		rightSide.setSplitPosition(75);
		
		//show explanations in the top of the right side
		Component explanationsPanel = createExplanationsPanel();
		rightSide.addComponent(explanationsPanel);
		
		//show repair plan and impact in bottom of right side
		HorizontalSplitPanel bottomRightSide = new HorizontalSplitPanel();
		bottomRightSide.setSizeFull();
		bottomRightSide.setSplitPosition(50);
		rightSide.addComponent(bottomRightSide);
		
		//show repair plan in the bottom left of the right side
		Component repairPlanPanel = createRepairPlanPanel();
		bottomRightSide.addComponent(repairPlanPanel);

		//show impact in bottom right of right side
		Component impactPanel = createImpactPanel();
		bottomRightSide.addComponent(impactPanel);
		return rightSide;
	}
	
	private Component createExplanationsPanel(){
		VerticalLayout l = new VerticalLayout();
		l.setSizeFull();
		l.setCaption("Explanations");
		
		explanationsPanel = new ExplanationsPanel();
		explanationsPanel.setCaption("Explanations");
		explanationsPanel.setHeight(null);
		explanationsPanel.addStyleName("explanations-panel");
		
		//put the options in the header of the portal
		optionsPanel = new ExplanationOptionsPanel(explanationsPanel);
		optionsPanel.setWidth(null);
//		l.addComponent(optionsPanel);
		
		//wrapper for scrolling
		Panel panel = new Panel(explanationsPanel);
		panel.setSizeFull();
		l.addComponent(panel);
		l.setExpandRatio(panel, 1.0f);
		
		WhitePanel configurablePanel = new WhitePanel(explanationsPanel);
		configurablePanel.addComponent(optionsPanel);
		return configurablePanel;
	}
	
	private Component createRepairPlanPanel(){
		VerticalLayout wrapper = new VerticalLayout();
		wrapper.setCaption("Repair Plan");
		wrapper.setSizeFull();
		
		repairPlanTable = new RepairPlanTable();
		wrapper.addComponent(repairPlanTable);
		wrapper.setExpandRatio(repairPlanTable, 1.0f);
		
		Button executeRepairButton = new Button("Execute");
		executeRepairButton.setHeight(null);
		executeRepairButton.addClickListener(new Button.ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				ORESession.getRepairManager().execute();
			}
		});
		wrapper.addComponent(executeRepairButton);
		wrapper.setComponentAlignment(executeRepairButton, Alignment.MIDDLE_RIGHT);
		
		return new WhitePanel(wrapper);
	}
	
	private Component createImpactPanel(){
		VerticalLayout wrapper = new VerticalLayout();
		wrapper.setCaption("Impact");
		wrapper.setSizeFull();
		
		impactTable = new ImpactTable();
		wrapper.addComponent(impactTable);
		wrapper.setExpandRatio(impactTable, 1.0f);
		
		return new WhitePanel(wrapper);
	}

	/* (non-Javadoc)
	 * @see com.vaadin.navigator.View#enter(com.vaadin.navigator.ViewChangeListener.ViewChangeEvent)
	 */
	@Override
	public void enter(ViewChangeEvent event) {
		if(firstViewVisit){
			ORESession.getExplanationManager().addExplanationProgressMonitor(this);
			loadUnsatisfiableClasses();
			firstViewVisit = false;
		}
	}
	
	/* (non-Javadoc)
	 * @see com.vaadin.ui.AbstractComponent#attach()
	 */
	@Override
	public void attach() {
		super.attach();
		ExplanationManager expMan = ORESession.getExplanationManager();
		expMan.setExplanationLimit(currentLimit);
		ORESession.getRepairManager().addListener(repairPlanTable);
		ORESession.getRepairManager().addListener(impactTable);
		ORESession.getExplanationManager().addListener(this);
	}
	
	/* (non-Javadoc)
	 * @see com.vaadin.ui.AbstractComponent#detach()
	 */
	@Override
	public void detach() {
		super.detach();
		ORESession.getRepairManager().removeListener(repairPlanTable);
		ORESession.getRepairManager().removeListener(impactTable);
		ORESession.getExplanationManager().removeListener(this);
//		ORESession.getExplanationManager().removeExplanationProgressMonitor(this);
	}
	
	private void loadUnsatisfiableClasses(){
		logger.info("Loading unsatisfiable classes...");
		
		IndexedContainer c = new IndexedContainer();
		c.addContainerProperty("class", String.class, null);
		c.addContainerProperty("root", String.class, null);
		try {
			ExplanationManager expMan = ORESession.getExplanationManager();
			Renderer renderer = ORESession.getRenderer();
			//first the root classes
			Set<OWLClass> classes = expMan.getRootUnsatisfiableClasses();
			for (OWLClass cls : classes) {
				Item i = c.addItem(cls);
				i.getItemProperty("class").setValue(renderer.render(cls));
//				boolean root = ORESession.getExplanationManager().getRootUnsatisfiableClasses().contains(cls);
//				i.getItemProperty("root").setValue(root ? "R" : "");
			}
			//then the derived classes
			classes = expMan.getDerivedUnsatisfiableClasses();
			for (OWLClass cls : classes) {
				Item i = c.addItem(cls);
				i.getItemProperty("class").setValue(renderer.render(cls));
//				boolean root = ORESession.getExplanationManager().getRootUnsatisfiableClasses().contains(cls);
//				i.getItemProperty("root").setValue(root ? "R" : "");
			}
		} catch (TimeOutException | ReasonerInterruptedException | InconsistentOntologyException e) {
			e.printStackTrace();
		}
		classesTable.setContainerDataSource(c);
	}
	
	private void computeExplanations(final Set<OWLClass> unsatClasses) {
		explanationsPanel.removeAllComponents();
		progressDialog = new ExplanationProgressDialog(unsatClasses, currentLimit);
		ORESession.getExplanationManager().addExplanationProgressMonitor(progressDialog);
		UI.getCurrent().addWindow(progressDialog);
		new Thread(new Runnable() {

			@Override
			public void run() {
				for (OWLClass cls : unsatClasses) {
					if(!progressDialog.isCancelled()){
						try {
							final Set<Explanation<OWLAxiom>> explanations = ORESession.getExplanationManager()
									.getUnsatisfiabilityExplanations(cls, currentLimit);
//							UI.getCurrent().access(new Runnable() {
//								@Override
//								public void run() {
//									showExplanations(explanations);
//								}
//							});
						} catch (Exception e) {
							e.printStackTrace();
						}
					} 
				}
				ORESession.getExplanationManager().removeExplanationProgressMonitor(progressDialog);
				UI.getCurrent().removeWindow(progressDialog);
			}
		}).start();
	}
	
	private void computeExplanations(){
		Set<OWLClass> unsatClasses = (Set<OWLClass>) classesTable.getValue();
		if(unsatClasses != null){
			computeExplanations(unsatClasses);
		}
	}
	
	private void showExplanation(final Explanation<OWLAxiom> explanation) {
		try {
			final ExplanationTable t = new ExplanationTable(explanation, selectedAxioms);
			t.addStyleName("explanation-table");
			if(explanation.getEntailment() != null){
				OWLClass cls = ((OWLSubClassOfAxiom)explanation.getEntailment()).getSubClass().asOWLClass();
				t.setCaption(ORESession.getRenderer().render(cls));
			}
		t.addValueChangeListener(new Property.ValueChangeListener() {
			{table2Listener.put(t, this);}
			
			@Override
			public void valueChange(ValueChangeEvent event) {
				selectedAxioms.removeAll(t.getExplanation().getAxioms());
				selectedAxioms.addAll((Collection<OWLAxiom>) event.getProperty().getValue());
				onAxiomSelectionChanged();
			}
		});
			ORESession.getRepairManager().addListener(t);
			WhitePanel c = new WhitePanel(t);
			c.setHeight(null);
			explanationsPanel.addComponent(c);
			explanationTables.add(t);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
//		tables = new HashSet<Table>();
//		Table table = generateTable(explanation);
//		tables.add(table);
//		explanationsPanel.addComponent(table);
//		table = generateTable(explanation);
//		tables.add(table);
//		explanationsPanel.addComponent(table);
	}
	
	private Collection<OWLAxiomBean> getBeans(Set<OWLAxiom> axioms){
		Collection<OWLAxiomBean> beans = new HashSet<>();
		
		for (OWLAxiom axiom : axioms) {
			OWLAxiomBean bean = axiom2Bean.get(axiom);
			if(bean == null){
				bean = new OWLAxiomBean(axiom);
				axiom2Bean.put(axiom, bean);
			}
			beans.add(bean);
		}
		
		return beans;
	}
	
	private Table generateTable(Explanation<OWLAxiom> explanation){
		final BeanItemContainer<OWLAxiomBean> container = new BeanItemContainer<>(OWLAxiomBean.class);
		container.addAll(getBeans(explanation.getAxioms()));
		
		Table table = new Table("axioms", container);
		table.setImmediate(true);
		table.addGeneratedColumn("selected", new ColumnGenerator() {

            @Override
            public Component generateCell(final Table source, final Object itemId, final Object columnId) {

                final OWLAxiomBean bean = (OWLAxiomBean) itemId;

                final CheckBox checkBox = new CheckBox();
                checkBox.setImmediate(true);
                checkBox.addValueChangeListener(new Property.ValueChangeListener() {
                    @Override
                    public void valueChange(final ValueChangeEvent event) {
                    	boolean value = (Boolean) event.getProperty().getValue();
                    	BeanItem<OWLAxiomBean> beanItem = container.getItem(itemId);
						beanItem.getItemProperty("selected").setValue(value);
                        bean.setSelected(value);
                        fireAxiomSelectionChanged(source, bean);
                    }

                });

                if (bean.isSelected()) {
                    checkBox.setValue(true);
                } else {
                    checkBox.setValue(false);
                }
                return checkBox;
            }
        });
		table.addGeneratedColumn("axiom", new ColumnGenerator() {
			
			@Override
			public Object generateCell(Table source, Object itemId, Object columnId) {
				 final OWLAxiomBean bean = (OWLAxiomBean) itemId;
				 
				 return ORESession.getRenderer().render(bean.getAxiom());
			}
		});
		table.setVisibleColumns("selected", "axiom");
		table.setColumnHeader("selected", "");
		table.setColumnHeader("axiom", "Axiom");
		table.setColumnExpandRatio("axiom", 1f);
		table.setWidth("100%");
		table.setPageLength(0);
		table.setHeightUndefined();
		return table;
	}
	
	private void fireAxiomSelectionChanged(Table sourceTable, OWLAxiomBean bean) {
		for (Table table : tables) {
			if(table != sourceTable){
				BeanItemContainer container = (BeanItemContainer) table.getContainerDataSource();
				MethodProperty<OWLAxiomBean> p = (MethodProperty<OWLAxiomBean>) container.getItem(bean).getItemProperty("selected");
				p.fireValueChange();
//				table.valueChange(new ValueChangeEvent() {
//					
//					@Override
//					public Property getProperty() {
//						// TODO Auto-generated method stub
//						return null;
//					}
//				});
			}
		}
	}
	
	private void onAxiomSelectionChanged(){
//		propagateAxiomSelection();
		//we have to remove here all listeners because
		for(Entry<ExplanationTable, Property.ValueChangeListener> e : table2Listener.entrySet()){
			e.getKey().removeValueChangeListener(e.getValue());
		}
//		UserSession.getRepairManager().clearRepairPlan();
		ORESession.getRepairManager().setAxiomsToRemove(selectedAxioms);
		for(Entry<ExplanationTable, Property.ValueChangeListener> e : table2Listener.entrySet()){
			e.getKey().addValueChangeListener(e.getValue());
		}
	}
	
	
	private void updateSelection(){
		for (Table t : explanationTables) {
			for (OWLAxiom axiom : selectedAxioms) {
				t.select(axiom);
			}
		}
	}
	
	private void showExplanations(final Set<Explanation<OWLAxiom>> explanations) {
		for (Explanation<OWLAxiom> exp : explanations) {
			showExplanation(exp);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.semanticweb.owl.explanation.api.ExplanationProgressMonitor#foundExplanation(org.semanticweb.owl.explanation.api.ExplanationGenerator, org.semanticweb.owl.explanation.api.Explanation, java.util.Set)
	 */
	@Override
	public void foundExplanation(final ExplanationGenerator<OWLAxiom> arg0, final Explanation<OWLAxiom> explanation,
			final Set<Explanation<OWLAxiom>> allExplanations) {
//		System.out.println(explanation);
		UI.getCurrent().access(new Runnable() {
	        @Override
	        public void run() {
//	        	showExplanation(explanation);
	        }
	    });
	}
	

	/* (non-Javadoc)
	 * @see org.semanticweb.owl.explanation.api.ExplanationProgressMonitor#isCancelled()
	 */
	@Override
	public boolean isCancelled() {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.ExplanationProgressMonitorExtended#allExplanationsFound(java.util.Set)
	 */
	@Override
	public void allExplanationsFound(final Set<Explanation<OWLAxiom>> allExplanations) {
		UI.getCurrent().access(new Runnable() {
	        @Override
	        public void run() {
	        	showExplanations(allExplanations);
	        }
	    });
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.ExplanationManagerListener#explanationLimitChanged(int)
	 */
	@Override
	public void explanationLimitChanged(int explanationLimit) {
		if(currentLimit != explanationLimit){
			currentLimit = explanationLimit;
		}
		computeExplanations();
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.ExplanationManagerListener#explanationTypeChanged(org.aksw.mole.ore.explanation.api.ExplanationType)
	 */
	@Override
	public void explanationTypeChanged(ExplanationType type) {
		if(currentExplanationType != type){
			currentExplanationType = type;
		}
		computeExplanations();
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.view.Refreshable#refreshRendering()
	 */
	@Override
	public void refreshRendering() {
		for (ExplanationTable explanationTable : explanationTables) {
			//refresh caption
			OWLClass cls = ((OWLSubClassOfAxiom)explanationTable.getExplanation().getEntailment()).getSubClass().asOWLClass();
			explanationTable.setCaption(ORESession.getRenderer().render(cls));
			//refresh content
			explanationTable.refreshRowCache();
		}
		impactTable.refreshRowCache();
		repairPlanTable.refreshRowCache();
	}

	
}
