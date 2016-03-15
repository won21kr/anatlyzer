package anatlyzer.atl.editor.builder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2m.atl.common.AtlNbCharFile;
import org.eclipse.m2m.atl.engine.Messages;

import anatlyzer.atl.analyser.AnalyserInternalError;
import anatlyzer.atl.analyser.AnalysisResult;
import anatlyzer.atl.editor.builder.AnalyserExecutor.AnalyserData;
import anatlyzer.atl.errors.Problem;
import anatlyzer.atl.errors.ProblemStatus;
import anatlyzer.atl.errors.atl_error.LocalProblem;
import anatlyzer.atl.index.AnalysisIndex;
import anatlyzer.atl.index.IndexChangeListener;
import anatlyzer.atl.model.ATLModel;
import anatlyzer.atl.model.ErrorUtils;
import anatlyzer.atl.util.AnalyserUtils.CannotLoadMetamodel;
import anatlyzer.atl.util.AnalyserUtils.PreconditionParseError;
import anatlyzer.ui.configuration.ConfigurationReader;
import anatlyzer.ui.configuration.TransformationConfiguration;
import anatlyzer.ui.util.WorkspaceLogger;

public class AnATLyzerBuilder extends IncrementalProjectBuilder {

	// Configure the listener to remove or add markers as status changes
	static {
		AnalysisIndex.getInstance().addListener(new MarkerUpdaterListerner());
	}
	
	static class MarkerUpdaterListerner implements IndexChangeListener {

		@Override
		public void analysisRegistered(IResource r, AnalysisResult result, AnalysisResult previous) { }

		@Override
		public void statusChanged(IResource r, Problem problem, ProblemStatus oldStatus) {
			TransformationConfiguration c = AnalysisIndex.getInstance().getConfiguration(r);			
			
			try {
				IMarker found = null;
				IMarker[] markers = r.findMarkers(MARKER_TYPE, false, 1);
				for (IMarker iMarker : markers) {
					if ( iMarker.getAttribute(PROBLEM) == problem ) {
						found = iMarker;
						break;
					}
				}
				
				if ( found != null ) {
					if ( ! c.isMarkerWanted(problem.getStatus() ) ) {
						found.delete();
					} else {
						// Change por instance the severity, if needed						
					}
				} else {
					if ( c.isMarkerWanted(problem.getStatus() ) ) {
						AtlNbCharFile help = new AtlNbCharFile(((IFile) r).getContents());
						AnalysisResult data  = AnalysisIndex.getInstance().getAnalysis(r);						
						addMarker(r, help, data, problem);
					}
				}
				
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	
	class SampleDeltaVisitor implements IResourceDeltaVisitor {
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			
			// Avoid checking transformations in "bin" directories, which
			// are normally marked as derived.
			if ( resource.isDerived(IResource.CHECK_ANCESTORS) ) {
				return false;
			}
			
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				// handle added resource
				checkATL(resource);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				checkATL(resource);
				break;
			}
			//return true to continue visiting children.
			return true;
		}
	}

	class SampleResourceVisitor implements IResourceVisitor {
		public boolean visit(IResource resource) {
			checkATL(resource);
			//return true to continue visiting children.
			return true;
		}
	}

	public static final String BUILDER_ID = "anatlyzer.atl.editor.anatlyzerATLBuilder";

	public static final String MARKER_TYPE = "anatlyzer.atl.editor.anatlyzerATLProblem";

	public static final String PROBLEM = "ANATLYZER_PROBLEM";

	public static final String ANALYSIS_DATA = "ANATLYZER_ANALYSIS_DATA";

	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
			throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	public void checkATL(IResource resource) {
		Supplier<AtlNbCharFile> helperCreator = () -> {
			try {
				return new AtlNbCharFile(((IFile) resource).getContents());
			} catch (CoreException e) {
				WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);				
			}
			return null;
		};
		
