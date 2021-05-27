package com.github.mweyssow.ast_parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.lang.StringBuffer;

public class ASTParser {
    private static final String functionCallFile = "function_call_sequences.txt";
    private static final String functionDefFile = "function_tokens.txt";

    private static Path fCallFp;
    private static Path fDefFp;

    private final static StringBuffer fCallBuffer = new StringBuffer();
    private final static StringBuffer fDefBuffer = new StringBuffer();

    private static Integer nbFilesParsed = 0;

    public static void main(String[] args) {
        initFiles(args[1]);
        long startTotalTime = System.currentTimeMillis();
        try {
            Files.walk(Paths.get(args[0]))
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        String fileName = f.toString();
                        if (fileName.endsWith(".java")) {
                            try {
                                System.out.println("Parsing file : " + f.toString());
                                SourceRoot sourceRoot = new SourceRoot(
                                    CodeGenerationUtils.mavenModuleRoot(ASTParser.class).resolve(args[0])
                                );
                                // the parser won't handle comments
                                sourceRoot.getParserConfiguration().setAttributeComments(false);
                                CompilationUnit cu = sourceRoot.parse("", f.toString());
                                cu.accept(new ClassOrInterfaceVisitor(), null);

                                // save function sequences data
                                Files.write(
                                    fCallFp,
                                    String.valueOf(fCallBuffer).getBytes(),
                                    StandardOpenOption.APPEND
                                );

                                // save function def data
                                Files.write(
                                    fDefFp,
                                    String.valueOf(fDefBuffer).getBytes(),
                                    StandardOpenOption.APPEND
                                );

                                nbFilesParsed += 1;

                                // Flush buffers
                                fCallBuffer.delete(0, fCallBuffer.length());
                                fDefBuffer.delete(0, fDefBuffer.length());
                            } catch (Exception e) {
                                System.out.println("Error happened : flushing buffer");
                            } catch(StackOverflowError soe) {
                                System.out.println("StackOverflowError while parsing");
                            } finally {
                                fCallBuffer.delete(0, fCallBuffer.length());
                                fDefBuffer.delete(0, fDefBuffer.length());
                            }
                        }
                    });
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
        long endTotalTime = System.currentTimeMillis();
        System.out.println(
            "Total execution time : " + (endTotalTime - startTotalTime) + "ms (" + nbFilesParsed + " files)"
        );
    }

    private static void initFiles(String baseDir) {
        try {
            Path dir = Paths.get("output");
            if (!Files.exists(Paths.get("output"))) {
                dir = Files.createDirectory(Paths.get("output"));
            }
            fCallFp = Files.createFile(dir.resolve(functionCallFile));
            fDefFp = Files.createFile(dir.resolve(functionDefFile));
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static class ClassOrInterfaceVisitor extends VoidVisitorAdapter<Object> {
        @Override
        public void visit(ClassOrInterfaceDeclaration c, Object arg) {
            super.visit(c, arg);
            try {
                if (c.isNestedType()) {
                    c.accept(new MethodDeclarationInnerClassVisitor(), null);
                } else {
                    c.accept(new MethodDeclarationVisitor(), null);
                }
            } catch (Exception ioe) {
                // System.out.println(ioe.getMessage());
            }
        }
    }

    private static class IsInnerClassVisitor extends GenericVisitorAdapter<Boolean, Void> {
        @Override
        public Boolean visit(ClassOrInterfaceDeclaration c, Void arg) {
            super.visit(c, arg);
            if (c.isNestedType())
                return Boolean.TRUE;
            else
                return Boolean.FALSE;
        }
    }

    private static class MethodDeclarationInnerClassVisitor extends VoidVisitorAdapter<Object> {
        @Override
        public void visit(MethodDeclaration m, Object arg) {
            super.visit(m, arg);

            String content = m.getName().toString();
            try {
                // add the whole function tokens to the buffer
                fDefBuffer.append(m.toString().replaceAll("[^\\S ]+", " "));
                fDefBuffer.append("\n");
                fCallBuffer.append(content);
                m.accept(new MethodCallVisitor(), null);
                fCallBuffer.append("\n");
            } catch (Exception ioe) {
                // System.out.println(ioe.getMessage());
            }
        }
    }

    private static class MethodDeclarationVisitor extends VoidVisitorAdapter<Object> {
        @Override
        public void visit(MethodDeclaration m, Object arg) {
            super.visit(m, arg);

            Node classData = m.getParentNode().get();
            Boolean isInInnerClass = classData.accept(new IsInnerClassVisitor(), null);
            try {
                if (isInInnerClass.equals(Boolean.FALSE)) {
                    // add the whole function tokens to the buffer
                    fDefBuffer.append(m.toString().replaceAll("[^\\S ]+", " "));
                    fDefBuffer.append("\n");
                    String content = m.getName().toString();
                    fCallBuffer.append(content);
                    m.accept(new MethodCallVisitor(), null);
                    fCallBuffer.append("\n");
                }
            } catch (NullPointerException e) {
                // System.out.println(e.getMessage());
            }
        }
    }

    private static class MethodCallVisitor extends VoidVisitorAdapter<Object> {
        @Override
        public void visit(MethodCallExpr m, Object arg) {
            super.visit(m, arg);
            String content = " " + m.getName();
            try {
                fCallBuffer.append(content);
            } catch (Exception ioe) {
                System.out.println(ioe.getMessage());
            }
        }
    }
}
