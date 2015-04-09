package anatlyzer.atl.editor.quickfix;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Point;

import anatlyzer.atl.editor.builder.AnATLyzerBuilder;
import anatlyzer.atl.editor.builder.AnalyserExecutor.AnalyserData;
import anatlyzer.atl.editor.witness.EclipseUseWitnessFinder;
import anatlyzer.atl.errors.Problem;
import anatlyzer.atl.quickfixast.QuickfixApplication;
import anatlyzer.atl.witness.IWitnessFinder.WitnessResult;

public class ConstraintSolvingQuickFix extends AbstractAtlQuickfix {
	
	@Override
	public void apply(IDocument document) {
		try {
			Problem problem = (Problem) marker.getAttribute(AnATLyzerBuilder.PROBLEM);
			AnalyserData analysisData = (AnalyserData) marker.getAttribute(AnATLyzerBuilder.ANALYSIS_DATA);

			WitnessResult result = new EclipseUseWitnessFinder().find(problem, analysisData);
			switch ( result ) {
			case ERROR_DISCARDED: MessageDialog.openInformation(null, "Constraint solving", "No error. Witness model could be found"); break;
			case ERROR_DISCARDED_DUE_TO_METAMODEL:	MessageDialog.openInformation(null, "Constraint solving", "Metamodel error!. Witness model could be found due to the meta-model not being 'instantiable'"); break;
			case ERROR_CONFIRMED: MessageDialog.openInformation(null, "Constraint solving", "Error confirmed!"); break;
			case INTERNAL_ERROR: MessageDialog.openInformation(null, "Constraint solving", "Internal error.");break;
			case CANNOT_DETERMINE: MessageDialog.openInformation(null, "Constraint solving", "Cannot be determined."); break;
			}
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}



	@Override
	public Point getSelection(IDocument document) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getAdditionalProposalInfo() {
		return "Generate witness with USE";
	}

	@Override
	public String getDisplayString() {
		return "Generate witness";
	}

	@Override
	public boolean isApplicable(IMarker marker) {
		// TODO: Decide which errors are amenable to constraint solving
		return true;
	}


	@Override
	public QuickfixApplication getQuickfixApplication() {
		throw new UnsupportedOperationException();
	}

}
