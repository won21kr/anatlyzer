package anatlyzer.atl.analyser.batch.invariants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import anatlyzer.atl.analyser.IAnalyserResult;
import anatlyzer.atl.analyser.generators.CSPModel2;
import anatlyzer.atl.analyser.generators.ErrorSlice;
import anatlyzer.atl.analyser.generators.GraphvizBuffer;
import anatlyzer.atl.model.ATLModel;
import anatlyzer.atl.model.TypeUtils;
import anatlyzer.atl.model.TypingModel;
import anatlyzer.atl.types.Metaclass;
import anatlyzer.atl.types.Type;
import anatlyzer.atl.util.ATLCopier;
import anatlyzer.atl.util.ATLSerializer;
import anatlyzer.atl.util.ATLUtils;
import anatlyzer.atl.util.BuildUtils;
import anatlyzer.atl.util.Pair;
import anatlyzer.atl.util.UnsupportedTranslation;
import anatlyzer.atlext.ATL.Binding;
import anatlyzer.atlext.ATL.ContextHelper;
import anatlyzer.atlext.ATL.InPatternElement;
import anatlyzer.atlext.ATL.LazyRule;
import anatlyzer.atlext.ATL.MatchedRule;
import anatlyzer.atlext.ATL.OutPatternElement;
import anatlyzer.atlext.ATL.Rule;
import anatlyzer.atlext.ATL.RuleResolutionInfo;
import anatlyzer.atlext.ATL.RuleWithPattern;
import anatlyzer.atlext.OCL.CollectionExp;
import anatlyzer.atlext.OCL.CollectionOperationCallExp;
import anatlyzer.atlext.OCL.Iterator;
import anatlyzer.atlext.OCL.IteratorExp;
import anatlyzer.atlext.OCL.LetExp;
import anatlyzer.atlext.OCL.NavigationOrAttributeCallExp;
import anatlyzer.atlext.OCL.OCLFactory;
import anatlyzer.atlext.OCL.OCLPackage;
import anatlyzer.atlext.OCL.OclExpression;
import anatlyzer.atlext.OCL.OclModel;
import anatlyzer.atlext.OCL.OclModelElement;
import anatlyzer.atlext.OCL.OclType;
import anatlyzer.atlext.OCL.OclUndefinedExp;
import anatlyzer.atlext.OCL.OperationCallExp;
import anatlyzer.atlext.OCL.OperatorCallExp;
import anatlyzer.atlext.OCL.PrimitiveExp;
import anatlyzer.atlext.OCL.SetExp;
import anatlyzer.atlext.OCL.VariableDeclaration;
import anatlyzer.atlext.OCL.VariableExp;

public class InvariantGraphGenerator {
	private ATLModel model;
	private IAnalyserResult result;

	public InvariantGraphGenerator(IAnalyserResult result) {
		this.result = result;
		this.model = result.getATLModel();
	}

	// Additional result: generated helpers
	protected List<TranslatedHelper> translatedHelpers = new ArrayList<TranslatedHelper>();
	public List<TranslatedHelper> getTranslatedHelpers() { return translatedHelpers; }
	
	public IInvariantNode replace(OclExpression expr) {
		translatedHelpers = new ArrayList<InvariantGraphGenerator.TranslatedHelper>();

		IInvariantNode result = analyse(expr, new Env(null));

		// Set a "NullParent"
		result.setParent(new NullParent());
		return result;
	}

	private final class NullParent implements IInvariantNode {
		@Override
		public void setParent(IInvariantNode node) { }

		@Override
		public boolean isUsed(InPatternElement e) {
			return false;
		}

		@Override
		public void getTargetObjectsInBinding(Set<OutPatternElement> elems) { }

		@Override
		public IInvariantNode getParent() { throw new UnsupportedOperationException(); }

		@Override
		public SourceContext<RuleWithPattern> getContext() { throw new UnsupportedOperationException(); }

		@Override
		public Pair<LetExp, LetExp> genIteratorBindings(CSPModel2 builder,
				Iterator it, Iterator targetIt) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public OclExpression genExpr(CSPModel2 builder) {
			return null;
		}

		@Override
		public void genErrorSlice(ErrorSlice slice) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void genGraphviz(GraphvizBuffer<IInvariantNode> gv) {
			throw new UnsupportedOperationException();			
		}