		check(resource, helperCreator, 
		() -> {
			IFile file = (IFile) resource;				
			try {
				return new AnalyserExecutor().exec(resource);
			} catch ( CoreException e) {
				WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);								
			} catch (IOException e) {
				WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);				
			} catch (CannotLoadMetamodel e) {
				try {
					addMarker(file, helperCreator.get(), null, e.getProblem());
				} catch (CoreException e1) {
					e.printStackTrace();
				}
			} catch (PreconditionParseError e) {
				try {
					addMarker(file, helperCreator.get(), null, e.getProblem());
				} catch (CoreException e1) {
					e.printStackTrace();
				}
			}
			return null;
		});
	}

	/**
	 * This is intended to be called using the IDocument text, not the contents of the resource in the filesystem.
	 * @param f
	 * @param string
	 */
	public void checkFromText(IResource resource, String text) {
		
		Supplier<AtlNbCharFile> helperCreator = () -> {
			return new AtlNbCharFile(new ByteArrayInputStream(text.getBytes()));
		};
		
		check(resource, helperCreator, 
		() -> {
			IFile file = (IFile) resource;				
			try {
				return new AnalyserExecutor().exec(resource, new ByteArrayInputStream(text.getBytes()), true);
			} catch ( CoreException e) {
				WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);								
			} catch (IOException e) {
				WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);				
			} catch (CannotLoadMetamodel e) {
				try {
					addMarker(file, helperCreator.get(), null, e.getProblem());
				} catch (CoreException e1) {
					e.printStackTrace();
				}
			} catch (PreconditionParseError e) {
				try {
					addMarker(file, helperCreator.get(), null, e.getProblem());
				} catch (CoreException e1) {
					e.printStackTrace();
				}
			}
			return null;
		});
	}		
	
	protected void check(IResource resource,  Supplier<AtlNbCharFile> currentFileHelperCreator, Supplier<AnalyserData> analysisExecutor) {

		if (resource instanceof IFile && resource.getName().endsWith(".atlc")) {
			readConfiguration((IFile) resource);
		}
		else if (resource instanceof IFile && resource.getName().endsWith(".atl")) {
			IFile file = (IFile) resource;
			TransformationConfiguration c = AnalysisIndex.getInstance().getConfiguration(resource);
			if ( c == null ) {
				c = initConfigurationForAtl(file);
			}
			
			deleteMarkers(file);

			AtlNbCharFile help = null;
			try {				
				// help = new AtlNbCharFile(file.getContents());
				help = currentFileHelperCreator.get();
				
				HashMap<String, AtlNbCharFile> helpers = new HashMap<String, AtlNbCharFile>();
				helpers.put(file.getLocation().toPortableString(), help);
				
				// AnalyserData data = new AnalyserExecutor().exec(resource);
				AnalyserData data = analysisExecutor.get();
				if ( data == null ) {
					System.out.println("May be syntax errors in " + resource.getName());
					return; // if there are syntax errors!
				}
				
				for (Problem problem : data.getNonIgnoredProblems()) {
					IFile problemFile = file;
					if ( problem instanceof LocalProblem ) {
						String loc = ((LocalProblem) problem).getFileLocation();
						
						// This ignores errors in preconditions written in @pre comments
						if ( ATLModel.PRECONDITIONS_LOCATION.equals(loc))
							continue;
						
						if ( loc == null ) {
							System.err.println("Warning: No location assigned to " + problem);
							problemFile = file; // Not sure when this might happen
						} else {
							problemFile = (IFile)ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(loc));
						}
						
						loc = problemFile.getLocation().toPortableString(); // I need to use the same location as the first helper creation above
						if ( ! helpers.containsKey(loc) ) {
							helpers.put(loc, new AtlNbCharFile(problemFile.getContents()));
						}
						help = helpers.get(loc);
					}
					
					if ( c.isMarkerWanted(problem.getStatus()) )
						addMarker(problemFile, help, data, problem);
				}
				
				// Launch the model finder automatically if the option is set
				if ( c != null && c.isContinousWitnessFinder() ) {
					execModelFinderJob((IFile) resource, data);
				}
				
			} catch (AnalyserInternalError e) {
				WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);				
			} catch (CoreException e) {
				e.printStackTrace();
				WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);
			/*
			} catch (IOException e) {
				WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);				
			} catch (CannotLoadMetamodel e) {
				try {
					addMarker(file, help, null, e.getProblem());
				} catch (CoreException e1) {
					e.printStackTrace();
				}
			*/
			}
		}
	}

	private void execModelFinderJob(IFile f, AnalyserData data) {
		// Cancel previous jobs (there should be at most one) before launching the new one
		Job[] jobs = Job.getJobManager().find(f);
		for (Job job : jobs) {
			if (!job.cancel()) {
				try {
					job.join();
				} catch (InterruptedException e) { }
			}
		}
		
		WitnessFinderJob job = new WitnessFinderJob(f, data);
		job.schedule();		
	}

	/**
	 * Reads the corresponding configuration file of a given transformation or
	 * initializes a default value. In both cases the configuration is added to
	 * the index.
	 * 
	 * @param atlFile
	 * @return
	 */
	private TransformationConfiguration initConfigurationForAtl(IFile atlFile) {
		IPath confPath = atlFile.getFullPath().removeFileExtension().addFileExtension("atlc");
		IFile confFile = atlFile.getWorkspace().getRoot().getFile(confPath);
		TransformationConfiguration c = readConfiguration(confFile, atlFile);		
		if ( c == null ) {
			c = TransformationConfiguration.getDefault();
			AnalysisIndex.getInstance().register(atlFile, c);							
		}
		return c;
	}
	
	private TransformationConfiguration readConfiguration(IFile file) {
		IPath atlPath = file.getFullPath().removeFileExtension().addFileExtension("atl");
		IFile atlFile = file.getWorkspace().getRoot().getFile(atlPath);
		return readConfiguration(file, atlFile);
	}
	

	private TransformationConfiguration readConfiguration(IFile confFile, IFile atlFile) {
		try {
			if ( confFile.exists() && atlFile.exists() ) {
				TransformationConfiguration c = ConfigurationReader.read(confFile.getContents());
				AnalysisIndex.getInstance().register(atlFile, c);				
				return c;
			}
		} catch (IOException e) {
			WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);
		} catch (CoreException e) {
			WorkspaceLogger.generateLogEntry(IStatus.ERROR, e);
		}		
		return null;
	}

	private static void addMarker(IResource file, AtlNbCharFile help, AnalysisResult data, Problem problem) throws CoreException {
		LocalProblem lp = (LocalProblem) problem;
		
		int tabWidth = -1;
		int severity = -1;
		
		severity = IMarker.SEVERITY_ERROR;
		/* UNTIL I HAVE INLINE QUICKFIXES, IT IS EASIER TO ACCESS TO ERRORS THAN WARNINGS
		switch ( problem.getSeverity() ) {
		case ERROR:
			severity = IMarker.SEVERITY_ERROR;
			break;
		case WARNING:
			severity = IMarker.SEVERITY_WARNING;
			break;
		case PERFORMANCE_SUGGESTION:
			severity = IMarker.SEVERITY_INFO;
			break;
		case STYLE_SUGGESTION:
			severity = IMarker.SEVERITY_INFO;
			break;
		}
		*/
		
		
		String description = ErrorUtils.getShortError(lp);
		String location = lp.getLocation();
		String[] parts = null; // 
		
		// Location may be null if there are some elements introduced
		// programatically
		if ( location == null ) {
			parts = new String[] { "0", "0" };
		} else {
			parts = location.split("-")[0].split(":"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		int lineNumber = Integer.parseInt(parts[0]);
		int columnNumber = Integer.parseInt(parts[1]);
		int charStart = 0;
		int charEnd = 0;
		try {
			if (location != null && location.indexOf('-') == -1) {
				location += '-' + location;
			}
			
			if ( location != null ) {
			int[] pos = help.getIndexChar(location, tabWidth);
				charStart = pos[0];
				charEnd = pos[1];
			}

			IMarker pbmMarker = file.createMarker(MARKER_TYPE);
			pbmMarker.setAttribute(PROBLEM, problem);
			pbmMarker.setAttribute(ANALYSIS_DATA, data);
			
			
			// pbmMarker.setAttribute(IMarker.SEVERITY, eclipseSeverity);
			pbmMarker.setAttribute(IMarker.SEVERITY, severity); 
			pbmMarker.setAttribute(IMarker.MESSAGE, description);
			pbmMarker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			pbmMarker.setAttribute(IMarker.LOCATION, Messages.getString("MarkerMaker.LINECOLUMN", //$NON-NLS-1$
					new Object[] {new Integer(lineNumber), new Integer(columnNumber)}));
			pbmMarker.setAttribute(IMarker.CHAR_START, charStart);
			pbmMarker.setAttribute(IMarker.CHAR_END, (charEnd > charStart) ? charEnd : charStart + 1);

			// pbmMarker.setAttribute(MarkerResolutionGenerator.ORIGINAL_PROBLEM, lp);
			
		} catch (Exception e) {
			// description += " [location \"" + location + "\" incorrectly reported because of error]"; //$NON-NLS-1$ //$NON-NLS-2$
			throw new RuntimeException(e);
			//$NON-NLS-1$//$NON-NLS-2$
		}

		// TODO Auto-generated method stub
	
		/*		EPackage pkProblem = null;
		EClass clProblem = null;
		EStructuralFeature sfSeverity = null;
		EStructuralFeature sfLocation = null;
		EStructuralFeature sfDescription = null;

		pkProblem = problem.eClass().getEPackage();
		clProblem = (EClass)pkProblem.getEClassifier("Problem"); //$NON-NLS-1$
		sfSeverity = clProblem.getEStructuralFeature("severity"); //$NON-NLS-1$
		sfLocation = clProblem.getEStructuralFeature("location"); //$NON-NLS-1$
		sfDescription = clProblem.getEStructuralFeature("description"); //$NON-NLS-1$

		String description = (String)problem.eGet(sfDescription);

		String location = (String)problem.eGet(sfLocation);
		String[] parts = location.split("-")[0].split(":"); //$NON-NLS-1$ //$NON-NLS-2$
		int lineNumber = Integer.parseInt(parts[0]);
		int columnNumber = Integer.parseInt(parts[1]);
		int charStart = 0;
		int charEnd = 0;
		try {
			if (location.indexOf('-') == -1) {
				location += '-' + location;
			}
			int[] pos = help.getIndexChar(location, tabWidth);
			charStart = pos[0];
			charEnd = pos[1];
		} catch (Exception e) {
			description += " [location \"" + location + "\" incorrectly reported because of error]"; //$NON-NLS-1$ //$NON-NLS-2$
			//$NON-NLS-1$//$NON-NLS-2$
		}

		String severity = ((EEnumLiteral)problem.eGet(sfSeverity)).getName();
		int eclipseSeverity = ((Integer)severities.get(severity)).intValue();

		try {
			IMarker pbmMarker = res.createMarker(PROBLEM_MARKER);
			pbmMarker.setAttribute(IMarker.SEVERITY, eclipseSeverity);
			pbmMarker.setAttribute(IMarker.MESSAGE, description);
			pbmMarker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			pbmMarker.setAttribute(IMarker.LOCATION, Messages.getString("MarkerMaker.LINECOLUMN", //$NON-NLS-1$
					new Object[] {new Integer(lineNumber), new Integer(columnNumber)}));
			pbmMarker.setAttribute(IMarker.CHAR_START, charStart);
			pbmMarker.setAttribute(IMarker.CHAR_END, (charEnd > charStart) ? charEnd : charStart + 1);
			*/
	}

	private void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
		}
	}

	protected void fullBuild(final IProgressMonitor monitor)
			throws CoreException {
		try {
			getProject().accept(new SampleResourceVisitor());
		} catch (CoreException e) {
		}
	}

	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor monitor) throws CoreException {
		// the visitor does the work.
		delta.accept(new SampleDeltaVisitor());
	}

}
