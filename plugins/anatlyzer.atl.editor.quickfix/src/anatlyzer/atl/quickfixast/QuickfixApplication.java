package anatlyzer.atl.quickfixast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

import anatlyzer.atlext.ATL.LocatedElement;

/**
 * This class represents a modification in the AST, allowing
 * to keep track of the changes for a proper subsequent handling,
 * for instance layout-preserving serialization.
 * @author jesus
 *
 * @param <T>
 */
public class QuickfixApplication {

	private ArrayList<Action> actions = new ArrayList<QuickfixApplication.Action>();
	
	public List<Action> getActions() {
		return actions;
	}
	
	public <T1 extends EObject, T2 extends EObject> void replace(T1 root, BiFunction<T1, Trace, T2> replacer) {
		//@SuppressWarnings("unchecked")
		//T root = (T) ATLCopier.copySingleElement(originalRoot);
		
		Trace trace = new Trace();
		T2 r = replacer.apply(root, trace);
		ReplacementAction action = new ReplacementAction(root, r, trace);
		
		actions.add(action);
		
		EcoreUtil.replace(root, r);
		// now r is the new root...
	}
	
	public <T1 extends EObject> void change(EObject root, 
			Supplier<T1> rootCreator,
			BiConsumer<T1, Trace> replacer) {	
		Trace trace = new Trace();
		
		T1 newRoot = rootCreator.get();
		EcoreUtil.replace(root, newRoot);
		
		replacer.accept(newRoot, trace);
		ReplacementAction action = new ReplacementAction(root, newRoot, trace);
		
		actions.add(action);
	}
	
	public <A extends LocatedElement, B extends LocatedElement> void insertAfter(A anchor, Supplier<B> supplier) {
		B element = supplier.get();
		
		// Add the creted element following the the anchor element
		EStructuralFeature feature = anchor.eContainingFeature();
		if ( ! (feature instanceof EReference) || ! feature.isMany() ) {
			throw new IllegalArgumentException();
		}
		EObject parent = anchor.eContainer();
		@SuppressWarnings("unchecked")
		EList<EObject> list = (EList<EObject>) parent.eGet(feature);
		list.add(list.indexOf(anchor) + 1 , element);
		
		actions.add(new InsertAfterAction(anchor, element));
	}

	@SuppressWarnings("unchecked")
	public void putIn(EObject receptor, EReference feature, Supplier<? extends EObject> creator) {
		EObject newObj= creator.get();
		if ( feature.isMany() ) {
			((List<EObject>) receptor.eGet(feature)).add(newObj);
		} else {
			receptor.eSet(feature, newObj);
		}
		actions.add(new PutInAction(receptor, feature, newObj));
	}

	public static class Trace {
		LinkedList<Object> preservedElements = new LinkedList<Object>();
		
		@SuppressWarnings("unchecked")
		public void preserve(Object obj) {
			preservedElements.add(obj);
			if ( obj instanceof Collection ) {
				preservedElements.addAll((Collection<? extends Object>) obj);
			}
		}

		public boolean isPreserved(EObject obj) {
			return preservedElements.contains(obj);
		}
	}

	public static class Action {
		protected EObject tgt;
		protected Trace trace;

		public Action(EObject tgt, Trace trace) {
			this.tgt = tgt;
			this.trace = trace;
		}
		
		public EObject getTgt() {
			return tgt;
		}

		public Trace getTrace() {
			return trace;
		}
	}
	
	public static class ReplacementAction extends Action {
		private EObject src;

		public ReplacementAction(EObject src, EObject tgt, Trace trace) {
			super(tgt, trace);
			this.src = src;
			this.tgt = tgt;
		}
		
		public EObject getSrc() {
			return src;
		}		
	}
	
	public static class InsertAfterAction extends Action {
		private EObject anchor;

		public InsertAfterAction(EObject anchor, EObject tgt) {
			super(tgt, new Trace());
			this.anchor = anchor;
		}
		
		public EObject getAnchor() {
			return anchor;
		}		
	}

	public static class PutInAction extends Action {

		private EObject receptor;
		private EReference feature;

		public PutInAction(EObject receptor, EReference feature, EObject newObj) {
			super(newObj, new Trace());
			this.receptor = receptor;
			this.feature = feature;
		}
		
		public EObject getReceptor() {
			return receptor;
		}

		public Action toMockReplacement() {
			Trace mockTrace = new Trace();
			EList<EReference> refs = receptor.eClass().getEAllReferences();
			for (EReference ref : refs) {
				if ( ref != feature ) {
					mockTrace.preserve(receptor.eGet(ref));
				}
			}
			return new ReplacementAction(receptor, receptor, mockTrace);
		}
		
		
	}
	
	public void move(Consumer<EObject> setter, Supplier<EObject> getter) {
		EObject src =getter.get();
		setter.accept(src);
	}

	public void apply() {
		// For the moment nothing... but should be called to ensure everything is in sync
	}



}
