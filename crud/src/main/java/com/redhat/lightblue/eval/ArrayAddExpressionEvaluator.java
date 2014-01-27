/*
 Copyright 2013 Red Hat, Inc. and/or its affiliates.

 This file is part of lightblue.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.redhat.lightblue.eval;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.redhat.lightblue.query.ArrayAddExpression;
import com.redhat.lightblue.query.UpdateOperator;
import com.redhat.lightblue.query.RValueExpression;
import com.redhat.lightblue.query.Value;

import com.redhat.lightblue.metadata.FieldTreeNode;
import com.redhat.lightblue.metadata.ArrayField;
import com.redhat.lightblue.metadata.ArrayElement;
import com.redhat.lightblue.metadata.ObjectArrayElement;
import com.redhat.lightblue.metadata.types.Type;

import com.redhat.lightblue.util.Path;
import com.redhat.lightblue.util.JsonDoc;

/**
 * Adds a field to an array
 */
public class ArrayAddExpressionEvaluator extends Updater {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayAddExpressionEvaluator.class);

    private final Path arrayField;
    private final int insertionIndex;
    private final ArrayField fieldMd;
    private final List<RValueData> values;
    private final JsonNodeFactory factory;

    private static final class RValueData {
        private final Path refPath;
        private final Type refType;
        private final Value value;

        public RValueData(Path refPath,
                          Type refType,
                          Value value) {
            this.refPath=refPath;
            this.refType=refType;
            this.value=value;
        }
    }

    public ArrayAddExpressionEvaluator(JsonNodeFactory factory,
                                       FieldTreeNode context,
                                       ArrayAddExpression expr) {
        this.factory=factory;
        if(expr.getOp()==UpdateOperator._insert) {
            // Path should include an index
            if(expr.getField().isIndex(expr.getField().numSegments()-1)) {
                arrayField=expr.getField().prefix(-1);
                insertionIndex=expr.getField().getIndex(expr.getField().numSegments()-1);
            } else {
                throw new EvaluationError("Index required in insertion:"+expr.getField());
            }
        } else {
            arrayField=expr.getField();
            insertionIndex=-1;
        }
        FieldTreeNode ftn=context.resolve(arrayField);
        if(ftn instanceof ArrayField) {
            fieldMd=(ArrayField)ftn;
            values=new ArrayList<RValueData>(expr.getValues().size());
            initializeArrayField(ftn, context, expr);
        } else {
            throw new EvaluationError("Array required:"+arrayField);
        }
    }
    
    private void initializeArrayField(FieldTreeNode ftn, FieldTreeNode context, ArrayAddExpression expr) {
        for(RValueExpression rvalue:expr.getValues()) {
            Path refPath=null;
            FieldTreeNode refMd=null;
            if(rvalue.getType()==RValueExpression.RValueType._dereference) {
                refPath=rvalue.getPath();
                refMd=context.resolve(refPath);
                if(refMd==null) {
                    throw new EvaluationError("Invalid dereference:"+refPath);
                }
            } 
            ArrayElement element=fieldMd.getElement();
            validateArrayElement(element, refMd, rvalue, refPath);
            values.add(new RValueData(refPath,refMd==null?null:refMd.getType(),rvalue.getValue()));
        }
    }
    
    private void validateArrayElement(ArrayElement element, FieldTreeNode refMd, RValueExpression rvalue, Path refPath) {
        if(element instanceof ObjectArrayElement) {
            if(refMd!=null&&!refMd.getType().equals(element.getType())) {
                throw new EvaluationError("Invalid assignment "+arrayField+" <- "+refPath);
            } else if(rvalue.getType()==RValueExpression.RValueType._value) {
                throw new EvaluationError("Object value expected for "+arrayField);
            }
        } else {
            if(refMd!=null&&!refMd.getType().equals(element.getType())) {
                throw new EvaluationError("Invalid assignment "+arrayField+"<-"+refPath);
            } else if(rvalue.getType()==RValueExpression.RValueType._emptyObject) {
                throw new EvaluationError("Value expected for "+arrayField);
            }
        }
    }
    
    @Override
    public boolean update(JsonDoc doc,FieldTreeNode contextMd,Path contextPath) {
        boolean ret=false;
        Path absPath=new Path(contextPath,arrayField);
        JsonNode node=doc.get(absPath);
        int insertTo=insertionIndex;
        if(node instanceof ArrayNode) {
            ArrayNode arrayNode=(ArrayNode)node;
            for(RValueData rvalueData:values) {
                LOGGER.debug("add element to {}",absPath);
                Object newValue=null;
                Type newValueType=null;
                JsonNode newValueNode=null;
                if(rvalueData.refPath!=null) {
                    JsonNode refNode=doc.get(new Path(contextPath,rvalueData.refPath));
                    if(refNode!=null) {
                        newValueNode=refNode.deepCopy();
                        newValue=rvalueData.refType.fromJson(newValueNode);
                        newValueType=rvalueData.refType;
                    }
                } else if(rvalueData.value!=null) {
                    newValue=rvalueData.value.getValue();
                    newValueNode=fieldMd.getElement().getType().toJson(factory,newValue);
                    newValueType=fieldMd.getElement().getType();
                } else {
                    newValueNode=factory.objectNode();                
                }
                LOGGER.debug("newValueType: " + newValueType);
                
                if(insertTo>=0) {
                    // If we're inserting, make sure we have that many elements
                    while(arrayNode.size()<insertTo) {
                        arrayNode.addNull();
                    }
                        
                    if(arrayNode.size()>insertTo) {
                        arrayNode.insert(insertTo,newValueNode);
                    } else {
                        arrayNode.add(newValueNode);
                    }   
                    insertTo++;
                } else {
                    arrayNode.add(newValueNode);
                }
                ret=true;
            }
        }
        return ret;
    }
 }
