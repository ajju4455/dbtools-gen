/*
 * DerbyRenderer.java
 *
 * Created on Sep 9, 2010
 *
 * Copyright 2010 Jeff Campbell. All rights reserved. Unauthorized reproduction
 * is a violation of applicable law. This material contains certain
 * confidential or proprietary information and trade secrets of Jeff Campbell.
 */

/*
 * http://www.sqlite.org/lang.html
 */
package org.dbtools.schema;

import java.io.PrintStream;
import java.util.*;

/**
 * @author Jeff Campbell
 */
public class SqliteRenderer extends SchemaRenderer {

    public static final String RENDERER_NAME = "sqlite";

    public SqliteRenderer() {
        super();
        this.setDbVendorName(RENDERER_NAME);
    }

    public SqliteRenderer(PrintStream ps) {
        super(ps);
        this.setDbVendorName(RENDERER_NAME);
    }

    @Override
    public String generateSchema(SchemaDatabase database, String[] tablesToGenerate, String[] viewsToGenerate, boolean dropTables, boolean createInserts) {
        showProgress("Generating SQL schema using Sqlite renderer ...", true);
        StringBuilder schema = new StringBuilder();

        List<SchemaTable> requestedTables = getTablesToGenerate(database, tablesToGenerate);
        List<SchemaView> requestedViews = getViewsToGenerate(database, viewsToGenerate);

        // drop schema
        if (dropTables) {
            generateDropSchema(false, true, schema, requestedTables, requestedViews);
        }

        // create tables
        for (SchemaTable table : requestedTables) {
            // reset values for new table
            schema.append(generateTableSchema(table, getTypes()));
        } // end of tables

        // create views
        for (SchemaView view : requestedViews) {
            // header
            schema.append("CREATE VIEW ").append(view.getName()).append(" AS \n");

            // SELECT
            schema.append("  SELECT \n");

            Iterator<SchemaViewField> vfItr = view.getViewFields().iterator();
            while (vfItr.hasNext()) {
                SchemaViewField viewField = vfItr.next();

                schema.append("\t").append(viewField.getExpression()).append(" ").append(viewField.getName());

                if (vfItr.hasNext()) {
                    schema.append(",\n");
                } else {
                    schema.append("\n");
                }
            }

            schema.append("  ").append(view.getViewPostSelectClause()).append(";");

            // end
            schema.append("\n\n");
        } // end of views

        // return 
        return schema.toString();
    }

    @Override
    public String formatDefaultValue(SchemaField field) {
        return formatSqliteDefaultValue(field);
    }

    public static String formatSqliteDefaultValue(SchemaField field) {
        String defaultValue = field.getDefaultValue();
        String newDefaultValue = "";

        Class<?> javaType = field.getJavaClassType();
        if (javaType == boolean.class || javaType == Boolean.class) {
            if (defaultValue.equalsIgnoreCase("TRUE") || defaultValue.equals("1")) {
                newDefaultValue = "1";
            } else {
                newDefaultValue = "0";
            }
        } else if (javaType == Date.class) {
            if (defaultValue.equalsIgnoreCase("now")) {
                newDefaultValue = "CURRENT_TIMESTAMP";
            }

        } else {
            newDefaultValue = SchemaRenderer.formatBaseDefaultValue(field);
        }

        return newDefaultValue;
    }

    @Override
    public String generatePostSchema(SchemaDatabase database, String[] tablesToGenerate) {
        StringBuilder postSchema = new StringBuilder();
        postSchema.append("\n\n");

        // no sequencers in sqlite

        return postSchema.toString();
    }

    public static String generateTableSchema(SchemaTable table, Map<String, String> types) {
        StringBuilder tableSchema = new StringBuilder();

        // add table header
        tableSchema.append("CREATE TABLE IF NOT EXISTS ");
        tableSchema.append(table.getName());
        tableSchema.append(" (\n");

        // add fields
        List<SchemaField> fields = table.getFields();
        List<SchemaField> indexFields = new ArrayList<SchemaField>();
        List<SchemaField> uniqueFields = new ArrayList<SchemaField>();
        SchemaField enumPKField = null;
        SchemaField enumValueField = null;

        for (int j = 0; j < fields.size(); j++) {
            SchemaField field = fields.get(j);

            String fieldName = field.getName();

            // add field
            // name
            tableSchema.append("\t");
            tableSchema.append(fieldName);

            // datatype
            tableSchema.append(" ");
            tableSchema.append(types.get(field.getJdbcType()));

            String defaultValue = field.getDefaultValue();
            if (defaultValue != null && !defaultValue.equals("")) {
                tableSchema.append(" DEFAULT ").append(formatSqliteDefaultValue(field));
            }

            // not null
            if (field.isNotNull() && !field.isIncrement()) {
                tableSchema.append(" NOT NULL");
            }
            if (field.isPrimaryKey()) {
                tableSchema.append(" PRIMARY KEY");
            }
            if (field.isIncrement()) {
                tableSchema.append("  AUTOINCREMENT");
            }
            if (field.isUnique()) {
                uniqueFields.add(field);
            }
            if (field.isIndex()) {
                indexFields.add(field); // add foreign key
            }

            tableSchema.append("");

            // if this is the last one, then don't put a ','
            if (j == fields.size() - 1) {
                // add unique fields
                if (uniqueFields.size() > 0) {
                    tableSchema.append(",\n\tUNIQUE(");

                    for (int k = 0; k < uniqueFields.size(); k++) {
                        SchemaField uField = uniqueFields.get(k);
                        if (k != 0) {
                            tableSchema.append(", ");
                        }
                        tableSchema.append(uField.getName());
                    }

                    tableSchema.append(")");
                }

                // add foreign keys fields
                for (SchemaField foreignKeyField : table.getForeignKeyFields()) {
                    tableSchema.append(",\n\tFOREIGN KEY (");
                    tableSchema.append(foreignKeyField.getName()).append(") REFERENCES ").append(foreignKeyField.getForeignKeyTable());
                    tableSchema.append(" (").append(foreignKeyField.getForeignKeyField()).append(")");
                }
            } else {
                // more fields to come...
                tableSchema.append(",\n");
            }

            // check for enumFields
            if (enumPKField == null && field.isPrimaryKey()) {
                enumPKField = field;
            }
            if (enumValueField == null && field.getJdbcType().equals(SchemaField.TYPE_VARCHAR)) {
                enumValueField = field;
            }
        }

        // check for uniqueDeclarations
        List<List<String>> uniqueDeclarations = table.getUniqueDeclarations();
        for (List<String> uniqueDeclaration : uniqueDeclarations) {
            String uniqueFieldString = "";

            for (int k = 0; k < uniqueDeclaration.size(); k++) {
                String uniqueField = uniqueDeclaration.get(k);

                if (k > 0) {
                    uniqueFieldString += ", ";
                }
                uniqueFieldString += uniqueField;
            }

            tableSchema.append(",\n\tUNIQUE(").append(uniqueFieldString).append(")");
        }

        // add table footer
        tableSchema.append("\n);\n\n");

        // create indexes
        for (SchemaField iField : indexFields) {
            tableSchema.append("CREATE INDEX IF NOT EXISTS ").append(table.getName()).append(iField.getName()).append("_IDX ON ").append(table.getName());
            tableSchema.append(" (").append(iField.getName()).append(");\n\n");
        }

        Map<String, SchemaTable> alreadyCreatedEnum = new HashMap<String, SchemaTable>();
        generateEnumSchema(tableSchema, table, alreadyCreatedEnum, enumPKField, enumValueField, true);

        return tableSchema.toString();
    }
}
