/**
 * BSD 3-Clause License
 *
 * Copyright © 2018, viadee Unternehmensberatung GmbH
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.viadee.bpm.vPAV.processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import de.viadee.bpm.vPAV.FileScanner;
import de.viadee.bpm.vPAV.processing.model.data.Anomaly;
import de.viadee.bpm.vPAV.processing.model.data.AnomalyContainer;
import de.viadee.bpm.vPAV.processing.model.data.BpmnElement;
import de.viadee.bpm.vPAV.processing.model.data.CamundaProcessVariableFunctions;
import de.viadee.bpm.vPAV.processing.model.data.ElementChapter;
import de.viadee.bpm.vPAV.processing.model.data.KnownElementFieldType;
import de.viadee.bpm.vPAV.processing.model.data.OutSetCFG;
import de.viadee.bpm.vPAV.processing.model.data.ProcessVariableOperation;
import de.viadee.bpm.vPAV.processing.model.data.VariableBlock;
import de.viadee.bpm.vPAV.processing.model.data.VariableOperation;
import soot.Body;
import soot.G;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeStmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.ClassicCompleteBlockGraph;

public class JavaReaderStatic implements JavaReader {

    public static final Logger LOGGER = Logger.getLogger(JavaReaderStatic.class.getName());

    /**
     * Checks a java delegate for process variable references with Static code analysis (read/write/delete).
     *
     * Constraints: names, which only could be determined at runtime, can't be analyzed. e.g.
     * execution.setVariable(execution.getActivityId() + "-" + execution.getEventName(), true)
     *
     * @param classFile
     *            - name of the class
     * @param element
     *            - Bpmn element
     * @param chapter
     *            - ElementChapter
     * @param fieldType
     *            - KnownElementFieldType
     * @param scopeId
     *            - Scope of the element
     * @return - Map of Process Variables
     */
    public Map<String, ProcessVariableOperation> getVariablesFromJavaDelegate(final String classFile,
            final BpmnElement element, final ElementChapter chapter, final KnownElementFieldType fieldType,
            final String scopeId) {

        final Map<String, ProcessVariableOperation> variables = new HashMap<String, ProcessVariableOperation>();

        if (classFile != null && classFile.trim().length() > 0) {

            final String sootPath = FileScanner.getSootPath();
      
            System.setProperty("soot.class.path", sootPath);

            final Set<String> classPaths = FileScanner.getJavaResourcesFileInputStream();
            final String delegateMethodName = "execute";

            variables.putAll(
                    classFetcher(classPaths, classFile, delegateMethodName, classFile, element, chapter, fieldType,
                            scopeId));
        }
        return variables;
    }

    /**
     *
     * Starting by the main JavaDelegate statically analyse the classes implemented for the bpmn element.
     *
     * @param classPaths - Set of classes that is included in inter-procedural analysis
     * @param className - name of currently analysed class
     * @param methodName - name of currently analysed method
     * @param classFile - location path of class
     * @param element - Bpmn element
     * @param chapter - ElementChapter
     * @param fieldType - KnownElementFieldType
     * @param scopeId - Scope of the element
     * @return
     */
    public Map<String, ProcessVariableOperation> classFetcher(final Set<String> classPaths, String className,
            String methodName,
            final String classFile, final BpmnElement element, final ElementChapter chapter,
            final KnownElementFieldType fieldType, final String scopeId) {

        Map<String, ProcessVariableOperation> processVariables = new HashMap<String, ProcessVariableOperation>();

        OutSetCFG outSet = new OutSetCFG(new ArrayList<VariableBlock>());

        classFetcherRecursive(classPaths, className, methodName, classFile, element, chapter,
                fieldType, scopeId, outSet, null);

        processVariables.putAll(outSet.getAllProcessVariables());

        // Add Java code level anomalies to BpmnElement so later it is included into
        try {

            addAnomaliesFoundInSourceCode(element, outSet);
        } catch (Exception e) {
            // TODO: handle exception
        }

        return processVariables;

    }

    /**
     *
     * Recursive call to find Camunda methods in other classes, called from the JavaDelegate.
     *
     * @param classPaths - Set of classes that is included in inter-procedural analysis
     * @param className -  name of currently analysed class
     * @param methodName - name of currently analysed method
     * @param classFile -  location path of class
     * @param element - Bpmn element
     * @param chapter - ElementChapter
     * @param fieldType - KnownElementFieldType
     * @param scopeId - Scope of the element
     * @return
     */
    public OutSetCFG classFetcherRecursive(final Set<String> classPaths, String className,
            String methodName,
            final String classFile, final BpmnElement element, final ElementChapter chapter,
            final KnownElementFieldType fieldType, final String scopeId,
            OutSetCFG outSet, VariableBlock originalBlock) {

    	if (System.getProperty("os.name").startsWith("Windows")) {
    		className = className.replace("\\", ".").replace(".java", "");
    	} else {
    		className = className.replace("/", ".").replace(".java", "");
    	}
        

        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);

        SootClass sootClass = Scene.v().forceResolve(className, SootClass.SIGNATURES);

        if (sootClass != null) {
            sootClass.setApplicationClass();
            Scene.v().loadNecessaryClasses();

            // Retrieve the method and its body
            SootMethod method = sootClass.getMethodByNameUnsafe(methodName);
            if (method != null) {
                final Body body = method.retrieveActiveBody();

                BlockGraph graph = new ClassicCompleteBlockGraph(body);

                // Prepare call graph for inter-procedural recursive call
                List<SootMethod> entryPoints = new ArrayList<SootMethod>();
                entryPoints.add(method);
                Scene.v().setEntryPoints(entryPoints);

                PackManager.v().getPack("cg").apply();
                CallGraph cg = Scene.v().getCallGraph();
                final List<Block> graphHeads = graph.getHeads();
                final List<Block> graphTails = graph.getTails();

                for (Block head : graphHeads) {

                    outSet = graphIterator(classPaths, cg, graph, head, graphTails, outSet, element, chapter, fieldType,
                            classFile, scopeId, originalBlock, className);
                }

                // variables.putAll(outSet.getAllProcessVariables());

            } else {
                LOGGER.warning("In class " + classFile + " - " + methodName + " method was not found by Soot");
            }
        } else {
            LOGGER.warning("Class " + classFile + " was not found by Soot");
        }

        return outSet;

    }

    /**
     * Iterate through the control-flow graph with an iterative data-flow analysis logic
     *
     *
     * @param graph
     *            - Control Flow graph of method
     * @param head
     *            - Starting Block of the CFG
     * @param blockTails
     *            - List of End Blocks of CFG
     * @param outSet
     *            - OUT set of CFG
     * @param element
     *            - Bpmn element
     * @param chapter
     *            - ElementChapter
     * @param fieldType
     *            - KnownElementFieldType
     * @param filePath
     *            - ResourceFilePath for ProcessVariableOperation
     * @param scopeId
     *            - Scope of BpmnElement
     * @return
     */
    private OutSetCFG graphIterator(final Set<String> classPaths, CallGraph cg, BlockGraph graph, Block head,
            List<Block> blockTails, OutSetCFG outSet,
            final BpmnElement element, final ElementChapter chapter, final KnownElementFieldType fieldType,
            final String filePath, final String scopeId, VariableBlock originalBlock, String oldClassName) {

        final Iterator<Block> graphIterator = graph.iterator();

        Block block;
        while (graphIterator.hasNext()) {
            block = graphIterator.next();

            // Collect the functions Unit by Unit via the blockIterator
            final VariableBlock vb = blockIteraror(classPaths, cg, block, outSet, element, chapter, fieldType, filePath,
                    scopeId, originalBlock, oldClassName);

            // depending if ouset already has that Block, only add varibles,
            // if not, then add the whole vb

            if (outSet.getVariableBlock(vb.getBlock()) == null) {
                outSet.addVariableBlock(vb);

            }
        }

        return outSet;
    }

    /**
     * Iterator through the source code line by line, collecting the ProcessVariables Camunda methods are interface
     * invocations appearing either in Assign statement or Invoke statement Constraint: Only String constants can be
     * precisely recognized.
     *
     * @param block
     *            - Block from CFG
     * @param InSet
     *            - OUT set of CFG
     * @param element
     *            - BpmnElement
     * @param chapter
     *            - ElementChapter
     * @param fieldType
     *            - KnownElementFieldType
     * @param filePath
     *            - ResourceFilePath for ProcessVariableOperation
     * @param scopeId
     *            - Scope of BpmnElement
     * @return
     */
    private VariableBlock blockIteraror(final Set<String> classPaths, final CallGraph cg, final Block block,
            OutSetCFG outSet, final BpmnElement element,
            final ElementChapter chapter, final KnownElementFieldType fieldType, final String filePath,
            final String scopeId, VariableBlock variableBlock, String oldClassName) {

        if (variableBlock == null) {
            variableBlock = new VariableBlock(block, new ArrayList<ProcessVariableOperation>());

        }
        final Iterator<Unit> unitIt = block.iterator();

        Unit unit;
        while (unitIt.hasNext()) {
            unit = unitIt.next();

            if (cg != null & (unit instanceof InvokeStmt || unit instanceof AssignStmt)) {

                Iterator<soot.jimple.toolkits.callgraph.Edge> sources = cg.edgesOutOf(unit);

                Edge src;
                while (sources.hasNext()) {
                    src = (Edge) sources.next();
                    String methodName = src.tgt().getName();

                    String className = src.tgt().getDeclaringClass().getName();
                    if (!className.equals(oldClassName)) {
                    	if (System.getProperty("os.name").startsWith("Windows")) {
                    		className = className.replace(".", "\\") + ".java";
                    	} else {
                    		className = className.replace(".", "/") + ".java";
                    	}
                        

                        if (classPaths.contains(className)) {
                            G.reset();

                            classFetcherRecursive(classPaths, className, methodName, className, element, chapter,
                                    fieldType,
                                    scopeId, outSet, variableBlock);

                        }
                    }
                }

                if (unit instanceof InvokeStmt) {

                    if (((InvokeStmt) unit).getInvokeExprBox().getValue() instanceof JInterfaceInvokeExpr) {

                        JInterfaceInvokeExpr expr = (JInterfaceInvokeExpr) ((InvokeStmt) unit).getInvokeExprBox()
                                .getValue();
                        if (expr != null) {
                            parseExpression(expr, variableBlock, element, chapter, fieldType, filePath, scopeId);
                        }

                    }
                }
            }
            if (unit instanceof AssignStmt) {

                if (((AssignStmt) unit).getRightOpBox().getValue() instanceof JInterfaceInvokeExpr) {

                    JInterfaceInvokeExpr expr = (JInterfaceInvokeExpr) ((AssignStmt) unit).getRightOpBox().getValue();

                    if (expr != null) {
                        parseExpression(expr, variableBlock, element, chapter, fieldType, filePath, scopeId);
                    }
                }

            }
        }

        return variableBlock;
    }

    /**
     *
     * Special parsing of statements to find Process Variable operations.
     * 
     * @param expr
     *            - Expression Unit from Statement
     * @param variableBlock
     *            - current VariableBlock
     * @param element
     *            - BpmnElement
     * @param chapter
     *            - ElementChapter
     * @param fieldType
     *            - KnownElementFieldType
     * @param filePath
     *            - ResourceFilePath for ProcessVariableOperation
     * @param scopeId
     *            - Scope of BpmnElement
     */
    private void parseExpression(JInterfaceInvokeExpr expr, VariableBlock variableBlock, BpmnElement element,
            ElementChapter chapter, KnownElementFieldType fieldType, String filePath, String scopeId) {

        String functionName = expr.getMethodRef().name();
        int numberOfArg = expr.getArgCount();
        String baseBox = expr.getBaseBox().getValue().getType().toString();

        CamundaProcessVariableFunctions foundMethod = CamundaProcessVariableFunctions
                .findByNameAndNumberOfBoxes(functionName, baseBox, numberOfArg);

        if (foundMethod != null) {

            int location = foundMethod.getLocation() - 1;
            VariableOperation type = foundMethod.getOperationType();

            if (expr.getArgBox(location).getValue() instanceof StringConstant) {

                StringConstant variableName = (StringConstant) expr.getArgBox(location).getValue();
                String name = variableName.value;

                variableBlock
                        .addProcessVariable(new ProcessVariableOperation(name,
                                element, chapter, fieldType, filePath, type, scopeId));
            }
        }
    }

    /**
     *
     * Find anomalies inside a Bpmn element as well.
     * 
     * @param element
     *            - BpmnElement
     * @param graph
     *            - CFG of method
     * @param outSet
     *            - OUT set of CFG
     * @param graphHeads
     *            - Starting Blocks of CFG
     * @param graphTails
     *            - End Blocks of CFG
     */
    private void addAnomaliesFoundInSourceCode(final BpmnElement element,
            final OutSetCFG outSet) {

        for (VariableBlock vb : outSet.getAllVariableBlocks()) {

            if (vb.getBlock().getIndexInMethod() == 0) {
                addAnomaliesFoundInPathsRecursive(element, vb.getBlock(), new LinkedList<String>(), outSet,
                        new LinkedList<ProcessVariableOperation>(), "");
            }
        }
    }

    /**
     *
     * Build LinkedList of ProcessVariableOperations recursively based on CFG paths.
     * 
     * @param element
     *            - BpmnElement
     * @param currentBlock
     *            - Block from CFG
     * @param currentPath
     *            - List of so far visited Blocks
     * @param outSet
     *            - OUT set of CFG
     * @param predecessorVaribalesList
     *            - Chain of Process Variables along the path
     * @param edge
     *            - Current edge between two Blocks
     */
    private void addAnomaliesFoundInPathsRecursive(final BpmnElement element, Block currentBlock,
            LinkedList<String> currentPath, final OutSetCFG outSet,
            LinkedList<ProcessVariableOperation> predecessorVaribalesList, String edge) {

        // List<AnomalyContainer> foundAnomalies = new ArrayList<AnomalyContainer>();
        currentPath.add(edge);

        // get the VariableBlock
        VariableBlock variableBlock = outSet.getVariableBlock(currentBlock);

        // set IN + this Variables as OUT
        LinkedList<ProcessVariableOperation> usedVariables = new LinkedList<ProcessVariableOperation>();
        usedVariables.addAll(variableBlock.getAllProcessVariables());

        // Based on last appearance of Variable decide on UR, DD, DU anomaly
        for (ProcessVariableOperation variable : usedVariables) {
            if (predecessorVaribalesList.lastIndexOf(variable) >= 0) {

                ProcessVariableOperation lastApperance = predecessorVaribalesList
                        .get(predecessorVaribalesList.lastIndexOf(variable));
                if (urSourceCode(lastApperance, variable)) {
                    element.addSourceCodeAnomaly(new AnomalyContainer(variable.getName(), Anomaly.UR,
                            element.getBaseElement().getId(), variable));
                }

                if (ddSourceCode(lastApperance, variable)) {
                    element.addSourceCodeAnomaly(new AnomalyContainer(variable.getName(), Anomaly.DD,
                            element.getBaseElement().getId(), variable));
                }

                if (duSourceCode(lastApperance, variable)) {
                    element.addSourceCodeAnomaly(new AnomalyContainer(variable.getName(), Anomaly.DU,
                            element.getBaseElement().getId(), variable));
                }
            }
        }

        // Prepare new chain of Variables including this Block's variables for
        // successors
        predecessorVaribalesList.addAll(usedVariables);

        List<Block> sucessors = currentBlock.getSuccs();
        for (Block sucessor : sucessors) {
            String newEdge = currentBlock.toShortString() + sucessor.toShortString();
            int occurance = Collections.frequency(currentPath, newEdge);
            if (occurance < 2) {
                addAnomaliesFoundInPathsRecursive(element, sucessor, currentPath, outSet, predecessorVaribalesList,
                        newEdge);
            }
        }

        currentPath.removeLast();
        for (ProcessVariableOperation pv : variableBlock.getAllProcessVariables()) {
            predecessorVaribalesList.removeLastOccurrence(pv);
        }

    }

    /**
     * UR anomaly: second last operation of PV is DELETE, last operation is READ
     * 
     * @param lastApperance
     *            - Previous ProcessVariable
     * @param currentApperance
     *            - Current ProcessVariable
     * @return - true/false
     */
    private boolean urSourceCode(final ProcessVariableOperation lastApperance,
            final ProcessVariableOperation currentApperance) {
        if (currentApperance.getOperation().equals(VariableOperation.READ)
                && lastApperance.getOperation().equals(VariableOperation.DELETE)) {
            return true;
        }
        return false;
    }

    /**
     * DD anomaly: second last operation of PV is DEFINE, last operation is DELETE
     * 
     * @param last
     *            - Previous ProcessVariable
     * @param pv
     *            - Current ProcessVariable
     * @return - true/false
     */
    private boolean ddSourceCode(final ProcessVariableOperation last, final ProcessVariableOperation pv) {
        if (pv.getOperation().equals(VariableOperation.WRITE) && last.getOperation().equals(VariableOperation.WRITE)) {
            return true;
        }
        return false;
    }

    /**
     * DU anomaly: second last operation of PV is DEFINE, last operation is DELETE
     * 
     * @param last
     *            - Previous ProcessVariable
     * @param pv
     *            - Current ProcessVariable
     * @return - true/false
     */
    private boolean duSourceCode(final ProcessVariableOperation last, final ProcessVariableOperation pv) {
        if (pv.getOperation().equals(VariableOperation.DELETE) && last.getOperation().equals(VariableOperation.WRITE)) {
            return true;
        }
        return false;
    }

}