		@Override
		public OclExpression genExprNorm(CSPModel2 builder) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public List<Iterator> genIterators(CSPModel2 builder) {
			throw new IllegalStateException();
		}
		
	}



	public class Env {
		SourceContext<? extends RuleWithPattern>  context;
		HashMap<VariableDeclaration, SourceContext<? extends RuleWithPattern> > map = new HashMap<>();
		public Env(SourceContext<? extends RuleWithPattern> context) { this.context = context; }
		public Env put(VariableDeclaration vd, SourceContext<? extends RuleWithPattern> context) {
			if ( context == null )
				throw new IllegalArgumentException();
			Env env = new Env(context); 
			env.map = new HashMap<>(map);
			env.map.put(vd, context);
			return env;
		}

		public Env putAll(List<VariableDeclaration> refs, SourceContext<? extends RuleWithPattern> sourceContext) {
			Env env = this;
			for (VariableDeclaration vd : refs) {
				env = env.put(vd, sourceContext);
			}
			return env;
		}

		
		public SourceContext<? extends RuleWithPattern>  getContext(VariableDeclaration v) {
			if ( ! map.containsKey(v) )
				throw new IllegalStateException("Not var for: " + v);
			return map.get(v);
		}
		
		public HashMap<? extends EObject, ? extends EObject> getVarMapping() {
			HashMap<EObject, EObject> mapping = new HashMap<EObject, EObject>();
			Set<Entry<VariableDeclaration, SourceContext<? extends RuleWithPattern>>> entrySet = map.entrySet();
			for (Entry<VariableDeclaration, SourceContext<? extends RuleWithPattern>> entry : entrySet) {
				mapping.put(entry.getKey(), entry.getValue().getRule().getInPattern().getElements().get(0));
			}
			return mapping;
		}
	}
	
	protected IInvariantNode analyse(OclExpression expr, Env env) {
		if ( expr instanceof CollectionOperationCallExp ) return checkColExp((CollectionOperationCallExp) expr, env);
		else if ( expr instanceof OperatorCallExp ) return checkOperatorCallExp((OperatorCallExp) expr, env);
		else if ( expr instanceof OperationCallExp ) return checkOperationCallExp((OperationCallExp) expr, env);
		else if ( expr instanceof NavigationOrAttributeCallExp ) return checkNavExp((NavigationOrAttributeCallExp) expr, env);
		else if ( expr instanceof IteratorExp ) return checkIteratorExp((IteratorExp) expr, env);
		else if ( expr instanceof VariableExp ) return checkVariableExp((VariableExp) expr, env);
		else if ( expr instanceof PrimitiveExp ) return new GenericExpNode(null, expr);
		else if ( expr instanceof OclUndefinedExp ) return new GenericExpNode(null, expr);
		else if ( expr instanceof CollectionExp ) return checkCollectionExp((CollectionExp) expr, env);
		
		throw new UnsupportedOperationException(expr.toString());
	}
	
	protected IInvariantNode analyse(OclExpression expr, Env env, Function<IInvariantNode, IInvariantNode> mapper, Function<List<IInvariantNode>, IInvariantNode> flattener) {
		IInvariantNode node = analyse(expr, env);

		if ( node instanceof MultiNode ) {
			MultiNode m = (MultiNode) node;
			
			List<IInvariantNode> ops = m.map(mapper);
			return flattener.apply(ops);
		} else {
			return mapper.apply(node);
		}
	}

	private IInvariantNode checkCollectionExp(CollectionExp expr, Env env) {
		if ( expr.getElements().size() > 0 )
			throw new UnsupportedTranslation("TODO: Support Col { } with elements");

		return new GenericExpNode(null, expr);
	}

	private void converHelper(ContextHelper h, OperationCallExp caller, SourceContext<? extends RuleWithPattern> sourceContext) {
		OclExpression body = ATLUtils.getBody(h);
		
		if ( ATLUtils.getArgumentTypes(h).length > 0 ) {
			throw new UnsupportedOperationException("Only helpers with 0 args supported. Not clear how to translate arguments if there is more than one corresponding rule for an argument type");
		}
		
		Env env = new Env(sourceContext).putAll(ATLUtils.findSelfReferences(h), sourceContext);		
		IInvariantNode result = analyse(body, env);
		result.setParent(new NullParent());

		translatedHelpers.add( new TranslatedHelper(h, result, sourceContext) );
	}
	
