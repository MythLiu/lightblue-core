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

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.metadata.TypeResolver;
import com.redhat.lightblue.metadata.PredefinedFields;
import com.redhat.lightblue.metadata.mongo.MongoDataStoreParser;
import com.redhat.lightblue.metadata.parser.Extensions;
import com.redhat.lightblue.metadata.parser.JSONMetadataParser;
import com.redhat.lightblue.metadata.types.DefaultTypes;
import com.redhat.lightblue.query.UpdateExpression;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.JsonUtils;
import com.redhat.lightblue.util.Path;
import com.redhat.lightblue.util.test.AbstractJsonNodeTest;


public class UpdaterTest extends AbstractJsonNodeTest {

    private static final JsonNodeFactory factory = JsonNodeFactory.withExactBigDecimals(true);
    
    private JsonDoc getDoc(String fname) throws Exception {
        JsonNode node = loadJsonNode(fname);
        return new JsonDoc(node);
    }

    private EntityMetadata getMd(String fname) throws Exception {
        JsonNode node = loadJsonNode(fname);
        Extensions<JsonNode> extensions = new Extensions<JsonNode>();
        extensions.addDefaultExtensions();
        extensions.registerDataStoreParser("mongo", new MongoDataStoreParser<JsonNode>());
        TypeResolver resolver = new DefaultTypes();
        JSONMetadataParser parser = new JSONMetadataParser(extensions, resolver, factory);
        EntityMetadata md=parser.parseEntityMetadata(node);
        PredefinedFields.ensurePredefinedFields(md);
        return md;
    }

    private UpdateExpression json(String s) throws Exception {
        return UpdateExpression.fromJson(JsonUtils.json(s.replace('\'','\"')));
    }

    @Test
    public void setSimpleFieldTest() throws Exception {
        JsonDoc doc=getDoc("./sample1.json");
        EntityMetadata md=getMd("./testMetadata.json");

        UpdateExpression expr=json("[ {'$set' : { 'field1' : 'set1', 'field2':'set2', 'field5': 0, 'field6.nf1':'set6' } }, {'$add' : { 'field3':1 } } ] ");
        
        Updater updater=Updater.getInstance(factory,md,expr);
        Assert.assertTrue(updater.update(doc,md.getFieldTreeRoot(),new Path()));
        Assert.assertEquals("set1",doc.get(new Path("field1")).asText());
        Assert.assertEquals("set2",doc.get(new Path("field2")).asText());
        Assert.assertEquals(4,doc.get(new Path("field3")).asInt());
        Assert.assertFalse(doc.get(new Path("field5")).asBoolean());
        Assert.assertEquals("set6",doc.get(new Path("field6.nf1")).asText());
    }

    @Test
    public void setArrayFieldTest() throws Exception {
        JsonDoc doc=getDoc("./sample1.json");
        EntityMetadata md=getMd("./testMetadata.json");

        UpdateExpression expr=json("{'$set' : { 'field6.nf5.0':'50', 'field6.nf6.1':'blah', 'field7.0.elemf1':'test'}} ");
        
        Updater updater=Updater.getInstance(factory,md,expr);
        Assert.assertTrue(updater.update(doc,md.getFieldTreeRoot(),new Path()));
        Assert.assertEquals(50,doc.get(new Path("field6.nf5.0")).intValue());
        Assert.assertEquals("blah",doc.get(new Path("field6.nf6.1")).asText());
        Assert.assertEquals("test",doc.get(new Path("field7.0.elemf1")).asText());
    }

    @Test
    public void refSet() throws Exception {
        JsonDoc doc=getDoc("./sample1.json");
        EntityMetadata md=getMd("./testMetadata.json");

        UpdateExpression expr=json("{'$set' : { 'field6.nf5.0': { '$valueof' : 'field3' }, 'field7.0' : {}}}");
        
        Updater updater=Updater.getInstance(factory,md,expr);
        Assert.assertTrue(updater.update(doc,md.getFieldTreeRoot(),new Path()));
        Assert.assertEquals(doc.get(new Path("field3")).intValue(),doc.get(new Path("field6.nf5.0")).intValue());
        JsonNode node=doc.get(new Path("field7.0"));
        Assert.assertNotNull(node);
        Assert.assertEquals(0,node.size());
        Assert.assertTrue(node instanceof ObjectNode);
    }

    @Test
    public void unset() throws Exception {
        JsonDoc doc=getDoc("./sample1.json");
        EntityMetadata md=getMd("./testMetadata.json");
        
        UpdateExpression expr=json("{'$unset' : [ 'field1', 'field6.nf2', 'field6.nf6.1','field7.1'] }");
        
        Updater updater=Updater.getInstance(factory,md,expr);
        Assert.assertTrue(updater.update(doc,md.getFieldTreeRoot(),new Path()));
        Assert.assertNull(doc.get(new Path("field1")));
        Assert.assertNull(doc.get(new Path("field6.nf2")));
        Assert.assertEquals("three",doc.get(new Path("field6.nf6.1")).asText());
        Assert.assertEquals(3,doc.get(new Path("field6.nf6#")).asInt());
        Assert.assertEquals(3,doc.get(new Path("field6.nf6")).size());
        Assert.assertEquals("elvalue2_1",doc.get(new Path("field7.1.elemf1")).asText());
        Assert.assertEquals(3,doc.get(new Path("field7#")).asInt());
        Assert.assertEquals(3,doc.get(new Path("field7")).size());
    }
    

