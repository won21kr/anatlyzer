package anatlyzer.atl.explanations.errors;

import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.provider.EcoreItemProviderAdapterFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.ReflectiveItemProviderAdapterFactory;
import org.eclipse.emf.edit.provider.resource.ResourceItemProviderAdapterFactory;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryContentProvider;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;

import anatlyzer.atl.errors.atl_error.NoBindingForCompulsoryFeature;
import anatlyzer.atl.explanations.AbstractAtlExplanation;
import anatlyzer.atl.types.Metaclass;
import anatlyzer.atlext.ATL.OutPatternElement;

public class NoBindingForCompulsoryFeatureExplanation extends AbstractAtlExplanation {

	@Override
	public boolean isApplicable() {
		return checkProblemType(NoBindingForCompulsoryFeature.class);
	}

	@Override
	public void setDetailedProblemDescription(StyledText text) {
		text.setText("This problem appears when a meta-model feature is compulsory but there is no binding to initialize it.");
		// Explain it may provoke issues in others trafos...
		// Put an example...
	}

	@Override
	public void setAdditionalInfo(Composite composite) {
	    NoBindingForCompulsoryFeature p = (NoBindingForCompulsoryFeature) getProblem();
	    OutPatternElement out = (OutPatternElement) p.getElement();
	    String mmName = ((Metaclass) out.getInferredType()).getModel().getName();
	    
	    EStructuralFeature f = p.getFeature();
		createMetamodelViewer(composite, mmName, f);	    
	}



}