	private IInvariantNode checkVariableExp(VariableExp expr, Env env) {
		return new VariableExpNode(expr, env.getContext(expr.getReferredVariable()));
	}

	private IInvariantNode checkIteratorExp(IteratorExp expr, final Env env) {
		IInvariantNode src = analyse(expr.getSource(), env);

		if ( src instanceof NoResolutionNode && !((NoResolutionNode) src).evaluateSubsequentNodes()) {
			return src;
		} else	if ( src instanceof MultiNode ) {
			MultiNode m = (MultiNode) src;
			
			List<IInvariantNode> paths = m.map(n -> {
				Env env2 = env.put(expr.getIterators().get(0), n.getContext());
				IInvariantNode body = analyse(expr.getBody(), env2);
		
				return new IteratorExpNode(n, body, expr);				
			});
		
			if ( expr.eContainingFeature() == OCLPackage.Literals.PROPERTY_CALL_EXP__SOURCE || 
				 expr.eContainingFeature() == null ) { // This is to handle the case in which the node is generated dynamically... 
				return new MultiNode(paths);
			} else {
				// TODO: Check types
				return new SplitIteratorSourceNode(expr, paths);				
			}
		} else {
//			Env env2 = env;
//			if ( ! (src instanceof NoResolutionNode) )
//				env2 = env.put(expr.getIterators().get(0), src.getContext());
			Env env2 = env.put(expr.getIterators().get(0), src.getContext());
			IInvariantNode body = analyse(expr.getBody(), env2);
	
			// TODO: Take into account cardinality and the kind of iterator?
			if ( body instanceof MultiNode ) {			
				SplitIteratorBodyNode split = new SplitIteratorBodyNode(((MultiNode) body).getNodes(), expr);
				body = split;
			}
			
			return new IteratorExpNode(src, body, expr);
		}		
	}
	
	private static HashSet<String> colRetOps = new HashSet<String>();
	static {
		colRetOps.add("flatten");
	}
	
	private AbstractInvariantReplacerNode checkColExp(CollectionOperationCallExp expr, Env env) {
		IInvariantNode src = analyse(expr.getSource(), env);

		// Depending on the kind of operation, we need to continue with a split path or to merge it.
		// This depends on whether the operation returns another collection or not, e.g., flatten
		if ( colRetOps.contains(expr.getOperationName()) ) {
			// TODO: XXX For flatten do not split, for asSet merge and split again the path
		}
		
		if ( src instanceof MultiNode ) {			
			SplitCollectionOperationNode split = new SplitCollectionOperationNode(((MultiNode) src).getNodes(), expr);
			src = split;
		}
		
		List<IInvariantNode> args = expr.getArguments().stream().map(a -> analyse(a, env)).collect(Collectors.toList());
		
		CollectionOperationCallExpNode node = new CollectionOperationCallExpNode(src, expr, args);
		return node;
	}

	protected IInvariantNode checkOperatorCallExp(OperatorCallExp self, Env env) {
		return analyse(self.getSource(), env, 
			(n) -> {
				List<IInvariantNode> args = self.getArguments().stream().map(a -> 
					analyse(a, env, (arg) -> arg, (nodes) -> new SplitOperand(nodes, self))).
					collect(Collectors.toList());
				return new OperatorCallExpNode(n, self, args);		
			}, 
			(nodes) -> new SplitNodeOperation(nodes, self));

		
//		IInvariantNode src = analyse(self.getSource(), env);

//		if ( src instanceof MultiNode ) {			
//			List<IInvariantNode> ops = ((MultiNode) src).getNodes().stream().map(n -> {
//				List<IInvariantNode> args = self.getArguments().stream().map(a -> analyse(a, env)).collect(Collectors.toList());
//				return new OperatorCallExpNode(n, self, args);					
//			}).collect(Collectors.toList());
//			
//			SplitNodeOperation split = new SplitNodeOperation(ops, self);
//			return split;

//			List<IInvariantNode> ops = ((MultiNode) src).getNodes().stream().map(n -> {
//					List<IInvariantNode> args = self.getArguments().stream().map(a -> analyse(a, env)).collect(Collectors.toList());
//					return new OperatorCallExpNode(n, self, args);					
//				}).collect(Collectors.toList());
//		
//			SplitNodeOperation split = new SplitNodeOperation(ops, self);
//			return split;			
//		} else {
//			List<IInvariantNode> args = self.getArguments().stream().
//					map(a -> analyse(a, env, (nodes) -> new SplitOperand(nodes, self) ) ).
//					collect(Collectors.toList());		
//			OperatorCallExpNode node = new OperatorCallExpNode(src, self, args);
//			return node;
//		}
	}
	