    @Test
    public void array_append() throws Exception {
        JsonDoc doc=getDoc("./sample1.json");
        EntityMetadata md=getMd("./testMetadata.json");
        
        UpdateExpression expr=json("{ '$append' : { 'field6.nf6' : [ 'five','six',{'$valueof':'field2' }] } }");
        
        Updater updater=Updater.getInstance(factory,md,expr);
        Assert.assertTrue(updater.update(doc,md.getFieldTreeRoot(),new Path()));
        
        Assert.assertEquals("one",doc.get(new Path("field6.nf6.0")).asText());
        Assert.assertEquals("two",doc.get(new Path("field6.nf6.1")).asText());
        Assert.assertEquals("three",doc.get(new Path("field6.nf6.2")).asText());
        Assert.assertEquals("four",doc.get(new Path("field6.nf6.3")).asText());
        Assert.assertEquals("five",doc.get(new Path("field6.nf6.4")).asText());
        Assert.assertEquals("six",doc.get(new Path("field6.nf6.5")).asText());
        Assert.assertEquals("value2",doc.get(new Path("field6.nf6.6")).asText());
        Assert.assertNull(doc.get(new Path("field6.ng6.7")));
        Assert.assertEquals(7,doc.get(new Path("field6.nf6#")).asInt());
        Assert.assertEquals(7,doc.get(new Path("field6.nf6")).size());
    }
    
    @Test
    public void array_insert() throws Exception {
        JsonDoc doc=getDoc("./sample1.json");
        EntityMetadata md=getMd("./testMetadata.json");
        
        UpdateExpression expr=json("{ '$insert' : { 'field6.nf6.2' : [ 'five','six',{'$valueof':'field2' }] } }");
        
        Updater updater=Updater.getInstance(factory,md,expr);
        Assert.assertTrue(updater.update(doc,md.getFieldTreeRoot(),new Path()));
        
        Assert.assertEquals("one",doc.get(new Path("field6.nf6.0")).asText());
        Assert.assertEquals("two",doc.get(new Path("field6.nf6.1")).asText());
        Assert.assertEquals("five",doc.get(new Path("field6.nf6.2")).asText());
        Assert.assertEquals("six",doc.get(new Path("field6.nf6.3")).asText());
        Assert.assertEquals("value2",doc.get(new Path("field6.nf6.4")).asText());
        Assert.assertEquals("three",doc.get(new Path("field6.nf6.5")).asText());
        Assert.assertEquals("four",doc.get(new Path("field6.nf6.6")).asText());
        Assert.assertNull(doc.get(new Path("field6.ng6.7")));
        Assert.assertEquals(7,doc.get(new Path("field6.nf6#")).asInt());
        Assert.assertEquals(7,doc.get(new Path("field6.nf6")).size());
     }
    
    @Test
    public void array_foreach_removeall() throws Exception {
        JsonDoc doc=getDoc("./sample1.json");
        EntityMetadata md=getMd("./testMetadata.json");
        
        UpdateExpression expr=json("{ '$foreach' : { 'field7' : '$all', '$update' : '$remove' } }");
        
        Updater updater=Updater.getInstance(factory,md,expr);
        Assert.assertTrue(updater.update(doc,md.getFieldTreeRoot(),new Path()));
        
        Assert.assertEquals(0,doc.get(new Path("field7")).size());
        Assert.assertEquals(0,doc.get(new Path("field7#")).asInt());
    }

    @Test
    public void array_foreach_removeone() throws Exception {
        JsonDoc doc=getDoc("./sample1.json");
        EntityMetadata md=getMd("./testMetadata.json");
        
        UpdateExpression expr=json("{ '$foreach' : { 'field7' : { 'field':'elemf1','op':'=','rvalue':'elvalue0_1'} , '$update' : '$remove' } }");        
        Updater updater=Updater.getInstance(factory,md,expr);
        Assert.assertTrue(updater.update(doc,md.getFieldTreeRoot(),new Path()));
        
        Assert.assertEquals(3,doc.get(new Path("field7")).size());
        Assert.assertEquals("elvalue1_1",doc.get(new Path("field7.0.elemf1")).asText());
        Assert.assertEquals(3,doc.get(new Path("field7#")).asInt());
        Assert.assertEquals(3,doc.get(new Path("field7")).size());
    }

    @Test
    public void array_foreach_modone() throws Exception {
        JsonDoc doc=getDoc("./sample1.json");
        EntityMetadata md=getMd("./testMetadata.json");
        
        UpdateExpression expr=json("{ '$foreach' : { 'field7' : { 'field':'elemf1','op':'=','rvalue':'elvalue0_1'} , '$update' : {'$set': { 'elemf1':'test'}} } }");        
        Updater updater=Updater.getInstance(factory,md,expr);
        Assert.assertTrue(updater.update(doc,md.getFieldTreeRoot(),new Path()));
        
        Assert.assertEquals(4,doc.get(new Path("field7")).size());
        Assert.assertEquals("test",doc.get(new Path("field7.0.elemf1")).asText());
    }
}
