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

import java.util.ArrayList;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.redhat.lightblue.metadata.ArrayElement;
import com.redhat.lightblue.metadata.ArrayField;
import com.redhat.lightblue.metadata.FieldTreeNode;
import com.redhat.lightblue.query.AllMatchExpression;
import com.redhat.lightblue.query.ForEachExpression;
import com.redhat.lightblue.query.QueryExpression;
import com.redhat.lightblue.query.RemoveElementExpression;
import com.redhat.lightblue.query.UpdateExpression;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.MutablePath;
import com.redhat.lightblue.util.Path;

/**
 * Evaluates a loop over the elements of an array
 */
public class ForEachExpressionEvaluator extends Updater {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForEachExpressionEvaluator.class);

    private final Path field;
    private final Path absField;
    private final ArrayField fieldMd;
    private final QueryEvaluator queryEvaluator;
    private final Updater updater;
    private final JsonNodeFactory factory;

    /**
     * Inner class for $all
     */
    private static class AllEvaluator extends QueryEvaluator {
        @Override
        public boolean evaluate(QueryEvaluationContext ctx) {
            ctx.setResult(true);
            return true;
        }
    }

    private static class RemoveEvaluator extends Updater {
        @Override
        public boolean update(JsonDoc doc,FieldTreeNode contextMd,Path contextPath) {
            return doc.modify(contextPath,null,false)!=null;
        }
    }

    public ForEachExpressionEvaluator(JsonNodeFactory factory,
                                      FieldTreeNode context,
                                      ForEachExpression expr) {
        this.factory=factory;
        // Resolve the field, make sure it is an array
        field=expr.getField();
        FieldTreeNode md=context.resolve(field);
        if(md instanceof ArrayField) {
            fieldMd=(ArrayField)md;
        } else {
            throw new EvaluationError("Field is not array:"+field);
        }
        if(field.nAnys()>0)
            throw new EvaluationError("Pattern not expected:"+field);
        absField=fieldMd.getFullPath();
        // Get a query evaluator
        QueryExpression query=expr.getQuery();
        if(query instanceof AllMatchExpression) {
            queryEvaluator=new AllEvaluator();
        } else {
            queryEvaluator=QueryEvaluator.getInstance(query,fieldMd.getElement());
        }

        // Get an updater to execute on each matching element
        UpdateExpression upd=expr.getUpdate();
        if(upd instanceof RemoveElementExpression) {
            updater=new RemoveEvaluator();
        } else {
            updater=Updater.getInstance(factory,fieldMd.getElement(),upd);
        }
    }
    
    @Override
    public boolean update(JsonDoc doc,FieldTreeNode contextMd,Path contextPath) {
        boolean ret=false;
        // Get a reference to the array field, and iterate all elements in the array
        ArrayNode arrayNode=(ArrayNode)doc.get(field);
        ArrayElement elementMd=fieldMd.getElement();
        if(arrayNode!=null) {
            int index=0;
            MutablePath itrPath=new MutablePath(contextPath);
            itrPath.push(field);
            MutablePath arrSizePath=itrPath.copy();
            arrSizePath.setLast(arrSizePath.getLast()+"#");
            arrSizePath.rewriteIndexes(contextPath);

            itrPath.push(index);
            // Copy the nodes to a separate list, so we iterate on the
            // new copy, and modify the original
            ArrayList<JsonNode> nodes=new ArrayList<JsonNode>();
            for(Iterator<JsonNode> itr=arrayNode.elements();itr.hasNext();) {
                nodes.add(itr.next());
            }
            for(JsonNode elementNode:nodes) {
                itrPath.setLast(index);
                Path elementPath=itrPath.immutableCopy();
                LOGGER.debug("itr:{}",elementPath);
                QueryEvaluationContext ctx=new QueryEvaluationContext(elementNode,elementPath);
                if(queryEvaluator.evaluate(ctx)) {
                    LOGGER.debug("query matches {}",elementPath);
                    if(updater.update(doc,elementMd,elementPath)) {
                        ret=true;
                        // Removal shifts nodes down
                        if(updater instanceof RemoveEvaluator) {
                            index--;
                        }
                    }
                } else {
                    LOGGER.debug("query does not match {}",elementPath);
                }
                index++;
            }
            if(ret) {
                doc.modify(arrSizePath,factory.numberNode(arrayNode.size()),false);
            }
        } 
        return ret;
    }
 }
