package witness.generator.mmext;

import org.eclipse.emf.ecore.EPackage;

import witness.generator.MetaModel;

/**
 * This strategy leaves the error meta-model as is.
 * @author jesus
 *
 */
public class ErrorPathMetamodelStrategy extends AbstractMetamodelExtension implements IMetamodelExtensionStrategy {
	
	public void extend(EPackage errorMM, MetaModel effectiveMM, MetaModel languageMM) {	
		System.out.println("Using 'Error Path Metamodel' strategy");
				
		// extend error meta-model with concrete children classes of abstract leaf classes
		extendMetamodelWithConcreteLeaves(errorMM, effectiveMM);
		extendMetamodelWithConcreteLeaves(errorMM, languageMM);			
	}
	
}
