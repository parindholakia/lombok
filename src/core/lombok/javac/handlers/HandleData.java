/*
 * Copyright (C) 2009-2014 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.javac.handlers;

import static lombok.core.handlers.HandlerUtil.*;
import static lombok.javac.Javac.CTC_INT;
import static lombok.javac.handlers.JavacHandlerUtil.*;

import java.lang.reflect.Modifier;

import lombok.AccessLevel;
import lombok.Column;
import lombok.ConfigurationKeys;
import lombok.Data;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.javac.JavacTreeMaker.TypeTag;
import lombok.javac.handlers.HandleConstructor.SkipIfConstructorExists;
import lombok.javac.handlers.JavacHandlerUtil.FieldAccess;

import org.eclipse.jdt.internal.ui.javadocexport.JavadocConsoleLineTracker;
import org.mangosdk.spi.ProviderFor;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.resources.javac;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTry;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

/**
 * Handles the {@code lombok.Data} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleData extends JavacAnnotationHandler<Data> {
	@Override public void handle(AnnotationValues<Data> annotation, JCAnnotation ast, JavacNode annotationNode) {
		handleFlagUsage(annotationNode, ConfigurationKeys.DATA_FLAG_USAGE, "@Data");
		
		deleteAnnotationIfNeccessary(annotationNode, Data.class);
		JavacNode typeNode = annotationNode.up();
		boolean notAClass = !isClass(typeNode);
		
		
		
		if (notAClass) {
			annotationNode.addError("@Data is only supported on a class.");
			return;
		}
		
		Boolean callSuper = annotation.getInstance().callSuper();
		
		String staticConstructorName = annotation.getInstance().staticConstructor();
		
		// TODO move this to the end OR move it to the top in eclipse.
		new HandleConstructor().generateRequiredArgsConstructor(typeNode, AccessLevel.PUBLIC, staticConstructorName, SkipIfConstructorExists.YES, annotationNode);
		new HandleGetter().generateGetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true);
		new HandleSetter().generateSetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true);
		new HandleEqualsAndHashCode().generateEqualsAndHashCodeForType(typeNode, annotationNode);
		new HandleToString().generateToStringForType(typeNode, annotationNode);
		handleParseResultSet(callSuper,typeNode, annotationNode);
		
	}
	


	
private boolean isNotValidCallToSuper(JavacNode typeNode){
	JCTree extending = Javac.getExtendsClause((JCClassDecl)typeNode.get());
	boolean isDirectDescendantOfObject=true;
	if (extending != null) {
		String p = extending.toString();
		isDirectDescendantOfObject = p.equals("Object") || p.equals("java.lang.Object");
	}
	return isDirectDescendantOfObject;
	
	
}
public void handleParseResultSet(boolean callSuper,JavacNode typeNode,JavacNode errorNode){
	
	if (callSuper && isNotValidCallToSuper(typeNode)) {
		errorNode.addError("Generating parser Method with a supercall to java.lang.Object is pointless.");
		return;
	}
		ListBuffer<JavacNode> columnList=new ListBuffer<JavacNode>();
		ListBuffer<String>    columnNames=new ListBuffer<String>();
						
				for (JavacNode field : typeNode.down()) {
					if (field.getKind() != Kind.FIELD) continue;
					JavacNode annotation=findAnnotation(Column.class, field,false);
					if(annotation!=null){
						Column columnInstance=createAnnotation(Column.class,annotation).getInstance();
						columnNames.append(columnInstance.value());
						columnList.append(field);	
				
					}
									
				}		
				if(columnList.isEmpty())
					return;
				
				switch(methodExists("mapRow",typeNode,1)){
				case EXISTS_BY_LOMBOK:
				case EXISTS_BY_USER:
					break;
				case NOT_EXISTS:
					List<JavacNode> columnTypeList=columnList.toList();
					List<String> columnNameList=columnNames.toList();
					JCMethodDecl method=createJDBCParser(typeNode,columnTypeList,columnNameList,errorNode.get());
					injectMethod(typeNode, method);
					
					JCMethodDecl constructorMethod=createConstructor(callSuper, typeNode, errorNode.get());
					injectMethod(typeNode, constructorMethod);
					break;
				default:
					break;
				}
				
				
				
	}
private JCMethodDecl createConstructor(boolean callSuper,JavacNode typeNode,JCTree source){

	JavacTreeMaker 			maker 					= 	typeNode.getTreeMaker();
	Name					resultSetObject			=   typeNode.toName("resultSet");	
	JCModifiers 			mods 					= 	maker.Modifiers(toJavacModifier(AccessLevel.PUBLIC), List.<JCAnnotation>nil());
	List<JCVariableDecl>  	methodParameters   		= 	List.of(maker.
			  											VarDef(maker.Modifiers(Flags.PARAMETER)
  															   ,resultSetObject
															   	   ,genTypeRef(typeNode,"java.sql.ResultSet")
													   	   	  ,null));
	List<JCExpression>    	methodThrows       		= 	List.of(genJavaLangTypeRef(typeNode,"Exception"));
	
	
	
    
	ListBuffer<JCStatement> statements=new ListBuffer<JCTree.JCStatement>();
	
	
	
	JCMethodInvocation superMethod=null;
	
	if(callSuper){
		
		superMethod=maker.Apply(List.<JCExpression>nil(),maker.Ident(typeNode.toName("super"))
				,List.<JCExpression>of(maker.Ident(typeNode.toName("resultSet"))));
		statements.append(maker.Exec(superMethod));
	}
	superMethod=maker.Apply(List.<JCExpression>nil(),
			maker.Select(maker.Ident(typeNode.toName("this")), typeNode.toName("mapRow")),
			List.<JCExpression>of(maker.Ident(typeNode.toName("resultSet"))));
	statements.append(maker.Exec(superMethod));
	
	JCBlock body = maker.Block(0,statements.toList());

	 return recursiveSetGeneratedBy(maker.MethodDef(
			    mods, 
			    typeNode.toName("<init>"), 
			    null,
			    List.<JCTypeParameter>nil(), 
			    methodParameters, 
			    methodThrows, 
			    body, 
			    null
			   ), source,typeNode.getContext());
}
private JCMethodDecl createJDBCParser(JavacNode typeNode,List<JavacNode> fields,List<String> columnNames,JCTree source){
	JavacTreeMaker 			maker 					= 	typeNode.getTreeMaker();
	Name					resultSetObject			=   typeNode.toName("resultSet");	
	JCModifiers   			publicStatic 			= 	maker.Modifiers(Modifier.PRIVATE);
	JCExpression			returnType					=	maker.TypeIdent(Javac.CTC_VOID);
	Name 					methodName				= 	typeNode.toName("mapRow");
	List<JCVariableDecl>  	methodParameters   		= 	List.of(maker.
			  											VarDef(maker.Modifiers(Flags.PARAMETER)
  															   ,resultSetObject
															   	   ,genTypeRef(typeNode,"java.sql.ResultSet")
													   	   	  ,null));
	List<JCExpression>    	methodThrows       		= 	List.of(genJavaLangTypeRef(typeNode,"Exception"));
	
    Name					objectName				= 	typeNode.toName("this");
    
	ListBuffer<JCStatement> statements=new ListBuffer<JCTree.JCStatement>();
	
	
    
	JCCatch catchStatement=createCatchBlock(source, maker, typeNode);
	List<JCCatch>  catchList=List.<JCCatch>of(catchStatement);
	for(int i=0;i<fields.size();i++){
		JCTry tryBlock=maker.Try(createAccessorBlock(objectName, 
				resultSetObject, maker,fields.get(i),columnNames.get(i)),catchList, null);
		statements.append(tryBlock);
	}
	
	
	
    JCBlock body = maker.Block(0, statements.toList());
   
    return recursiveSetGeneratedBy(maker.MethodDef(
		    publicStatic, 
		    methodName, 
		    returnType,
		    List.<JCTypeParameter>nil(), 
		    methodParameters, 
		    methodThrows, 
		    body, 
		    null
		   ), source,typeNode.getContext());
    
    

}

private JCBlock createAccessorBlock(Name dataObject,Name resultSetName,JavacTreeMaker maker,JavacNode node,String columnName){
	
	JCVariableDecl declaredVar=(JCVariableDecl)node.get();
	JCExpression varType=declaredVar.vartype;
	
	String setterName=toSetterName(node);
	String resultSetMethodName=getResultSetMethodName(varType);
	
	
	JCMethodInvocation resultsetMethod=maker.Apply(List.<JCExpression>nil(),
	maker.Select(maker.Ident(resultSetName), node.toName(resultSetMethodName)),
	List.<JCExpression>of(maker.Literal(columnName)));
	
	JCMethodInvocation setterMethodInvocation=maker.Apply(List.<JCExpression>nil(),
			maker.Select(maker.Ident(dataObject),node.toName(setterName)),
			List.<JCExpression>of(resultsetMethod));
	JCBlock block= maker.Block(0, List.<JCStatement>of(maker.Exec(setterMethodInvocation)));
	return block;
}
private String getResultSetMethodName(JCExpression varType){
	String methodName=null;
	String vartypeString=varType.toString().toLowerCase();
	if(vartypeString.equals("boolean"))
		methodName="getString";
	if(vartypeString.contains("string"))
		methodName="getString";
	if(vartypeString.equals("long"))
		methodName="getLong";
	if(vartypeString.equals("int"))
		methodName="getInt";
	if(vartypeString.equals("float"))
		methodName="getFloat";
	if(vartypeString.equals("double"))
		methodName="getDouble";
	return methodName;
	
	
		
	
}
private JCCatch createCatchBlock(JCTree source,JavacTreeMaker maker,JavacNode typeNode){
	JCExpression 				varType 			=	genTypeRef(typeNode,"NullPointerException");
	JCVariableDecl				catchParam			=	maker.VarDef(maker.Modifiers(Flags.PARAMETER), typeNode.toName("ex"), varType, null);
	JCBlock 					catchBlock			=   maker.Block(0, List.<JCStatement>nil());
	JCCatch						catchStatement		= 	maker.Catch(catchParam, catchBlock);
	return recursiveSetGeneratedBy(catchStatement, source, typeNode.getContext());
}



}
