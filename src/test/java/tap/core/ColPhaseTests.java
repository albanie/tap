package tap.core;

import static org.junit.Assert.*;

import org.apache.avro.Schema;
import org.junit.Test;

import tap.Phase;



public class ColPhaseTests {
    String one = 
        "{\"name\" : \"one\", \"type\":\"string\"}".replaceAll(" ", "");
    String fields = "\"fields\":[" + one + "," +
    "{\"name\" : \"two\", \"type\":\"int\"},"+
    "{\"name\" : \"three\", \"type\":\"int\"},"+
    "{\"name\" : \"four\", \"type\":\"int\"},"+
    "{\"name\" : \"five\", \"type\":\"int\"}"+
    "]";
    Schema schema = Schema.parse("{\"name\":\"test\", \"type\":\"record\", "+fields+"}");

    @Test
    public void groupSchemas() {        
        Schema groupSchema = Phase.group(schema, "one , two", "three,four,five");
        assertTrue(groupSchema.toString().contains(fields.replaceAll(" ", "")));      		
    }
    
    @Test
    public void groupSchemaWithSortOrdering() {
        Schema groupSchema = Phase.group(schema, "one , two desc");
        assertEquals(2, groupSchema.getFields().size());
        Schema.Field one = groupSchema.getField("one");
        Schema.Field two = groupSchema.getField("two");
        
        assertNotNull(one);
        assertNotNull(two);
        assertEquals(Schema.Field.Order.ASCENDING, one.order());
        assertEquals(Schema.Field.Order.DESCENDING, two.order());
    }
    
    @Test
    public void willTagSortByFields() {
    	Schema generated = Phase.groupAndSort(schema, "one", "two desc");
    	
    	assertEquals(2, generated.getFields().size());
        Schema.Field one = generated.getField("one");
        Schema.Field two = generated.getField("two");
        
        assertNotNull(one);
        assertNotNull(two);
        assertEquals(Schema.Field.Order.ASCENDING, one.order());
        assertEquals(Schema.Field.Order.DESCENDING, two.order());
        
        assertNull(one.getProp("x-sort"));
        assertEquals("true", two.getProp("x-sort"));
    }
    
    @Test
    public void willHandleFieldSpecifiedInBothGroupAndSortBy() {
    	Schema generated = Phase.groupAndSort(schema, "one", "one desc");
    	assertEquals(1, generated.getFields().size());
        Schema.Field one = generated.getField("one");
        
        assertNotNull(one);
        assertEquals(Schema.Field.Order.DESCENDING, one.order());
        
        assertNull(one.getProp("x-sort"));
    }

    @Test
    public void groupSchemaOneMultiList() {        
        Schema groupSchema = Phase.group(schema, " one , two");
        assertTrue(groupSchema.toString().contains(one));
        assertTrue(groupSchema.toString().contains("two"));
        assertFalse(groupSchema.toString().contains("three"));
    }
    
    @Test
    public void groupSchemaOneList() {        
        Schema groupSchema = Phase.group(schema, " one ");
        assertTrue(groupSchema.toString().contains(one));
        assertFalse(groupSchema.toString().contains("two"));
    }
    
    @Test
    public void groupSchemaOneAndNullList() {        
        Schema groupSchema = Phase.group(schema, " one ", null);
        assertTrue(groupSchema.toString().contains(one));
        assertFalse(groupSchema.toString().contains("two"));
    }
    
    @Test
    public void groupSchemaNullAndOneList() {        
        Schema groupSchema = Phase.group(schema, null, " one ");
        assertTrue(groupSchema.toString().contains(one));
        assertFalse(groupSchema.toString().contains("two"));
    }
    
    @Test
    public void groupSchemaOneItem() {        
        Schema groupSchema = Phase.group(schema, " one ");
        assertTrue(groupSchema.toString().contains(one));
        assertFalse(groupSchema.toString().contains("two"));
    }
    
    @Test
    public void groupSchemaNoItems() {        
        Schema groupSchema = Phase.group(schema);
        assertTrue(groupSchema.toString().contains("\"fields\":[]"));
    }
    
    @Test
    public void groupInvalidField() {
        try {
            Schema schema = Schema.parse("{\"name\":\"test\", \"type\":\"record\", \"fields\":[]}");
            System.out.println(Phase.group(schema, "missing"));
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("missing"));
        }
    }
}