	protected IInvariantNode checkOperationCallExp(OperationCallExp self, Env env) {
		if ( self.getOperationName().equals("allInstances") ) {
			List<SourceContext<? extends RuleWithPattern>> rules = findTargetOcurrences((OclModelElement) self.getSource());
			if ( rules.size() == 0 ) 
				throw new IllegalStateException("No rules for " + ((OclModelElement) self.getSource()).getName());
			
			List<IInvariantNode> nodes = rules.stream().map(r -> {
				return new AllInstancesNode((SourceContext<RuleWithPattern>) r, this.result);
			}).collect(Collectors.toList());
			
			if ( nodes.size() == 1 ) {
				return nodes.get(0);
			} else {
				return new MultiNode(nodes);
			}
		} else if ( self.getOperationName().equals("oclIsKindOf") ) {
			IInvariantNode src = analyse(self.getSource(), env);

			if ( src instanceof MultiNode ) {			
				List<IInvariantNode> ops = ((MultiNode) src).getNodes().stream().map(n -> {
					return createKindOfNode(self, n);
				}).collect(Collectors.toList());
				
				SplitNodeOperation split = new SplitNodeOperation(ops, self);
				return split;
			} else {
				return createKindOfNode(self, src);
			}
		} else {
			// In contrast to colOp, the split operations goes after
			IInvariantNode src = analyse(self.getSource(), env);

			if ( src instanceof MultiNode ) {			
				List<IInvariantNode> ops = ((MultiNode) src).getNodes().stream().map(n -> {
					List<IInvariantNode> args = self.getArguments().stream().map(a -> analyse(a, env)).collect(Collectors.toList());
					return new OperationCallExpNode(n, self, args);					
				}).collect(Collectors.toList());
				
				SplitNodeOperation split = new SplitNodeOperation(ops, self);
				return split;
			} else {
				if ( isBuiltinOperation(self) ) {
					// That's fine, just copy the result
				} else {
					for(ContextHelper h : self.getDynamicResolvers()) {
						converHelper(h, self, src.getContext());
					}
					// Parameter passing should be seamlessly handled by the args mapping below
				}
				
				List<IInvariantNode> args = self.getArguments().stream().map(a -> analyse(a, env)).collect(Collectors.toList());				
				return new OperationCallExpNode(src, self, args);
			}
			
//			// Similar to colOp
//			IInvariantNode src = analyse(self.getSource(), env);
//
//			if ( src instanceof MultiNode ) {			
//				SplitNodeOperation split = new SplitNodeOperation(((MultiNode) src).getNodes(), self);
//				src = split;
//			}
//			
//			List<IInvariantNode> args = self.getArguments().stream().map(a -> analyse(a, env)).collect(Collectors.toList());			
//			// CollectionOperationCallExpNode node = new CollectionOperationCallExpNode(src, expr, args);
//			// return node;			
//			return new OperationCallExpNode(src, self, args);
		}
	}

	private boolean isBuiltinOperation(OperationCallExp self) {
		return self.getStaticResolver() == null;
	}

	private IInvariantNode createKindOfNode(OperationCallExp self, IInvariantNode src) {
		// Let's try to follow the same strategy
		List<SourceContext<? extends RuleWithPattern>> rules = findTargetOcurrences((OclModelElement) self.getArguments().get(0));
		if ( rules.size() == 0 ) 
			throw new IllegalStateException();
		
		return new KindOfNode(src, self, rules);
				
		// This cannot be like this, because if this returns a MultiNode we need a way to
		// merge it, and this is precisely the task of KindOfNode
//		List<IInvariantNode> nodes = rules.stream().map(r -> {
//			return new KindOfNode(src, self, (SourceContext<RuleWithPattern>) r);
//		}).collect(Collectors.toList());
//		
//		if ( nodes.size() == 1 ) {
//			return nodes.get(0);
//		} else {
//			return new MultiNode(nodes);
//		}
	}


