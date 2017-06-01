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
import lombok.javac.handlers.HandleConstructor.SkipIfConstructorExists;

import org.mangosdk.spi.ProviderFor;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.resources.javac;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
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
		
		String staticConstructorName = annotation.getInstance().staticConstructor();
		
		// TODO move this to the end OR move it to the top in eclipse.
		new HandleConstructor().generateRequiredArgsConstructor(typeNode, AccessLevel.PUBLIC, staticConstructorName, SkipIfConstructorExists.YES, annotationNode);
		new HandleGetter().generateGetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true);
		new HandleSetter().generateSetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true);
		new HandleEqualsAndHashCode().generateEqualsAndHashCodeForType(typeNode, annotationNode);
		new HandleToString().generateToStringForType(typeNode, annotationNode);
		handleParseResultSet(typeNode, annotationNode);
	}
public void handleParseResultSet(JavacNode typeNode,JavacNode errorNode){
		
		ListBuffer<JavacNode> columnList=new ListBuffer<JavacNode>();
						
				for (JavacNode field : typeNode.down()) {
					if (field.getKind() != Kind.FIELD) continue;
					if(hasAnnotation(Column.class, field))
						columnList.append(field);				
				}		
				if(columnList.isEmpty())
					return;
				
				switch(methodExists("mapRow",typeNode,1)){
				case EXISTS_BY_LOMBOK:
				case EXISTS_BY_USER:
					break;
				case NOT_EXISTS:
					List<JavacNode> columnTypeList=columnList.toList();
					JCMethodDecl method=createJDBCParser(typeNode,columnTypeList ,errorNode.get());
					injectMethod(typeNode, method);
					break;
				default:
					break;
				}
				
				
				
	}
private JCMethodDecl createJDBCParser(JavacNode typeNode,List<JavacNode> fields,JCTree source){
	JavacTreeMaker 			maker 					= 	typeNode.getTreeMaker();
	Name					resultSetObject			=   typeNode.toName("resultSet")
;	JCModifiers   			publicStatic 			= 	maker.Modifiers(Modifier.PUBLIC);
	JCExpression  			returnType 				= 	genTypeRef(typeNode,typeNode.getName());
	Name 					methodName				= 	typeNode.toName("mapRow");
	List<JCVariableDecl>  	methodParameters   		= 	List.of(maker.
			  											VarDef(maker.Modifiers(Flags.PARAMETER)
  															   ,resultSetObject
															   	   ,genTypeRef(typeNode,"java.sql.ResultSet")
													   	   	  ,null));
	List<JCExpression>    	methodThrows       		= 	List.of(genJavaLangTypeRef(typeNode,"Exception"));
	
    Name					objectName				= 	typeNode.toName("dataObject");
    
	ListBuffer<JCStatement> statements=new ListBuffer<JCTree.JCStatement>();
	
	/*MyClass dataObject=new MyClass();*/
	statements.append(maker.VarDef(maker.Modifiers(0)
			,objectName
			,returnType
			,maker.NewClass(null, List.<JCExpression>nil(),returnType,List.<JCExpression>nil(), null)));
    
	
//	JCMethodInvocation resultsetNext=maker.Apply(List.<JCExpression>nil(),
//			maker.Select(maker.Ident(resultSetObject), typeNode.toName("next")),
//			List.<JCExpression>nil());
//	statements.append(maker.Exec(resultsetNext));
	
	statements.append(maker.Return(maker.Ident(objectName)));
	
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

}
