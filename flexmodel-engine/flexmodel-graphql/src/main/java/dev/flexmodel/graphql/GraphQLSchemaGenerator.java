package dev.flexmodel.graphql;

import dev.flexmodel.codegen.AbstractGenerator;
import dev.flexmodel.codegen.GenerationContext;
import dev.flexmodel.codegen.ModelClass;
import dev.flexmodel.codegen.ModelField;
import dev.flexmodel.codegen.StringUtils;
import dev.flexmodel.model.field.EnumRefField;
import dev.flexmodel.model.field.RelationField;
import dev.flexmodel.model.field.ScalarType;
import dev.flexmodel.model.field.TypedField;

import java.io.PrintWriter;
import java.util.Map;

/**
 * Generates GraphQL schema SDL from model definitions.
 *
 * @author cjbi
 */
public class GraphQLSchemaGenerator extends AbstractGenerator {

    private final Map<String, String> typeMapping = Map.ofEntries(
        Map.entry(ScalarType.STRING.getType(), "String"),
        Map.entry(ScalarType.FLOAT.getType(), "Float"),
        Map.entry(ScalarType.INT.getType(), "Int"),
        Map.entry(ScalarType.LONG.getType(), "Int"),
        Map.entry(ScalarType.BOOLEAN.getType(), "Boolean"),
        Map.entry(ScalarType.DATETIME.getType(), "String"),
        Map.entry(ScalarType.DATE.getType(), "String"),
        Map.entry(ScalarType.TIME.getType(), "String"),
        Map.entry(ScalarType.JSON.getType(), "JSON")
    );

    private final Map<String, String> comparisonMapping = Map.ofEntries(
        Map.entry(ScalarType.STRING.getType(), "StringComparisonExp"),
        Map.entry(ScalarType.FLOAT.getType(), "FloatComparisonExp"),
        Map.entry(ScalarType.INT.getType(), "IntComparisonExp"),
        Map.entry(ScalarType.LONG.getType(), "IntComparisonExp"),
        Map.entry(ScalarType.BOOLEAN.getType(), "BooleanComparisonExp"),
        Map.entry(ScalarType.DATETIME.getType(), "DateTimeComparisonExp"),
        Map.entry(ScalarType.DATE.getType(), "DateComparisonExp"),
        Map.entry(ScalarType.TIME.getType(), "DateTimeComparisonExp"),
        Map.entry(ScalarType.JSON.getType(), "JSONComparisonExp")
    );

    private final I18nUtil i18n = new I18nUtil();

    private String toGraphQLType(ModelField itt, GenerationContext context) {
        if (itt.isRelationField()) {
            RelationField rf = (RelationField) itt.getOriginal();
            String typeName = StringUtils.capitalize(StringUtils.snakeToCamel(rf.getFrom()));
            if (rf.isMultiple()) {
                return "[" + typeName + "]";
            } else {
                return typeName;
            }
        } else if (itt.isBasicField()) {
            TypedField<?, ?> f = (TypedField<?, ?>) itt.getOriginal();
            if (itt.isIdentity()) {
                return "ID";
            }
            return typeMapping.getOrDefault(f.getType(), "String");
        } else if (itt.isEnumField() && context.containsEnumClass(((EnumRefField) itt.getOriginal()).getFrom())) {
            EnumRefField ef = (EnumRefField) itt.getOriginal();
            String typeName = StringUtils.capitalize(StringUtils.snakeToCamel(ef.getFrom()));
            if (ef.isMultiple()) {
                return "[" + typeName + "]";
            } else {
                return typeName;
            }
        } else {
            return "String";
        }
    }