	private List<SourceContext<? extends RuleWithPattern>> findTargetOcurrences(OclModelElement targetType) {
		// TODO: Consider subtyping relationships
		return model.allObjectsOf(RuleWithPattern.class).stream().
			filter(r -> r.getOutPattern() != null).
			// filter(r -> r.getOutPattern().getElements().stream().anyMatch(ope -> TypingModel.equalTypes(ope.getInferredType(), targetType.getInferredType()))).
			
			// We ask, is OPE a subtype of the T.allInstances() in the postcondition?
			flatMap(r -> r.getOutPattern().getElements().stream()).
			filter(ope -> TypingModel.assignableTypesStatic(targetType.getInferredType(), ope.getInferredType())).
			map(ope -> {
				Rule rule = ope.getOutPattern().getRule();
				if ( rule instanceof MatchedRule ) {
					return new MatchedRuleContext(ope, (MatchedRule) rule);
				} else {
					return new LazyRuleContext(ope, (LazyRule) rule);
				}
			}).
			collect(Collectors.toList());
	
		/*
		return model.allObjectsOf(RuleWithPattern.class).stream().
				filter(r -> r.getOutPattern() != null).
				// filter(r -> r.getOutPattern().getElements().stream().anyMatch(ope -> TypingModel.equalTypes(ope.getInferredType(), targetType.getInferredType()))).
				
				// We ask, is OPE a subtype of the T.allInstances() in the postcondition?
				flatMap(r -> r.getOutPattern().getElements().stream()).
				filter(ope -> TypingModel.assignableTypesStatic(targetType.getInferredType(), ope.getInferredType())).
				map(ope -> {
					Rule rule = ope.getOutPattern().getRule();
					if ( rule instanceof MatchedRule ) {
						new MatchedRuleContext(ope, (MatchedRule) rule);
					} else {
						return null;
					}
				}).
				collect(Collectors.toList());
		*/
	}

	private IInvariantNode checkNavExp(NavigationOrAttributeCallExp self, Env env) {
		// Is it a true navigation, not a helper call
		if ( self.getUsedFeature() != null ) {

			IInvariantNode source = analyse(self.getSource(), env, 
					(src) -> checkNavMapper(self, env, src), 
					// (nodes) -> new SplitNavigationExpNode(nodes, self));
					(nodes) -> new MultiNode(nodes));
					
//			IInvariantNode src = analyse(self.getSource(), env);
//			if ( src instanceof MultiNode ) {
//				((MultiNode) src).getNodes()
//			} else {
//				return checkNavMapper(self, env, src);
//			}
			
			// p.net.capacity
			// 
			return source;
			
		} else {
			throw new UnsupportedOperationException();
		}
	}

	protected IInvariantNode checkNavMapper(NavigationOrAttributeCallExp self,
			Env env, IInvariantNode source) {
		List<Binding> allBindings = source.getContext().getOutputPatternElement().
				getBindings().stream().filter(b -> b.getWrittenFeature() == self.getUsedFeature()).
				collect(Collectors.toList());

		
		if ( allBindings.size() == 0 ) {
			EReference opposite = getOpposite((EStructuralFeature) self.getUsedFeature());
			if ( opposite != null ) {
				// Find any binding writing the opposite
				allBindings = model.allObjectsOf(Binding.class).stream().
						filter(b -> b.getWrittenFeature() == opposite).
						collect(Collectors.toList());
				
				if ( allBindings.size() == 0 ) {
					throw new UnsupportedOperationException("TODO: Compute default values: " + self.getName() + ":" + self.getLocation());
				} else {
					// CSPModel builder = new CSPModel();

					// T.allInstances()->select(t | t.oppositeFeature = sourceOf(navExp))
					Metaclass m = (Metaclass) TypeUtils.getUnderlyingType(self.getInferredType());						
					OclModelElement ome = OCLFactory.eINSTANCE.createOclModelElement();
					ome.setName(m.getName());
					OclModel model = OCLFactory.eINSTANCE.createOclModel();
					model.setName(m.getModel().getName());
					ome.setModel(model);
					// ome.setInferredType(getMetaclass(m.getModel().getName(), opposite.getEReferenceType()));
					ome.setInferredType(getMetaclass(m.getModel().getName(), m.getKlass()));
					
					OperationCallExp allInstances = OCLFactory.eINSTANCE.createOperationCallExp();
					allInstances.setOperationName("allInstances");
					allInstances.setSource(ome);
					
					IteratorExp select = OCLFactory.eINSTANCE.createIteratorExp();
					select.setName("select");
					select.setSource(allInstances);
					Iterator it = OCLFactory.eINSTANCE.createIterator();
					it.setVarName(m.getName().toLowerCase()+"_");
					select.getIterators().add(it);
					
					// body
					VariableExp refToIt = OCLFactory.eINSTANCE.createVariableExp();
					refToIt.setReferredVariable(it);
					NavigationOrAttributeCallExp navOpposite = OCLFactory.eINSTANCE.createNavigationOrAttributeCallExp();
					navOpposite.setName(opposite.getName());
					navOpposite.setSource(refToIt);
					navOpposite.setUsedFeature(opposite);
					
					OperationCallExp equals = null;
					if ( opposite.isMany() ) {
						equals = OCLFactory.eINSTANCE.createCollectionOperationCallExp();
						equals.setOperationName("includes");
					} else {
						equals = OCLFactory.eINSTANCE.createOperatorCallExp();
						equals.setOperationName("=");
					} 
					equals.setSource(navOpposite);
					equals.getArguments().add((OclExpression) ATLCopier.copySingleElement(self.getSource()));							
					
					select.setBody(equals);
					
					System.out.println(ATLSerializer.serialize(select));
					
					// System.out.println(ATLSerializer.serialize(select.eContainer()));
					return analyse(select, env);
				}
			} else {
				return new DefaultValueNode(source.getContext(), self); 					
			}				
		}
					
		
		Binding b = allBindings.get(0);
		if ( isPrimitiveBinding(b) ) {
			return new AttributeNavigationNode(source, self, b);
		} else {						
			return handleReferenceBinding(self, env, source, allBindings);
		}
	}

