package com.github.mweyssow.ast_parser;

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
    private static Path filePath;
    private static Integer nbFilesParsed = 0;
    private final static StringBuffer strToFile = new StringBuffer();

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
                                CompilationUnit cu = sourceRoot.parse("", f.toString());
                                cu.accept(new ClassOrInterfaceVisitor(), null);

                                // Save methods data
                                Files.write(
                                    filePath,
                                    String.valueOf(strToFile).getBytes(),
                                    StandardOpenOption.APPEND
                                );
                                nbFilesParsed += 1;
                                strToFile.delete(0, strToFile.length());
                            } catch (Exception e) {
                                System.out.println("Error happened : flushing buffer");
                            } catch(StackOverflowError soe) {
                                System.out.println("StackOverflowError while parsing");
                            } finally {
                                strToFile.delete(0, strToFile.length());
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
            filePath = Files.createFile(dir.resolve(functionCallFile));
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
            } catch (Exception ioe) { }
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
                strToFile.append(content);
                m.accept(new MethodCallVisitor(), null);
                strToFile.append("\n");
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
                    String content = m.getName().toString();
                    strToFile.append(content);
                    m.accept(new MethodCallVisitor(), null);
                    strToFile.append("\n");
                }
            } catch (NullPointerException e) {  }
            catch (Exception ioe) {  }
        }
    }

    private static class MethodCallVisitor extends VoidVisitorAdapter<Object> {
        @Override
        public void visit(MethodCallExpr m, Object arg) {
            super.visit(m, arg);
            String content = " " + m.getName();
            try {
                strToFile.append(content);
            } catch (Exception ioe) {
                System.out.println(ioe.getMessage());
            }
        }
    }
}
