/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.teleport.v2.datastream.values.DatastreamRow;
import com.google.cloud.teleport.v2.templates.DataStreamToSQL;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test cases for the {@link DatastreamToDML} class. */
public class DatastreamToDMLTest {

  private static final Logger LOG = LoggerFactory.getLogger(DatastreamToDMLTest.class);
  private static final String JSON_STRING =
      "{"
          + "\"text_column\":\"value\","
          + "\"quoted_text_column\":\"Test Values: '!@#$%^\","
          + "\"null_byte_text_column\":\"Test Values: He\\u0000s made\","
          + "\"_metadata_schema\":\"MY_SCHEMA\","
          + "\"_metadata_table\":\"MY_TABLE$NAME\""
          + "}";

  private JsonNode getRowObj(String jsonString) {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode rowObj;
    try {
      rowObj = mapper.readTree(jsonString);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return rowObj;
  }

  /**
   * Test whether {@link DatastreamToPostgresDML#getValueSql(JsonNode, String, Map)} converts data
   * into correct strings. String columnValue = getValueSql(rowObj, columnName, tableSchema);
   */
  @Test
  public void testGetValueSql() {
    JsonNode rowObj = this.getRowObj(JSON_STRING);

    String expectedTextContent = "'value'";
    String testSqlContent =
        DatastreamToPostgresDML.of(null)
            .getValueSql(rowObj, "text_column", new HashMap<String, String>());
    assertEquals(expectedTextContent, testSqlContent);

    // Single quotes are escaped by 2 single quotes in SQL
    String expectedQuotedTextContent = "'Test Values: ''!@#$%^'";
    String testQuotedSqlContent =
        DatastreamToPostgresDML.of(null)
            .getValueSql(rowObj, "quoted_text_column", new HashMap<String, String>());
    assertEquals(expectedQuotedTextContent, testQuotedSqlContent);

    // Null bytes are escaped with blanks values
    String expectedNullByteTextContent = "'Test Values: Hes made'";
    String testNullByteSqlContent =
        DatastreamToPostgresDML.of(null)
            .getValueSql(rowObj, "null_byte_text_column", new HashMap<String, String>());
    assertEquals(expectedNullByteTextContent, testNullByteSqlContent);
  }

  /**
   * Test whether {@link DatastreamToPostgresDML#getValueSql(JsonNode, String, Map)} converts array
   * data into correct integer array syntax.
   */
  @Test
  public void testIntArrayWithNullTypeCoercion() {
    String arrayJson =
        "{\"number_array\": {"
            + "\"nestedArray\": ["
            + "  {\"nestedArray\": null, \"elementValue\": null},"
            + "  {\"nestedArray\": null, \"elementValue\": 456}"
            + "], \"elementValue\": null}}";
    JsonNode rowObj = this.getRowObj(arrayJson);
    Map<String, String> tableSchema = new HashMap<>();
    tableSchema.put("number_array", "_int4");
    DatastreamToPostgresDML dml = DatastreamToPostgresDML.of(null);
    String expectedInt = "ARRAY[NULL,456]";

    String actualInt =
        DatastreamToPostgresDML.of(null).getValueSql(rowObj, "number_array", tableSchema);

    assertEquals(expectedInt, actualInt);
  }

  /**
   * Test whether {@link DatastreamToPostgresDML#getValueSql(JsonNode, String, Map)} converts array
   * data into correct integer array syntax.
   */
  @Test
  public void testIntArrayTypeCoercion() {
    String arrayJson =
        "{\"number_array\": {"
            + "\"nestedArray\": ["
            + "  {\"nestedArray\": null, \"elementValue\": 123},"
            + "  {\"nestedArray\": null, \"elementValue\": 456}"
            + "], \"elementValue\": null}}";
    JsonNode rowObj = this.getRowObj(arrayJson);
    Map<String, String> tableSchema = new HashMap<>();
    tableSchema.put("number_array", "_int4");
    DatastreamToPostgresDML dml = DatastreamToPostgresDML.of(null);
    String expectedInt = "ARRAY[123,456]";

    String actualInt =
        DatastreamToPostgresDML.of(null).getValueSql(rowObj, "number_array", tableSchema);

    assertEquals(expectedInt, actualInt);
  }

  /**
   * Test whether {@link DatastreamToPostgresDML#getValueSql(JsonNode, String, Map)} converts array
   * data into correct text array syntax.
   */
  @Test
  public void testTextArrayTypeCoercion() {
    String arrayJson =
        "{\"text_array\": {"
            + "\"nestedArray\": ["
            + "  {\"nestedArray\": null, \"elementValue\": \"apple\"},"
            + "  {\"nestedArray\": null, \"elementValue\": \"cherry\"}"
            + "], \"elementValue\": null}}";
    JsonNode rowObj = this.getRowObj(arrayJson);
    Map<String, String> tableSchema = new HashMap<>();
    tableSchema.put("text_array", "_text");
    DatastreamToPostgresDML dml = DatastreamToPostgresDML.of(null);
    String expectedInt = "ARRAY['apple','cherry']";

    String actualInt =
        DatastreamToPostgresDML.of(null).getValueSql(rowObj, "text_array", tableSchema);

    assertEquals(expectedInt, actualInt);
  }

  /**
   * Test whether {@link DatastreamToDML#getTargetSchemaName} converts the Oracle schema into the
   * correct Postgres schema.
   */
  @Test
  public void testGetPostgresSchemaName() {
    DatastreamToDML datastreamToDML = DatastreamToPostgresDML.of(null);
    JsonNode rowObj = this.getRowObj(JSON_STRING);
    DatastreamRow row = DatastreamRow.of(rowObj);

    String expectedSchemaName = "my_schema";
    String schemaName = datastreamToDML.getTargetSchemaName(row);
    assertEquals(schemaName, expectedSchemaName);
  }

  /**
   * Test whether {@link DatastreamToPostgresDML#getTargetTableName} converts the Oracle table into
   * the correct Postgres table.
   */
  @Test
  public void testGetPostgresTableName() {
    DatastreamToDML datastreamToDML = DatastreamToPostgresDML.of(null);
    JsonNode rowObj = this.getRowObj(JSON_STRING);
    DatastreamRow row = DatastreamRow.of(rowObj);

    String expectedTableName = "my_table$name";
    String tableName = datastreamToDML.getTargetTableName(row);
    assertEquals(expectedTableName, tableName);
  }

  /** Test cleaning schema map. */
  @Test
  public void testParseSchemaMap() {
    Map<String, String> singleItemExpected =
        new HashMap<String, String>() {
          {
            put("a", "b");
          }
        };
    Map<String, String> doubleItemExpected =
        new HashMap<String, String>() {
          {
            put("a", "b");
            put("c", "d");
          }
        };

    assertThat(DataStreamToSQL.parseSchemaMap("")).isEmpty();
    assertThat(DataStreamToSQL.parseSchemaMap("a:b")).isEqualTo(singleItemExpected);
    assertThat(DataStreamToSQL.parseSchemaMap("a:b,c:d")).isEqualTo(doubleItemExpected);
  }
}