	protected IInvariantNode handleReferenceBinding(
			NavigationOrAttributeCallExp self, Env env, IInvariantNode source,
			List<Binding> allBindings) {
		// We support several bindings, like:
		//    	relations <- s.entities,
		//		relations <- s.relships
		// We traverse all bindings, generating one resolution per binding

		List<IInvariantNode> resolutions = new ArrayList<IInvariantNode>();
		
		HashSet<Rule> included = new HashSet<>();
		//for(RuleResolutionInfo rri : allBindings.stream().flatMap(binding -> binding.getResolvedBy().stream()).collect(Collectors.toList())) {
		for(Binding binding : allBindings) {

			// We may need to split the binding if it uses target elements directly
			// Poor's man approach, assuming assignments like: pkeys <- Sequence {idCol}
			// We need to consider also calls to lazy rules
			
			List<OutPatternElement> refsToTargets = ATLUtils.findAllVarExp(binding.getValue()).stream().
					filter(v -> v.getReferredVariable() instanceof OutPatternElement).
					map(v -> (OutPatternElement) v.getReferredVariable()).distinct().collect(Collectors.toList());
			
			if ( refsToTargets.size() > 0) {
				for(OutPatternElement ope : refsToTargets) { 					
					SourceContext<? extends RuleWithPattern> newCtx = source.getContext().newOutPatternElement(ope);
					resolutions.add(new ReferenceNavigationTargetAssignmentNode(source, self, binding, newCtx, env, ope));
				}
			} else {
				for(RuleResolutionInfo rri : binding.getResolvedBy()) { 
					if ( included.contains(rri.getRule()))
							continue;
					included.add(rri.getRule());
					MatchedRuleContext newCtx = new MatchedRuleContext(rri.getRule().getOutPattern().getElements().get(0), rri.getRule());						
					resolutions.add(new ReferenceNavigationNode(source, self, binding, newCtx, env));						
				}
			}
		}
		
		if ( resolutions.size() == 0 ) {
			// Beaware that no resolutions node also handles the case of having only lazy rules
			// in the RHS.
			return new NoResolutionNode(source, self, allBindings.get(0));
		}

		if ( resolutions.size() == 1 ) {
			return resolutions.get(0);
		} else {
			return new MultiNode(resolutions);
		}
	}

	
	private Type getMetaclass(String mmName, EClass eReferenceType) {
		return this.result.getNamespaces().getNamespace(mmName).getMetaclassCached(eReferenceType);
	}

	private EReference getOpposite(EStructuralFeature f) {
		if ( f instanceof EReference && ((EReference) f).getEOpposite() != null ) {
			return ((EReference) f).getEOpposite();
		}
		return null;
	}