    @Override
    public void write(PrintWriter out, GenerationContext context) {
        out.println("schema {");
        out.println("  query: Query");
        out.println("  mutation: Mutation");
        out.println("}");

        // Enums
        for (var ec : context.getEnumClassList()) {
            out.println();
            out.println("enum " + ec.getShortClassName() + " {");
            for (String el : ec.getElements()) {
                out.println("  " + el);
            }
            out.println("}");
        }

        // Query type
        out.println();
        out.println("\"" + i18n.getString("gql.query.comment") + "\"");
        out.println("type Query {");
        if (context.getModelClassList().isEmpty()) {
            out.println("  _health: String");
        }
        for (var it : context.getModelClassList()) {
            String key = it.getShortClassName();
            String qkey = StringUtils.uncapitalize(key);
            out.println();
            out.println(" \"" + i18n.getString("gql.query.find.comment", it.getSchemaName(), it.getOriginal().getName()) + "\"");
            out.println("  " + DataFetchers.FIND.getKeyFunc().apply(it.getSchemaName(), qkey) + "(");
            out.println("    \"" + i18n.getString("gql.query.where.comment") + "\"");
            out.println("    where: " + key + "BoolExp");
            out.println("    \"" + i18n.getString("gql.query.order_by.comment") + "\"");
            out.println("    orderBy: " + key + "OrderBy");
            out.println("    \"" + i18n.getString("gql.query.size.comment") + "\"");
            out.println("    size: Int");
            out.println("    \"" + i18n.getString("gql.query.page.comment") + "\"");
            out.println("    page: Int");
            out.println("  ): [" + key + "!]!");
            out.println();
            out.println("  \"" + i18n.getString("gql.query.aggregate.comment", it.getSchemaName(), it.getOriginal().getName()) + "\"");
            out.println("  " + DataFetchers.AGGREGATE.getKeyFunc().apply(it.getSchemaName(), qkey) + "(");
            out.println("    \"" + i18n.getString("gql.query.where.comment") + "\"");
            out.println("    where: " + key + "BoolExp");
            out.println("    \"" + i18n.getString("gql.query.order_by.comment") + "\"");
            out.println("    orderBy: " + key + "OrderBy");
            out.println("    \"" + i18n.getString("gql.query.size.comment") + "\"");
            out.println("    size: Int");
            out.println("    \"" + i18n.getString("gql.query.page.comment") + "\"");
            out.println("    page: Int");
            out.println("  ): " + key + "Aggregate!");
            out.println();
            if (it.getIdField() != null) {
                out.println("  \"" + i18n.getString("gql.query.by_id.comment", it.getSchemaName(), it.getOriginal().getName()) + "\"");
                out.println("  " + DataFetchers.BY_ID.getKeyFunc().apply(it.getSchemaName(), qkey) + "(");
                out.println("    id: ID!");
                out.println("  ): " + key);
            }
        }
        out.println("}");

        // Mutation type
        out.println();
        out.println("\"" + i18n.getString("gql.mutation.comment") + "\"");
        out.println("type Mutation {");
        if (context.getModelClassList().isEmpty()) {
            out.println("  _noop: String");
        }
        for (var it : context.getModelClassList()) {
            String key = it.getShortClassName();
            String qkey = StringUtils.uncapitalize(key);
            out.println();
            out.println("  \"" + i18n.getString("gql.mutation.delete.comment", it.getSchemaName(), it.getOriginal().getName()) + "\"");
            out.println("  " + DataFetchers.MUTATION_DELETE.getKeyFunc().apply(it.getSchemaName(), qkey) + "(");
            out.println("    \"" + i18n.getString("gql.mutation.delete.filter.comment") + "\"");
            out.println("    where: " + key + "BoolExp!");
            out.println("  ): MutationResponse");
            if (it.getIdField() != null) {
                out.println();
                out.println("  \"" + i18n.getString("gql.mutation.delete_by_id.comment", it.getSchemaName(), it.getOriginal().getName()) + "\"");
                out.println("  " + DataFetchers.MUTATION_DELETE_BY_ID.getKeyFunc().apply(it.getSchemaName(), qkey) + "(");
                out.println("    id: ID!");
                out.println("  ): " + key);
                out.println();
                out.println("  \"" + i18n.getString("gql.mutation.update_by_id.comment", it.getSchemaName(), it.getOriginal().getName()) + "\"");
                out.println("  " + DataFetchers.MUTATION_UPDATE_BY_ID.getKeyFunc().apply(it.getSchemaName(), qkey) + "(");
                out.println("   set: " + key + "SetInput");
                out.println("   id: ID!");
                out.println("  ): " + key);
            }
            out.println();
            out.println("  \"" + i18n.getString("gql.mutation.create.comment", it.getSchemaName(), it.getOriginal().getName()) + "\"");
            out.println("  " + DataFetchers.MUTATION_CREATE.getKeyFunc().apply(it.getSchemaName(), qkey) + "(");
            out.println("    data: " + key + "InsertInput");
            out.println("  ): " + key);
            out.println();
            out.println("  \"" + i18n.getString("gql.mutation.update.comment", it.getSchemaName(), it.getOriginal().getName()) + "\"");
            out.println("  " + DataFetchers.MUTATION_UPDATE.getKeyFunc().apply(it.getSchemaName(), qkey) + "(");
            out.println("    set: " + key + "SetInput");
            out.println("    where: " + key + "BoolExp!");
            out.println("  ): MutationResponse");
        }
        out.println("}");

        // Model types
        out.println();
        for (var it : context.getModelClassList()) {
            String key = it.getShortClassName();
            out.println("  \"" + i18n.getString("gql.type.model.comment", it.getSchemaName(), it.getOriginal().getName()) + "\"");
            out.println("type " + key + " {");
            for (var f : it.getAllFields()) {
                out.println("  " + f.getName() + " : " + toGraphQLType(f, context));
            }
            out.println("  _join: Query");
            out.println("  _join_mutation: Mutation");
            out.println("}");
            out.println();
            out.println("type " + key + "Aggregate {");
            out.println("  _count(distinct: Boolean, field: " + key + "SelectField): Int!");
            out.println("  _max: " + key + "!");
            out.println("  _min: " + key + "!");
            out.println("  _sum: " + key + "!");
            out.println("  _avg: " + key + "AvgFields!");
            out.println("  _join: Query");
            out.println("  _join_mutation: Mutation");
            out.println("}");
            out.println();
            out.println("type " + key + "AvgFields {");
            for (var f : it.getAllFields()) {
                if (!f.isRelationField()) {
                    out.println("  " + f.getName() + ": Float");
                }
            }
            out.println("}");
            out.println();
        }

        // Input types
        for (var it : context.getModelClassList()) {
            String key = it.getShortClassName();
            out.println();
            out.println("\"" + i18n.getString("gql.bool_expr.comment", key) + "\"");
            out.println("input " + key + "BoolExp {");
            out.println("  and: [" + key + "BoolExp!]");
            out.println("  or: [" + key + "BoolExp!]");
            for (var f : it.getAllFields()) {
                if (!f.isRelationField() && !f.isEnumField()) {
                    TypedField<?, ?> tf = (TypedField<?, ?>) f.getOriginal();
                    out.println("  " + f.getName() + ": " + comparisonMapping.getOrDefault(tf.getType(), "StringComparisonExp"));
                } else if (f.isEnumField() && context.containsEnumClass(((EnumRefField) f.getOriginal()).getFrom())) {
                    EnumRefField enumField = (EnumRefField) f.getOriginal();
                    out.println("  " + f.getName() + ": " + StringUtils.capitalize(StringUtils.snakeToCamel(enumField.getFrom())) + "ComparisonExp");
                } else {
                    out.println("  " + f.getName() + ": StringComparisonExp");
                }
            }
            out.println("}");
            out.println();
            out.println("\"" + i18n.getString("gql.order_by.comment", key) + "\"");
            out.println("input " + key + "OrderBy {");
            for (var f : it.getAllFields()) {
                if (!f.isRelationField()) {
                    out.println("  " + f.getName() + ": OrderBy");
                }
            }
            out.println("}");
            out.println();
            out.println("input " + key + "InsertInput {");
            for (var f : it.getAllFields()) {
                if (!f.isRelationField()) {
                    out.println("  " + f.getName() + ": " + toGraphQLType(f, context));
                }
            }
            out.println("}");
            out.println();
            out.println("input " + key + "SetInput {");
            for (var f : it.getAllFields()) {
                if (!f.isRelationField()) {
                    out.println("  " + f.getName() + ": " + toGraphQLType(f, context));
                }
            }
            out.println("}");
        }

        // Select field enums
        for (var it : context.getModelClassList()) {
            String key = it.getShortClassName();
            out.println();
            out.println("enum " + key + "SelectField {");
            for (var f : it.getAllFields()) {
                if (!f.isRelationField()) {
                    out.println("  " + f.getName());
                }
            }
            out.println("}");
        }

        // Directives
        out.println();
        out.println("\"" + i18n.getString("gql.directive.internal.comment") + "\"");
        out.println("directive @internal on VARIABLE_DEFINITION");
        out.println("\"" + i18n.getString("gql.directive.export.comment") + "\"");
        out.println("directive @export(as: String) on FIELD");
        out.println("\"" + i18n.getString("gql.directive.transform.comment") + "\"");
        out.println("directive @transform(get: String!) on FIELD");
        out.println();

        // Scalars
        out.println("scalar JSON");
        out.println("scalar DateTime");
        out.println("scalar Date");
        out.println("scalar Time");

        // Comparison exp inputs
        out.println();
        writeComparisonExp(out, "Int", "Int", new String[]{"_eq", "_ne", "_gt", "_lt", "_gte", "_lte", "_in", "_nin"});
        writeComparisonExp(out, "Float", "Float", new String[]{"_eq", "_ne", "_gt", "_lt", "_gte", "_lte", "_in", "_nin"});
        writeComparisonExp(out, "String", "String", new String[]{"_eq", "_ne", "_in", "_nin", "_contains", "_not_contains", "_starts_with", "_ends_with"});
        writeComparisonExp(out, "JSON", "String", new String[]{"_eq", "_ne", "_in", "_nin", "_contains", "_not_contains", "_starts_with", "_ends_with"});
        writeComparisonExp(out, "Boolean", "Boolean", new String[]{"_eq", "_ne"});
        writeComparisonExp(out, "Date", "Date", new String[]{"_eq", "_ne", "_gt", "_lt", "_gte", "_lte", "_in", "_nin", "_between"});
        writeComparisonExp(out, "DateTime", "DateTime", new String[]{"_eq", "_ne", "_gt", "_lt", "_gte", "_lte", "_in", "_nin", "_between"});
        writeComparisonExp(out, "Time", "Time", new String[]{"_eq", "_ne", "_gt", "_lt", "_gte", "_lte", "_in", "_nin", "_between"});

        // Enum comparison exps
        for (var ec : context.getEnumClassList()) {
            out.println();
            out.println("\"" + i18n.getString("gql.comparison_exp.comment", "EnumRef") + "\"");
            out.println("input " + ec.getShortClassName() + "ComparisonExp {");
            out.println("  _eq: " + ec.getShortClassName());
            out.println("  _ne: " + ec.getShortClassName());
            out.println("  _in: [" + ec.getShortClassName() + "!]");
            out.println("  _nin: [" + ec.getShortClassName() + "!]");
            out.println("}");
            out.println();
        }

        // OrderBy enum
        out.println();
        out.println("\"" + i18n.getString("gql.enum.order_by.comment") + "\"");
        out.println("enum OrderBy {");
        out.println("    \"" + i18n.getString("gql.enum.order_by.asc.comment") + "\"");
        out.println("  asc");
        out.println("    \"" + i18n.getString("gql.enum.order_by.desc.comment") + "\"");
        out.println("  desc");
        out.println("}");

        // MutationResponse
        out.println();
        out.println("\"" + i18n.getString("gql.mutation.response.comment") + "\"");
        out.println("type MutationResponse {");
        out.println("  \"" + i18n.getString("gql.mutation.response.affected_rows.comment") + "\"");
        out.println("  affectedRows: Int!");
        out.println("  _join: Query");
        out.println("  _join_mutation: Mutation");
        out.println("}");
    }

    private void writeComparisonExp(PrintWriter out, String name, String fieldType, String[] operators) {
        out.println();
        out.println("\"" + i18n.getString("gql.comparison_exp.comment", name) + "\"");
        out.println("input " + name + "ComparisonExp {");
        for (String op : operators) {
            if (op.equals("_in") || op.equals("_nin") || op.equals("_between")) {
                out.println("  " + op + ": [" + fieldType + "!]");
            } else {
                out.println("  " + op + ": " + fieldType);
            }
        }
        out.println("}");
    }
}
