package anatlyzer.atl.editor.quickfix.errors;

import java.util.*;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.text.BadLocationException;

import anatlyzer.atl.editor.quickfix.AbstractAtlQuickfix;
import anatlyzer.atl.editor.quickfix.util.ATLUtils2;
import anatlyzer.atl.editor.quickfix.util.Conversions;
import anatlyzer.atl.editor.quickfix.util.stringDistance.Levenshtein;
import anatlyzer.atl.editor.quickfix.util.stringDistance.StringDistance;
import anatlyzer.atl.util.ATLUtils;
import anatlyzer.atlext.ATL.Helper;
import anatlyzer.atlext.ATL.LazyRule;
import anatlyzer.atlext.ATL.MatchedRule;
import anatlyzer.atlext.ATL.OutPattern;
import anatlyzer.atlext.ATL.Rule;
import anatlyzer.atlext.OCL.IteratorExp;
import anatlyzer.atlext.OCL.OclExpression;
import anatlyzer.atlext.OCL.OperationCallExp;
import anatlyzer.atlext.OCL.VariableDeclaration;
import anatlyzer.atlext.OCL.Iterator;
import anatlyzer.atlext.OCL.VariableExp;
import anatlyzer.atl.types.Type;

public abstract class OperationNotFoundAbstractQuickFix extends AbstractAtlQuickfix {
	protected static final int threshold = 3;				// threshold distance to try an operation name with +1 or -1 params
	protected Map<Integer, List<String>> candidateOps;		// to be populated by children classes
	private StringDistance sd = new StringDistance(new Levenshtein());
	
	protected abstract Map<Integer, List<String>> populateCandidateOps();		// force to populate the Map somehow
	
	protected static HashMap<String, List<CollType>> primitiveParam = new HashMap<>();		// probably move this to superclass
	static {
		primitiveParam.put("append", Collections.singletonList(CollType.UserDefined));
		primitiveParam.put("at", Collections.singletonList(CollType.Integer));
		primitiveParam.put("subsequence", Arrays.asList(CollType.Integer, CollType.Integer));
		primitiveParam.put("refGetValue", Collections.singletonList(CollType.String));
		primitiveParam.put("resolveTemp", Arrays.asList(CollType.String, CollType.UserDefined));
	}
	
	enum CollType { 
		Integer ("0"), 
		String("''"),
		UserDefined("param");
		
		private String defaultLiteral;
		
		CollType(String dl) { this.defaultLiteral = dl; }		
		public String defaultLiteral() { return this.defaultLiteral;}
		public void setDefaultLiteral(String dl) { this.defaultLiteral = dl; }
	}	
	
	/**
	 * Auxiliary operation for getClosest
	 * @param op
	 * @param numPar
	 * @param distance
	 * @return
	 */
	protected int getClosestDistance(String op, int numPar, List<Integer> distance) {
		if (!(this.candidateOps.containsKey(numPar))) return 1000;
		distance.addAll(this.sd.distance(op, this.candidateOps.get(numPar)));	
		System.out.println(this.candidateOps.get(numPar)+"\n"+distance);		
		return Collections.min(distance);
	}
	
	/**
	 * Heuristic method to obtain closest operation name, given an operation op. The algorithm takes
	 * into account the number of parameters and uses {@link Levenshtein} distance
	 * @param op
	 * @param numPar number of parameters of operation op
	 * @return
	 */
	protected String getClosest(String op, int numPar) {
		HashMap<Integer, List<Integer>> distances = new HashMap<>();
		distances.put(numPar, new ArrayList<Integer>());
		
		int minDistance = this.getClosestDistance(op, numPar, distances.get(numPar));
		
		if (minDistance >= OperationNotFoundAbstractQuickFix.threshold) {		
			List<Integer> pars2explore = new ArrayList<Integer>();
			pars2explore.add(numPar+1);
			if (numPar > 0) pars2explore.add(numPar-1);			
			
			int minD = 10;
			int param = -1;
			
			for (int p : pars2explore) {
				distances.put(p, new ArrayList<Integer>());
				int currentD = this.getClosestDistance(op, p, distances.get(p)); 
				if (currentD < minD) {
					param = p;
					minD = currentD;
				}
			}
			if (minD < minDistance) {
				numPar = param;
				minDistance = minD;
			}
		}
			
		int closestIndex = distances.get(numPar).indexOf(minDistance);
		String closestOp = this.candidateOps.get(numPar).get(closestIndex);
		System.out.println("Closest is "+closestOp);
		return closestOp;					
	}
	
	/**
	 * @param closest
	 * @return number of params of closest
	 */
	protected int getParamsClosest(String closest) {
		for (int par : Arrays.asList(0, 1, 2)) {
			if (this.candidateOps.getOrDefault(par, Collections.emptyList()).contains(closest)) 
				return par;			
		}
		return 0;
	}
	
	private Helper getHelper(String name) {
		List<Helper> helpers = ATLUtils.getAllHelpers(this.getATLModel());
		Optional<Helper> helper = helpers.stream().filter( p -> ATLUtils.getHelperName(p).equals(name)).findAny();
		return helper.orElse(null);
	}
	
	private void fixParams( Helper replaced, OperationCallExp c, int numP, int paramsClosest ) {
		Type [] types = ATLUtils.getArgumentTypes(replaced);
		for (int i = numP; i < paramsClosest; i++) {
			c.getArguments().add(Conversions.createDefaultOCLLiteral(types[i]));
		}	
	}
	
	private void fixParams( String replaced, OperationCallExp c, int numP, int paramsClosest ) {
		for (int i = numP; i < paramsClosest; i++) {
			CollType ct = primitiveParam.get(replaced).get(i);
			OclExpression exp = Conversions.createDefaultOCLLiteral(ct.name());
			if (exp instanceof VariableExp) {
				VariableExp ve = (VariableExp) exp;
				List<VariableDeclaration> lvd = ATLUtils2.getAvailableDeclarations(c);
				if (lvd.size()>0) ve.setReferredVariable(lvd.get(0));		// TODO: Do type checking
				else {} // TODO: create an object?
			}
			c.getArguments().add(exp);
		}	
	}
	
	protected void fixParams( String closest, OperationCallExp c) {
		int paramsClosest = this.getParamsClosest(closest);
		int numP = c.getArguments().size();
		
		Helper replaced = this.getHelper(closest);		
		
		if (paramsClosest > numP) {
			System.out.println("You need to add "+(paramsClosest - numP )+" params");
			if (replaced != null) this.fixParams(replaced, c, numP, paramsClosest);
			else if (primitiveParam.containsKey(closest)) this.fixParams(closest, c, numP, paramsClosest);
		}
		else if (paramsClosest < numP) {
			System.out.println("You need to remove "+(numP - paramsClosest )+" params");
			// We remove the last ones?
			for (int i = 0; i< numP-paramsClosest; i++)
				c.getArguments().remove(c.getArguments().size()-1);
		}
		else System.out.println("number of params match.");
	}
}