	// Predicates
	
	private boolean isPrimitiveBinding(Binding b) {
		return b.getWrittenFeature() instanceof EAttribute;
	}
	
	
	public class MultiNode extends AbstractInvariantReplacerNode {

		private List<IInvariantNode> nodes;

		public MultiNode(List<IInvariantNode> resolutions) {
			super(null);
			this.nodes = resolutions;
		}

		public List<IInvariantNode> map(Function<IInvariantNode, IInvariantNode> mapper) {
			return nodes.stream().map(n -> mapper.apply(n)).collect(Collectors.toList());
		}

		public List<IInvariantNode> getNodes() {
			return nodes;
		}
		
		@Override
		public OclExpression genExpr(CSPModel2 builder) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void genErrorSlice(ErrorSlice slice) {
			throw new UnsupportedOperationException();			
		}

		@Override
		public void getTargetObjectsInBinding(Set<OutPatternElement> elems) { 
			throw new UnsupportedOperationException();
		}
		
		@Override
		public boolean isUsed(InPatternElement e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void genGraphviz(GraphvizBuffer<IInvariantNode> gv) {
			throw new UnsupportedOperationException();			
		}

		@Override
		public OclExpression genExprNorm(CSPModel2 builder) {
			throw new UnsupportedOperationException();
		}

	}

	
	
	public static abstract class SourceContext<T extends RuleWithPattern> {
		protected OutPatternElement ope;
		protected T rule;

		public SourceContext(OutPatternElement ope, T rule) {
			this.ope = ope;
			this.rule = rule;
		}

		public abstract SourceContext<? extends RuleWithPattern> newOutPatternElement(OutPatternElement ope2);

		public OutPatternElement getOutputPatternElement() {
			return ope;
		}

		public T getRule() {
			return rule;
		}		
	}
	
	public static class MatchedRuleContext extends SourceContext<MatchedRule> {

		public MatchedRuleContext(OutPatternElement ope, MatchedRule rule) {
			super(ope, rule);
		}

		@Override
		public SourceContext<MatchedRule> newOutPatternElement(OutPatternElement ope2) {
			return new MatchedRuleContext(ope2, this.rule);
		}
		
	}

	public static class LazyRuleContext extends SourceContext<LazyRule> {

		public LazyRuleContext(OutPatternElement ope, LazyRule rule) {
			super(ope, rule);
		}

		@Override
		public SourceContext<LazyRule> newOutPatternElement(OutPatternElement ope2) {
			return new LazyRuleContext(ope2, this.rule);
		}
		
	}

	
	
	public static class TranslatedHelper {
		private ContextHelper helper;
		private IInvariantNode body;
		private SourceContext<? extends RuleWithPattern> sourceContext;

		public TranslatedHelper(ContextHelper h, IInvariantNode body, SourceContext<? extends RuleWithPattern> sourceContext) {
			this.helper = h;
			this.body = body;
			this.sourceContext = sourceContext;
		}
		
		public ContextHelper getHelper() {
			return helper;
		}
		
		public IInvariantNode getBody() {
			return body;
		}

		public ContextHelper genSourceHelper() {
			CSPModel2 builder = new CSPModel2();
			builder.initWithoutThisModuleContext();
			
			
			String name = ATLUtils.getHelperName(helper); // TODO: Generate unique names???

			if ( sourceContext.getRule().getInPattern().getElements().size() > 1 ) 
				throw new UnsupportedOperationException();
			
			InPatternElement ipe = sourceContext.getRule().getInPattern().getElements().get(0);
			
			OclType ctxType = (OclType) ATLCopier.copySingleElement(ipe.getType());
			OclType retType = (OclType) ATLCopier.copySingleElement(ATLUtils.getHelperReturnType(helper)); // ... This needs to be back-translated if it is not primitive!

			ATLUtils.findSelfReferences(helper).forEach(v -> builder.addToScope(ipe, v));

			OclExpression body = this.body.genExpr(builder);
			
			ContextHelper newHelper = BuildUtils.createContextOperationHelper(name, ctxType, body, retType);
			newHelper.getAnnotations().put("DO_NOT_ADD_THIS_MODULE", "true");
			return newHelper;
		}

		public void genErrorSlice(ErrorSlice slice) {
			this.body.genErrorSlice(slice);
		}

	}
}

