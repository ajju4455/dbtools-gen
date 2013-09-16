/*
 * SchemaRenderer.java
 *
 * Created on March 8, 2004
 *
 * Copyright 2006 Jeff Campbell. All rights reserved. Unauthorized reproduction 
 * is a violation of applicable law. This material contains certain 
 * confidential or proprietary information and trade secrets of Jeff Campbell.
 */

/*
 * http://www.ianywhere.com/developer/product_manuals/sqlanywhere/1000/en/html/dbrfen10/dbrfen10.html
 */

package org.dbtools.schema;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Jeff Campbell
 */
public class IAnywhereRenderer extends SchemaRenderer {

    public static final String RENDERER_NAME = "ianywhere";

    public IAnywhereRenderer() {
        super();
        this.setDbVendorName(RENDERER_NAME);
    }

    public IAnywhereRenderer(PrintStream ps) {
        super(ps);
        this.setDbVendorName(RENDERER_NAME);
    }

    @Override
    public String generateSchema(SchemaDatabase database, String[] tablesToGenerate, String[] viewsToGenerate, boolean dropTables, boolean createInserts) {
        showProgress("Generating SQL schema using iAnywhere renderer ...", true);
        StringBuilder schema = new StringBuilder();
        List<ForeignKey> foreignKeysToCreate = new ArrayList<ForeignKey>();

        // Because jdbc connection to iAnywhere assumes all fields to be NOT NULL by default
        // the following statement is added to treat fields the same as
        // ALL OTHER DATABASES!!!
        schema.append("SET OPTION allow_nulls_by_default = 'ON';\n\n");

        List<SchemaTable> requestedTables = getTablesToGenerate(database, tablesToGenerate);
        List<SchemaView> requestedViews = getViewsToGenerate(database, viewsToGenerate);

        // drop schema
        if (dropTables) {
            generateDropSchema(true, true, schema, requestedTables, requestedViews);
        }

        // create tables
        for (SchemaTable table : requestedTables) {
            // reset values for new table
            List<SchemaField> indexFields = new ArrayList<SchemaField>();
            List<SchemaField> uniqueFields = new ArrayList<SchemaField>();


            String tableType = table.getParameter("tableType");
            if (tableType == null) {
                tableType = "";
            }

            // add table header
            schema.append("CREATE ").append(tableType).append(" TABLE ");
            schema.append(table.getName());
            schema.append(" (\n");

            // add fields
            List<SchemaField> fields = table.getFields();
            SchemaField enumPKField = null;
            SchemaField enumValueField = null;

            for (int j = 0; j < fields.size(); j++) {
                SchemaField field = fields.get(j);

                // add field
                // name
                schema.append("\t");
                schema.append(field.getName());

                // datatype
                schema.append(" ");
                schema.append(getTypes().get(field.getJdbcType()));

                //check for size for datatype
                if (field.getSize() > 0) {
                    schema.append("(");
                    schema.append(field.getSize());

                    // check for decimals
                    if (field.getDecimals() > 0) {
                        schema.append(", ").append(field.getDecimals());
                    }

                    schema.append(")");
                }

                String defaultValue = field.getDefaultValue();
                if (defaultValue != null && !defaultValue.equals("")) {
                    schema.append(" DEFAULT ").append(formatDefaultValue(field));

//                    if (defaultValue.equalsIgnoreCase("NULL")) {
//                        schema.append(" DEFAULT NULL");
//                    } else {
//                        schema.append(" DEFAULT '");
//                        schema.append(defaultValue);
//                        schema.append("'");
//                    }

                }

                // not null
                if (field.isNotNull() && !field.isIncrement()) {
                    schema.append(" NOT NULL");
                }

                if (field.isIncrement()) {
                    schema.append(" DEFAULT AUTOINCREMENT");
                }

                if (field.isPrimaryKey()) {
                    schema.append(" PRIMARY KEY");
                }

                if (field.isUnique()) {
                    uniqueFields.add(field);
                }

                if (field.isIndex()) {
                    indexFields.add(field);
                }

                // add foreign key
                if (!field.getForeignKeyField().equals("")) {
                    foreignKeysToCreate.add(new ForeignKey(table.getName(), field.getName(), field.getForeignKeyTable(), field.getForeignKeyField()));
                }


                schema.append("");

                // if this is the last one, then don't put a ','
                if (j == fields.size() - 1) {
                    // add unique fields
                    if (uniqueFields.size() > 0) {
                        schema.append(",\n\tUNIQUE(");

                        for (int k = 0; k < uniqueFields.size(); k++) {
                            SchemaField uField = uniqueFields.get(k);
                            if (k != 0) {
                                schema.append(", ");
                            }

                            schema.append(uField.getName());
                        }

                        schema.append(")");
                    }

                    // add forengn keys fields
//                    if (foreignKeyFields.size() > 0) {
//                        for (int k = 0; k < foreignKeyFields.size(); k++) {
//                            SchemaField foreignKeyField = (SchemaField) foreignKeyFields.get(k);
//                            schema.append(",\n\tFOREIGN KEY (");
//                            schema.append(foreignKeyField.getName()).append(") REFERENCES ").append(foreignKeyField.getForeignKeyTable()).append(" (").append(foreignKeyField.getForeignKeyField()).append(")");
//                        }
//                    }
                } else {
                    // more fields to come...
                    schema.append(",\n");
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
            List uniqueDeclarations = table.getUniqueDeclarations();
            for (Object uniqueDeclaration : uniqueDeclarations) {
                String uniqueFieldString = "";

                List uniqueFieldsCombo = (List) uniqueDeclaration;
                for (int k = 0; k < uniqueFieldsCombo.size(); k++) {
                    String uniqueField = (String) uniqueFieldsCombo.get(k);

                    if (k > 0) {
                        uniqueFieldString += ", ";
                    }

                    uniqueFieldString += uniqueField;
                }

                schema.append(",\n\tUNIQUE(").append(uniqueFieldString).append(")");
            }

            // add table footer
            schema.append("\n);");

            // create indexes
            for (SchemaField indexField : indexFields) {
                SchemaField iField = (SchemaField) indexField;
                schema.append("\nCREATE INDEX ").append(table.getName()).append(iField.getName()).append("_IDX ON ").append(table.getName()).append(" (").append(iField.getName()).append(");");
            }

            schema.append("\n\n");

            generateEnumSchema(schema, table, getAlreadyCreatedEnum(), enumPKField, enumValueField, createInserts);
        } // end of tables

        // create foreign keys
        for (ForeignKey fk : foreignKeysToCreate) {
            schema.append("ALTER TABLE ").append(fk.getPrimaryKeyTable()).append("\n");
            schema.append("ADD CONSTRAINT ").append(fk.getPrimaryKeyTable()).append("_").append(fk.getPrimaryKeyField()).append("_FK\n");
            schema.append("FOREIGN KEY (").append(fk.getPrimaryKeyField()).append(")\n");
            schema.append("REFERENCES ").append(fk.getForeignKeyTable()).append(" (").append(fk.getForeignKeyField()).append(");\n");
            schema.append("\n");
        }

        // create views
        for (SchemaView view : requestedViews) {
            // header
            schema.append("CREATE VIEW ").append(view.getName()).append(" AS \n");

            // SELECT
            schema.append("  SELECT \n");

            Iterator vfItr = view.getViewFields().iterator();
            while (vfItr.hasNext()) {
                SchemaViewField viewField = (SchemaViewField) vfItr.next();

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

//    public String formatDefaultValue(SchemaField field) {
//        String defaultValue = field.getDefaultValue();
//        String newDefaultValue = "";
//        
//        Class javaType = field.getJavaType();
//        if (javaType == boolean.class) {
//            if (defaultValue.toUpperCase().equals("TRUE") || defaultValue.equals("1"))
//                newDefaultValue = "'true'";
//            else
//                newDefaultValue = "'false'";
//        } else {
//            newDefaultValue = super.formatDefaultValue(field);
//        }
//        
//        return newDefaultValue;
//    }

}
