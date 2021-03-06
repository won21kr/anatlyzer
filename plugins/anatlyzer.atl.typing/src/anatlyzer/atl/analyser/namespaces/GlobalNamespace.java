package anatlyzer.atl.analyser.namespaces;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

import anatlyzer.atl.util.Pair;

public class GlobalNamespace {
	private static final String META_METAMODEL_KEY = "_ECORE_";
	private HashSet<Resource> resources = new HashSet<Resource>();
	private HashMap<String, MetamodelNamespace> namesToMetamodels = new HashMap<String, MetamodelNamespace>();
	
	public static final String THIS_MODULE = "thisModule";
	private TransformationNamespace tspace;
	private Map<String, Resource>	logicalNamesToMetamodels;
	private ResourceSet rs;
	
	private MetamodelNamespace metaMetamodel;
	
	
	public GlobalNamespace(Collection<Resource> r, Map<String, Resource> logicalNamesToMetamodels) {
		this.metaMetamodel = new MetamodelNamespace(META_METAMODEL_KEY, EcorePackage.eINSTANCE.eResource(), null);

		for (Resource resource : r) {
			resources.add(resource);
		}
				
		for (String key : logicalNamesToMetamodels.keySet()) {
			namesToMetamodels.put(key, new MetamodelNamespace(key, logicalNamesToMetamodels.get(key), metaMetamodel));
		}
		
		this.logicalNamesToMetamodels = Collections.unmodifiableMap(logicalNamesToMetamodels);
		
	}

	public MetamodelNamespace getMetaMetamodel() {
		return metaMetamodel;
	}
	
	/** 
	 * Forces a reload of all cached information.
	 * TODO: Do this more fine-grained
	 */
	public void invalidate() {
		for (String key : logicalNamesToMetamodels.keySet()) {
			namesToMetamodels.put(key, new MetamodelNamespace(key, logicalNamesToMetamodels.get(key), metaMetamodel));
		}
		this.subtypes = new HashMap<EClass, ArrayList<ClassNamespace>>();
	}

	
	public GlobalNamespace(ResourceSet nrs, HashMap<String, Resource> logicalNamesToResources) {
		this(nrs.getResources(), logicalNamesToResources);
		this.rs = nrs;
	}

	public Map<String, Resource> getLogicalNamesToMetamodels() {
		HashMap<String, Resource> res = new HashMap<String, Resource>(logicalNamesToMetamodels);
		res.put(metaMetamodel.getName(), metaMetamodel.getResource());
		return res;
	}
	
	public TransformationNamespace getTransformationNamespace() {
		if ( tspace == null ) {
			tspace  = new TransformationNamespace(); // Lazy initialization to ensure it is created in the thread's context
		}
		return tspace;
	}
	
	public Pair<EClassifier, MetamodelNamespace> resolve(EClassifier proxy) {
		if ( rs == null )
			return null;
		
		EClassifier c = (EClassifier) EcoreUtil.resolve(proxy, rs);
		EPackage pkg = c.getEPackage();
		for (MetamodelNamespace m : namesToMetamodels.values()) {
			if ( m.loadedPackages.contains(pkg) ) {
				return new Pair<EClassifier, MetamodelNamespace>(c, m);  
			}
		}
		return null;
		
		
//		for (MetamodelNamespace m : namesToMetamodels.values()) {
//			/*
//			EObject obj = m.resource.getResourceSet().getEObject(((InternalEObject) proxy).eProxyURI(), false);
//			if ( obj != null ) {
//				System.out.println("Propertly obtained!!!" + proxy);
//				return new Pair<EClassifier, MetamodelNamespace>((EClassifier) obj, m);				
//			}
//			*/
//			
//			EClassifier c  = m.uriToClassifier.get(EMFUtils.getURI(proxy, m.resourceSet));
//			if ( c != null ) {
//				return new Pair<EClassifier, MetamodelNamespace>(c, m);
//			}
//		}
//		return null;
//	}
	}
	
	public MetamodelNamespace getNamespace(String name) {
		if ( namesToMetamodels.containsKey(name) ) 
			return namesToMetamodels.get(name);
		
		return null;
	}
	
	public List<MetamodelNamespace> getMetamodels() {
		return new ArrayList<MetamodelNamespace>(namesToMetamodels.values());
	}

	private HashMap<EClass, ArrayList<ClassNamespace>> subtypes = new HashMap<EClass, ArrayList<ClassNamespace>>();
	public Collection<ClassNamespace> getDirectSubclasses(EClass eClass) {
		ArrayList<ClassNamespace> result = subtypes.get(eClass);
		if ( result != null)
			return result;

		result = new ArrayList<ClassNamespace>();
		for(MetamodelNamespace m : namesToMetamodels.values()) {
			for(ITypeNamespace t : m.classifiers.values()) {
				if ( t instanceof ClassNamespace ) {
					ClassNamespace c = (ClassNamespace) t;
					
					if ( c.eClass.getESuperTypes().contains(eClass)) {
						result.add(c);
					}					
				}
			}			
		}
		
		subtypes.put(eClass, result);
		return result;
	}


	
}
