package anatlyzer.atl.editor.quickfix.visualization;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.zest.core.viewers.GraphViewer;
import org.eclipse.zest.layouts.algorithms.TreeLayoutAlgorithm;

import anatlyzer.atl.analyser.AnalysisResult;
import anatlyzer.atl.editor.quickfix.search.ISearchEdge;
import anatlyzer.atl.editor.quickfix.search.ISearchState;
import anatlyzer.atl.editor.quickfix.search.InteractiveSearch;
import anatlyzer.atl.editor.quickfix.search.InteractiveSearch.ISearchListener;
import anatlyzer.atl.editor.quickfix.search.SearchPath;

public class RepairTransformationView extends ViewPart implements ISearchListener{
	public static final String ID = "anatlyzer.atl.editor.quickfix.visualization.RepairTransformationView";
	private AnalysisResult analysis;
	private Button btnExecute;
	private Button btnStepSelected;
	private Composite cmpGraph;
	private GraphViewer graph;
	
	public RepairTransformationView() {
	}

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		
		Composite composite_1 = new Composite(parent, SWT.NONE);
		composite_1.setLayout(new GridLayout(1, false));
		//composite_1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		Composite composite = new Composite(composite_1, SWT.NONE);
		composite.setLayout(new GridLayout(3, false));
		composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		
		Label lblNewLabel = new Label(composite, SWT.NONE);
		lblNewLabel.setText("Strategy:");
		
		Combo cmbStrategies = new Combo(composite, SWT.NONE);
		GridData gd_cmbStrategies = new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1);
		gd_cmbStrategies.widthHint = 161;
		cmbStrategies.setLayoutData(gd_cmbStrategies);
		
		btnExecute = new Button(composite, SWT.NONE);
		btnExecute.setText("Execute");
		
		btnStepSelected = new Button(composite, SWT.NONE);
		btnStepSelected.setText("Step selected");
		
		btnStepAll = new Button(composite, SWT.NONE);
		btnStepAll.setText("Step all");
		
		cmpGraph = new Composite(composite_1, SWT.NONE);
		cmpGraph.setLayout(new FillLayout());
		cmpGraph.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, 1, 1));
		cmpGraph.setBounds(0, 0, 64, 64);
		
		initHandlers();
	}

	private void initHandlers() {
		btnExecute.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				startFixing();
			}
		});
		
		btnStepSelected.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				nextStepSelected();
			}			
		});

		btnStepAll.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				nextStepAll();
			}			
		});

	}

	@Override
	public void setFocus() {
		
	}

	public void setAnalysis(AnalysisResult analysis) {
		this.analysis = analysis;
	}

	private InteractiveSearch searcher;
	
	private void startFixing() {
		SearchPath path = new SearchPath();
		searcher = new InteractiveSearch(path, analysis);
		searcher.setListener(this);
		
		if ( graph == null ) {		
			graph = new GraphViewer(cmpGraph, SWT.V_SCROLL | SWT.H_SCROLL);
			graph.setContentProvider(new SearchContentProvider());
			graph.setLabelProvider(new SearchLabelProvider2());
			graph.setLayoutAlgorithm(new TreeLayoutAlgorithm(), true);
			graph.applyLayout();
		
			graph.addSelectionChangedListener(new GraphSelectionListener());
		}
		
		
		
		graph.setInput(new SearchContentProvider.StartNode(searcher));
		graph.refresh(true);
		
	}
	
	private void nextStepAll() {
		List<ISearchEdge> allEdges = searcher.getAllEdges();
		if ( allEdges.size() == 0 ) {
			searcher.expand();
		} else {
			allEdges.stream().
				filter(e -> e.getTarget().getNextStates().size() == 0).
				map(e -> e.getTarget()).forEach(s -> {
					((InteractiveSearch) s).expand();
				});
		}
		graph.refresh();
		graph.applyLayout();
	}

	private void nextStepSelected() {
		List<ISearchEdge> allEdges = searcher.getAllEdges();
		if ( allEdges.size() == 0 ) {
			searcher.expand();
		} else {
			selected.forEach(s -> {
				((InteractiveSearch) s).expand();
			});
		}
		graph.refresh();
		graph.applyLayout();
	}

	
	@Override
	public void newBranch(ISearchState state, ISearchEdge newEdge) {
		/*
		if ( state == searcher ) {
			graph.refresh(graph.getInput());
		} else {
			graph.refresh(state);
		}
		*/
	}

	private List<ISearchState> selected = new ArrayList<ISearchState>();
	private Button btnStepAll;

	public class GraphSelectionListener implements ISelectionChangedListener {

		@SuppressWarnings("unchecked")
		@Override
		public void selectionChanged(SelectionChangedEvent event) {
			IStructuredSelection s = (IStructuredSelection) event.getSelection();
			selected.clear();
			s.iterator().forEachRemaining(e -> {
				if ( e instanceof ISearchState)  {
					selected.add((ISearchState) e); 
				}
			});
		}
		
	}

}