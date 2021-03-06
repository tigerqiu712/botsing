package eu.stamp.botsing.model.generation.callsequence;


import eu.stamp.botsing.model.generation.analysis.classpath.CPAnalysor;
import eu.stamp.botsing.model.generation.analysis.sourcecode.StaticAnalyser;
import eu.stamp.botsing.model.generation.analysis.testcases.DynamicAnalyser;
import eu.stamp.botsing.model.generation.testcase.execution.TestExecutor;
import org.evosuite.Properties;
import org.evosuite.classpath.ClassPathHacker;
import org.evosuite.classpath.ClassPathHandler;
import org.evosuite.setup.InheritanceTree;
import org.evosuite.testcase.DefaultTestCase;
import org.evosuite.testcase.execution.EvosuiteError;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.testcase.statements.StringPrimitiveStatement;
import org.evosuite.testcase.statements.numeric.BooleanPrimitiveStatement;
import org.evosuite.testcase.variable.VariableReference;
import org.evosuite.utils.generic.GenericMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;


public class CallSequenceCollector {
    private static final Logger LOG = LoggerFactory.getLogger(CallSequenceCollector.class);

    private String[] projectClassPaths;
    public CallSequenceCollector(String cp){
        projectClassPaths=cp.split(File.pathSeparator);
    }
    public CallSequenceCollector(String[] jarsCp ){
        projectClassPaths = jarsCp.clone();
    }
    private List<String> interestingClasses =  new ArrayList<String>();


    private static Statement currentThreadStmt;
    private static Statement getContextClassLoaderStmt;
    private static BooleanPrimitiveStatement booleanStmnt;

    StaticAnalyser staticAnalyser =  new StaticAnalyser();
    DynamicAnalyser dynamicAnalyser =  new DynamicAnalyser();

    public void collect(){

        //pre-processes before starting the analysis
        if(projectClassPaths == null){
            LOG.error("Project classpath should be set before the model generation.");
        }
        // Class path handler
        handleClassPath();

        // Static Analysis
        detectInterestingClasses();
        generateCFGS();
        staticAnalyser.analyse(interestingClasses);

        // Dynamic Analysis
        dynamicAnalyser.analyse(interestingClasses);

        // Checking the exported call sequences <TEMP>
        CallSequencesPoolManager.getInstance().report();
    }

    private void handleClassPath() {
        ClassPathHandler.getInstance().changeTargetClassPath(projectClassPaths);
        List<String> cpList = Arrays.asList(projectClassPaths);
        for (String cp: cpList){
            try {
                ClassPathHacker.addFile(cp);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        CPAnalysor.analyzeClass(cpList);
    }


    private void generateCFGS() {
        for(String clazz: interestingClasses){
            LOG.info("Analyzing class "+ clazz);
            DefaultTestCase test = buildLoadClassTestCase(clazz);
            ExecutionResult execResult = TestExecutor.getInstance().execute(test, Integer.MAX_VALUE);
            if (!execResult.getAllThrownExceptions().isEmpty()) {
                Throwable t = execResult.getAllThrownExceptions().iterator().next();
                try {
                    throw t;
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
            LOG.info("The process of generating CFG for class {} is finished.",clazz);
        }
    }

    private void detectInterestingClasses() {
        InheritanceTree projectTree = CPAnalysor.getInheritanceTree();
        for (String clazz:  projectTree.getAllClasses()){
            if (clazz.startsWith(Properties.TARGET_CLASS_PREFIX)){
                interestingClasses.add(clazz);
            }
        }
    }


    private static DefaultTestCase buildLoadClassTestCase(String className) throws EvosuiteError {
        DefaultTestCase test = new DefaultTestCase();

        StringPrimitiveStatement saveClassNameToStringStatement = new StringPrimitiveStatement(test, className);
        VariableReference string0 = test.addStatement(saveClassNameToStringStatement);

        try {
            if (currentThreadStmt == null){
                Method currentThreadMethod = Thread.class.getMethod("currentThread");
                currentThreadStmt = new MethodStatement(test,
                        new GenericMethod(currentThreadMethod, currentThreadMethod.getDeclaringClass()), null,
                        Collections.emptyList());
            }
            VariableReference currentThreadVar = test.addStatement(currentThreadStmt);

            if (getContextClassLoaderStmt == null){
                Method getContextClassLoaderMethod = Thread.class.getMethod("getContextClassLoader");
                getContextClassLoaderStmt = new MethodStatement(test,
                        new GenericMethod(getContextClassLoaderMethod, getContextClassLoaderMethod.getDeclaringClass()),
                        currentThreadVar, Collections.emptyList());
            }
            VariableReference contextClassLoaderVar = test.addStatement(getContextClassLoaderStmt);

            if (booleanStmnt == null) {
                booleanStmnt = new BooleanPrimitiveStatement(test, true);
            }
            VariableReference boolean0 = test.addStatement(booleanStmnt);

            Method forNameMethod = Class.class.getMethod("forName",String.class, boolean.class, ClassLoader.class);
            Statement forNameStmt = new MethodStatement(test,
                    new GenericMethod(forNameMethod, forNameMethod.getDeclaringClass()), null,
                    Arrays.asList(string0, boolean0, contextClassLoaderVar));
            test.addStatement(forNameStmt);
            return test;
        } catch (NoSuchMethodException | SecurityException e) {
            throw new EvosuiteError("Unexpected exception while creating test for instrumenting class "+className );
        }

    }

}
